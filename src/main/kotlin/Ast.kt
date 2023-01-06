package io.github.mathmaster13.avcbasic

typealias AstList = ArrayList<Ast>

sealed interface Ast {
    fun visit(): String // TODO this is default for debug only
    fun evaluate(): Ast = this // constant folding
    
    data class Program(val children: AstList) : Ast {
        override fun visit(): String =
            visitAll(children) { "${it.visit()}\n" }
                .append("LIT #00 LIT2 #ff #0f STA #ef")
                .toString()
        override fun evaluate(): Ast = evaluateAll(children)
    }
    data class BinOp(var left: Ast, val op: Token.ArithmeticOp, var right: Ast) : Ast {
        override fun evaluate(): Ast {
            left = left.evaluate()
            right = right.evaluate()
            if (left is Num && right is Num) return Num(op.operation((left as Num).value, (right as Num).value))
            val leftIsZero = left == Num(0)
            return if (leftIsZero || right == Num(0)) when (op) {
                // optimizations for zero if only one number is known
                Token.ArithmeticOp.ADD -> {
                    if (leftIsZero) right else left
                }
                Token.ArithmeticOp.SUBTRACT -> {
                    if (leftIsZero) UnaryOp(right, Token.ArithmeticOp.SUBTRACT) else left
                }
                Token.ArithmeticOp.MULTIPLY -> Num(0)
                Token.ArithmeticOp.DIVIDE -> {
                    if (leftIsZero) Num(0) else throw ArithmeticException("Division by zero")
                }
            } else this
        }
        override fun visit(): String = "${left.visit()} ${right.visit()} ${
            when (op) {
                Token.ArithmeticOp.ADD -> "CLC ADC2"
                Token.ArithmeticOp.SUBTRACT -> "SEC SBC2"
                else -> run {
                    val (sr1, sr2, multLabel) = nextLabels
                    """OVR2 SWP POP OVR XOR LIT #07 SFT LIT #01 XOR LIT #00 SWP2 ROT2 ${removeSign(sr1)} SWP2 ${removeSign(sr2)} ${when (op) {
                        Token.ArithmeticOp.MULTIPLY -> "MUL2"
                        Token.ArithmeticOp.DIVIDE -> "SWP2 DVM2 POP2"
                        else -> throw AssertionError("major L - this should be unreachable")
                    }} SWP2 POP DUP LIT2 .absc($multLabel) JNZ2 POP $NEGATE
                    .lbl($multLabel)"""
                }
            }
        }"
        
