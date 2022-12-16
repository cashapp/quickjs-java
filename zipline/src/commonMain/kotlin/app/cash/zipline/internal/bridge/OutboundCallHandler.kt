/*
 * Copyright (C) 2022 Block, Inc.
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
package app.cash.zipline.internal.bridge

import app.cash.zipline.Call
import app.cash.zipline.CallResult
import app.cash.zipline.ZiplineFunction
import app.cash.zipline.ZiplineScope
import app.cash.zipline.ZiplineScoped
import app.cash.zipline.ZiplineService
import app.cash.zipline.internal.decodeFromStringFast
import kotlin.coroutines.Continuation
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Generated code uses this to make outbound calls.
 */
@PublishedApi
internal class OutboundCallHandler(
  private val serviceName: String,
  private val endpoint: Endpoint,
  private val functionsList: List<ZiplineFunction<*>>,
) {
  /** Used by generated code when closing a service. */
  var closed = false
    private set

  /** Used by generated code to call a function. */
  fun call(
    service: ZiplineService,
    scope: ZiplineScope,
    functionIndex: Int,
    vararg args: Any?,
  ): Any? {
    val function = functionsList[functionIndex] as ReturningZiplineFunction<*>
    if (function.isClose) {
      closed = true
      scope.remove(service)
    }
    val argsList = args.toList()
    val internalCall = InternalCall(
      serviceName = serviceName,
      function = function,
      args = argsList
    )
    val externalCall = endpoint.callCodec.encodeCall(internalCall, service)
    val callStart = when (service) {
      !is SuspendCallback<*> -> endpoint.eventListener.callStart(externalCall)
      else -> Unit // Don't call callStart() for suspend callbacks.
    }
    val encodedResult = endpoint.outboundChannel.call(externalCall.encodedCall)
    return endpoint.withTakeScope(scope) {
      val callResult = endpoint.callCodec.decodeResult(function, encodedResult)
      when (service) {
        !is SuspendCallback<*> -> {
          endpoint.eventListener.callEnd(externalCall, callResult, callStart)
        }
        else -> {
          Unit // Don't call callEnd() for suspend callbacks.
        }
      }
      return@withTakeScope callResult.result.getOrThrow()
    }
  }

  /** Used by generated code to call a suspending function. */
  suspend fun callSuspending(
    service: ZiplineService,
    scope: ZiplineScope,
    functionIndex: Int,
    vararg args: Any?,
  ): Any? {
    val function = functionsList[functionIndex] as SuspendingZiplineFunction<*>
    val argsList = args.toList()
    val suspendCallback = RealSuspendCallback<Any?>(scope)
    val internalCall = InternalCall(
      serviceName = serviceName,
      function = function,
      suspendCallback = suspendCallback,
      args = argsList,
    )
    val externalCall = endpoint.callCodec.encodeCall(internalCall, service)
    suspendCallback.internalCall = internalCall
    suspendCallback.externalCall = externalCall
    suspendCallback.callStart = endpoint.eventListener.callStart(externalCall)

    return suspendCancellableCoroutine { continuation ->
      suspendCallback.continuation = continuation
      endpoint.incompleteContinuations += continuation
      endpoint.scope.launch {
        val encodedCancelCallback = endpoint.outboundChannel.call(externalCall.encodedCall)
        val cancelCallback = endpoint.json.decodeFromStringFast(
          cancelCallbackSerializer,
          encodedCancelCallback,
        )

        continuation.invokeOnCancellation {
          endpoint.scope.launch {
            if (!suspendCallback.completed) {
              cancelCallback.cancel()
            }
          }
        }
      }
    }
  }

  private inner class RealSuspendCallback<R>(
    override val scope: ZiplineScope
  ) : SuspendCallback<R>, HasPassByReferenceName, ZiplineScoped {
    lateinit var internalCall: InternalCall
    lateinit var externalCall: Call
    lateinit var continuation: Continuation<R>
    var callStart: Any? = null

    override var passbyReferenceName: String? = null

    /** True once this has been called. Used to prevent cancel-after-complete. */
    var completed = false

    override fun success(result: R) {
      call(Result.success(result))
    }

    override fun failure(result: Throwable) {
      call(Result.failure(result))
    }

    private fun call(result: Result<R>) {
      val suspendCall = endpoint.callCodec.lastInboundCall!!
      val callResult = CallResult(result, suspendCall.encodedCall, suspendCall.serviceNames)
      completed = true

      // Suspend callbacks are one-shot. When triggered, remove them immediately.
      val name = passbyReferenceName
      if (name != null) endpoint.remove(name)
      endpoint.incompleteContinuations -= continuation

      endpoint.eventListener.callEnd(externalCall, callResult, callStart)
      continuation.resumeWith(result)
    }

    override fun toString() = "SuspendCallback/$internalCall"
  }

  override fun toString() = serviceName
}
