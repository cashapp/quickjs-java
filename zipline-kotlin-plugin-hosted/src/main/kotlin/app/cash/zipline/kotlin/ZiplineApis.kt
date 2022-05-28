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
package app.cash.zipline.kotlin

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.IrPropertySymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.types.starProjectedType
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.isVararg
import org.jetbrains.kotlin.name.FqName

/** Looks up APIs used by the code rewriters. */
internal class ZiplineApis(
  private val pluginContext: IrPluginContext,
) {
  private val packageFqName = FqName("app.cash.zipline")
  private val bridgeFqName = FqName("app.cash.zipline.internal.bridge")
  private val serializationFqName = FqName("kotlinx.serialization")
  private val serializationModulesFqName = FqName("kotlinx.serialization.modules")
  private val serializersModuleFqName = serializationModulesFqName.child("SerializersModule")
  private val ziplineFqName = packageFqName.child("Zipline")
  val ziplineServiceFqName = packageFqName.child("ZiplineService")
  private val ziplineServiceSerializerFunctionFqName =
    packageFqName.child("ziplineServiceSerializer")
  private val ziplineServiceAdapterFunctionFqName = bridgeFqName.child("ziplineServiceAdapter")
  private val ziplineServiceAdapterFqName = bridgeFqName.child("ZiplineServiceAdapter")
  private val endpointFqName = bridgeFqName.child("Endpoint")
  val flowFqName = FqName("kotlinx.coroutines.flow").child("Flow")
  private val collectionsFqName = FqName("kotlin.collections")

  val any: IrClassSymbol
    get() = pluginContext.referenceClass(FqName("kotlin.Any"))!!

  val kSerializer: IrClassSymbol
    get() = pluginContext.referenceClass(serializationFqName.child("KSerializer"))!!

  val map: IrClassSymbol
    get() = pluginContext.referenceClass(collectionsFqName.child("Map"))!!

  val list: IrClassSymbol
    get() = pluginContext.referenceClass(collectionsFqName.child("List"))!!

  val listOfKSerializerStar: IrSimpleType
    get() = list.typeWith(kSerializer.starProjectedType)

  val mutableMap: IrClassSymbol
    get() = pluginContext.referenceClass(collectionsFqName.child("MutableMap"))!!

  val serializerFunction: IrSimpleFunctionSymbol
    get() = pluginContext.referenceFunctions(serializationFqName.child("serializer"))
      .single {
        it.owner.extensionReceiverParameter?.type?.classFqName == serializersModuleFqName &&
          it.owner.valueParameters.isEmpty() &&
          it.owner.typeParameters.size == 1
      }

  val flowSerializerFunction: IrSimpleFunctionSymbol
    get() = pluginContext.referenceFunctions(bridgeFqName.child("flowSerializer"))
      .single()

  val mutableMapOfFunction: IrSimpleFunctionSymbol
    get() = pluginContext.referenceFunctions(collectionsFqName.child("mutableMapOf"))
      .single { it.owner.valueParameters.isEmpty() }

  val listOfFunction: IrSimpleFunctionSymbol
    get() = pluginContext.referenceFunctions(collectionsFqName.child("listOf"))
      .single { it.owner.valueParameters.firstOrNull()?.isVararg == true }

  val mutableMapPutFunction: IrSimpleFunctionSymbol
    get() = pluginContext.referenceFunctions(
      collectionsFqName.child("MutableMap").child("put")
    ).single()

  val listGetFunction: IrSimpleFunctionSymbol
    get() = pluginContext.referenceFunctions(
      collectionsFqName.child("List").child("get")
    ).single()

  val inboundBridgeContextFqName = bridgeFqName.child("InboundBridge").child("Context")

  val inboundBridgeContext: IrClassSymbol
    get() = pluginContext.referenceClass(inboundBridgeContextFqName)!!

  val inboundBridgeContextSerializersModule: IrPropertySymbol
    get() = pluginContext.referenceProperties(
      inboundBridgeContextFqName.child("serializersModule")
    ).single()

  val inboundCallHandler: IrClassSymbol
    get() = pluginContext.referenceClass(bridgeFqName.child("InboundCallHandler"))!!

  val inboundCallHandlerCall: IrSimpleFunctionSymbol
    get() = pluginContext.referenceFunctions(
      bridgeFqName.child("InboundCallHandler").child("call")
    ).single()

  val inboundCallHandlerCallSuspending: IrSimpleFunctionSymbol
    get() = pluginContext.referenceFunctions(
      bridgeFqName.child("InboundCallHandler").child("callSuspending")
    ).single()

  val outboundCallInvoke: IrSimpleFunctionSymbol
    get() = pluginContext.referenceFunctions(bridgeFqName.child("OutboundCall").child("invoke"))
      .single()

  val outboundCallInvokeSuspending: IrSimpleFunctionSymbol
    get() = pluginContext.referenceFunctions(
      bridgeFqName.child("OutboundCall").child("invokeSuspending")
    ).single()

  val outboundCallParameter: IrSimpleFunctionSymbol
    get() = pluginContext.referenceFunctions(bridgeFqName.child("OutboundCall").child("parameter"))
      .single()

  val outboundBridgeContextFqName = bridgeFqName.child("OutboundBridge").child("Context")

  val outboundBridgeContext: IrClassSymbol
    get() = pluginContext.referenceClass(outboundBridgeContextFqName)!!

  val outboundBridgeContextClosed: IrPropertySymbol
    get() = pluginContext.referenceProperties(
      outboundBridgeContextFqName.child("closed")
    ).single()

  val outboundBridgeContextNewCall: IrSimpleFunctionSymbol
    get() = pluginContext.referenceFunctions(
      outboundBridgeContextFqName.child("newCall")
    ).single()

  val outboundBridgeContextSerializersModule: IrPropertySymbol
    get() = pluginContext.referenceProperties(
      outboundBridgeContextFqName.child("serializersModule")
    ).single()

  val ziplineService: IrClassSymbol
    get() = pluginContext.referenceClass(ziplineServiceFqName)!!

  val ziplineServiceAdapter: IrClassSymbol
    get() = pluginContext.referenceClass(ziplineServiceAdapterFqName)!!

  val ziplineServiceAdapterSerialName: IrPropertySymbol
    get() = pluginContext.referenceProperties(
      ziplineServiceAdapterFqName.child("serialName")
    ).single()

  val ziplineServiceAdapterInboundCallHandlers: IrSimpleFunctionSymbol
    get() = ziplineServiceAdapter.functions.single {
      it.owner.name.identifier == "inboundCallHandlers"
    }

  val ziplineServiceAdapterOutboundService: IrSimpleFunctionSymbol
    get() = ziplineServiceAdapter.functions.single {
      it.owner.name.identifier == "outboundService"
    }

  /** Keys are functions like `Zipline.take()` and values are their rewrite targets. */
  val ziplineServiceAdapterFunctions: Map<IrFunctionSymbol, IrSimpleFunctionSymbol> = listOf(
    rewritePair(ziplineFqName.child("take")),
    rewritePair(endpointFqName.child("take")),
    rewritePair(ziplineFqName.child("bind")),
    rewritePair(endpointFqName.child("bind")),
    rewritePair(ziplineServiceAdapterFunctionFqName),
    rewritePair(ziplineServiceSerializerFunctionFqName),
  ).toMap()

  /** Maps overloads from the user-friendly function to its internal rewrite target. */
  private fun rewritePair(funName: FqName): Pair<IrSimpleFunctionSymbol, IrSimpleFunctionSymbol> {
    val overloads = pluginContext.referenceFunctions(funName)
    val rewriteTarget = overloads.single {
      it.owner.valueParameters.lastOrNull()?.type?.classFqName == ziplineServiceAdapterFqName
    }
    val original = overloads.single { it != rewriteTarget }
    return original to rewriteTarget
  }
}
