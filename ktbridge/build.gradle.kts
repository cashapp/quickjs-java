plugins {
  kotlin("multiplatform")
  id("com.vanniktech.maven.publish")
}

kotlin {
  jvm {
    withJava()
  }

  js {
    browser {
    }
  }

  sourceSets {
    val commonMain by getting {
      dependencies {
        api(Dependencies.okioMultiplatform)
      }
    }
    val jvmMain by getting {
      dependencies {
        api(Dependencies.androidxAnnotation)
        api(project(":quickjs:jvm"))
      }
    }
    val jvmTest by getting {
      dependencies {
        implementation(project(":ktbridge:testing"))
        implementation(Dependencies.junit)
        implementation(Dependencies.truth)
      }
    }
  }
}

tasks {
  val jvmTest by getting {
    dependsOn(":ktbridge:testing:jsBrowserProductionWebpack")
  }
}
