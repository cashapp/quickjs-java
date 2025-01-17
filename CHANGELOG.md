# Change Log

## Unreleased

Nothing yet!


## [1.19.0] - 2024-12-03
[1.19.0]: https://github.com/cashapp/zipline/releases/tag/1.19.0

 * Upgrade: [Kotlin 2.1.0](https://github.com/JetBrains/kotlin/releases/tag/v2.1.0)


## [1.18.0] - 2024-11-14
[1.18.0]: https://github.com/cashapp/zipline/releases/tag/1.18.0

 * Fix: Prevent clients from caching the dev server's responses.
 * New: "compile" subcommand in 'zipline-cli' compiles `.js` files to `.zipline` files.
 * Upgrade: [Kotlin Serialization 1.7.3][kotlin_serialization_1_7_3].
 * Upgrade: [kotlinx.coroutines 1.9.0][kotlinx_coroutines_1_9_0].
 * Upgrade: [Okio 3.9.1][okio_3_9_1].


## [1.17.0] - 2024-08-28
[1.17.0]: https://github.com/cashapp/zipline/releases/tag/1.17.0

 * New: Expose `globalThis.gc()` function into the guest code.
 * Upgrade: [Kotlin 2.0.20](https://github.com/JetBrains/kotlin/releases/tag/v2.0.20)


## [1.16.0] - 2024-07-17

 * Fix: Support 16KB page sizes in our Android native library. This is necessary to prevent an
   `UnsatisfiedLinkError` crash on Android 15 / API level 35.


## [1.15.0] - 2024-07-16

 * New: Use different dispatchers for cache vs code execution. Both the cache and QuickJS need to be
   thread-confined, but they don't need to be confined to the same thread. With this release we've
   added a new `CoroutineDispatcher` parameter to `ZiplineLoader.withCache()`. It is used when
   reading and writing the cache. The existing `CoroutineDispatcher` used to create the
   `ZiplineLoader` is used to access QuickJS.
 * New: `EventListener.cacheHit()` is called on each cache hit.


## [1.14.0] - 2024-07-09

 * New: `ZiplineLoader.load()` and `loadOnce()` now accept a suspending function.
 * New: Don't crash in `ZiplineLoader` when writing to the disk cache fails. Use the new
   `LoaderEventListener` type to observe such failures.
 * Fix: Don't crash in Gradle if the Zipline runtime library is absent.
 * New: Extend Kotlin's `AutoCloseable` in `Zipline`, `ZiplineService` and `ZiplineScope`.
 * Upgrade: [Oracle Linux 8][oracle_linux_8] for x86_64 Linux (`amd64/oraclelinux:8` on Docker).
 * Upgrade: [Kotlin Serialization 1.7.1][kotlin_serialization_1_7_1].


## [1.13.0] - 2024-06-14

 * Fix: Don't derive the Zipline compiler's output directory from the Kotlin/JS output directory.
   Zipline's outputs are now emitted to directories named like `build/zipline/Development`, and
   `build/zipline/ProductionWebpack`. This avoids a `PropertyQueryException` crash in Gradle.


## [1.12.0] - 2024-06-13

 * Fix: Don't allocate a stack trace when canceling a coroutine or a job. We've observed that
   applications create many instances of `CancellationException`, such as when canceling a flow
   in routine business logic. Unfortunately, we've also seen that creating the stack traces for
   these exceptions is slow on Kotlin/Native. With this update Zipline reuses a single instance of
   CancellationException everywhere. We believe that losing the diagnostic information is worth
   the performance benefit.
 * Fix: Don't break the configuration cache. Zipline's compile task violated a Gradle requirement
   by reading another task's property before that task had completed.
 * New: `ZiplineFunction.asDynamicFunction()` short-circuits Kotlin Serialization in Kotlin/JS. This
   new mechanism allows performance-sensitive code to reduce the amount of work required to call
   bridged functions.
 * Upgrade: [Kotlin Serialization 1.7.0][kotlin_serialization_1_7_0].


## [1.11.0] - 2024-06-05

 * New: `apiTracking` property on the `zipline { }` Gradle extension allows disabling API generation.
 * Fix: Calling `Zipline.close()` will now call `ZiplineService.close()` on all bound services. Strong
   references to the services will also be cleared to prevent reference cycles on native.
 * Upgrade: [Kotlin 2.0.0][kotlin_2_0_0]


## [1.10.1] - 2024-05-13

 * Fix: Build the released artifact on macOS instead of Linux to work around an issue packaging
   native dependencies.


## [1.10.0] - 2024-05-01

 * Fix: Clean source paths that show up in stack traces.
 * Fix: Don't leak Zipline instances. We had a bug where our memory-leak detection itself introduced
   a memory leak. We held a reference to a Zipline instance that was intended to be used to report
   services that were garbage collected but not closed.


## [1.9.0] - 2024-04-11

 * Breaking: Reorder the parameters in `ZiplineLoader` so `FileSystem` always precedes `Path`.
 * Fix: Release unused services in `Zipline.close()`. This was a memory leak.
 * Fix: Don't break Gradle's configuration cache in the `ziplineApiDump` task.
 * New: `ZiplineCryptography` adds a `SecureRandom` API for guest code.
 * New: `Zipline.getOrPutAttachment()` lets you attach application data to a Zipline instance.
 * New: Support building Zipline with the new Kotlin K2 compiler.
 * Upgrade: [Kotlin 1.9.23][kotlin_1_9_23]
 * Upgrade: [kotlinx.coroutines 1.8.0][kotlinx_coroutines_1_8_0]
 * Upgrade: [Okio 3.9.0][okio_3_9_0]
 * Upgrade: [SQLDelight 2.0.2][sqldelight_2_0_2]


## [1.8.0] - 2024-01-11

 * Fix: Don't crash validating signatures on Android 7.x. We incorrectly used an API that wasn't
   available until API 26+.
 * New: `FreshnessChecker` decides whether to load cached code. If it returns false,
   `EventListener.applicationLoadSkippedNotFresh()` will be called.
 * New: `EventListener.manifestReady()` is called when the manifest is fetched and verified, but
   before any code is downloaded.
 * Upgrade: [Okio 3.7.0][okio_3_7_0]


## [1.7.0] - 2023-11-30

* New: Gradle APIs to optimize production builds for either small artifact size or developer
  experience. Call the appropriate functions in the `zipline {}` block of your build file:
    ```kotlin
    zipline {
      ...
      optimizeForSmallArtifactSize()
    }
    ```
 * Fix: Don't crash when very large `Long` values are sent over a bridged API. Zipline uses JSON to
   encode values shared between host and guest, and that converts all primitive numeric types to
   `Double`. It is necessary to add `@Contextual` to all serialized `Long` values to get this fix.


## [1.6.0] - 2023-11-20

* Upgrade: [SQLDelight 2.0.0][sqldelight_2_0_0]


## [1.5.1] - 2023-11-14

 * Fix: remove the Zipline version from the `klib` metadata in the `zipline-cinterop-quickjs`
   artifact. This restores the behavior from 1.4.0 to work around [KT-62515].


## [1.5.0] - 2023-11-02

 * New: `Zipline.eventListener` can be used to get the `EventListener` from a `Zipline` instance.
 * Upgrade: [Kotlin 1.9.20](https://square.github.io/okio/changelog/#version-360)


## [1.4.0] - 2023-10-31

 * New: `EventListener.Factory` can be used to scope events to a particular `Zipline` instance.
 * New: Support arbitrary metadata in the `ZiplineManifest`. This new `Map<String, String>` can be
   produced in your `build.gradle.kts` file, and consumed from the `ZiplineManifest` instance.

    ```kotlin
    zipline {
      ...
      metadata.put("build_timestamp", "2023-10-25T12:00:00T")
    }
    ```

 * Upgrade: [OkHttp 4.12.0](https://square.github.io/okhttp/changelogs/changelog_4x/#version-4120)
 * Upgrade: [Okio 3.6.0](https://square.github.io/okio/changelog/#version-360)


## [1.3.0] - 2023-09-20

 * Fix: Configure a 6 MiB stack size by default. Previously Zipline didn't enforce any stack
   size limit, which resulted in difficult-to-diagnose crashes when the stack size was exceeded.
   Callers must manually ensure their calling stack sizes are larger than 6 MiB!
 * Fix: Always include type parameters for nested parameterized types.
 * Fix: Don't double-free when calling `NSData.dataWithBytesNoCopy`. We had a bug where we were
   double-freeing memory in the Kotlin/Native `EcdsaP256` signature verifier.
 * Upgrade: [Kotlin Serialization 1.6.0][kotlin_serialization_1_6_0].


## [1.2.0] - 2023-08-09

 * Upgrade: [Kotlin 1.9.0](https://github.com/JetBrains/kotlin/releases/tag/v1.9.0)
 * Upgrade: [kotlinx.coroutines 1.7.3][kotlinx_coroutines_1_7_3]


## [1.1.0] - 2023-07-30

 * New: Gradle tasks `ziplineApiCheck` and `ziplineApiDump`. These tasks work like Kotlin’s
   [Binary compatibility validator](https://github.com/Kotlin/binary-compatibility-validator):
   the _Dump_ task writes your public API to a file (`api/zipline-api.toml`) and the the _Check_
   task confirms that your public API matches that file. These two tasks expose the IDs Zipline uses
   for functions. The `:ziplineApiCheck` task configures itself a dependency of Gradle's `:check`
   task: you'll need to run `:ziplineApiDump` when applying this update and each time your public
   API changes going forward.
 * Upgrade: [Kotlin 1.8.21][kotlin_1_8_21].
 * Upgrade: [kotlinx.coroutines 1.7.2][kotlinx_coroutines_1_7_2].
 * Upgrade: [Kotlin Serialization 1.5.1][kotlin_serialization_1_5_1].


## [1.0.0] - 2023-06-12

This is Zipline's initial stable release.

With this release we commit to compatibility between host and guest programs. In particular, host
applications built with any Zipline 1.x release will be able to execute guest applications built
with any other 1.y release. (Application developers must write compatible interfaces to take
advantage of this!)

The following are now stable:

 * The manifest file format (`manifest.zipline.json`)
 * The library file format and bytecode within (`my-library.zipline`)
 * The host-guest call protocol
 * The internal host-guest APIs for async calls, console logging, and leak notifications

As we add features and performance improvements to future releases, we will test compatibility
with 1.0.

We expect to someday do ‘Zipline 2.0’ that uses WebAssembly. When that happens we’ll make sure the
2.x tools can also produce programs that run on 1.x hosts.

 * Fix: Don't allow services with the different generic parameters to collide in the cache. We had
   a severe bug where two services would share serializers for unrelated types. This would typically
   result in a `ClassCastException` at runtime.


## [0.9.20] - 2023-06-01

 * Downgrade: [Kotlin 1.8.20][kotlin_1_8_20]. (Our users aren't ready for 1.8.21 yet.)
 * Downgrade: [Kotlin Serialization 1.5.0][kotlin_serialization_1_5_1]. (Requires Kotlin 1.8.21.)


## [0.9.19] - 2023-06-01

 * Breaking: Change the calling convention between host and guest code to identify functions by IDs
   instead of by their signatures. We renamed `ZiplineFunction.name` to `signature` and added a new
   `id` property.
 * Breaking: Change the built-in services to share a single identifier rather than bridging them
   separately based on feature (`console`, `event_loop`, `event_listener`.)
 * Breaking: Move `ZiplineManifest` from `app.cash.zipline.loader` to `app.cash.zipline`. It was
   also promoted to the main `zipline` artifact.
 * New: `ZiplineService.targetType` can be used to inspect the function declarations on the peer's
   version of a service.
 * New: `EventListener.manifestVerified()` signals successful signature checks of the manifest.
 * New: Convert `zipline-profiler` into a multiplatform artifact.
 * Upgrade: [Kotlin 1.8.21][kotlin_1_8_21].
 * Upgrade: [kotlinx.coroutines 1.7.1][kotlinx_coroutines_1_7_1].
 * Upgrade: [Kotlin Serialization 1.5.1][kotlin_serialization_1_5_1].


## [0.9.18] - 2023-04-17

 * New: Support pass-by-reference of `StateFlow` values.
 * Upgrade: [Kotlin 1.8.20][kotlin_1_8_20].
 * Fix: Don't crash applying source maps to QuickJS bytecode. We had a longstanding off-by-one
   error interpreting an encoded function's flags.
 * Fix: Retry web sockets when polling for fresh code in development mode. Previously we fell back
   to polling after a single web socket error.
 * Fix: Don't `ClassCastException` when running Gradle in continuous mode. We were failing to post
   web socket updates when fresh code is available.


## [0.9.17] - 2023-03-15

 * Upgrade: [Kotlin 1.8.10][kotlin_1_8_10].
 * Upgrade: [Kotlin Serialization 1.5.0][kotlin_serialization_1_5_0].
 * Fix: Support function overloads in `ZiplineService` interfaces.
 * Fix: Workaround a crash in Kotlin/JS incremental compilation. We were using a constant string in
   a `js(...)` literal.


## [0.9.16] - 2023-02-09

 * New `withDevelopmentServerPush()` subscribes to the local development server's websocket to
   trigger hot reloads. This is lower-latency and more efficient than polling.
 * Upgrade: [Kotlin 1.8.0][kotlin_1_8_0].


## [0.9.15] - 2023-01-25

 * Fix: Don't crash if canceled with a 0-delay job enqueued. We had a bug where calling
   `Zipline.close()` could race with an enqueued job.
 * Fix: Don't crash in the JS CoroutineEventLoop. This replaces an `IllegalStateException` with a
   `CancellationException` when making a suspending call after the Zipline is closed.
 * Fix: Do not set `-Xir-per-module`. This is no longer necessary, and may have prevented
   whole-program module generation.
 * New: Support Webpack builds. In addition to modular builds that emit many `.zipline` files per
   program, webpack builds emit a single minified `.zipline` file. (In both cases a single manifest
   file is used.)
 * New: We've added event listener events for the loader's initializer and main function.


## [0.9.14] - 2023-01-16

 * Fix: Don’t force `suspend` functions to suspend. We've changed our calling convention so
   suspendable functions are executed inline and on the same call stack until they suspend. If such
   functions return without suspending, the async dispatch is skipped.
 * Fix: Provide more information when calling a closed service.
 * Fix: Clean up file names in stack traces.
 * New: Add a `ZiplineManifest` to `LoadResult.Success`.


## [0.9.13] - 2022-12-22

 * New: `ZiplineScope` is a new mechanism to close pass-by-reference services and flows. Pass a
   `ZiplineScope` to `Zipline.take()` or implement `ZiplineScoped` in a `ZiplineService` to declare
   a scope, then call `ZiplineScope.close()` to close all received services. Note that Flows
   that were passed-by-reference previously needed to be collected exactly once; with this change
   Flows may be collected any number of times.
 * New: Configure the development HTTP server's local port in Gradle:
    ```kotlin
    zipline {
      ...
      httpServerPort.set(22364)
    }
    ```
 * New: Include the service name in `ZiplineApiMismatchException`.
 * Fix: Prevent `.zipline` files from being stored in the HTTP cache. We've added
   'Cache-Control: no-store' request headers to downloads to prevent caching that's redundant
   with ZiplineLoader's cache.
 * Fix: Make `ZiplineService.close()` idempotent for outbound services.


## [0.9.12] - 2022-12-06

 * New: Add `ZiplineFunction.isSuspending`.
 * New: Add events for `ziplineCreate()`, `moduleLoadStart()`, and `moduleLoadEnd()`.


## [0.9.11] - 2022-12-05

 * New: Publish an event when Zipline is closed.
 * Fix: Avoid a linear search through endpoint names.


## [0.9.10] - 2022-11-30

 * New: Add support for `var` and `val` declarations in service interfaces.
 * Fix: Update Gradle task to serve zipline files for compatibility with Gradle 7.6.


## [0.9.9] - 2022-11-16

 * Changed: Roll back Kotlin 1.7.20 to ensure downstream users can still use Compose easily. The plugin remains compatible with 1.7.21.


## [0.9.8] - 2022-11-16

 * New: Support Kotlin 1.7.21.


## [0.9.7] - 2022-11-11

 * Breaking: Change `EventListener` to pass the `Zipline` instance when it is available.
 * Breaking: Introduce `EventListener.applicationLoadSkipped()` when a downloaded manifest hasn't
   changed.
 * New: The development server (`serveDevelopmentZipline`) now notifies code changes via web socket.
   Connect to `/ws` to receive `"reload"` messages.


## [0.9.6] - 2022-10-13

 * Fix: Don't fail with `unexpected call` errors because code was not being rewritten by our Kotlin
   compiler plugin.


## [0.9.5] - 2022-10-06

 * New: Support `@Contextual` as a type annotation on `ZiplineService` parameters and return types.
   This will attempt to find a contextual serializer for the annotated type.
 * Breaking: Rename `LoadedZipline` to `LoadResult`. This allows `ZiplineLoader.load()` to return
   a flow that includes both successes and failures.
 * Breaking: Remove `eval()` support from QuickJs. As a security measure Zipline doesn't support
   evaluating JavaScript at runtime.


## [0.9.4] - 2022-09-07

 * New: Build in a basic HTTP client for Apple platforms.
 * Breaking change: Wrap exceptions thrown in bridged calls with `ZiplineException`. Previously
   these were wrapped in `Exception` which was difficult to catch generically.


## [0.9.3] - 2022-08-23

 * Breaking change: Move `SerializersModule` from a property of `ZiplineLoader` to a parameter in
   `load()` and `loadOnce()`. This enables using a single loader for different applications that
   have different serializers.
 * Breaking change: Make `ZiplineCache` a top-level type. It has its own lifecycle and is best
   managed directly.
 * Breaking change: Pass a `startValue` parameter consistently to event listener callbacks.
 * New: Extension `OkHttpClient.asZiplineHttpClient()` makes it easier to use general-purpose
   Zipline APIs from multiplatform code.


## [0.9.2] - 2022-08-22

 * Breaking change: `ZiplineLoader.load(...)` is no longer suspending.
 * Breaking change: Don't require implementors of `ZiplineHttpClient` to implement URL resolution.
 * Breaking change: Include a default clock implementation on iOS.
 * Breaking change: Require callers to explicitly opt out of signature checks. Pass
   `ManifestVerifier.Companion.NO_SIGNATURE_CHECKS` to use `ZiplineLoader` without code signature
   verification.
 * New: Support ECDSA P-256 for signatures.


## [0.9.1] - 2022-08-18

 * New: Gradle extension `zipline {}` block.
 * New: Compile files in parallel.
 * New: Replace webpack with a dedicated static file server. Use `serveDevelopmentZipline` or
   `serveProductionZipline` to serve an application locally.
 * Fix: Always run Kotlin/JS in strict mode.
 * Upgrade: [Kotlin Serialization 1.4.0][kotlin_serialization_1_4_0].


## [0.9.0] - 2022-08-05

 * New: `ZiplineLoader` is a new module to launch Zipline applications quickly. It supports caching
   including offline launching, code signing, and launching from a flow.
 * New: Zipline's Gradle plugin makes it fast and easy to use build Zipline applications.
 * New: `EventListener` makes it easy to observe with Zipline performance and problems.
 * Upgrade: [Kotlin 1.7.10][kotlin_1_7_10].


## [0.1.0] - 2021-09-30

We've changed this project to focus exclusively on executing Kotlin/JS libraries.

We plan to drop support for executing arbitrary JavaScript code. If you've been using either
QuickJS Java (this project's name until September 2021) or Duktape Android (this project's name
until June 2021), those projects remain as git branches but will not receive further updates.

The project's new Maven coordinates are `app.cash.zipline:zipline`.

### Added

* `Zipline` is a new entry point for connecting to Kotlin/JS libraries.
* `ZiplineReference` supports sending service objects across platforms.
* `ZiplineSerializer` supports sending serializers objects across platforms.
* `FlowReference` supports sending `Flow` objects across platforms.
* `InterruptHandler` interrupts executing JavaScript.
* `MemoryUsage` interrogates the state of the JavaScript runtime.

### Changed

* `QuickJs` entry point moved to `app.cash.zipline`.


# QuickJS Java change log

## [0.9.2] - 2021-08-04

### Added

* `compile()` method takes JS source and produces a version-specific bytecode representation.
* `execute()` method takes version-specific bytecode and runs it.


### Changed

* Methods are no longer `synchronized`. If you are performing concurrent access add your own synchronization.


### Fixed

* Self-extract native libraries from JAR when running on the JVM.
* Correct UTF-8 handling of multi-byte graphemes to avoid mismatch between Java's modified UTF-8 and QuickJS's traditional UTF-8.


## [0.9.1] - 2021-07-12

JVM artifact is now available at `app.cash.quickjs:quickjs-jvm` for Linux and Mac OS!

### Fixed

* Handle null argument array which was sometimes supplied to native code instead of a zero-element array.
* Properly track the associated proxy class from native code to avoid a segfault.
* Eliminate a segfault during engine close when cleaning up proxied objects.


## [0.9.0] - 2021-06-14

Backing JS engine change to QuickJS.
Package name is now `app.cash.quickjs`.
Entrypoint is `QuickJs` class.
Maven coordinates are now `app.cash.quickjs:quickjs-android`.
The API and behavior should otherwise be unchanged.


[Unreleased]: https://github.com/cashapp/quickjs-java/compare/0.10.0...HEAD
[0.9.2]: https://github.com/cashapp/quickjs-java/releases/tag/0.9.2
[0.9.1]: https://github.com/cashapp/quickjs-java/releases/tag/0.9.1
[0.9.0]: https://github.com/cashapp/quickjs-java/releases/tag/0.9.0



# Duktape Android change log

## Version 1.4.0 *(2021-06-14)*

 * New: Update to Duktape 2.6.0.
 * Fix: Correct a few JNI reference leaks which may have eventually caused a native crash.
 * Migrated to AndroidX annotations.

## Version 1.3.0 *(2018-08-02)*

 * New: update to Duktape 2.2.1.
 * Fix: update build settings to reduce AAR output size.

## Version 1.2.0 *(2017-09-08)*

 * New: support for arrays of supported types as arguments between Java/JavaScript.
 * New: update to Duktape 1.8.0.
 * Fix: explicitly release temporary JVM objects when returning from calls to Java from JavaScript.
 * Fix: allocate a local frame when binding Java interfaces to allow many methods and arguments.

## Version 1.1.0 *(2016-11-08)*

 * New: support parsing common date formats in JavaScript's "new Date('str')" constructor.
 * Fix: Duktape.evaluate returns null if the implicit return type is unsupported.

## Version 1.0.0 *(2016-09-28)*

 * Renamed Duktape.proxy and Duktape.bind to Duktape.get and Duktape.set.
 * New: support for arguments of type Object between Java/JavaScript.
 * New: support variadic (VarArgs) functions on Java/JavaScript calls.
 * Fix: Make creation and use of a Duktape instance thread-safe.

## Version 0.9.6 *(2016-08-31)*

 * New: call JavaScript methods from Java via proxies.
 * New: update to Duktape 1.5.0.

## Version 0.9.5 *(2016-03-07)*

 * New: call Java methods from JavaScript.
 * New: improved stacktraces. Includes both JavaScript and Java code from the call stack.
 * New: update to Duktape 1.4.0.

## Version 0.9.4 *(2015-11-02)*

 * New: expose JavaScript stacktraces when things fail.

## Version 0.9.3 *(2015-10-07)*

 * Fix: Use global refs in JNI.

## Version 0.9.2 *(2015-10-06)*

 * Fix: Get the timezone from Java rather than using UTC.
 * Fix: Use recommended flags for building.

## Version 0.9.1 *(2015-09-22)*

 * Fix: Correctly propagate errors as exceptions.


## Version 0.9.0 *(2015-09-08)*

Initial release.


[KT-62515]: https://youtrack.jetbrains.com/issue/KT-62515
[kotlin_1_7_10]: https://github.com/JetBrains/kotlin/releases/tag/v1.7.10
[kotlin_1_8_0]: https://github.com/JetBrains/kotlin/releases/tag/v1.8.0
[kotlin_1_8_10]: https://github.com/JetBrains/kotlin/releases/tag/v1.8.10
[kotlin_1_8_20]: https://github.com/JetBrains/kotlin/releases/tag/v1.8.20
[kotlin_1_8_21]: https://github.com/JetBrains/kotlin/releases/tag/v1.8.21
[kotlin_1_9_20]: https://github.com/JetBrains/kotlin/releases/tag/v1.9.20
[kotlin_1_9_23]: https://github.com/JetBrains/kotlin/releases/tag/v1.9.23
[kotlin_2_0_0]: https://github.com/JetBrains/kotlin/releases/tag/v2.0.0
[kotlin_serialization_1_4_0]: https://github.com/Kotlin/kotlinx.serialization/releases/tag/v1.4.0
[kotlin_serialization_1_5_0]: https://github.com/Kotlin/kotlinx.serialization/releases/tag/v1.5.0
[kotlin_serialization_1_5_1]: https://github.com/Kotlin/kotlinx.serialization/releases/tag/v1.5.1
[kotlin_serialization_1_6_0]: https://github.com/Kotlin/kotlinx.serialization/releases/tag/v1.6.0
[kotlin_serialization_1_7_0]: https://github.com/Kotlin/kotlinx.serialization/releases/tag/v1.7.0
[kotlin_serialization_1_7_1]: https://github.com/Kotlin/kotlinx.serialization/releases/tag/v1.7.1
[kotlin_serialization_1_7_3]: https://github.com/Kotlin/kotlinx.serialization/releases/tag/v1.7.3
[kotlinx_coroutines_1_7_1]: https://github.com/Kotlin/kotlinx.coroutines/releases/tag/1.7.1
[kotlinx_coroutines_1_7_2]: https://github.com/Kotlin/kotlinx.coroutines/releases/tag/1.7.2
[kotlinx_coroutines_1_7_3]: https://github.com/Kotlin/kotlinx.coroutines/releases/tag/1.7.3
[kotlinx_coroutines_1_8_0]: https://github.com/Kotlin/kotlinx.coroutines/releases/tag/1.8.0
[kotlinx_coroutines_1_9_0]: https://github.com/Kotlin/kotlinx.coroutines/releases/tag/1.9.0
[okio_3_7_0]: https://square.github.io/okio/changelog/#version-370
[okio_3_9_0]: https://square.github.io/okio/changelog/#version-390
[okio_3_9_1]: https://square.github.io/okio/changelog/#version-391
[oracle_linux_8]: https://docs.oracle.com/en/operating-systems/oracle-linux/8/
[sqldelight_2_0_0]: https://cashapp.github.io/sqldelight/2.0.0/changelog/#200-2023-07-26
[sqldelight_2_0_2]: https://cashapp.github.io/sqldelight/2.0.2/changelog/#202-2024-04-05
