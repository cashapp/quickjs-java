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
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageLocationWithRange
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.builders.IrBlockBodyBuilder
import org.jetbrains.kotlin.ir.builders.IrBlockBuilder
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.IrGeneratorContext
import org.jetbrains.kotlin.ir.builders.Scope
import org.jetbrains.kotlin.ir.builders.declarations.IrClassBuilder
import org.jetbrains.kotlin.ir.builders.declarations.IrFunctionBuilder
import org.jetbrains.kotlin.ir.builders.declarations.IrValueParameterBuilder
import org.jetbrains.kotlin.ir.builders.declarations.addConstructor
import org.jetbrains.kotlin.ir.builders.declarations.buildClass
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irGetField
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.createBlockBody
import org.jetbrains.kotlin.ir.expressions.IrDelegatingConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrExpressionBody
import org.jetbrains.kotlin.ir.expressions.IrInstanceInitializerCall
import org.jetbrains.kotlin.ir.expressions.IrMemberAccessExpression
import org.jetbrains.kotlin.ir.expressions.IrReturn
import org.jetbrains.kotlin.ir.expressions.impl.IrClassReferenceImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrDelegatingConstructorCallImplWithShape
import org.jetbrains.kotlin.ir.expressions.impl.IrInstanceInitializerCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrReturnImpl
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrConstructorSymbol
import org.jetbrains.kotlin.ir.symbols.IrFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.IrPropertySymbol
import org.jetbrains.kotlin.ir.symbols.IrReturnTargetSymbol
import org.jetbrains.kotlin.ir.symbols.IrSymbol
import org.jetbrains.kotlin.ir.symbols.impl.IrFieldSymbolImpl
import org.jetbrains.kotlin.ir.symbols.impl.IrPropertySymbolImpl
import org.jetbrains.kotlin.ir.symbols.impl.IrSimpleFunctionSymbolImpl
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.SYNTHETIC_OFFSET
import org.jetbrains.kotlin.ir.util.companionObject
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.createDispatchReceiverParameter
import org.jetbrains.kotlin.ir.util.createImplicitParameterDeclarationWithWrappedDescriptor
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.StandardClassIds

/** Returns a string as specified by ZiplineFunction.signature. */
internal val IrSimpleFunction.signature: String
  get() = buildString {
    val property = correspondingPropertySymbol
    if (property != null) {
      val name = property.owner.name.asString()
      if (valueParameters.size == 1) {
        // setter
        append(
          "var $name: ${(valueParameters[0].type as IrSimpleType).asString()}",
        )
      } else {
        // getter
        append(
          "val $name: ${(returnType as IrSimpleType).asString()}",
        )
      }
    } else {
      if (isSuspend) {
        append("suspend ")
      }
      append(
        "fun ${name.identifier}(${valueParameters.joinToString { (it.type as IrSimpleType).asString() }}): ${(returnType as IrSimpleType).asString()}",
      )
    }
  }

/** Returns a string as specified by ZiplineFunction.id. */
internal val IrSimpleFunction.id: String
  get() = signature.signatureHash()

/** Thrown on invalid or unexpected input code. */
class ZiplineCompilationException(
  override val message: String,
  val element: IrElement? = null,
  val severity: CompilerMessageSeverity = CompilerMessageSeverity.ERROR,
) : Exception(message)

/** Finds the line and column of [irElement] within this file. */
fun IrFile.locationOf(irElement: IrElement?): CompilerMessageSourceLocation {
  val sourceRangeInfo = fileEntry.getSourceRangeInfo(
    beginOffset = irElement?.startOffset ?: SYNTHETIC_OFFSET,
    endOffset = irElement?.endOffset ?: SYNTHETIC_OFFSET,
  )
  return CompilerMessageLocationWithRange.create(
    path = sourceRangeInfo.filePath,
    lineStart = sourceRangeInfo.startLineNumber + 1,
    columnStart = sourceRangeInfo.startColumnNumber + 1,
    lineEnd = sourceRangeInfo.endLineNumber + 1,
    columnEnd = sourceRangeInfo.endColumnNumber + 1,
    lineContent = null,
  )!!
}

