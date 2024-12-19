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
package app.cash.zipline

import app.cash.zipline.internal.ByteStringAsHexSerializer
import app.cash.zipline.internal.MANIFEST_MAX_SIZE
import app.cash.zipline.internal.isTopologicallySorted
import app.cash.zipline.internal.jsonForManifest
import app.cash.zipline.internal.signaturePayload
import app.cash.zipline.internal.topologicalSort
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8

/**
 * Describes a Zipline application that can be loaded and executed.
 *
 * Use [ZiplineManifest.create] to create instances.
 */
@Serializable
@ExposedCopyVisibility // For binary compatibility.
data class ZiplineManifest private constructor(
  /** Metadata on this manifest that isn't authenticated by a signature. */
  val unsigned: Unsigned = Unsigned(),

  /** This is an ordered map; its modules are always topologically sorted. */
  val modules: Map<String, Module> = mapOf(),

  /**
   * JS module ID for the application (ie. "./alpha-app.js").
   * This will usually be the last module in the manifest once it is topologically sorted.
   */
  val mainModuleId: String,

  /** Fully qualified main function to start the application (ie. "zipline.ziplineMain"). */
  val mainFunction: String? = null,

  /** Version to represent the code as defined in this manifest */
  val version: String? = null,

  /**
   * Arbitrary application-layer metadata about this release. Use this to embed build or diagnostic
   * metadata, such as the timestamp that this application was built.
   */
  val metadata: Map<String, String> = mapOf(),
) {
  init {
    require(modules.keys.toList().isTopologicallySorted { id -> modules[id]!!.dependsOnIds }) {
      "Modules are not topologically sorted and can not be loaded"
    }
  }

  /**
   * Properties of this manifest not authenticated by a signature. We prefer to define properties
   * in the top-level manifest wherever possible.
   */
  @Serializable
  data class Unsigned(
    /**
     * A manifest may include many signatures, in order of preference. The keys of the map are the
     * signing key names. The values of the map are hex-encoded signatures.
     *
     * This is unsigned to solve a chicken-egg problem: we can't sign the output of the signature.
     */
    val signatures: Map<String, String> = mapOf(),

    /**
     * The newest timestamp that this manifest is known to be fresh. Typically, a manifest is fresh
     * at the moment it is downloaded. If this field is null the caller should determine freshness
     * independently.
     *
     * This is unsigned so that embedded manifests may be updated to track the time they were
     * downloaded at.
     */
    val freshAtEpochMs: Long? = null,

    /**
     * Optional URL to resolve module URLs against when downloading. If null, module URLs are
     * relative to the URL that this manifest was loaded from.
     *
     * This is unsigned so that cached manifests may be updated to track the URL that they were
     * originally fetched from.
     */
    val baseUrl: String? = null,
  )

  @Serializable
  data class Module(
    /** This may be an absolute URL, or relative to an enclosing manifest. */
    val url: String,
    @Serializable(with = ByteStringAsHexSerializer::class)
    val sha256: ByteString,
    val dependsOnIds: List<String> = listOf(),
  )

  val signatures: Map<String, String>
    get() = unsigned.signatures
  val freshAtEpochMs: Long?
    get() = unsigned.freshAtEpochMs
  val baseUrl: String?
    get() = unsigned.baseUrl

  @Deprecated(
    message = "This is here for binary-compatibility only",
    level = DeprecationLevel.HIDDEN,
  )
  fun copy(
    signatures: Map<String, String> = this.signatures,
    freshAtEpochMs: Long? = this.freshAtEpochMs,
    baseUrl: String? = this.baseUrl,
    modules: Map<String, Module> = this.modules,
    mainModuleId: String = this.mainModuleId,
    mainFunction: String? = this.mainFunction,
    version: String? = this.version,
  ): ZiplineManifest = copy(
    unsigned = unsigned.copy(
      freshAtEpochMs = freshAtEpochMs,
      signatures = signatures,
      baseUrl = baseUrl,
    ),
    modules = modules,
    mainModuleId = mainModuleId,
    mainFunction = mainFunction,
    version = version,
    metadata = this.metadata,
  )

  @Deprecated(
    message = "This is here for binary-compatibility only",
    level = DeprecationLevel.HIDDEN,
  )
  fun copy(
    unsigned: Unsigned = this.unsigned,
    modules: Map<String, Module> = this.modules,
    mainModuleId: String = this.mainModuleId,
    mainFunction: String? = this.mainFunction,
    version: String? = this.version,
  ): ZiplineManifest = copy(
    unsigned = unsigned,
    modules = modules,
    mainModuleId = mainModuleId,
    mainFunction = mainFunction,
    version = version,
    metadata = this.metadata,
  )

  fun copy(
    signatures: Map<String, String> = this.signatures,
    freshAtEpochMs: Long? = this.freshAtEpochMs,
    baseUrl: String? = this.baseUrl,
    modules: Map<String, Module> = this.modules,
    mainModuleId: String = this.mainModuleId,
    mainFunction: String? = this.mainFunction,
    version: String? = this.version,
    metadata: Map<String, String> = this.metadata,
  ): ZiplineManifest = copy(
    unsigned = unsigned.copy(
      freshAtEpochMs = freshAtEpochMs,
      signatures = signatures,
      baseUrl = baseUrl,
    ),
    modules = modules,
    mainModuleId = mainModuleId,
    mainFunction = mainFunction,
    version = version,
    // Defensive copy!
    metadata = metadata.toMap(),
  )

  /**
   * Returns a byte string representation of this manifest appropriate for signing and signature
   * verification. The encoding omits [data not covered by signing][unsigned] and is
   * deterministically-encoded.
   *
   * Use this to sign a manifest without [ManifestSigner], such as when signing with a hardware
   * security module. Create a manifest from those externally-generated signatures with [copy].
   */
  val signaturePayload: ByteString
    get() {
      val signaturePayloadString = signaturePayload(encodeJson())
      return signaturePayloadString.encodeUtf8()
    }

  fun encodeJson(): String {
    val result = jsonForManifest.encodeToString(this)
    check(result.length <= MANIFEST_MAX_SIZE) {
      "manifest larger than $MANIFEST_MAX_SIZE: ${result.length}"
    }
    return result
  }

  companion object {
    @Deprecated(
      message = "This is here for binary-compatibility only",
      level = DeprecationLevel.HIDDEN,
    )
    fun create(
      modules: Map<String, Module>,
      mainFunction: String? = null,
      mainModuleId: String? = null,
      version: String? = null,
      builtAtEpochMs: Long? = null,
      baseUrl: String? = null,
    ): ZiplineManifest {
      return create(
        modules = modules,
        mainFunction = mainFunction,
        mainModuleId = mainModuleId,
        version = version,
        builtAtEpochMs = builtAtEpochMs,
        baseUrl = baseUrl,
        metadata = mapOf(),
      )
    }

    fun create(
      modules: Map<String, Module>,
      mainFunction: String? = null,
      mainModuleId: String? = null,
      version: String? = null,
      builtAtEpochMs: Long? = null,
      baseUrl: String? = null,
      metadata: Map<String, String> = mapOf(),
    ): ZiplineManifest {
      val sortedModuleIds = modules.keys
        .toList()
        .topologicalSort { id ->
          modules[id]?.dependsOnIds
            ?: throw IllegalArgumentException("Unexpected [id=$id] is not found in modules keys")
        }
      return ZiplineManifest(
        unsigned = Unsigned(
          signatures = mapOf(),
          freshAtEpochMs = builtAtEpochMs,
          baseUrl = baseUrl,
        ),
        modules = sortedModuleIds.associateWith { id ->
          modules[id]
            ?: throw IllegalArgumentException("Unexpected [id=$id] is not found in modules keys")
        },
        mainModuleId = mainModuleId ?: sortedModuleIds.last(),
        mainFunction = mainFunction,
        version = version,
        // Defensive copy!
        metadata = metadata.toMap(),
      )
    }

    fun decodeJson(s: String): ZiplineManifest {
      check(s.length <= MANIFEST_MAX_SIZE) {
        "manifest larger than $MANIFEST_MAX_SIZE: ${s.length}"
      }
      return jsonForManifest.decodeFromString(s)
    }
  }
}
