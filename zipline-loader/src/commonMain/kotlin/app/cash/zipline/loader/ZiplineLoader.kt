/*
 * Copyright (C) 2021 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package app.cash.zipline.loader

import app.cash.zipline.EventListener
import app.cash.zipline.Zipline
import app.cash.zipline.loader.fetcher.Fetcher
import app.cash.zipline.loader.fetcher.HttpFetcher
import app.cash.zipline.loader.fetcher.fetch
import app.cash.zipline.loader.receiver.FsSaveReceiver
import app.cash.zipline.loader.receiver.Receiver
import app.cash.zipline.loader.receiver.ZiplineLoadReceiver
import kotlin.random.Random
import kotlin.time.Duration
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString
import okio.FileSystem
import okio.Path

/**
 * Gets code from an HTTP server, or optional local cache
 * or embedded filesystem, and handles with a receiver
 * (by default, loads it into a zipline instance).
 *
 * Loader attempts to load code as quickly as possible with
 * concurrent network downloads and code loading.
 */
class ZiplineLoader(
  private val dispatcher: CoroutineDispatcher,
  private val serializersModule: SerializersModule,
  private val eventListener: EventListener,
  private val httpClient: ZiplineHttpClient,
  private val fetchers: List<Fetcher> = listOf(HttpFetcher(httpClient, eventListener)),
) {
  private var concurrentDownloadsSemaphore = Semaphore(3)
  var concurrentDownloads = 3
    set(value) {
      require(value > 0)
      field = value
      concurrentDownloadsSemaphore = Semaphore(value)
    }

  suspend fun loadOrFallBack(
    applicationId: String,
    serializersModule: SerializersModule,
    manifestUrl: String,
  ): Zipline {
    return try {
      val zipline = Zipline.create(
        dispatcher, serializersModule
      )
      load(applicationId, zipline, manifestUrl)
      zipline
    } catch (e: Exception) {
      val zipline = Zipline.create(
        dispatcher, serializersModule
      )
      loadByApplicationId(applicationId, zipline)
      zipline
    }
  }

  suspend fun loadContinuously(
    applicationId: String,
    manifestUrlFlow: Flow<String>,
    pollingInterval: Duration,
  ): Flow<Zipline> = manifestFlow(applicationId, manifestUrlFlow, pollingInterval)
    .mapNotNull { manifest ->
      val zipline = Zipline.create(dispatcher, serializersModule, eventListener)
      load(applicationId, zipline, manifest)
      zipline
    }

  /** Load application into Zipline without fallback on failure functionality */
  suspend fun load(applicationId: String, zipline: Zipline, manifestUrl: String) =
    load(applicationId, zipline, fetchZiplineManifest(applicationId, manifestUrl, false))

  suspend fun load(
    applicationId: String,
    zipline: Zipline,
    manifest: ZiplineManifest,
  ) = receive(applicationId, ZiplineLoadReceiver(zipline), manifest)

  suspend fun download(
    applicationId: String,
    downloadDir: Path,
    downloadFileSystem: FileSystem,
    manifestUrl: String,
  ) {
    download(
      applicationId, downloadDir, downloadFileSystem,
      fetchZiplineManifest(applicationId, manifestUrl, false)
    )
  }

  suspend fun download(
    applicationId: String,
    downloadDir: Path,
    downloadFileSystem: FileSystem,
    manifest: ZiplineManifest,
  ) {
    writeManifestToDisk(applicationId, downloadFileSystem, downloadDir, manifest)
    receive(
      applicationId = applicationId,
      receiver = FsSaveReceiver(downloadFileSystem, downloadDir),
      manifest = manifest
    )
  }

  suspend fun loadByApplicationId(
    applicationId: String,
    zipline: Zipline,
  ) = load(applicationId, zipline, fetchZiplineManifest(applicationId, "", true))

  /**
   * Returns a manifest equivalent to [this], but with module URLs resolved against [baseUrl]. This
   * way consumers of the manifest don't need to know the URL that the manifest was downloaded from.
   */
  private fun ZiplineManifest.resolveUrls(baseUrl: String): ZiplineManifest {
    return copy(
      modules = modules.mapValues { (_, module) ->
        module.copy(url = httpClient.resolve(baseUrl, module.url))
      }
    )
  }

  /**
   * Continuously downloads [urlFlow] and emits when it changes.
   *
   * TODO(jwilson): use a web socket instead of polling every 500ms.
   */
  private suspend fun manifestFlow(
    applicationId: String,
    urlFlow: Flow<String>,
    pollingInterval: Duration?,
  ): Flow<ZiplineManifest> {
    val rebounced = when {
      pollingInterval != null -> urlFlow.rebounce(pollingInterval)
      else -> urlFlow
    }

    return rebounced.mapNotNull { url ->
      fetchZiplineManifest(applicationId, url, false)
    }.distinctUntilChanged()
  }

  private suspend fun receive(
    applicationId: String,
    receiver: Receiver,
    manifest: ZiplineManifest,
  ) {
    coroutineScope {
      val loads = manifest.modules.map {
        ModuleJob(applicationId, it.key, it.value, receiver)
      }
      for (load in loads) {
        val loadJob = launch { load.run() }

        val downstreams = loads.filter { load.id in it.module.dependsOnIds }
        for (downstream in downstreams) {
          downstream.upstreams += loadJob
        }
      }
    }
  }

  private inner class ModuleJob(
    val applicationId: String,
    val id: String,
    val module: ZiplineModule,
    val receiver: Receiver,
  ) {
    val upstreams = mutableListOf<Job>()

    /**
     * Fetch and receive ZiplineFile module
     */
    suspend fun run() {
      val byteString = fetchers
        .fetch(
          concurrentDownloadsSemaphore = concurrentDownloadsSemaphore,
          applicationId = applicationId,
          id = id,
          sha256 = module.sha256,
          url = module.url,
        )

      upstreams.joinAll()
      withContext(dispatcher) {
        receiver.receive(byteString, id, module.sha256)
      }
    }
  }

  private suspend fun fetchZiplineManifest(
    applicationId: String,
    manifestUrl: String,
    fallbackOnFailure: Boolean,
  ): ZiplineManifest {
    val manifestForApplicationId = if (fallbackOnFailure) {
      applicationId
    } else {
      null
    }
    val manifestByteString = fetchers.fetch(
      concurrentDownloadsSemaphore = concurrentDownloadsSemaphore,
      applicationId = applicationId,
      id = getApplicationManifestFileName(applicationId)!!,
      // sha256 is set to random for the manifest since we don't know what the real sha256 is when
      //   we download from the network, a random sha256 won't have a false positive cache or
      //   embedded fetch hit.
      sha256 = Random.Default.nextBytes(64).toByteString(),
      url = manifestUrl,
      manifestForApplicationId = manifestForApplicationId
    )

    val manifest = try {
      Json.decodeFromString(ZiplineManifest.serializer(), manifestByteString.utf8())
    } catch (e: Exception) {
      eventListener.manifestParseFailed(applicationId, manifestUrl, e)
      throw e
    }

    return httpClient.resolveUrls(manifest, manifestUrl)
  }

  private fun writeManifestToDisk(
    applicationId: String,
    fileSystem: FileSystem,
    dir: Path,
    manifest: ZiplineManifest,
  ) {
    fileSystem.createDirectories(dir)
    fileSystem.write(dir / getApplicationManifestFileName(applicationId)!!) {
      write(Json.encodeToString(manifest).encodeUtf8())
    }
  }

  companion object {
    const val APPLICATION_MANIFEST_FILE_NAME_SUFFIX = "manifest.zipline.json"
    internal fun getApplicationManifestFileName(manifestForApplicationId: String?) =
      manifestForApplicationId?.let { "$it.$APPLICATION_MANIFEST_FILE_NAME_SUFFIX" }
  }
}