/** `return ...` */
internal fun IrBuilderWithScope.irReturn(
  value: IrExpression,
  returnTargetSymbol: IrReturnTargetSymbol,
  type: IrType = value.type,
): IrReturn {
  return IrReturnImpl(
    startOffset = startOffset,
    endOffset = endOffset,
    type = type,
    returnTargetSymbol = returnTargetSymbol,
    value = value,
  )
}

/** Set up reasonable defaults for a generated function or constructor. */
fun IrFunctionBuilder.initDefaults(original: IrElement) {
  this.startOffset = original.startOffset.toSyntheticIfUnknown()
  this.endOffset = original.endOffset.toSyntheticIfUnknown()
  this.origin = IrDeclarationOrigin.DEFINED
  this.visibility = DescriptorVisibilities.PUBLIC
  this.modality = Modality.OPEN
  this.isPrimary = true
}

/** Set up reasonable defaults for a generated class. */
fun IrClassBuilder.initDefaults(original: IrElement) {
  this.startOffset = original.startOffset.toSyntheticIfUnknown()
  this.endOffset = original.endOffset.toSyntheticIfUnknown()
  this.name = Name.special("<no name provided>")
  this.visibility = DescriptorVisibilities.LOCAL
}

/** Set up reasonable defaults for a value parameter. */
fun IrValueParameterBuilder.initDefaults(original: IrElement) {
  this.startOffset = original.startOffset.toSyntheticIfUnknown()
  this.endOffset = original.endOffset.toSyntheticIfUnknown()
}

/**
 * When we generate code based on classes outside of the current module unit we get elements that
 * use `UNDEFINED_OFFSET`. Make sure we don't propagate this further into generated code; that
 * causes LLVM code generation to fail.
 */
private fun Int.toSyntheticIfUnknown(): Int {
  return when (this) {
    UNDEFINED_OFFSET -> SYNTHETIC_OFFSET
    else -> this
  }
}

fun IrConstructor.irConstructorBody(
  context: IrGeneratorContext,
  blockBody: DeclarationIrBuilder.(MutableList<IrStatement>) -> Unit,
) {
  val constructorIrBuilder = DeclarationIrBuilder(
    generatorContext = context,
    symbol = IrSimpleFunctionSymbolImpl(),
    startOffset = startOffset,
    endOffset = endOffset,
  )
  body = context.irFactory.createBlockBody(
    startOffset = constructorIrBuilder.startOffset,
    endOffset = constructorIrBuilder.endOffset,
  ) {
    constructorIrBuilder.blockBody(statements)
  }
}

fun DeclarationIrBuilder.irDelegatingConstructorCall(
  context: IrGeneratorContext,
  symbol: IrConstructorSymbol,
  typeArgumentsCount: Int = 0,
  valueArgumentsCount: Int = 0,
  block: IrDelegatingConstructorCall.() -> Unit = {},
): IrDelegatingConstructorCall {
  val result = IrDelegatingConstructorCallImplWithShape(
    startOffset = startOffset,
    endOffset = endOffset,
    type = context.irBuiltIns.unitType,
    symbol = symbol,
    typeArgumentsCount = typeArgumentsCount,
    valueArgumentsCount = valueArgumentsCount,
    // Note: These three parameters are unused in the Kotlin 2.1.0 implementation of this factory.
    contextParameterCount = 0,
    hasDispatchReceiver = false,
    hasExtensionReceiver = false,
  )
  result.block()
  return result
}

fun DeclarationIrBuilder.irInstanceInitializerCall(
  context: IrGeneratorContext,
  classSymbol: IrClassSymbol,
): IrInstanceInitializerCall {
  return IrInstanceInitializerCallImpl(
    startOffset = startOffset,
    endOffset = endOffset,
    classSymbol = classSymbol,
    type = context.irBuiltIns.unitType,
  )
}

