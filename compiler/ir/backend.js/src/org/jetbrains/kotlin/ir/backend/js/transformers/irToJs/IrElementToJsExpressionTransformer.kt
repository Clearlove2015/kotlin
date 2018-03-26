/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.backend.js.transformers.irToJs

import org.jetbrains.kotlin.ir.backend.js.utils.isPrimary
import org.jetbrains.kotlin.ir.backend.js.utils.name
import org.jetbrains.kotlin.ir.backend.js.utils.parameterCount
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.symbols.IrConstructorSymbol
import org.jetbrains.kotlin.js.backend.ast.*

class IrElementToJsExpressionTransformer : BaseIrElementToJsNodeTransformer<JsExpression, Nothing?> {
    override fun visitExpressionBody(body: IrExpressionBody, data: Nothing?): JsExpression {
        return body.expression.accept(this, data)
    }

    override fun <T> visitConst(expression: IrConst<T>, data: Nothing?): JsExpression {
        val kind = expression.kind
        return when (kind) {
            is IrConstKind.String -> JsStringLiteral(kind.valueOf(expression))
            is IrConstKind.Null -> JsNullLiteral()
            is IrConstKind.Boolean -> JsBooleanLiteral(kind.valueOf(expression))
            is IrConstKind.Byte -> JsIntLiteral(kind.valueOf(expression).toInt())
            is IrConstKind.Short -> JsIntLiteral(kind.valueOf(expression).toInt())
            is IrConstKind.Int -> JsIntLiteral(kind.valueOf(expression))
            is IrConstKind.Long,
            is IrConstKind.Char -> super.visitConst(expression, data)
            is IrConstKind.Float -> JsDoubleLiteral(kind.valueOf(expression).toDouble())
            is IrConstKind.Double -> JsDoubleLiteral(kind.valueOf(expression))
        }
    }

    override fun visitStringConcatenation(expression: IrStringConcatenation, data: Nothing?): JsExpression {
        // TODO revisit
        return expression.arguments.fold<IrExpression, JsExpression>(JsStringLiteral("")) { jsExpr, irExpr ->
            JsBinaryOperation(
                JsBinaryOperator.ADD,
                jsExpr,
                irExpr.accept(this, data)
            )
        }
    }

    override fun visitGetValue(expression: IrGetValue, data: Nothing?): JsExpression {
        return JsNameRef(expression.symbol.name.toJsName())
    }

    override fun visitSetVariable(expression: IrSetVariable, data: Nothing?): JsExpression {
        val ref = JsNameRef(expression.symbol.name.toJsName())
        val value = expression.value.accept(this, data)
        return JsBinaryOperation(JsBinaryOperator.ASG, ref, value)
    }

    override fun visitCall(expression: IrCall, data: Nothing?): JsExpression {
        // TODO rewrite more accurately, right now it just copy-pasted and adopted from old version
        // TODO support:
        // * ir intrinsics
        // * js be intrinsics
        // * js function
        // * getters and setters
        // * binary and unary operations

        val symbol = expression.symbol

        val dispatchReceiver = expression.dispatchReceiver?.accept(this, data)
        val extensionReceiver = expression.extensionReceiver?.accept(this, data)

        // TODO sanitize name
        val symbolName = (symbol.owner as IrSimpleFunction).name.asString()
        val ref = if (dispatchReceiver != null) JsNameRef(symbolName, dispatchReceiver) else JsNameRef(symbolName)

        val arguments =
            // TODO mapTo?
            (0 until expression.symbol.parameterCount).map {
                val argument = expression.getValueArgument(it)
                if (argument != null) {
                    argument.accept(this, data)
                } else {
                    JsPrefixOperation(JsUnaryOperator.VOID, JsIntLiteral(1))
                }
            }


        if (symbol is IrConstructorSymbol && symbol.isPrimary) {
            return JsNew(JsNameRef((symbol.owner.parent as IrClass).name.asString()), arguments)
        }

        return JsInvocation(ref, extensionReceiver?.let { listOf(extensionReceiver) + arguments } ?: arguments)
    }

    override fun visitWhen(expression: IrWhen, data: Nothing?): JsExpression {
        // TODO check when w/o else branch and empty when
        return expression.toJsNode(this, data, ::JsConditional)!!
    }
}