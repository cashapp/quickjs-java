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

import com.google.crypto.tink.subtle.Ed25519.verify
import java.security.GeneralSecurityException
import okio.ByteString

/**
 * Ed25519 verifying.
 *
 * <h3>Usage</h3>
 *
 * <pre>`// get the publicKey from the other party.
 * Ed25519Verify verifier = new Ed25519Verify(publicKey);
 * try {
 * verifier.verify(signature, message);
 * } catch (GeneralSecurityException e) {
 * // all the rest of security exceptions.
 * }
`</pre> *
 *
 * @since 1.1.0
 */
class Ed25519Verify(publicKey: ByteString) {
  private val publicKey: ByteString

  init {
    require(publicKey.size == PUBLIC_KEY_LEN) {
      "Given public key's length is not $PUBLIC_KEY_LEN."
    }
    this.publicKey = publicKey
  }

  @Throws(GeneralSecurityException::class)
  fun verify(signature: ByteString, data: ByteString) {
    if (signature.size != SIGNATURE_LEN) {
      throw GeneralSecurityException("The length of the signature is not $SIGNATURE_LEN.")
    }
    if (!verify(data, signature, publicKey)) {
      throw GeneralSecurityException("Signature check failed.")
    }
  }

  companion object {
    const val PUBLIC_KEY_LEN = Field25519.FIELD_LEN
    const val SIGNATURE_LEN = Field25519.FIELD_LEN * 2
  }
}
