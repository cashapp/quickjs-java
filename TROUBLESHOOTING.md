# Troubleshooting

For contributors to Zipline, the following can be exceptions encountered which don't have obvious fixes (yet).

## Configure Android SDK Location for Gradle/IntelliJ

To let your build resolve Android SDK files, add the following in a `local.properties` file at the root of the Zipline repo directory. If you're using Android Studio to open the Zipline project, this step may not be necessary.

On macOS, you'll replace `{your username}` with your local account username. On other platforms the path will be different.

```
sdk.dir=/Users/{your username}/Library/Android/sdk
```

If you don't have Android SDK downloaded yet, the easiest way is to install Android Studio with default configuration with `brew install android-studio`. It will download the Android SDK to the above location in macOS and setup required usage terms approvals.

## Missing JNI Libraries

```
java.lang.ExceptionInInitializerError
	at app.cash.zipline.Zipline$Companion.create(Zipline.kt:175)
	at app.cash.zipline.Zipline$Companion.create$default(Zipline.kt:170)
	at app.cash.zipline.ConsoleTest.<init>(ConsoleTest.kt:36)
	at java.base/jdk.internal.reflect.NativeConstructorAccessorImpl.newInstance0(Native Method)
  ...
Caused by: java.lang.IllegalStateException: Unable to read /jni/aarch64/libquickjs.dylib from JAR
	at app.cash.zipline.QuickJsNativeLoaderKt.loadNativeLibrary(QuickJsNativeLoader.kt:36)
	at app.cash.zipline.QuickJs.<clinit>(QuickJs.kt:35)
	... 46 more
```

For tests like `app.cash.zipline.ConsoleTest`, failures with the above stacktrace point to missing `.dylib` prebuilt C libraries necessary for using QuickJS from the JVM.

Download [the latest `jni-binaries` artifact](https://nightly.link/cashapp/zipline/workflows/build/trunk/jni-binaries) from our GitHub CI, and extract its contents to the `zipline/src/jvmMain/resources/jni/` directory.

## Build JNI Libraries Locally

Zipline uses Zig to cross-compile its JVM native libraries to all platforms and architectures.
This is only tested on macOS, but may work on Linux, too. Windows is not supported.

First, download or install Zig 0.13.0 to your system.
Then, execute these commands:

```
$ cd zipline
$ zig build -p src/jvmMain/resources/jni/
```
