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

import app.cash.zipline.internal.HostPlatform
import app.cash.zipline.internal.JsPlatform
import app.cash.zipline.internal.bridge.CallChannel
import app.cash.zipline.internal.bridge.Endpoint
import app.cash.zipline.internal.bridge.InboundBridge
import app.cash.zipline.internal.bridge.OutboundBridge
import java.io.Closeable
import java.util.logging.Logger
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule

actual class Zipline private constructor(
  val quickJs: QuickJs,
  dispatcher: CoroutineDispatcher,
) : Closeable {
  private val endpoint = Endpoint(
    dispatcher = dispatcher,
    outboundChannel = object : CallChannel {
      /** Lazily fetch the channel to call into JS. */
      private val jsInboundBridge: CallChannel by lazy(mode = LazyThreadSafetyMode.NONE) {
        quickJs.get(
          name = "app_cash_zipline_inboundChannel",
          type = CallChannel::class.java
        )
      }

      override fun serviceNamesArray(): Array<String> {
        return jsInboundBridge.serviceNamesArray()
      }

      override fun invoke(
        instanceName: String,
        funName: String,
        encodedArguments: Array<String>
      ): Array<String> {
        check(!closed) { "Zipline closed" }
        return jsInboundBridge.invoke(instanceName, funName, encodedArguments)
      }

      override fun invokeSuspending(
        instanceName: String,
        funName: String,
        encodedArguments: Array<String>,
        callbackName: String
      ) {
        check(!closed) { "Zipline closed" }
        return jsInboundBridge.invokeSuspending(instanceName, funName, encodedArguments, callbackName)
      }

      override fun disconnect(instanceName: String): Boolean {
        return jsInboundBridge.disconnect(instanceName)
      }
    }
  )

  actual val serializersModule: SerializersModule
    get() = endpoint.userSerializersModule!!

  actual val engineVersion: String
    get() = quickJsVersion

  actual val serviceNames: Set<String>
    get() = endpoint.serviceNames

  actual val clientNames: Set<String>
    get() = endpoint.clientNames

  private var closed = false

  init {
    // Eagerly publish the channel so they can call us.
    quickJs.set(
      name = "app_cash_zipline_outboundChannel",
      type = CallChannel::class.java,
      instance = endpoint.inboundChannel
    )

    // Connect platforms using our newly-bootstrapped channels.
    val jsPlatform = endpoint.get<JsPlatform>(
      name = "zipline/js"
    )
    endpoint.set<HostPlatform>(
      name = "zipline/host",
      instance = RealHostPlatform(dispatcher, jsPlatform)
    )
  }

  actual fun <T : Any> get(name: String): T {
    error("unexpected call to Zipline.get: is the Zipline plugin configured?")
  }

  @PublishedApi
  internal fun <T : Any> get(name: String, bridge: OutboundBridge<T>): T {
    check(!closed) { "closed" }
    return endpoint.get(name, bridge)
  }

  actual fun <T : Any> set(name: String, instance: T) {
    error("unexpected call to Zipline.set: is the Zipline plugin configured?")
  }

  @PublishedApi
  internal fun <T : Any> set(name: String, bridge: InboundBridge<T>) {
    check(!closed) { "closed" }
    endpoint.set(name, bridge)
  }

  /**
   * Release resources held by this instance. It is an error to do any of the following after
   * calling close:
   *
   *  * Call [get] or [set].
   *  * Accessing [quickJs].
   *  * Accessing the objects returned from [get].
   */
  override fun close() {
    closed = true
    quickJs.close()
  }

  companion object {
    fun create(
      dispatcher: CoroutineDispatcher,
      serializersModule: SerializersModule = EmptySerializersModule
    ): Zipline {
      val quickJs = QuickJs.create()
      // TODO(jwilson): figure out a 512 KiB limit caused intermittent stack overflow failures.
      quickJs.maxStackSize = 0L
      return Zipline(quickJs, dispatcher)
        .apply {
          endpoint.userSerializersModule = serializersModule
        }
    }
  }
}

private class RealHostPlatform(
  val dispatcher: CoroutineDispatcher,
  val jsPlatform: JsPlatform,
) : HostPlatform {
  private val logger = Logger.getLogger(Zipline::class.qualifiedName)

  override fun setTimeout(timeoutId: Int, delayMillis: Int) {
    CoroutineScope(EmptyCoroutineContext).launch(dispatcher) {
      delay(delayMillis.toLong())
      jsPlatform.runJob(timeoutId)
    }
  }

  override fun consoleMessage(level: String, message: String) {
    when (level) {
      "warn" -> logger.warning(message)
      "error" -> logger.severe(message)
      else -> logger.info(message)
    }
  }
}
