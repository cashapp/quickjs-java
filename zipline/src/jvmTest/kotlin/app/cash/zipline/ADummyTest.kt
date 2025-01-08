/*
 * Copyright (C) 2025 Cash App
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
package app.cash.zipline

import app.cash.zipline.testing.loadTestingJs
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Test

/**
 * A test which alphabetically will run first. This does first initialization of Zipline outside
 * of `runTest` so that its timeouts do not apply.
 */
class ADummyTest {
  private val zipline = Zipline.create(Dispatchers.Unconfined)

  @After fun after() {
    zipline.close()
  }

  @Test fun run() {
    zipline.loadTestingJs()
    zipline.quickJs.evaluate("testing.app.cash.zipline.testing.initZipline()")
  }
}
