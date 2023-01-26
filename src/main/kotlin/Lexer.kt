package io.github.mathmaster13.avcbasic

@JvmInline
value class TokenList @PublishedApi internal constructor(private val list: ArrayList<Token<*>>) : MutableList<Token<*>> by list {
    constructor() : this(ArrayList())
}

class Lexer(private var code: String) {
    val tokens = TokenList()
    val nextToken: Token<*>
        get() {
            code = code.trim()
            if (code.length > 1 && code.substring(0, 2) == "//") { // remove comments
                val split = newline.split(code, 2)
                if (split.size > 1) {
                    code = split[1]
                    @Suppress("RecursivePropertyAccessor")
                    return nextToken
                }
                return add(Token.EOF)
            }
            if (code.isBlank()) return add(Token.EOF)
            for (factory in Token.TokenFactory.all) {
                val (codeLeft, token) = factory.tryGet(code) ?: continue
                code = codeLeft
                return add(token)
            }
            throw LexerError("The lexer has detected invalid code:\n$code")
        }

    @Suppress("NOTHING_TO_INLINE", "ControlFlowWithEmptyBody")
    inline fun scanAll() {
        while (nextToken != Token.EOF) {}
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun <T> add(t: Token<T>): Token<T> {
        tokens.add(t)
        return t
    }

    companion object {
        val newline = Regex("\r?\n|\r")
    }
}

class LexerError(message: String? = null) : RuntimeException(message)
