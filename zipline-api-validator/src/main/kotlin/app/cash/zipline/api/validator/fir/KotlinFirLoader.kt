/*
 * Copyright (C) 2023 Cash App
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
package app.cash.zipline.api.validator.fir

import java.io.File
import org.jetbrains.kotlin.KtVirtualFileSourceFile
import org.jetbrains.kotlin.cli.common.GroupedKtSources
import org.jetbrains.kotlin.cli.common.config.addKotlinSourceRoots
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity.ERROR
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity.EXCEPTION
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity.LOGGING
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.cli.jvm.compiler.VfsBasedProjectEnvironment
import org.jetbrains.kotlin.cli.jvm.compiler.pipeline.IncrementalCompilationApi
import org.jetbrains.kotlin.cli.jvm.compiler.pipeline.ModuleCompilerInput
import org.jetbrains.kotlin.cli.jvm.compiler.pipeline.compileModuleToAnalyzedFirViaLightTreeIncrementally
import org.jetbrains.kotlin.cli.jvm.config.addJvmClasspathRoots
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.com.intellij.openapi.vfs.StandardFileSystems
import org.jetbrains.kotlin.com.intellij.openapi.vfs.VirtualFileManager
import org.jetbrains.kotlin.com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.JVMConfigurationKeys
import org.jetbrains.kotlin.diagnostics.DiagnosticReporterFactory
import org.jetbrains.kotlin.fir.pipeline.FirResult
import org.jetbrains.kotlin.metadata.jvm.deserialization.JvmProtoBufUtil
import org.jetbrains.kotlin.modules.TargetId

/**
 * Loads classes using the compiler tools into the Frontend Intermediate Representation (FIR), so
 * they can be inspected.
 *
 * Note that this class is copied from Redwood.
 * https://github.com/cashapp/redwood/blob/afe1c9f5f95eec3cff46837a4b2749cbaf72af8b/redwood-tooling-schema/src/main/kotlin/app/cash/redwood/tooling/schema/schemaParserFir.kt#L28
 */
internal class KotlinFirLoader(
  private val javaHome: File,
  private val jdkRelease: Int,
  private val sources: Collection<File>,
  private val classpath: Collection<File>,
) : AutoCloseable {
  private val disposable = Disposer.newDisposable()

  private val messageCollector = object : MessageCollector {
    override fun clear() = Unit
    override fun hasErrors() = false

    override fun report(
      severity: CompilerMessageSeverity,
      message: String,
      location: CompilerMessageSourceLocation?,
    ) {
      val destination = when (severity) {
        LOGGING -> null
        EXCEPTION, ERROR -> System.err
        else -> System.out
      }
      destination?.println(message)
    }
  }

  /**
   * @param targetName an opaque identifier for this operation.
   */
  fun load(targetName: String): FirResult {
    val configuration = CompilerConfiguration()
    configuration.put(CommonConfigurationKeys.MODULE_NAME, targetName)
    configuration.put(CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY, messageCollector)
    configuration.put(CommonConfigurationKeys.USE_FIR, true)
    configuration.put(JVMConfigurationKeys.JDK_HOME, javaHome)
    configuration.put(JVMConfigurationKeys.JDK_RELEASE, jdkRelease)
    configuration.addKotlinSourceRoots(sources.map { it.absolutePath })
    configuration.addJvmClasspathRoots(classpath.toList())

    val environment = KotlinCoreEnvironment.createForProduction(
      disposable,
      configuration,
      EnvironmentConfigFiles.JVM_CONFIG_FILES,
    )
    val project = environment.project

    val localFileSystem = VirtualFileManager.getInstance().getFileSystem(
      StandardFileSystems.FILE_PROTOCOL,
    )
    val files = buildList {
      for (source in sources) {
        source.walkTopDown().filter { it.isFile }.forEach {
          this += localFileSystem.findFileByPath(it.absolutePath)!!
        }
      }
    }

    val sourceFiles = files.mapTo(mutableSetOf(), ::KtVirtualFileSourceFile)
    val input = ModuleCompilerInput(
      targetId = TargetId(JvmProtoBufUtil.DEFAULT_MODULE_NAME, targetName),
      groupedSources = GroupedKtSources(
        platformSources = sourceFiles,
        commonSources = emptyList(),
        sourcesByModuleName = mapOf(JvmProtoBufUtil.DEFAULT_MODULE_NAME to sourceFiles),
      ),
      configuration = configuration,
    )

    val reporter = DiagnosticReporterFactory.createReporter(messageCollector)

    val globalScope = GlobalSearchScope.allScope(project)
    val packagePartProvider = environment.createPackagePartProvider(globalScope)
    val projectEnvironment = VfsBasedProjectEnvironment(
      project = project,
      localFileSystem = localFileSystem,
      getPackagePartProviderFn = { packagePartProvider },
    )

    /*
        projectEnvironment: VfsBasedProjectEnvironment,
    messageCollector: MessageCollector,
    compilerConfiguration: CompilerConfiguration,
    input: ModuleCompilerInput,
    diagnosticsReporter: BaseDiagnosticsCollector,
    incrementalExcludesScope: AbstractProjectFileSearchScope?,
     */
    @OptIn(IncrementalCompilationApi::class) // We are not within the Kotlin compiler.
    return compileModuleToAnalyzedFirViaLightTreeIncrementally(
      projectEnvironment,
      messageCollector,
      configuration,
      input,
      reporter,
      null,
    )
  }

  override fun close() {
    disposable.dispose()
  }
}
