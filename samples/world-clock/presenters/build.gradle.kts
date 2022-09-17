import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
  kotlin("multiplatform")
  id("com.android.library")
  kotlin("plugin.serialization")
  id("app.cash.zipline")
}

kotlin {
  iosArm64()
  iosX64()
  iosSimulatorArm64()

  android()

  js {
    browser()
    binaries.executable()
  }

  sourceSets {
    val commonMain by getting {
      dependencies {
        api("app.cash.zipline:zipline")
        api("org.jetbrains.kotlinx:kotlinx-datetime:0.4.0") // TODO gradle/libs.versions.toml
      }
    }
    val hostMain by creating {
      dependsOn(commonMain)
      dependencies {
        implementation("app.cash.zipline:zipline-loader")
        api(libs.okio.core)
      }
    }

    val androidMain by getting {
      dependsOn(hostMain)
      dependencies {
        implementation(libs.okHttp.core)
      }
    }
    val iosMain by creating {
      dependsOn(hostMain)
    }
    targets.withType<KotlinNativeTarget> {
      val main by compilations.getting
      main.defaultSourceSet.dependsOn(iosMain)
    }
  }
}

android {
  compileSdk = libs.versions.compileSdk.get().toInt()
  namespace = "app.cash.zipline.samples.worldclock.presenters"
}

zipline {
  mainFunction.set("app.cash.zipline.samples.worldclock.main")
}