plugins {
  id("java-library")
  id("com.vanniktech.maven.publish")
}

java {
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
}

sourceSets {
  val main by getting {
    java.srcDir("../common/java")
  }
  val test by getting {
    java.srcDir("../common/test")
  }
}

dependencies {
  api(Dependencies.androidxAnnotation)

  testImplementation(Dependencies.junit)
  testImplementation(Dependencies.truth)
}