        companion object {
            private var label = 'A'
            val nextLabels get() = Triple("AVCBASIC_SR1_$label", "AVCBASIC_SR2_$label", "AVCBASIC_MULT_${label++}")
            fun removeSign(label: String) = "DUP LIT #07 SFT LIT #01 XOR LIT2 .absc($label) JNZ2\n$NEGATE\n.lbl($label)"
        }
    }
    data class RelOp(var left: Ast, val op: Token.RelOp, var right: Ast) : Ast {
        override fun evaluate(): Ast {
            left.evaluate()
            right.evaluate()
            if (left is Num && right is Num) return Bool(op.operation((left as Num).value, (right as Num).value))
            return this
        }
        override fun visit(): String =
            "${if (op == Token.RelOp.NE) "${left.visit()} ${right.visit()}" else "${right.visit()} ${left.visit()}"} ${
            when (op) {
                Token.RelOp.GT -> "GTH2"
                Token.RelOp.EQ -> "EQU2"
                Token.RelOp.GE -> "GTHk2 LIT #00 SWP2 ROT2 EQU2 SWP POP IOR"
                Token.RelOp.LT -> "GTHk2 LIT #00 SWP2 ROT2 EQU2 ROT IOR SEC ADC"
                Token.RelOp.LE -> "GTH2 LIT #00 SEC ADC"
                Token.RelOp.NE -> "SEC SBC2" // subtraction works as NE since a - b is nonzero if a != b
            }
        }"
        @Suppress("NOTHING_TO_INLINE")
        inline fun not(): Ast = copy(op = this.op.not)
    }
    data class Bool(val value: Boolean) : Ast { // ONLY for boolean constant folding
        override fun visit(): String = throw AssertionError("Boolean AST nodes should never appear in a final AST.")
    }
    data class UnaryOp(var operand: Ast, val op: Token.ArithmeticOp) : Ast {
        override fun evaluate(): Ast {
            operand = operand.evaluate()
            if (operand is Num) return when (op) {
                Token.ArithmeticOp.ADD -> Num((operand as Num).value)
                Token.ArithmeticOp.SUBTRACT -> Num((-(operand as Num).value).toShort())
                else -> throw AssertionError("Multiplicative or division operator in UnaryOp: $this")
            }
            return this
        }
        override fun visit(): String = "${operand.visit()}${
            when (op) {
                Token.ArithmeticOp.ADD -> ""
                Token.ArithmeticOp.SUBTRACT -> NEGATE
                else -> throw AssertionError("Multiplicative or division operator in UnaryOp: $this")
            }
        }"
    }
    data class Num(val value: Short) : Ast {
        override fun visit(): String {
            val asString = value.toUShort().toString(16).padStart(4, '0')
            return "LIT2 #${asString.substring(0, 2)} #${asString.substring(2)}"
        }
    }
    data class Str(val value: String) : Ast {
        override fun visit(): Nothing = throw AssertionError("Str.visit should be unreachable")
    }
    data class Var(val value: Token.ID) : Ast { // only for use in expressions, not assignments
        override fun visit(): String {
            if (value !in usedVars.keys) throw UninitializedVariableException("Variable ${value.value} used before initialization")
            val addr = usedVars[value]!!
            return "${Num(addr).visit()} LDA2"
        }
        
        companion object {
            val usedVars =  hashMapOf<Token.ID, Short>()
        }
        
        class UninitializedVariableException(message: String) : RuntimeException(message)
    }
    data class LabeledStatement(val label: LabelDirective?, var statement: Ast) : Ast {
        override fun evaluate(): Ast {
            statement = statement.evaluate()
            if (label == null) return statement
            return this
        }
        override fun visit() = "${if (label != null) "${label.visit()}\n" else ""}${statement.visit()}"
    }
    data class LabelDirective(val label: Token.Label) : Ast {
        override fun visit(): String {
            if (label in usedLabels) throw ParseError("Label ${label.value} cannot be used more than once")
            usedLabels.add(label)
            return ".lbl(${label.value})"
        }
        
        companion object {
            val usedLabels =  hashSetOf<Token.Label>()
        }
    }
    data class PrintStatement(val expressions: AstList) : Ast {
        override fun evaluate(): Ast {
            for (i in expressions.indices) {
                val newExpr = expressions[i].evaluate()
                expressions[i] = if (newExpr is Num) Str(newExpr.value.toString()) else newExpr
            }
            return this
        }
        override fun visit(): String {
            return visitAllStringBuilder(expressions) { expr ->
                when (expr) {
                    is Str -> expr.value.forEach {
                        append("LIT #${it.code.toUByte().toString(16).padStart(2, '0')} LIT2 #ff #09 STA\n")
                    }
                    is UnaryOp, is BinOp, is Num, is Var, is Rnd -> run {
                        val (l1, l2) = nextLabels
                        // TODO remove the null character from the end
                        append("LIT #00\n${expr.visit()}\n${removeSign(l1)}\nLIT2 #00 #0a DVM2 POP LIT #30 CLC ADC SWP ROT IORk LIT2 .absc($l1) JNZ2\nPOP POP .lbl($l2)\nLIT2 #ff #09 STAk POP POP LIT2 .absc($l2) JNZ2\n")
                    }
                    else -> throw AssertionError("Statement-type AST node in the expression list of a PRINT statement")
                }
            }.append("LIT #0a LIT2 #ff #09 STA\n").toString()
        }
        companion object {
            private var label = 'A'
            val nextLabels get() = Pair("AVCBASIC_PRINT_1$label", "AVCBASIC_PRINT_2${label++}")
            fun removeSign(label: String) = "DUP LIT #07 SFT LIT #00 CLC SBC LIT2 .absc($label) JNZ2\n$NEGATE\nLIT '- LIT2 #ff #09 STA\n.lbl($label)" // same as BinOp.removeSign but with a print
        }
    }
    data class IfStatement(var condition: Ast, var statement: Ast) : Ast {
        override fun evaluate(): Ast {
            condition = condition.evaluate()
            statement = statement.evaluate()
            if (condition is Bool) return if ((condition as Bool).value) statement else NoOp
            return this
        }
        override fun visit(): String {
            if (condition !is RelOp) throw AssertionError("Condition of IF is not a RelOp.")
            val negated = (condition as RelOp).not()
            val label = nextLabel
            return "${negated.visit()} LIT2 .absc($label) JNZ2\n${statement.visit()}\n.lbl($label)"
        }
        companion object {
            private var label = 'A'
            val nextLabel get() = "AVCBASIC_IF_${label++}"
        }
    }
    data class GotoStatement(val label: Token.Label, val subroutine: Boolean) : Ast {
        override fun visit() = "LIT2 .absc(${label.value}) ${if (subroutine) "JSR2" else "JMP2"}"
    }
    data class InputStatement(val vars: ArrayList<Token.ID>) : Ast {
        // no evaluation needed since it's a user input function
        override fun visit(): String {
            // TODO real implementation
            // This implementation just stores 0 to all variables in the statement
            return visitAll(vars) { "${AssignStatement(it, Num(0)).visit()}\n" }.toString().trim()
        }
    }
    data class AssignStatement(val variable: Token.ID, var expression: Ast) : Ast {
        override fun evaluate(): Ast {
            expression = expression.evaluate()
            // TODO convert an assignment to a no-op if a variable is unused or folded away
            // TODO implement folding variables away
            return this
        }
        override fun visit(): String {
            val addr = if (variable in Var.usedVars.keys) Var.usedVars[variable]!! else run {
                val address = nextAddress
                Var.usedVars[variable] = address
                address
            }
            return "${expression.visit()} ${Num(addr).visit()} STA2"
        }
        companion object {
            private var addr = 0xff00.toShort()
            val nextAddress get(): Short {
                addr = (addr - 2).toShort() // addr -= 2 tries to set an Int to addr. ridiculous, and really hard to excuse for kotlin
                return addr
            }
        }
    }
    data class AsmDirective(val asm: String) : Ast {
        override fun evaluate(): Ast {
            if (".lbl(AVCBASIC_" in asm || ".label(AVCBASIC" in asm) throw UnsupportedOperationException("Assembly labels beginning with AVCBASIC_ are reserved for use by the compiler and cannot be made through the ASM keyword.")
            return this
        }
        override fun visit() = asm
    }
    // TODO will be data objects
    object Rnd : Ast {
        override fun visit() = "LIT2 #ff #02 LDA LIT #00"
    }
    object ReturnStatement : Ast {
        override fun visit() = "JMPr2"
    }
    object NoOp : Ast {
        override fun visit() = ""
    }
    companion object {
        @Suppress("NOTHING_TO_INLINE")
        inline fun Ast.evaluateAll(list: AstList): Ast {
            for (i in list.indices) list[i] = list[i].evaluate()
            return this
        }

        inline fun <T> visitAll(list: List<T>, function: (T) -> String): StringBuilder =
            visitAllStringBuilder(list) { append(function(it)) }

        inline fun <T> visitAllStringBuilder(list: List<T>, function: StringBuilder.(T) -> Unit): StringBuilder {
            val s = StringBuilder()
            list.forEach { s.function(it) }
            return s
        }
        
        const val NEGATE = "LIT2 #00 #00 SWP2 SEC SBC2" // 0 - n
    }
}
