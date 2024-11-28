import com.android.build.gradle.BaseExtension
import com.diffplug.gradle.spotless.SpotlessExtension
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import com.vanniktech.maven.publish.SonatypeHost
import java.net.URI
import java.net.URL
import kotlinx.validation.ApiValidationExtension
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.dokka.DokkaConfiguration.Visibility
import org.jetbrains.dokka.gradle.AbstractDokkaTask
import org.jetbrains.dokka.gradle.DokkaMultiModuleTask
import org.jetbrains.dokka.gradle.DokkaTaskPartial
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.targets.js.testing.KotlinJsTest
import org.jetbrains.kotlin.gradle.targets.jvm.tasks.KotlinJvmTest
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeTest
import org.jetbrains.kotlin.gradle.tasks.CInteropProcess

buildscript {
  repositories {
    mavenCentral()
    google()
    gradlePluginPortal()
  }
  dependencies {
    classpath(libs.android.gradle.plugin)
    classpath(libs.binary.compatibility.validator.gradle.plugin)
    classpath(libs.mavenPublish.gradle.plugin)
    classpath(libs.kotlin.gradle.plugin)
    classpath(libs.kotlin.serialization)
    classpath(libs.dokka.gradle.plugin)
    classpath(libs.shadowJar.gradle.plugin)
    classpath(libs.cklib.gradle.plugin)
    classpath(libs.sqldelight.gradle.plugin)
    classpath(libs.google.ksp)
  }
}

plugins {
  id("com.github.gmazzo.buildconfig") version "5.5.1" apply false
  alias(libs.plugins.spotless)
}

apply(plugin = "org.jetbrains.dokka")

apply(plugin = "com.vanniktech.maven.publish.base")

configure<SpotlessExtension> {
  kotlin {
    target("**/*.kt")
    ktlint()
      .editorConfigOverride(
        mapOf(
          "ktlint_standard_comment-spacing" to "disabled", // TODO Re-enable
          "ktlint_standard_filename" to "disabled",
          "ktlint_standard_indent" to "disabled", // TODO Re-enable
        )
      )
  }
}

allprojects {
  group = "app.cash.zipline"
  version = project.property("VERSION_NAME") as String

  repositories {
    mavenCentral()
    google()
  }
}

subprojects {
  plugins.withId("com.android.library") {
    extensions.configure<BaseExtension> {
      lintOptions {
        textReport = true
        textOutput("stdout")
        lintConfig = rootProject.file("lint.xml")

        isCheckDependencies = true
        isCheckTestSources = false // TODO true https://issuetracker.google.com/issues/138247523
        isExplainIssues = false

        // We run a full lint analysis as build part in CI, so skip vital checks for assemble task.
        isCheckReleaseBuilds = false
      }
    }
  }

  tasks.withType(Test::class).configureEach {
    // https://github.com/cashapp/zipline/issues/848
    jvmArgs = jvmArgs!! + "-Xss2048k"

    testLogging {
      if (System.getenv("CI") == "true") {
        events = setOf(TestLogEvent.STARTED, TestLogEvent.FAILED, TestLogEvent.SKIPPED, TestLogEvent.PASSED)
      }
      exceptionFormat = TestExceptionFormat.FULL
    }
  }
}

tasks.named("dokkaHtmlMultiModule", DokkaMultiModuleTask::class.java).configure {
  moduleName.set("Zipline")
}

