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
@file:OptIn(ExperimentalSerializationApi::class) // Zipline must track ContextualSerializer API changes.

package app.cash.zipline.internal.bridge

import app.cash.zipline.ZiplineFunction
import app.cash.zipline.ZiplineService
import kotlinx.serialization.ContextualSerializer
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.modules.SerializersModule

/**
 * Adapts [ZiplineService] implementations to receive incoming and send outgoing calls. Most
 * implementations are generated.
 */
@PublishedApi
internal abstract class ZiplineServiceAdapter<T : ZiplineService> : KSerializer<T> {
  private val contextualSerializer = ContextualSerializer(PassByReference::class)
  abstract val serializers: List<KSerializer<*>>
  abstract val serialName: String
  abstract val simpleName: String

  override val descriptor = contextualSerializer.descriptor

  abstract fun ziplineFunctions(
    serializersModule: SerializersModule,
  ): List<ZiplineFunction<T>>

  abstract fun outboundService(
    callHandler: OutboundCallHandler,
  ): T

  override fun serialize(encoder: Encoder, value: T) {
    contextualSerializer.serialize(encoder, SendByReference(value, this))
  }

  override fun deserialize(decoder: Decoder): T {
    val reference = contextualSerializer.deserialize(decoder) as ReceiveByReference
    return reference.take(this)
  }

  override fun equals(other: Any?) =
    other is ZiplineServiceAdapter<*> &&
    this::class == other::class &&
    serializers == other.serializers

  override fun hashCode() = this::class.hashCode()

  override fun toString() = simpleName
}

@PublishedApi
internal fun <T : ZiplineService> ziplineServiceAdapter(): ZiplineServiceAdapter<T> {
  error("unexpected call to ziplineServiceAdapter(): is the Zipline plugin configured?")
}

@PublishedApi
internal fun <T : ZiplineService> ziplineServiceAdapter(
  ziplineServiceAdapter: ZiplineServiceAdapter<T>,
): ZiplineServiceAdapter<T> {
  return ziplineServiceAdapter
}

/**
 * Returns a string like `kotlinx.coroutines.Flow<kotlin.String>`. This can be used as a unique
 * key to identify a serializer for [typeName] that has [serializers] for its type arguments.
 */
@PublishedApi
internal fun serialName(typeName: String, serializers: List<KSerializer<*>>): String {
  return buildString {
    append(typeName)
    serializers.joinTo(this, separator = ",", prefix = "<", postfix = ">") {
      descriptorName(it.descriptor)
    }
  }
}

private fun descriptorName(typeName: SerialDescriptor, visited: MutableSet<SerialDescriptor> = mutableSetOf()): String {
  // Check if we've already visited this descriptor to avoid infinite recursion
  if (!visited.add(typeName)) {
    return typeName.serialName
  }

  return buildString {
    append(typeName.serialName)

    if (typeName.elementsCount > 0) {
      append('<')

      val elementIndices = 0 until typeName.elementsCount
      for (i in elementIndices) {
        append(descriptorName(typeName.getElementDescriptor(i), visited))
        if (i < elementIndices.last) {
          append(',')
        }
      }

      append('>')
    }
  }
}
