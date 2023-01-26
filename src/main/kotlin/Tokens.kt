package io.github.mathmaster13.avcbasic

/**
 * A very fancy token ADT.
 */
// TODO merge all the regex-related factories together
sealed interface Token<T> {
    val value: T

    @JvmInline
    value class Number(override val value: Short) : Token<Short> {
        companion object : RegexFactory<Number>() {
            override val regex = Regex("[0-9]+")
            override val converter: String.() -> Number? = l@ { Number(toShortOrNull() ?: return@l null) }
        }
    }

    enum class ArithmeticOp(val isUnary: Boolean, val operation: (Short, Short) -> Short) : Token<ArithmeticOp> {
        ADD(true, { p: Short, q: Short -> (p + q).toShort() }),
        SUBTRACT(true, { p: Short, q: Short -> (p - q).toShort() }),
        MULTIPLY(false, { p: Short, q: Short -> (p * q).toShort() }),
        DIVIDE(false, { p: Short, q: Short -> (p / q).toShort() });

        override val value = this

        companion object : TokenFactory<ArithmeticOp> {
            override fun tryGet(input: String): Pair<String, ArithmeticOp>? {
                val op = ArithmeticOp.fromChar(input[0]) ?: return null
                return Pair(input.substring(1), op)
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
        PRINT, IF, THEN, GOTO, INPUT, LET, GOSUB, RETURN, END, RND, ASM;

        override val value = this

        companion object : RegexFactory<Keyword>() {
            override val regex = Regex("PRINT|IF|THEN|GOTO|INPUT|LET|GOSUB|RETURN|END|RND|ASM")
            override val converter: (String) -> Keyword = ::valueOf
        }
    }

    enum class RelOp(val operation: (Short, Short) -> Boolean) : Token<RelOp> {
        GT({ p: Short, q: Short -> p > q }), LT({ p: Short, q: Short -> p < q }), EQ({ p: Short, q: Short -> p == q }),
        NE({ p: Short, q: Short -> p != q }), GE({ p: Short, q: Short -> p >= q }), LE({ p: Short, q: Short -> p <= q });

        override val value = this // TODO should value be the operation?
        val not: RelOp
            get() = when (this) {
                GT -> LE
                LT -> GE
                EQ -> NE
                NE -> EQ
                GE -> LT
                LE -> GT
            }

        companion object : RegexFactory<RelOp>() {
            override val converter: String.() -> RelOp? = ::fromString
            override val regex = Regex(">=?|<=?|[=!]=")

            private fun fromString(input: String) = when (input) {
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

    @JvmInline
    value class Str(override val value: String) : Token<String> {
        companion object : RegexFactory<Str>() {
            override val regex = Regex("\".*?\"", RegexOption.DOT_MATCHES_ALL)
            override val converter: String.() -> Str = { Str(substring(1, length - 1)) }
        }
    }

    @JvmInline
    value class ID(override val value: Char) : Token<Char> {
        // future compatibility
//        constructor(s: String) : this(if (s.length != 1) throw LexerError("Invalid identifier: $s") else s[0])

        companion object : TokenFactory<ID> {
            override fun tryGet(input: String): Pair<String, ID>? {
                val id = run {
                    val charToCheck = input[0]
                    if (charToCheck < 'A' || charToCheck > 'Z') return null
                    charToCheck
                }
                return Pair(input.substring(1), ID(id))
            }

            // future compatibility
//            val regex = Regex("[a-z]")
        }
    }

    @JvmInline
    value class Label(override val value: Char) : Token<Char> {
        // future compatibility
        constructor(s: String) : this(if (s.length != 1) throw LexerError("Invalid label: $s") else s[0])

        companion object : TokenFactory<Label> {
            override fun tryGet(input: String): Pair<String, Label>? {
                val label = run {
                    val charToCheck = input[0]
                    if (charToCheck < 'a' || charToCheck > 'z') return null
                    charToCheck
                }
                return Pair(input.substring(1), Label(label))
            }

            // future compatibility
            val regex = Regex("[a-z]")
        }
    }

    data object LeftParen : Token<LeftParen>, SingleCharFactory<LeftParen>() {
        override val char = '('
    }

    data object RightParen : Token<RightParen>, SingleCharFactory<RightParen>() {
        override val char = ')'
    }

    data object Comma : Token<Comma>, SingleCharFactory<Comma>() {
        override val char = ','
    }

    data object EqAssign : Token<EqAssign>, SingleCharFactory<EqAssign>() {
        override val char = '='
    }

    data object EOF : Token<EOF> {
        override val value = this
    }

    sealed interface TokenFactory<T : Token<*>> {
        fun tryGet(input: String): Pair<String, T>?

        companion object {
            val all = listOf(Number, ArithmeticOp, Keyword, RelOp, Str, ID, Label, LeftParen, RightParen, Comma, EqAssign)
        }
    }

    sealed class RegexFactory<T : Token<*>> : TokenFactory<T> {
        final override fun tryGet(input: String): Pair<String, T>? {
            val matchResult = regex.matchAt(input, 0)?.value ?: return null
            return Pair(input.substring(matchResult.length), converter(matchResult) ?: return null)
        }
        abstract val converter: (String) -> T?
        abstract val regex: Regex
    }

    @Suppress("UNCHECKED_CAST")
    sealed class SingleCharFactory<T : Token<T>> : Token<T>, TokenFactory<T> {
        override val value get() = this as T
        abstract val char: Char

        final override fun tryGet(input: String): Pair<String, T>? =
            if (input[0] != char) null
            else Pair(input.substring(1), this as T)
    }
}