allprojects {
  tasks.withType<DokkaTaskPartial>().configureEach {
    dokkaSourceSets.configureEach {
      documentedVisibilities.set(setOf(
        Visibility.PUBLIC,
        Visibility.PROTECTED
      ))
      reportUndocumented.set(false)
      jdkVersion.set(11)

      perPackageOption {
        matchingRegex.set("app\\.cash\\.zipline\\.internal\\..*")
        suppress.set(true)
      }
      perPackageOption {
        matchingRegex.set("app\\.cash\\.zipline\\.loader\\.internal\\..*")
        suppress.set(true)
      }

      sourceLink {
        localDirectory.set(rootProject.projectDir)
        remoteUrl.set(URL("https://github.com/cashapp/zipline/tree/trunk/"))
        remoteLineSuffix.set("#L")
      }
    }
  }

  // Workaround for https://github.com/Kotlin/dokka/issues/2977.
  // We disable the C Interop IDE metadata task when generating documentation using Dokka.
  tasks.withType<AbstractDokkaTask> {
    @Suppress("UNCHECKED_CAST")
    val taskClass = Class.forName("org.jetbrains.kotlin.gradle.targets.native.internal.CInteropMetadataDependencyTransformationTask") as Class<Task>
    parent?.subprojects?.forEach {
      dependsOn(it.tasks.withType(taskClass))
    }
  }

  // Don't attempt to sign anything if we don't have an in-memory key. Otherwise, the 'build' task
  // triggers 'signJsPublication' even when we aren't publishing (and so don't have signing keys).
  tasks.withType<Sign>().configureEach {
    enabled = project.findProperty("signingInMemoryKey") != null
  }

  plugins.withId("org.jetbrains.kotlin.multiplatform") {
    configure<KotlinMultiplatformExtension> {
      jvmToolchain(11)
      @Suppress("OPT_IN_USAGE")
      compilerOptions {
        freeCompilerArgs.addAll("-opt-in=app.cash.zipline.EngineApi")
      }
      // https://youtrack.jetbrains.com/issue/KT-61573
      targets.configureEach {
        compilations.configureEach {
          compilerOptions.configure {
            freeCompilerArgs.addAll("-Xexpect-actual-classes")
          }
        }
      }
    }
  }

  plugins.withId("org.jetbrains.kotlin.jvm") {
    configure<KotlinJvmProjectExtension> {
      jvmToolchain(11)
    }
  }

  plugins.withId("com.vanniktech.maven.publish.base") {
    configure<PublishingExtension> {
      repositories {
        maven {
          name = "testMaven"
          url = rootProject.layout.buildDirectory.dir("testMaven").get().asFile.toURI()
        }

        /*
         * Want to push to an internal repository for testing?
         * Set the following properties in ~/.gradle/gradle.properties.
         *
         * internalUrl=YOUR_INTERNAL_URL
         * internalUsername=YOUR_USERNAME
         * internalPassword=YOUR_PASSWORD
         *
         * Then run the following command to publish a new internal release:
         *
         * ./gradlew publishAllPublicationsToInternalRepository -DRELEASE_SIGNING_ENABLED=false
         */
        val internalUrl = providers.gradleProperty("internalUrl").orNull
        if (internalUrl != null) {
          maven {
            name = "internal"
            url = URI(internalUrl)
            credentials {
              username = providers.gradleProperty("internalUsername").get()
              password = providers.gradleProperty("internalPassword").get()
            }
          }
        }
      }
    }
    configure<MavenPublishBaseExtension> {
      publishToMavenCentral(SonatypeHost.DEFAULT, automaticRelease = true)
      signAllPublications()
      pom {
        description.set("Runs Kotlin/JS libraries in Kotlin/JVM and Kotlin/Native programs")
        name.set(project.name)
        url.set("https://github.com/cashapp/zipline/")
        licenses {
          license {
            name.set("The Apache Software License, Version 2.0")
            url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
            distribution.set("repo")
          }
        }
        developers {
          developer {
            id.set("cashapp")
            name.set("Cash App")
          }
        }
        scm {
          url.set("https://github.com/cashapp/zipline/")
          connection.set("scm:git:https://github.com/cashapp/zipline.git")
          developerConnection.set("scm:git:ssh://git@github.com/cashapp/zipline.git")
        }
      }
    }
  }
}

allprojects {
  tasks.withType<KotlinJvmTest>().configureEach {
    environment("ZIPLINE_ROOT", rootDir)
  }

  tasks.withType<KotlinNativeTest>().configureEach {
    environment("SIMCTL_CHILD_ZIPLINE_ROOT", rootDir)
    environment("ZIPLINE_ROOT", rootDir)
  }

  tasks.withType<KotlinJsTest>().configureEach {
    environment("ZIPLINE_ROOT", rootDir.toString())
  }
}

subprojects {
  plugins.withId("binary-compatibility-validator") {
    configure<ApiValidationExtension> {
      // Making this properly internal requires some SQLDelight work.
      ignoredPackages += "app.cash.zipline.loader.internal.cache"
      // Making this properly internal requires adopting test facets.
      ignoredPackages += "app.cash.zipline.loader.internal.fetcher"
    }
  }
}
