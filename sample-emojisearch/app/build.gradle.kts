plugins {
  id("com.android.application")
  id("kotlin-android")
}

android {
  compileSdk = 31

  defaultConfig {
    applicationId = "app.cash.zipline.samples.emojisearch"
    minSdk = 21
    targetSdk = 30
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    vectorDrawables {
      useSupportLibrary = true
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
  }
  kotlinOptions {
    jvmTarget = "1.8"
    useIR = true
  }
  buildFeatures {
    compose = true
  }
  composeOptions {
    kotlinCompilerExtensionVersion = "1.0.2"
    kotlinCompilerVersion = "1.5.21"
  }
  packagingOptions {
    resources {
      excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
  }
}

dependencies {
  implementation(project(":sample-emojisearch:presenters"))
  implementation(project(":zipline"))
  implementation("io.coil-kt:coil-compose:1.3.2")
  implementation("androidx.core:core-ktx:1.6.0")
  implementation("androidx.appcompat:appcompat:1.3.1")
  implementation("com.google.android.material:material:1.4.0")
  implementation("androidx.compose.ui:ui:1.0.2")
  implementation("androidx.compose.material:material:1.0.2")
  implementation("androidx.compose.ui:ui-tooling-preview:1.0.2")
  implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.3.1")
  implementation("androidx.activity:activity-compose:1.4.0-alpha01")
  implementation("com.android.support:support-annotations:28.0.0")
  testImplementation("junit:junit:4.13.2")
  androidTestImplementation("androidx.test.ext:junit:1.1.3")
  androidTestImplementation("androidx.test.espresso:espresso-core:3.4.0")
  androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.0.2")
  debugImplementation("androidx.compose.ui:ui-tooling:1.0.2")
}

val copyPresentersJs = tasks.register<Copy>("copyPresentersJs") {
  destinationDir = buildDir.resolve("generated/presentersJs")
  if (true) {
    // Production, which is minified JavaScript.
    from(projectDir.resolve("$rootDir/sample-emojisearch/presenters/build/distributions/presenters.js"))
    dependsOn(":sample-emojisearch:presenters:jsBrowserProductionWebpack")
  } else {
    // Development, which is not minified and has useful stack traces.
    from(projectDir.resolve("$rootDir/sample-emojisearch/presenters/build/developmentExecutable/presenters.js"))
    dependsOn(":sample-emojisearch:presenters:jsBrowserDevelopmentWebpack")
  }
}
android {
  sourceSets {
    named("main") {
      resources.srcDir(copyPresentersJs)
    }
  }
  applicationVariants.configureEach {
    registerGeneratedResFolders(project.files(copyPresentersJs.map { it.destinationDir }))
  }
}
