package io.github.mathmaster13.avcbasic

/**
 * A very fancy token ADT.
 */ 
sealed interface Token<T> {
    val value: T
    
    data class Number(override val value: Short) : Token<Short> {
        companion object : TokenFactory<Number> {
            private val regex = Regex("[0-9]+")
            override fun tryGet(input: String): Pair<String, Number>? {
                val matchResult = regex.matchAt(input.trim(), 0)?.value ?: return null
                val integer = matchResult.toShortOrNull() ?: return null
                return Pair(input.substring(matchResult.length).trim(), Number(integer))
            }
        }
    }
    enum class ArithmeticOp(val isUnary: Boolean, val operation: (Short, Short) -> Short) : Token<ArithmeticOp> {
        ADD(true, { p: Short, q: Short -> (p + q).toShort() }), SUBTRACT(true, { p: Short, q: Short -> (p - q).toShort() }),
        MULTIPLY(false, { p: Short, q: Short -> (p * q).toShort() }), DIVIDE(false, { p: Short, q: Short -> (p / q).toShort() });
        
        override val value = this
        
        companion object : TokenFactory<ArithmeticOp> {
            override fun tryGet(input: String): Pair<String, ArithmeticOp>? {
                val op = ArithmeticOp.fromChar(input[0]) ?: return null
                return Pair(input.substring(1).trim(), op)
            }
            private fun fromChar(input: Char) = when (input) {
                '+' -> ADD
                '-' -> SUBTRACT
                '*' -> MULTIPLY
                '/' -> DIVIDE
                else -> null
            }
        }
    }
    enum class Keyword : Token<Keyword> {
        PRINT, IF, THEN, GOTO, INPUT, LET, GOSUB, RETURN, RND, ASM;
        
        override val value = this
        
        companion object : TokenFactory<Keyword> {
            private val regex = Regex("(PRINT|IF|THEN|GOTO|INPUT|LET|GOSUB|RETURN|RND|ASM)")
            override fun tryGet(input: String): Pair<String, Keyword>? {
                val matchResult = regex.matchAt(input, 0)?.value ?: return null
                return Pair(input.substring(matchResult.length).trim(), Keyword.valueOf(matchResult))
            }
        }
    }
    enum class RelOp(val operation: (Short, Short) -> Boolean) : Token<RelOp> {
        GT({ p: Short, q: Short -> p > q }), LT({ p: Short, q: Short -> p < q }), EQ({ p: Short, q: Short -> p == q }),
        NE({ p: Short, q: Short -> p != q }), GE({ p: Short, q: Short -> p >= q }), LE({ p: Short, q: Short -> p <= q });

        override val value = this
        val not: RelOp get() = when (this) {
            GT -> LE
            LT -> GE
            EQ -> NE
            NE -> EQ
            GE -> LT
            LE -> GT
        }

        companion object : TokenFactory<RelOp> {
            private val regex = Regex("(>=?|<=?|[=!]=)")
            override fun tryGet(input: String): Pair<String, RelOp>? {
                val matchResult = regex.matchAt(input, 0)?.value ?: return null
                return Pair(input.substring(matchResult.length).trim(), RelOp.fromString(matchResult)!!)
            }
            private fun fromString(input: String) = when(input) {
                ">" -> GT
                "<" -> LT
                "==" -> EQ
                "!=" -> NE
                ">=" -> GE
                "<=" -> LE
                else -> null
            }
        }
    }
    data class Str(override val value: String) : Token<String> {
        companion object : TokenFactory<Str> {
            private val regex = Regex("\".*?\"", RegexOption.DOT_MATCHES_ALL)
            override fun tryGet(input: String): Pair<String, Str>? {
                val matchResult = regex.matchAt(input, 0)?.value ?: return null
                return Pair(input.substring(matchResult.length).trim(), Str(matchResult.substring(1, matchResult.length - 1)))
            }
        }
    }
    data class ID(override val value: Char) : Token<Char> {
        companion object : TokenFactory<ID> {
            override fun tryGet(input: String): Pair<String, ID>? {
                val id = run {
                    val charToCheck = input[0]
                    if (charToCheck < 'A' || charToCheck > 'Z') return null
                    charToCheck
                }
                return Pair(input.substring(1).trim(), ID(id))
            }
        }
    }
    data class Label(override val value: Char) : Token<Char> {
        companion object : TokenFactory<Label> {
            override fun tryGet(input: String): Pair<String, Label>? {
                val label = run {
                    val charToCheck = input[0]
                    if (charToCheck < 'a' || charToCheck > 'z') return null
                    charToCheck
                }
                return Pair(input.substring(1).trim(), Label(label))
            }
        }
    }
    // TODO these should be data objects when that becomes a thing
    object LeftParen : Token<LeftParen>, TokenFactory<LeftParen> {
        override val value = this
        override fun tryGet(input: String): Pair<String, LeftParen>? = tryGet(input, '(', this)
    }
    object RightParen : Token<RightParen>, TokenFactory<RightParen> {
        override val value = this
        override fun tryGet(input: String): Pair<String, RightParen>? = tryGet(input, ')', this)
    }
    object Comma : Token<Comma>, TokenFactory<Comma> {
        override val value = this
        override fun tryGet(input: String): Pair<String, Comma>? = tryGet(input, ',', this)
    }
    object EqAssign : Token<EqAssign>, TokenFactory<EqAssign> {
        override val value = this
        override fun tryGet(input: String): Pair<String, EqAssign>? = tryGet(input, '=', this)
    }
    object EOF : Token<EOF> {
        override val value = this
    }
    
    sealed interface TokenFactory<T : Token<*>> {
        fun tryGet(input: String): Pair<String, T>?
        companion object {
            val all = listOf(Number, ArithmeticOp, Keyword, RelOp, Str, ID, Label, LeftParen, RightParen, Comma, EqAssign)
        }
    }
    
    companion object {
        @Suppress("NOTHING_TO_INLINE")
        private inline fun <T> tryGet(input: String, charToCheck: Char, objToReturn: T): Pair<String, T>?
                where T  : Token<T>,
                T : TokenFactory<T> {
            if (input[0] != charToCheck) return null
            return Pair(input.substring(1).trim(), objToReturn)
        }
    }
}
