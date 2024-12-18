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
package app.cash.zipline.profiler

import app.cash.zipline.QuickJs
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.SYSTEM
import okio.use

internal class SamplingProfilerTest {
  private val quickJs = QuickJs.create()

  @BeforeTest fun setUp() {
    quickJs.evaluate(
      """
      |function fib0() {
      |  return 1;
      |}
      |
      |function fib1() {
      |  return 1;
      |}
      |
      |function fib2() {
      |  return fib1() + fib0();
      |}
      |
      |function fib3() {
      |  return fib2() + fib1();
      |}
      |
      |function fib4() {
      |  return fib3() + fib2();
      |}
      |
      |function fib5() {
      |  return fib4() + fib3();
      |}
      |
      |function fib6() {
      |  return fib5() + fib4();
      |}
      |
      |function fib7() {
      |  return fib6() + fib5();
      |}
      |
      |function fib8() {
      |  return fib7() + fib6();
      |}
      |
      |function fib9() {
      |  return fib8() + fib7();
      |}
      """.trimMargin(),
      "smallFibs.js",
    )
    quickJs.evaluate(
      """
      |function fib10() {
      |  return fib9() + fib8();
      |}
      |
      |function fib11() {
      |  return fib10() + fib9();
      |}
      |
      |function fib12() {
      |  return fib11() + fib10();
      |}
      |
      |function fib13() {
      |  return fib12() + fib11();
      |}
      |
      |function fib14() {
      |  return fib13() + fib12();
      |}
      |
      |function fib15() {
      |  return fib14() + fib13();
      |}
      |
      |function fib16() {
      |  return fib15() + fib14();
      |}
      |
      |function fib17() {
      |  return fib16() + fib15();
      |}
      |
      |function fib18() {
      |  return fib17() + fib16();
      |}
      |
      |function fib19() {
      |  return fib18() + fib17();
      |}
      |
      |function fib20() {
      |  return fib19() + fib18();
      |}
      |
      """.trimMargin(),
      "bigFibs.js",
    )
  }

  @AfterTest fun tearDown() {
    quickJs.close()
  }

  /** This test just confirms the sampling profiler completes normally. */
  @Test
  fun happyPath() {
    quickJs.startCpuSampling(FileSystem.SYSTEM, "fibonacci.hprof".toPath()).use {
      for (i in 0 until 100) {
        quickJs.evaluate("""fib20()""".trimMargin())
      }
    }
  }
}
