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

import org.jetbrains.kotlin.backend.common.ScopeWithIr
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.ir.addFakeOverrides
import org.jetbrains.kotlin.backend.common.ir.createImplicitParameterDeclarationWithWrappedDescriptor
import org.jetbrains.kotlin.backend.common.ir.isSuspend
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.declarations.addConstructor
import org.jetbrains.kotlin.ir.builders.declarations.addDispatchReceiver
import org.jetbrains.kotlin.ir.builders.declarations.addFunction
import org.jetbrains.kotlin.ir.builders.declarations.addTypeParameter
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.builders.declarations.buildClass
import org.jetbrains.kotlin.ir.builders.irAs
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irExprBody
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irInt
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.builders.irTemporary
import org.jetbrains.kotlin.ir.builders.irTrue
import org.jetbrains.kotlin.ir.builders.irVararg
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrTypeParameter
import org.jetbrains.kotlin.ir.declarations.IrTypeParametersContainer
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.IrTypeParameterSymbol
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.IrTypeSystemContextImpl
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.makeNullable
import org.jetbrains.kotlin.ir.types.starProjectedType
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.patchDeclarationParents
import org.jetbrains.kotlin.ir.util.substitute
import org.jetbrains.kotlin.name.Name

/**
 * Adds an `Adapter` nested class to the companion object of an interface that extends
 * `ZiplineService`. See `SampleService.Companion.ManualAdapter` for a sample implementation
 * that this class attempts to generate.
 */
