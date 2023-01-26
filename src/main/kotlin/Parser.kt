package io.github.mathmaster13.avcbasic

class Parser(private val tokens: TokenList) {
    private var index = 0 // All functions move the index to the next unprocessed token upon completion.
    fun parse(): Ast.Program {
        val ast = Ast.Program(AstList())
        while (index < tokens.size) {
            if (tokens[index] == Token.EOF) break
            try {
                ast.children.add(labeledStatement())
            } catch (e: IndexOutOfBoundsException) {
                parseError("The final statement could not be parsed (IndexOutOfBoundsException).")
            }
        }
        return ast
    }

    private fun labeledStatement(): Ast.LabeledStatement = Ast.LabeledStatement(labelDirective(), statement())
    private fun labelDirective(): Ast.LabelDirective? {
        val label = tokens[index]
        if (label is Token.Label) {
            index++
            return Ast.LabelDirective(label)
        }
        return null
    }

    private fun statement(): Ast {
        val keyword = tokens[index]
        if (keyword !is Token.Keyword) parseError("Statements must start with a keyword.")
        index++ // set up for next parse
        return when (keyword) {
            Token.Keyword.ASM -> asmDirective()
            Token.Keyword.THEN -> parseError("Dangling THEN (THEN keyword not parsed during IF statement parsing)", index - 1)
            Token.Keyword.RND -> parseError("RND is a number and cannot begin a statement.")
            Token.Keyword.RETURN -> Ast.ReturnStatement
            Token.Keyword.PRINT -> Ast.PrintStatement(expressionList())
            Token.Keyword.IF -> ifStatement()
            Token.Keyword.GOTO -> gotoStatement(false)
            Token.Keyword.GOSUB -> gotoStatement(true)
            Token.Keyword.INPUT -> Ast.InputStatement(varList())
            Token.Keyword.LET -> assignStatement()
            Token.Keyword.END -> Ast.EndStatement
        }
    }

    private fun expressionList(): AstList {
        val output = AstList()
        var nextToken = tokens[index]

        fun checkNext() {
            if (nextToken is Token.Str) {
                index++
                output.add(Ast.Str(nextToken.value as String))
            } else output.add(expression())
            nextToken = tokens[index]
        }

        checkNext()
        while (nextToken == Token.Comma) {
            index++
            nextToken = tokens[index]
            checkNext()
        }
        return output
    }

    private fun expression(): Ast {
        var nextToken = tokens[index]
        val unaryOp: Token.ArithmeticOp?
        val isUnaryOp = if (nextToken is Token.ArithmeticOp) {
            if (nextToken.isUnary) {
                unaryOp = nextToken
                index++
                nextToken = tokens[index]
                true
            } else parseError("Multiplication and division are not unary operators.")
        } else {
            unaryOp = null
            false
        }
        var output: Ast = if (isUnaryOp) Ast.UnaryOp(term(), unaryOp!!) else term()
        nextToken = tokens[index]
        while (nextToken is Token.ArithmeticOp) {
            var op: Token.ArithmeticOp
            when (nextToken) {
                Token.ArithmeticOp.MULTIPLY, Token.ArithmeticOp.DIVIDE -> parseError("An unknown error occurred: Received * or / in expression()")
                else -> run {
                    op = nextToken as Token.ArithmeticOp
                    index++
                    nextToken = tokens[index]
                }
            }
            output = Ast.BinOp(output, op, term())
        }
        return output
    }

    private fun term(): Ast {
        var output: Ast = factor()
        var nextToken = tokens[index]
        while (nextToken is Token.ArithmeticOp) {
            var op: Token.ArithmeticOp
            when (nextToken) {
                Token.ArithmeticOp.ADD, Token.ArithmeticOp.SUBTRACT -> return output
                else -> run {
                    op = nextToken as Token.ArithmeticOp
                    index++
                    nextToken = tokens[index]
                }
            }
            output = Ast.BinOp(output, op, factor())
        }
        return output
    }

    private fun factor(): Ast {
        val output = when (val nextToken = tokens[index]) {
            is Token.ID -> Ast.Var(nextToken)
            is Token.Number -> Ast.Num(nextToken.value)
            Token.LeftParen -> null
            Token.Keyword.RND -> Ast.Rnd
            else -> parseError("An unknown error occurred: recieved a token other than a variable name, number, expression, or RND in factor()")
        }
        index++
        return output ?: run { // for LeftParen
            val maybeOutput = expression()
            if (tokens[index] != Token.RightParen) parseError("Left parentheses require matching right parentheses.")
            index++
            maybeOutput
        }
    }

    private fun gotoStatement(subroutine: Boolean): Ast.GotoStatement {
        val label = tokens[index]
        if (label !is Token.Label) parseError("GOTO and GOSUB require that they are followed by a label.")
        index++
        return Ast.GotoStatement(label, subroutine)
    }

    private fun ifStatement(): Ast.IfStatement {
        val left = try {
            expression()
        } catch (e: ParseError) {
            parseError("An IF-THEN statement must contain an expression after the IF keyword.")
        }
        val relOp = tokens[index]
        if (relOp !is Token.RelOp) parseError("IF-THEN statements require a relational operator.")
        index++
        val relOpAst = try {
            Ast.RelOp(left, relOp, expression())
        } catch (e: ParseError) {
            parseError("An IF-THEN statement must contain an expression after the relational operator.")
        }
        if (tokens[index] != Token.Keyword.THEN) parseError("The condition of an IF statement must be followed by the keyword THEN.")
        index++
        return Ast.IfStatement(relOpAst, labeledStatement())
    }

    private fun varList(): ArrayList<Token.ID> {
        val output = ArrayList<Token.ID>()
        var nextToken = tokens[index]
        if (nextToken !is Token.ID) parseError("INPUT must take one or more variables separated by commas.")
        output.add(nextToken)
        index++

        nextToken = tokens[index]
        while (nextToken == Token.Comma) {
            index++
            nextToken = tokens[index]
            if (nextToken !is Token.ID) parseError("INPUT lists must not have trailing commas.")
            output.add(nextToken)
            index++
            nextToken = tokens[index]
        }
        return output
    }

    private fun assignStatement(): Ast.AssignStatement {
        val id = tokens[index]
        if (id !is Token.ID) parseError("The LET keyword must be followed by a variable name.")
        index++
        if (tokens[index] != Token.EqAssign) parseError("A variable declaration must have an equal sign after the variable name.")
        index++
        return try {
            Ast.AssignStatement(id, expression())
        } catch (e: ParseError) {
            parseError("A variable declaration must contain an expression after the equal sign.")
        }
    }

    private fun asmDirective(): Ast.AsmDirective {
        val str = tokens[index]
        if (str !is Token.Str) parseError("ASM directives must be followed by a string of assembly code.")
        index++
        return Ast.AsmDirective(str.value)
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun parseError(message: String, idx: Int = index): Nothing =
        throw ParseError("$message\nDebug Info:\nToken List: $tokens\nIndex: $idx")
}

class ParseError(message: String? = null) : RuntimeException(message)