fun IrSimpleFunction.irFunctionBody(
  context: IrGeneratorContext,
  scopeOwnerSymbol: IrSymbol,
  blockBody: IrBlockBodyBuilder.() -> Unit,
) {
  val bodyBuilder = IrBlockBodyBuilder(
    startOffset = startOffset,
    endOffset = endOffset,
    context = context,
    scope = Scope(scopeOwnerSymbol),
  )
  body = bodyBuilder.blockBody {
    blockBody()
  }
}

/** Create a private val with a backing field and an accessor function. */
fun irVal(
  pluginContext: IrPluginContext,
  propertyType: IrType,
  declaringClass: IrClass,
  propertyName: Name,
  overriddenProperty: IrPropertySymbol? = null,
  initializer: IrBlockBuilder.() -> IrExpressionBody,
): IrProperty {
  val irFactory = pluginContext.irFactory
  val result = irFactory.createProperty(
    startOffset = declaringClass.startOffset,
    endOffset = declaringClass.endOffset,
    origin = IrDeclarationOrigin.DEFINED,
    symbol = IrPropertySymbolImpl(),
    name = propertyName,
    visibility = overriddenProperty?.owner?.visibility ?: DescriptorVisibilities.PRIVATE,
    modality = Modality.FINAL,
    isVar = false,
    isConst = false,
    isLateinit = false,
    isDelegated = false,
    isExternal = false,
    isExpect = false,
    isFakeOverride = false,
    containerSource = null,
  ).apply {
    overriddenSymbols = listOfNotNull(overriddenProperty)
    parent = declaringClass
  }

  result.backingField = irFactory.createField(
    startOffset = declaringClass.startOffset,
    endOffset = declaringClass.endOffset,
    origin = IrDeclarationOrigin.PROPERTY_BACKING_FIELD,
    symbol = IrFieldSymbolImpl(),
    name = result.name,
    type = propertyType,
    visibility = DescriptorVisibilities.PRIVATE,
    isFinal = true,
    isExternal = false,
    isStatic = false,
  ).apply {
    parent = declaringClass
    correspondingPropertySymbol = result.symbol
    val initializerBuilder = IrBlockBuilder(
      startOffset = declaringClass.startOffset,
      endOffset = declaringClass.endOffset,
      context = pluginContext,
      scope = Scope(symbol),
    )
    this.initializer = initializerBuilder.initializer()
  }

  result.getter = irFactory.createSimpleFunction(
    startOffset = declaringClass.startOffset,
    endOffset = declaringClass.endOffset,
    origin = IrDeclarationOrigin.DEFAULT_PROPERTY_ACCESSOR,
    name = Name.special("<get-${propertyName.identifier}>"),
    visibility = overriddenProperty?.owner?.getter?.visibility ?: DescriptorVisibilities.PRIVATE,
    isInline = false,
    isExpect = false,
    returnType = propertyType,
    modality = Modality.FINAL,
    symbol = IrSimpleFunctionSymbolImpl(),
    isTailrec = false,
    isSuspend = false,
    isOperator = false,
    isInfix = false,
    isExternal = false,
    containerSource = null,
    isFakeOverride = false,
  ).apply {
    parent = declaringClass
    correspondingPropertySymbol = result.symbol
    overriddenSymbols = listOfNotNull(overriddenProperty?.owner?.getter?.symbol)
    createDispatchReceiverParameter()
    irFunctionBody(
      context = pluginContext,
      scopeOwnerSymbol = symbol,
    ) {
      +irReturn(
        value = irGetField(
          irGet(dispatchReceiverParameter!!),
          result.backingField!!,
        ),
      )
    }
  }

  return result
}

internal fun IrBuilderWithScope.irKClass(
  containerClass: IrClass,
): IrClassReferenceImpl {
  return IrClassReferenceImpl(
    startOffset = startOffset,
    endOffset = endOffset,
    type = context.irBuiltIns.kClassClass.typeWith(containerClass.defaultType),
    symbol = containerClass.symbol,
    classType = containerClass.defaultType,
  )
}

