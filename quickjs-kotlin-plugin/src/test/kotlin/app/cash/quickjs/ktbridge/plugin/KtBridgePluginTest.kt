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

package app.cash.quickjs.ktbridge.plugin

import app.cash.quickjs.KtBridge
import app.cash.quickjs.testing.EchoRequest
import app.cash.quickjs.testing.EchoResponse
import app.cash.quickjs.testing.EchoService
import app.cash.quickjs.testing.GenericEchoService
import app.cash.quickjs.testing.KtBridgePair
import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import kotlin.test.assertEquals
import org.jetbrains.kotlin.compiler.plugin.ComponentRegistrar
import org.junit.Test

/**
 * Confirm bridge calls are rewritten to use `OutboundClientFactory` or `InboundService` as
 * appropriate.
 */
class KtBridgePluginTest {
  @Test
  fun `ktBridge set rewritten to receive inbound calls`() {
    val result = compile(
      sourceFile = SourceFile.kotlin(
        "main.kt",
        """
        package app.cash.quickjs.testing
        
        import app.cash.quickjs.KtBridge
        
        class TestingEchoService(
          private val greeting: String
        ) : EchoService {
          override fun echo(request: EchoRequest): EchoResponse {
            return EchoResponse("${'$'}greeting from the compiler plugin, ${'$'}{request.message}")
          }
        }
        
        fun prepareJsBridges(ktBridge: KtBridge) {
          ktBridge.set<EchoService>("helloService", EchoJsAdapter, TestingEchoService("hello"))
        }
        """
      )
    )
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

    val bridges = KtBridgePair()
    val mainKt = result.classLoader.loadClass("app.cash.quickjs.testing.MainKt")
    mainKt.getDeclaredMethod("prepareJsBridges", KtBridge::class.java).invoke(null, bridges.a)

    val helloService = KtBridgeTestInternals.getEchoClient(bridges.b, "helloService")
    assertThat(helloService.echo(EchoRequest("Jesse")))
      .isEqualTo(EchoResponse("hello from the compiler plugin, Jesse"))
  }

  @Test
  fun `ktBridge set rewritten to make outbound calls`() {
    val result = compile(
      sourceFile = SourceFile.kotlin(
        "main.kt",
        """
        package app.cash.quickjs.testing
        
        import app.cash.quickjs.KtBridge
        
        fun getHelloService(ktBridge: KtBridge): EchoService {
          return ktBridge.get("helloService", EchoJsAdapter)
        }
        """
      )
    )
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

    val bridges = KtBridgePair()

    val testingEchoService = object : EchoService {
      override fun echo(request: EchoRequest): EchoResponse {
        return EchoResponse("greetings from the compiler plugin, ${request.message}")
      }
    }
    KtBridgeTestInternals.setEchoService(bridges.b, "helloService", testingEchoService)

    val mainKt = result.classLoader.loadClass("app.cash.quickjs.testing.MainKt")
    val helloService = mainKt.getDeclaredMethod("getHelloService", KtBridge::class.java)
      .invoke(null, bridges.a) as EchoService

    assertThat(helloService.echo(EchoRequest("Jesse")))
      .isEqualTo(EchoResponse("greetings from the compiler plugin, Jesse"))
  }

  @Test
  fun `ktBridge set type argument is not an interface`() {
    val result = compile(
      sourceFile = SourceFile.kotlin(
        "main.kt",
        """
        package app.cash.quickjs.testing
        
        import app.cash.quickjs.KtBridge
        
        fun prepareJsBridges(ktBridge: KtBridge) {
          ktBridge.set<TestingEchoService>("helloService", EchoJsAdapter, TestingEchoService)
        }

        object TestingEchoService : EchoService {
          override fun echo(request: EchoRequest): EchoResponse = error("")
        }
        """
      )
    )
    assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode, result.messages)
    assertThat(result.messages)
      .contains("(6, 12): The type argument to KtBridge.set() must be an interface type")
  }

  @Test
  fun `ktBridge get type argument is not an interface`() {
    val result = compile(
      sourceFile = SourceFile.kotlin(
        "main.kt",
        """
        package app.cash.quickjs.testing
        
        import app.cash.quickjs.KtBridge
        
        fun getHelloService(ktBridge: KtBridge): String {
          return ktBridge.get("helloService", EchoJsAdapter)
        }
        """
      )
    )
    assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode, result.messages)
    assertThat(result.messages)
      .contains("(6, 19): The type argument to KtBridge.get() must be an interface type")
  }

  @Test
  fun `generic service`() {
    val result = compile(
      sourceFile = SourceFile.kotlin(
        "main.kt",
        """
        package app.cash.quickjs.testing
        
        import app.cash.quickjs.KtBridge
        
        class TestingGenericEchoService : GenericEchoService<String> {
          override fun genericEcho(request: String): List<String> {
            return listOf("received a generic ${'$'}request!")
          }
        }
        
        fun prepareJsBridges(ktBridge: KtBridge) {
          ktBridge.set<GenericEchoService<String>>(
            "genericService",
            GenericJsAdapter,
            TestingGenericEchoService()
          )
        }
        """
      )
    )
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

    val bridges = KtBridgePair()
    val mainKt = result.classLoader.loadClass("app.cash.quickjs.testing.MainKt")
    mainKt.getDeclaredMethod("prepareJsBridges", KtBridge::class.java).invoke(null, bridges.a)

    val helloService = KtBridgeTestInternals.getGenericEchoService(bridges.b, "genericService")
    assertThat(helloService.genericEcho("Jesse")).containsExactly("received a generic Jesse!")
  }

  @Test
  fun `generic client`() {
    val result = compile(
      sourceFile = SourceFile.kotlin(
        "main.kt",
        """
        package app.cash.quickjs.testing
        
        import app.cash.quickjs.KtBridge
        
        fun getGenericService(ktBridge: KtBridge): GenericEchoService<String> {
          return ktBridge.get("genericService", GenericJsAdapter)
        }
        """
      )
    )
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

    val bridges = KtBridgePair()

    val testingService = object : GenericEchoService<String> {
      override fun genericEcho(request: String): List<String> {
        return listOf("received a generic $request!")
      }
    }
    KtBridgeTestInternals.setGenericEchoService(bridges.b, "genericService", testingService)

    val mainKt = result.classLoader.loadClass("app.cash.quickjs.testing.MainKt")
    val service = mainKt.getDeclaredMethod("getGenericService", KtBridge::class.java)
      .invoke(null, bridges.a) as GenericEchoService<String>

    assertThat(service.genericEcho("Jesse"))
      .containsExactly("received a generic Jesse!")
  }
}

fun compile(
  sourceFiles: List<SourceFile>,
  plugin: ComponentRegistrar = KtBridgeComponentRegistrar(),
): KotlinCompilation.Result {
  return KotlinCompilation().apply {
    sources = sourceFiles
    useIR = true
    compilerPlugins = listOf(plugin)
    inheritClassPath = true
  }.compile()
}

fun compile(
  sourceFile: SourceFile,
  plugin: ComponentRegistrar = KtBridgeComponentRegistrar(),
): KotlinCompilation.Result {
  return compile(listOf(sourceFile), plugin)
}