internal class AdapterGenerator(
  private val pluginContext: IrPluginContext,
  private val messageCollector: MessageCollector,
  private val ziplineApis: ZiplineApis,
  private val scope: ScopeWithIr,
  private val original: IrClass
) {
  private val irFactory = pluginContext.irFactory
  private val irTypeSystemContext = IrTypeSystemContextImpl(pluginContext.irBuiltIns)

  private val bridgedInterface = BridgedInterface.create(
    pluginContext,
    messageCollector,
    ziplineApis,
    scope,
    original,
    "Zipline.take()",
    original.defaultType
  )

  /** Returns an expression that references the adapter, creating it if necessary. */
  fun adapterExpression(type: IrSimpleType): IrExpression {
    val adapterClass = generateAdapterIfAbsent()
    val irBlockBodyBuilder = irBlockBodyBuilder(pluginContext, scope, original)
    return irBlockBodyBuilder.adapterExpression(adapterClass, type)
  }

  private fun IrBuilderWithScope.adapterExpression(
    adapterClass: IrClass,
    adapterType: IrSimpleType,
  ): IrExpression {
    // Given a declaration like this: interface SampleService<K, V> : ZiplineService
    //     and an instance like this: SampleService<String, Long>
    // Creates a map from the type parameters to their concrete types.
    val substitutionMap = mutableMapOf<IrTypeParameterSymbol, IrType>()
    for (i in original.typeParameters.indices) {
      substitutionMap[original.typeParameters[i].symbol] = adapterType.arguments[i] as IrType
    }

    // listOf(
    //   typeOf<SampleRequest<String>>(),
    //   typeOf<SampleResponse<Long>>(),
    // )
    val typesExpressions = bridgedInterface.serializedTypes().map { serializedType ->
      irCall(
        callee = ziplineApis.typeOfFunction,
        type = ziplineApis.kType.defaultType,
      ).apply {
        putTypeArgument(0, serializedType.substitute(substitutionMap))
      }
    }
    val typesList = irCall(ziplineApis.listOfFunction).apply {
      type = ziplineApis.listOfKType
      putTypeArgument(0, ziplineApis.kType.defaultType)
      putValueArgument(
        0,
        irVararg(
          ziplineApis.kType.defaultType,
          typesExpressions,
        )
      )
    }

    // Adapter<String, Long>(...)
    return irCall(
      callee = adapterClass.constructors.single().symbol,
      type = adapterClass.typeWith(adapterType.arguments.map { it as IrType })
    ).apply {
      for ((i, typeArgument) in adapterType.arguments.withIndex()) {
        putTypeArgument(i, typeArgument as IrType)
      }
      putValueArgument(0, typesList)
    }
  }

  /** Creates the adapter if necessary. */
  fun generateAdapterIfAbsent(): IrClass {
    val companion = getOrCreateCompanion(original, pluginContext)
    return getOrCreateAdapterClass(companion)
  }

  private fun IrTypeParametersContainer.copyTypeParametersFromOriginal(
    suffix: String,
    isReified: Boolean = false,
  ): Map<IrTypeParameter, IrTypeParameter> {
    val result = mutableMapOf<IrTypeParameter, IrTypeParameter>()
    for (typeParameter in original.typeParameters) {
      result[typeParameter] = addTypeParameter {
        this.name = Name.identifier("${typeParameter.name.identifier}$suffix")
        this.superTypes += typeParameter.superTypes
        this.variance = typeParameter.variance
        this.isReified = isReified
      }
    }
    return result
  }

  private fun getOrCreateAdapterClass(
    companion: IrClass
  ): IrClass {
    // class Adapter : ZiplineServiceAdapter<SampleService>(
    //   val types: List<KType>,
    // ), KSerializer<SampleService> {
    //   ...
    // }
    val existing = companion.declarations.firstOrNull {
      it is IrClass && it.name.identifier == "Adapter"
    }
    if (existing != null) return existing as IrClass

    val adapterClass = irFactory.buildClass {
      initDefaults(original)
      name = Name.identifier("Adapter")
      visibility = DescriptorVisibilities.INTERNAL
    }.apply {
      copyTypeParametersFromOriginal("X")
      parent = companion
      superTypes = listOf(
        ziplineApis.ziplineServiceAdapter.typeWith(original.defaultType),
        ziplineApis.kSerializer.typeWith(original.defaultType),
      )
      createImplicitParameterDeclarationWithWrappedDescriptor()
    }

    adapterClass.addConstructor {
      initDefaults(original)
      visibility = DescriptorVisibilities.INTERNAL
    }.apply {
      addValueParameter {
        initDefaults(original)
        name = Name.identifier("types")
        type = ziplineApis.listOfKType
      }
      irConstructorBody(pluginContext) { statements ->
        statements += irDelegatingConstructorCall(
          context = pluginContext,
          symbol = ziplineApis.ziplineServiceAdapter.constructors.single(),
          typeArgumentsCount = 1
        ) {
          putTypeArgument(0, original.defaultType)
        }
        statements += irInstanceInitializerCall(
          context = pluginContext,
          classSymbol = adapterClass.symbol,
        )
      }
    }

    val typesProperty = irTypesProperty(
      adapterClass,
      adapterClass.constructors.single().valueParameters[0]
    )
    adapterClass.declarations += typesProperty

    val serialNameProperty = irSerialNameProperty(adapterClass)
    adapterClass.declarations += serialNameProperty

    var nextId = 0
    val ziplineFunctionClasses = bridgedInterface.bridgedFunctions.associateWith {
      irZiplineFunctionClass(
        "ZiplineFunction${nextId++}",
        bridgedInterface,
        adapterClass,
        it,
      )
    }

    adapterClass.declarations += ziplineFunctionClasses.values

    val ziplineFunctionsFunction = irZiplineFunctionsFunction(
      bridgedInterface = bridgedInterface,
      adapterClass = adapterClass,
      ziplineFunctionClasses = ziplineFunctionClasses,
      typesProperty = typesProperty,
    )

    val outboundServiceClass = irOutboundServiceClass(bridgedInterface, adapterClass)
    val outboundServiceFunction = irOutboundServiceFunction(
      bridgedInterface = bridgedInterface,
      adapterClass = adapterClass,
      outboundServiceClass = outboundServiceClass,
    )

    adapterClass.declarations += outboundServiceClass

    adapterClass.addFakeOverrides(
      irTypeSystemContext,
      listOf(
        serialNameProperty,
        ziplineFunctionsFunction,
        outboundServiceFunction
      )
    )

    companion.declarations += adapterClass
    companion.patchDeclarationParents(original)
    return adapterClass
  }

  /**
   * Override `ZiplineServiceAdapter.serialName`. The constant value is the service's simple name,
   * like "SampleService".
   */
  private fun irSerialNameProperty(adapterClass: IrClass): IrProperty {
    return irVal(
      pluginContext = pluginContext,
      propertyType = pluginContext.symbols.string.defaultType,
      declaringClass = adapterClass,
      propertyName = ziplineApis.ziplineServiceAdapterSerialName.owner.name,
      overriddenProperty = ziplineApis.ziplineServiceAdapterSerialName,
    ) {
      irExprBody(irString(original.name.identifier))
    }
  }

  /** Override `ZiplineServiceAdapter.ziplineFunctions()`. */
  private fun irZiplineFunctionsFunction(
    bridgedInterface: BridgedInterface,
    adapterClass: IrClass,
    ziplineFunctionClasses: Map<IrSimpleFunctionSymbol, IrClass>,
    typesProperty: IrProperty,
  ): IrSimpleFunction {
    // override fun ziplineFunctions(
    //   serializersModule: SerializersModule,
    // ): List<ZiplineFunction<SampleService>> { ... }
    val ziplineFunctionT = ziplineApis.ziplineFunction.typeWith(bridgedInterface.type)
    val listOfZiplineFunctionT = ziplineApis.list.typeWith(ziplineFunctionT)

    val ziplineFunctionsFunction = adapterClass.addFunction {
      initDefaults(original)
      name = ziplineApis.ziplineServiceAdapterZiplineFunctions.owner.name
      returnType = listOfZiplineFunctionT
    }.apply {
      addDispatchReceiver {
        initDefaults(original)
        type = adapterClass.defaultType
      }
      addValueParameter {
        initDefaults(original)
        name = Name.identifier("serializersModule")
        type = ziplineApis.serializersModule.defaultType
      }
      overriddenSymbols = listOf(ziplineApis.ziplineServiceAdapterZiplineFunctions)
    }

    ziplineFunctionsFunction.irFunctionBody(
      context = pluginContext,
      scopeOwnerSymbol = ziplineFunctionsFunction.symbol,
    ) {
      val typesLocal = irTemporary(
        value = irCall(
          callee = typesProperty.getter!!
        ).apply {
          dispatchReceiver = irGet(ziplineFunctionsFunction.dispatchReceiverParameter!!)
        },
        nameHint = "types",
        isMutable = false
      ).apply {
        origin = IrDeclarationOrigin.DEFINED
      }

      val serializers = bridgedInterface.declareSerializerTemporaries(
        statementsBuilder = this@irFunctionBody,
        serializersModuleParameter = ziplineFunctionsFunction.valueParameters[0],
        typesExpression = typesLocal,
      )

      // Call each ZiplineFunction constructor.
      val expressions = mutableListOf<IrExpression>()
      for ((bridgedFunction, handlerClass) in ziplineFunctionClasses) {
        // ZiplineFunction0(
        //   listOf<KSerializer<*>>(
        //     serializer1,
        //     serializer2,
        //   ),
        //   sampleResponseSerializer,
        // )
        expressions += irCall(
          callee = handlerClass.constructors.single().symbol,
          type = handlerClass.defaultType
        ).apply {
          putValueArgument(0, irCall(ziplineApis.listOfFunction).apply {
            putTypeArgument(0, ziplineApis.kSerializer.starProjectedType)
            putValueArgument(0,
              irVararg(
                ziplineApis.kSerializer.starProjectedType,
                bridgedFunction.owner.valueParameters.map { irGet(serializers[it.type]!!) }
              )
            )
          })
          putValueArgument(1, irGet(serializers[bridgedFunction.owner.returnType]!!))
        }
      }

      // return listOf<ZiplineFunction<SampleService>>(
      //   ZiplineFunction0(...),
      //   ZiplineFunction1(...),
      // )
      +irReturn(irCall(ziplineApis.listOfFunction).apply {
        putTypeArgument(0, ziplineFunctionT)
        putValueArgument(0,
          irVararg(
            ziplineFunctionT,
            expressions,
          )
        )
      })
    }

    return ziplineFunctionsFunction
  }

  // class ZiplineFunction0(
  //   argSerializers: List<KSerializer<out Any?>>,
  //   resultSerializer: KSerializer<out Any?>,
  // ) : ZiplineFunction<SampleService>(
  //   "fun ping(app.cash.zipline.SampleRequest): app.cash.zipline.SampleResponse",
  //   argSerializers,
  //   resultSerializer,
  // ) {
  //   ...
  // }
  private fun irZiplineFunctionClass(
    className: String,
    bridgedInterface: BridgedInterface,
    adapterClass: IrClass,
    bridgedFunction: IrSimpleFunctionSymbol,
  ): IrClass {
    val ziplineFunction = irFactory.buildClass {
      initDefaults(original)
      name = Name.identifier(className)
      visibility = DescriptorVisibilities.PRIVATE
    }.apply {
      parent = adapterClass
      superTypes = listOf(ziplineApis.ziplineFunction.typeWith(bridgedInterface.type))
      createImplicitParameterDeclarationWithWrappedDescriptor()
    }

    ziplineFunction.addConstructor {
      initDefaults(original)
    }.apply {
      addValueParameter {
        initDefaults(original)
        name = Name.identifier("argSerializers")
        type = ziplineApis.listOfKSerializerStar
      }
      addValueParameter {
        initDefaults(original)
        name = Name.identifier("resultSerializer")
        type = ziplineApis.kSerializer.starProjectedType
      }
      irConstructorBody(pluginContext) { statements ->
        statements += irDelegatingConstructorCall(
          context = pluginContext,
          symbol = ziplineApis.ziplineFunction.constructors.single(),
          valueArgumentsCount = 3,
        ) {
          putValueArgument(0, irString(bridgedFunction.owner.signature))
          putValueArgument(1, irGet(valueParameters[0]))
          putValueArgument(2, irGet(valueParameters[1]))
        }
        statements += irInstanceInitializerCall(
          context = pluginContext,
          classSymbol = ziplineFunction.symbol,
        )
      }
    }

    val callFunction = irCallFunction(
      ziplineFunctionClass = ziplineFunction,
      callSuspending = bridgedFunction.isSuspend,
      bridgedInterface = bridgedInterface,
    )

    // We add overrides here so we can call them below.
    ziplineFunction.addFakeOverrides(
      irTypeSystemContext,
      listOf(callFunction)
    )

    callFunction.irFunctionBody(
      context = pluginContext,
      scopeOwnerSymbol = callFunction.symbol,
    ) {
      +irReturn(
        irCallServiceFunction(
          bridgedInterface = bridgedInterface,
          callFunction = callFunction,
          bridgedFunction = bridgedFunction,
        )
      )
    }

    return ziplineFunction
  }

  /** Override either `ZiplineFunction.call()` or `ZiplineFunction.callSuspending()`. */
  private fun irCallFunction(
    ziplineFunctionClass: IrClass,
    bridgedInterface: BridgedInterface,
    callSuspending: Boolean,
  ): IrSimpleFunction {
    // override fun call(service: SampleService, args: List<*>): Any? {
    // }
    val ziplineFunctionCall = when {
      callSuspending -> ziplineApis.ziplineFunctionCallSuspending
      else -> ziplineApis.ziplineFunctionCall
    }
    return ziplineFunctionClass.addFunction {
      initDefaults(original)
      name = ziplineFunctionCall.owner.name
      returnType = pluginContext.symbols.any.defaultType.makeNullable()
      isSuspend = callSuspending
    }.apply {
      addDispatchReceiver {
        initDefaults(original)
        type = ziplineFunctionClass.defaultType
      }
      addValueParameter {
        initDefaults(original)
        name = Name.identifier("service")
        type = bridgedInterface.type
      }
      addValueParameter {
        initDefaults(original)
        name = Name.identifier("args")
        type = ziplineApis.list.starProjectedType
      }
      overriddenSymbols = listOf(ziplineFunctionCall)
    }
  }

  /** service.function(args[0] as Arg1Type, args[1] as Arg2Type) */
  private fun IrBuilderWithScope.irCallServiceFunction(
    bridgedInterface: BridgedInterface,
    callFunction: IrSimpleFunction,
    bridgedFunction: IrSimpleFunctionSymbol,
  ): IrExpression {
    return irCall(
      type = bridgedInterface.resolveTypeParameters(bridgedFunction.owner.returnType),
      callee = bridgedFunction,
      valueArgumentsCount = bridgedFunction.owner.valueParameters.size,
    ).apply {
      dispatchReceiver = irGet(callFunction.valueParameters[0])

      for (p in bridgedFunction.owner.valueParameters.indices) {
        putValueArgument(
          p,
          irAs(
            irCall(ziplineApis.listGetFunction).apply {
              dispatchReceiver = irGet(callFunction.valueParameters[1])
              putValueArgument(0, irInt(p))
            },
            bridgedInterface.resolveTypeParameters(
              bridgedFunction.owner.valueParameters[p].type
            ),
          )
        )
      }
    }
  }

  /** Override `ZiplineServiceAdapter.outboundService()`. */
  private fun irOutboundServiceFunction(
    bridgedInterface: BridgedInterface,
    adapterClass: IrClass,
    outboundServiceClass: IrClass,
  ): IrSimpleFunction {
    // override fun outboundService(callHandler: OutboundCallHandler): SampleService
    val outboundServiceFunction = adapterClass.addFunction {
      initDefaults(original)
      name = ziplineApis.ziplineServiceAdapterOutboundService.owner.name
      returnType = bridgedInterface.type
    }.apply {
      addDispatchReceiver {
        initDefaults(original)
        type = adapterClass.defaultType
      }
      addValueParameter {
        initDefaults(original)
        name = Name.identifier("callHandler")
        type = ziplineApis.outboundCallHandler.defaultType
      }
      overriddenSymbols = listOf(ziplineApis.ziplineServiceAdapterOutboundService)
    }
    outboundServiceFunction.irFunctionBody(
      context = pluginContext,
      scopeOwnerSymbol = outboundServiceFunction.symbol,
    ) {
      +irReturn(
        irCall(
          callee = outboundServiceClass.constructors.single().symbol,
          type = outboundServiceClass.defaultType
        ).apply {
          putValueArgument(0, irGet(outboundServiceFunction.valueParameters[0]))
        }
      )
    }
    return outboundServiceFunction
  }

  // private class GeneratedOutboundService(
  //   private val callHandler: OutboundCallHandler
  // ) : SampleService {
  //   override fun ping(request: SampleRequest): SampleResponse { ... }
  // }
  private fun irOutboundServiceClass(
    bridgedInterface: BridgedInterface,
    adapterClass: IrClass,
  ): IrClass {
    val outboundServiceClass = irFactory.buildClass {
      initDefaults(original)
      name = Name.identifier("GeneratedOutboundService")
      visibility = DescriptorVisibilities.PRIVATE
    }.apply {
      parent = adapterClass
      superTypes = listOf(bridgedInterface.type)
      createImplicitParameterDeclarationWithWrappedDescriptor()
    }

    val constructor = outboundServiceClass.addConstructor {
      initDefaults(original)
    }.apply {
      addValueParameter {
        initDefaults(original)
        name = Name.identifier("callHandler")
        type = ziplineApis.outboundCallHandler.defaultType
      }
    }
    constructor.irConstructorBody(pluginContext) { statements ->
      statements += irDelegatingConstructorCall(
        context = pluginContext,
        symbol = ziplineApis.any.constructors.single(),
      )
      statements += irInstanceInitializerCall(
        context = pluginContext,
        classSymbol = outboundServiceClass.symbol,
      )
    }

    val callHandlerProperty = irCallHandlerProperty(
      outboundServiceClass,
      constructor.valueParameters[0]
    )
    outboundServiceClass.declarations += callHandlerProperty

    for ((i, overridesList) in bridgedInterface.bridgedFunctionsWithOverrides.values.withIndex()) {
      outboundServiceClass.irBridgedFunction(
        functionIndex = i,
        bridgedInterface = bridgedInterface,
        callHandlerProperty = callHandlerProperty,
        overridesList = overridesList,
      )
    }

    outboundServiceClass.addFakeOverrides(
      irTypeSystemContext,
      outboundServiceClass.functions.toList(),
    )

    return outboundServiceClass
  }

  // override val callHandler: OutboundCallHandler = callHandler
  private fun irCallHandlerProperty(
    outboundCallHandlerClass: IrClass,
    irCallHandlerParameter: IrValueParameter
  ): IrProperty {
    return irVal(
      pluginContext = pluginContext,
      propertyType = ziplineApis.outboundCallHandler.defaultType,
      declaringClass = outboundCallHandlerClass,
      propertyName = Name.identifier("callHandler")
    ) {
      irExprBody(irGet(irCallHandlerParameter))
    }
  }

  // override val types: List<Ktype> = types
  private fun irTypesProperty(
    adapterClass: IrClass,
    irTypesParameter: IrValueParameter
  ): IrProperty {
    return irVal(
      pluginContext = pluginContext,
      propertyType = ziplineApis.listOfKType,
      declaringClass = adapterClass,
      propertyName = Name.identifier("types")
    ) {
      irExprBody(irGet(irTypesParameter))
    }
  }

  private fun IrClass.irBridgedFunction(
    functionIndex: Int,
    bridgedInterface: BridgedInterface,
    callHandlerProperty: IrProperty,
    overridesList: List<IrSimpleFunctionSymbol>,
  ): IrSimpleFunction {
    // override fun ping(request: SampleRequest): SampleResponse {
    //   return callHandler.call(this, 0, request) as SampleResponse
    // }
    val bridgedFunction = overridesList[0].owner
    val functionReturnType = bridgedInterface.resolveTypeParameters(bridgedFunction.returnType)
    val result = addFunction {
      initDefaults(original)
      name = bridgedFunction.name
      isSuspend = bridgedFunction.isSuspend
      returnType = functionReturnType
    }.apply {
      overriddenSymbols = overridesList
      addDispatchReceiver {
        initDefaults(original)
        type = defaultType
      }
    }

    for (valueParameter in bridgedFunction.valueParameters) {
      result.addValueParameter {
        initDefaults(original)
        name = valueParameter.name
        type = bridgedInterface.resolveTypeParameters(valueParameter.type)
      }
    }

    // return callHandler.call(this, 0, request) as SampleResponse
    result.irFunctionBody(
      context = pluginContext,
      scopeOwnerSymbol = result.symbol
    ) {
      val callHandlerLocal = irTemporary(
        value = irCall(
          callee = callHandlerProperty.getter!!
        ).apply {
          dispatchReceiver = irGet(result.dispatchReceiverParameter!!)
        },
        nameHint = "callHandler",
        isMutable = false
      ).apply {
        origin = IrDeclarationOrigin.DEFINED
      }

      // If this is the close() function, tell the OutboundCallHandler that this instance is closed.
      //   callHandler.close = true
      if (bridgedFunction.isZiplineClose()) {
        +irCall(ziplineApis.outboundCallHandlerClosed.owner.setter!!).apply {
          dispatchReceiver = irGet(callHandlerLocal)
          putValueArgument(0, irTrue())
        }
      }

      // One of:
      //   return callHandler.call(service, index, arg0, arg1, arg2)
      //   return callHandler.callSuspending(service, index, arg0, arg1, arg2)
      val callFunction = when {
        bridgedFunction.isSuspend -> ziplineApis.outboundCallHandlerCallSuspending
        else -> ziplineApis.outboundCallHandlerCall
      }
      val call = irCall(callFunction).apply {
        dispatchReceiver = irGet(callHandlerLocal)
        putValueArgument(
          0,
          irGet(result.dispatchReceiverParameter!!),
        )
        putValueArgument(
          1,
          irInt(functionIndex),
        )
        putValueArgument(
          2,
          irVararg(
            elementType = pluginContext.symbols.any.defaultType.makeNullable(),
            values = result.valueParameters.map {
              irGet(
                type = it.type,
                variable = it.symbol,
              )
            }
          )
        )
      }

      +irReturn(
        value = irAs(
          argument = call,
          type = functionReturnType
        ),
        returnTargetSymbol = result.symbol,
        type = pluginContext.irBuiltIns.nothingType,
      )
    }

    return result
  }

  private fun IrSimpleFunction.isZiplineClose() =
    name.asString() == "close" && valueParameters.isEmpty()
}