fun irBlockBodyBuilder(
  irPluginContext: IrGeneratorContext,
  scopeWithIr: ScopeWithIr,
  original: IrElement,
): IrBlockBodyBuilder {
  return IrBlockBodyBuilder(
    context = irPluginContext,
    scope = scopeWithIr.scope,
    startOffset = original.startOffset.toSyntheticIfUnknown(),
    endOffset = original.endOffset.toSyntheticIfUnknown(),
  )
}

fun irBlockBuilder(
  irPluginContext: IrGeneratorContext,
  scopeWithIr: ScopeWithIr,
  original: IrElement,
): IrBlockBuilder {
  return IrBlockBuilder(
    context = irPluginContext,
    scope = scopeWithIr.scope,
    startOffset = original.startOffset.toSyntheticIfUnknown(),
    endOffset = original.endOffset.toSyntheticIfUnknown(),
  )
}

/** This creates `companion object` if it doesn't exist already. */
fun getOrCreateCompanion(
  enclosing: IrClass,
  irPluginContext: IrPluginContext,
): IrClass {
  val existing = enclosing.companionObject()
  if (existing != null) return existing

  val irFactory = irPluginContext.irFactory
  val anyType = irPluginContext.referenceClass(StandardClassIds.Any)!!
  val companionClass = irFactory.buildClass {
    initDefaults(enclosing)
    name = Name.identifier("Companion")
    visibility = DescriptorVisibilities.PUBLIC
    kind = ClassKind.OBJECT
    isCompanion = true
  }.apply {
    parent = enclosing
    superTypes = listOf(irPluginContext.irBuiltIns.anyType)
    createImplicitParameterDeclarationWithWrappedDescriptor()
  }

  companionClass.addConstructor {
    initDefaults(enclosing)
    visibility = DescriptorVisibilities.PRIVATE
  }.apply {
    irConstructorBody(irPluginContext) { statements ->
      statements += irDelegatingConstructorCall(
        context = irPluginContext,
        symbol = anyType.constructors.single(),
      )
      statements += irInstanceInitializerCall(
        context = irPluginContext,
        classSymbol = companionClass.symbol,
      )
    }
  }

  enclosing.declarations.add(companionClass)
  return companionClass
}

// https://github.com/JetBrains/kotlin/blob/d625d9a988f3a7a344ce1687b085ff7c811e916c/plugins/kotlinx-serialization/kotlinx-serialization.backend/src/org/jetbrains/kotlinx/serialization/compiler/backend/ir/IrBuilderWithPluginContext.kt#L199-L211
fun IrBuilderWithScope.irInvoke(
  dispatchReceiver: IrExpression? = null,
  callee: IrFunctionSymbol,
  vararg args: IrExpression,
  typeHint: IrType? = null,
): IrMemberAccessExpression<*> {
  assert(callee.isBound) { "Symbol $callee expected to be bound" }
  val returnType = typeHint ?: callee.owner.returnType
  val call = irCall(callee, type = returnType)
  call.dispatchReceiver = dispatchReceiver
  args.forEachIndexed(call::putValueArgument)
  return call
}

// https://github.com/JetBrains/kotlin/blob/d625d9a988f3a7a344ce1687b085ff7c811e916c/plugins/kotlinx-serialization/kotlinx-serialization.backend/src/org/jetbrains/kotlinx/serialization/compiler/backend/ir/IrBuilderWithPluginContext.kt#L213-L225
fun IrBuilderWithScope.irInvoke(
  dispatchReceiver: IrExpression? = null,
  callee: IrFunctionSymbol,
  typeArguments: List<IrType?>,
  valueArguments: List<IrExpression>,
  returnTypeHint: IrType? = null,
): IrMemberAccessExpression<*> =
  irInvoke(
    dispatchReceiver,
    callee,
    *valueArguments.toTypedArray(),
    typeHint = returnTypeHint,
  ).also { call -> typeArguments.forEachIndexed(call::putTypeArgument) }
