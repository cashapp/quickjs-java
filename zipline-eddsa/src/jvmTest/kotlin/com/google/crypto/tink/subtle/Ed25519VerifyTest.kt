// Copyright 2017 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
////////////////////////////////////////////////////////////////////////////////
package com.google.crypto.tink.subtle

import java.security.GeneralSecurityException
import okio.ByteString.Companion.decodeHex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Tink's unit tests for [Ed25519Verify].
 */
class Ed25519VerifyTest {
  @Test
  fun testVerificationWithPublicKeyLengthDifferentFrom32Byte() {
    assertThrows(IllegalArgumentException::class.java) {
      Ed25519Verify(ByteArray(31))
    }
    assertThrows(IllegalArgumentException::class.java) {
      Ed25519Verify(ByteArray(33))
    }
  }

  @Test
  fun testVerificationWithWycheproofVectors() {
    var errors = 0
    val testGroups = loadEddsaTestJson().testGroups
    for (group in testGroups) {
      val key = group.key
      val publicKey = key.pk.decodeHex().toByteArray()
      val tests = group.tests
      for (testcase in tests) {
        val tcId = "testcase ${testcase.tcId} (${testcase.comment})"
        val msg = testcase.msg.decodeHex().toByteArray()
        val sig = testcase.sig.decodeHex().toByteArray()
        val result = testcase.result
        val verifier = Ed25519Verify(publicKey)
        try {
          verifier.verify(sig, msg)
          if (result == "invalid") {
            println("FAIL ${tcId}: accepting invalid signature")
            errors++
          }
        } catch (ex: GeneralSecurityException) {
          if (result == "valid") {
            println("FAIL ${tcId}: rejecting valid signature, exception: $ex")
            errors++
          }
        }
      }
    }
    assertEquals(0, errors.toLong())
  }
}
