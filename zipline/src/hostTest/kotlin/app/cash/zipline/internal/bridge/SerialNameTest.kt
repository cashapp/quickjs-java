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
package app.cash.zipline.internal.bridge

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer

class SerialNameTest {
  @Test
  fun basicSerializer() {
    val serialName = serialName(
      "SomeType",
      serializers = listOf(
        serializer<String>(),
      ),
    )
    assertEquals("SomeType<kotlin.String>", serialName)
  }

  @Test
  fun multipleSerializers() {
    val serialName = serialName(
      "SomeType",
      serializers = listOf(
        serializer<String>(),
        serializer<Int>(),
        serializer<Boolean>(),
      ),
    )
    assertEquals("SomeType<kotlin.String,kotlin.Int,kotlin.Boolean>", serialName)
  }

  @Test
  fun listSerializer() {
    val serialName = serialName(
      "SomeType",
      serializers = listOf(
        serializer<List<String>>(),
      ),
    )
    assertEquals("SomeType<kotlin.collections.ArrayList<kotlin.String>>", serialName)
  }

  @Test
  fun mapSerializer() {
    val serialName = serialName(
      "SomeType",
      serializers = listOf(
        serializer<Map<String, Int>>(),
      ),
    )
    assertEquals("SomeType<kotlin.collections.LinkedHashMap<kotlin.String,kotlin.Int>>", serialName)
  }

  @Test
  fun mapOfListsSerializer() {
    val serialName = serialName(
      "SomeType",
      serializers = listOf(
        serializer<Map<String, List<Int>>>(),
      ),
    )
    assertEquals(
      "SomeType<kotlin.collections.LinkedHashMap<kotlin.String,kotlin.collections.ArrayList<kotlin.Int>>>",
      serialName,
    )
  }

  @Test
  fun recursiveSerializer() {
    val serialName = serialName(
      "SomeType",
      serializers = listOf(
        serializer<SomeRecursiveType>(),
      ),
    )
    assertEquals(
      expected = "SomeType<" +
        "app.cash.zipline.internal.bridge.SomeRecursiveType<" +
        "app.cash.zipline.internal.bridge.SomeRecursiveType?<app.cash.zipline.internal.bridge.SomeRecursiveType?>" +
        ">>",
      actual = serialName,
    )
  }
}

@Serializable
private data class SomeRecursiveType(
  val someData: SomeRecursiveType?,
)
