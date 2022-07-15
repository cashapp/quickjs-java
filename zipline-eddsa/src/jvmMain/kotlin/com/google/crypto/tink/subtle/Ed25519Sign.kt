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

import com.google.crypto.tink.subtle.Ed25519.getHashedScalar
import com.google.crypto.tink.subtle.Ed25519.scalarMultWithBaseToBytes
import com.google.crypto.tink.subtle.Ed25519.sign
import com.google.crypto.tink.subtle.Random.randBytes
import okio.ByteString

/**
 * Ed25519 signing.
 *
 * # Usage
 *
 * ```
 * val keyPair = Ed25519Sign.KeyPair.newKeyPair()
 *
 * // securely store keyPair and share keyPair.getPublicKey()
 * val signer = Ed25519Sign(keyPair.getPrivateKey())
 * val signature = signer.sign(message)
 * ```
 *
 * @since 1.1.0
 *
 * @param privateKey 32-byte random sequence.
 */
class Ed25519Sign(privateKey: ByteString) {
  private val hashedPrivateKey: ByteString
  private val publicKey: ByteString

  init {
    require(privateKey.size == SECRET_KEY_LEN) {
      "Given private key's length is not $SECRET_KEY_LEN"
    }
    hashedPrivateKey = getHashedScalar(privateKey)
    publicKey = scalarMultWithBaseToBytes(hashedPrivateKey)
  }

  fun sign(data: ByteString): ByteString {
    return sign(data, publicKey, hashedPrivateKey)
  }

  /** Defines the KeyPair consisting of a private key and its corresponding public key.  */
  class KeyPair private constructor(
    val publicKey: ByteString,
    val privateKey: ByteString,
  ) {
    companion object {
      /** Returns a new `<publicKey / privateKey>` KeyPair.  */
      fun newKeyPair(): KeyPair {
        return newKeyPairFromSeed(randBytes(Field25519.FIELD_LEN))
      }

      /** Returns a new `<publicKey / privateKey>` KeyPair generated from a seed. */
      fun newKeyPairFromSeed(secretSeed: ByteString): KeyPair {
        require(secretSeed.size == Field25519.FIELD_LEN) {
          "Given secret seed length is not ${Field25519.FIELD_LEN}"
        }
        val publicKey = scalarMultWithBaseToBytes(getHashedScalar(secretSeed))
        return KeyPair(publicKey, secretSeed)
      }
    }
  }

  companion object {
    const val SECRET_KEY_LEN = Field25519.FIELD_LEN
  }
}
