package model

class Parser(tokens: Sequence<Token>) {
    private val tokens: List<Token> = tokens.toList()
    private var position: Int = 0

    fun parse(): List<Statement> {
        val statements = mutableListOf<Statement>()
        while (!isAtEnd()) {
            statements.add(parseDeclaration())
        }
        return statements
    }

    private fun parseDeclaration(): Statement {
        if (match(TokenType.FUN)) return parseFunctionDeclaration()
        if (match(TokenType.VAR)) return parseVarDeclaration()
        return parseStatement()
    }

    private fun parseStatement(): Statement {
        if (match(TokenType.IF)) return parseIfStatement()
        if (match(TokenType.WHILE)) return parseWhileStatement()
        if (match(TokenType.RETURN)) return parseReturnStatement()
        if (match(TokenType.PRINT)) return parsePrintStatement()
        if (match(TokenType.LBRACE)) return Statement.BlockStatement(parseBlock())
        return parseExpressionStatement()
    }

    private fun parseFunctionDeclaration(): Statement {
        val name = consume(TokenType.ID, "Ожидается имя функции.")
        consume(TokenType.LPAREN, "Ожидается '(' после имени функции.")

        val params = mutableListOf<Statement.Parameter>()
        if (!check(TokenType.RPAREN)) {
            do {
                val paramName = consume(TokenType.ID, "Ожидается имя параметра.")
                consume(TokenType.COLON, "Ожидается ':' после имени параметра.")
                val paramType = parseType()
                params.add(Statement.Parameter(paramName.value, paramType))
            } while (match(TokenType.COMMA))
        }
        consume(TokenType.RPAREN, "Ожидается ')' после списка параметров.")

        var returnType: Type = Type.Null
        if (match(TokenType.COLON)) {
            returnType = parseType()
        }

        consume(TokenType.LBRACE, "Ожидается '{' перед телом функции.")
        val body = parseBlock()
        return Statement.FunctionStatement(name.value, params, returnType, body)
    }

    private fun parseReturnStatement(): Statement {
        var value: Expression? = null
        if (!check(TokenType.SEMICOLON)) {
            value = parseExpression()
        }
        consume(TokenType.SEMICOLON, "Ожидается ';' после 'return'.")
        return Statement.ReturnStatement(value)
    }

    private fun parseType(): Type {
        val name = consume(TokenType.ID, "Ожидается имя типа.")
        return when (name.value) {
            "Number"  -> Type.Number
            "String"  -> Type.String
            "Boolean" -> Type.Boolean
            else -> throw Exception(
                "Ошибка парсера: Line ${name.line}, Col ${name.column}: Неизвестный тип '${name.value}'."
            )
        }
    }

    private fun parseVarDeclaration(): Statement {
        val name = consume(TokenType.ID, "Ожидается имя переменной.")
        var initializer: Expression? = null

        if (match(TokenType.EQ)) {
            initializer = parseExpression()
        }

        consume(TokenType.SEMICOLON, "Ожидается ';' после объявления переменной.")
        return Statement.VarStatement(name.value, initializer)
    }

    private fun parseIfStatement(): Statement {
        consume(TokenType.LPAREN, "Ожидается '(' после 'if'.")
        val condition = parseExpression()
        consume(TokenType.RPAREN, "Ожидается ')' после условия 'if'.")

        val thenBranch = parseStatement()
        var elseBranch: Statement? = null

        if (match(TokenType.ELSE)) {
            elseBranch = parseStatement()
        }

        return Statement.IfStatement(condition, thenBranch, elseBranch)
    }

    private fun parseExpressionStatement(): Statement {
        val expr = parseExpression()
        consume(TokenType.SEMICOLON, "Ожидается ';' после выражения.")
        return Statement.ExpressionStatement(expr)
    }

    private fun parseBlock(): List<Statement> {
        val statements = mutableListOf<Statement>()

        while (!check(TokenType.RBRACE) && !isAtEnd()) {
            statements.add(parseDeclaration())
        }

        consume(TokenType.RBRACE, "Ожидается '}' после блока.")
        return statements
    }

    private fun parseExpression(): Expression = parseAssignment()

    private fun parseAssignment(): Expression {
        val expr = parseLogicalOr()

        if (match(TokenType.EQ)) {
            val equals = previous()
            val value = parseAssignment()

            if (expr is Expression.VariableExpression) {
                return Expression.AssignExpression(expr.name, value)
            }

            if (expr is Expression.IndexExpression) {
                return Expression.IndexAssignExpression(expr.array, expr.index, value)
            }

            throw Exception("Ошибка при парсинге: Line ${equals.line}: Недопустимо для присваивания.")
        }

        return expr
    }

    private fun parseLogicalOr(): Expression {
        var expr = parseLogicalAnd()

        while (match(TokenType.OR)) {
            val op = previous().type
            val right = parseLogicalAnd()
            expr = Expression.BinaryExpression(expr, op, right)
        }

        return expr
    }

    private fun parseLogicalAnd(): Expression {
        var expr = parseEquality()

        while (match(TokenType.AND)) {
            val op = previous().type
            val right = parseEquality()
            expr = Expression.BinaryExpression(expr, op, right)
        }

        return expr
    }

    private fun parseEquality(): Expression {
        var expr = parseComparison()

        while (match(TokenType.EQEQ, TokenType.NEQ)) {
            val op = previous().type
            val right = parseComparison()
            expr = Expression.BinaryExpression(expr, op, right)
        }

        return expr
    }

    private fun parseComparison(): Expression {
        var expr = parseTerm()

        while (match(TokenType.LT, TokenType.LTEQ, TokenType.GT, TokenType.GTEQ)) {
            val op = previous().type
            val right = parseTerm()
            expr = Expression.BinaryExpression(expr, op, right)
        }

        return expr
    }

    private fun parseTerm(): Expression {
        var expr = parseFactor()

        while (match(TokenType.PLUS, TokenType.MINUS)) {
            val op = previous().type
            val right = parseFactor()
            expr = Expression.BinaryExpression(expr, op, right)
        }

        return expr
    }

    private fun parseFactor(): Expression {
        var expr = parseUnary()

        while (match(TokenType.STAR, TokenType.SLASH)) {
            val op = previous().type
            val right = parseUnary()
            expr = Expression.BinaryExpression(expr, op, right)
        }

        return expr
    }

    private fun parseUnary(): Expression {
        if (match(TokenType.EXCL, TokenType.MINUS)) {
            val op = previous().type
            val right = parseUnary()
            return Expression.UnaryExpression(op, right)
        }

        return parseCall()
    }

    private fun parseCall(): Expression {
        var expr = parsePrimary()

        while (true) {
            if (match(TokenType.LPAREN)) {
                expr = finishCall(expr)
            } else if (match(TokenType.LBRACKET)) {
                val index = parseExpression()
                consume(TokenType.RBRACKET, "Ожидается ']' после индекса.")
                expr = Expression.IndexExpression(expr, index)
            } else break
        }

        return expr
    }

    private fun finishCall(callee: Expression): Expression {
        val arguments = mutableListOf<Expression>()

        if (!check(TokenType.RPAREN)) {
            do {
                arguments.add(parseExpression())
            } while (match(TokenType.COMMA))
        }

        consume(TokenType.RPAREN, "Ожидается ')' после аргументов вызова.")
        return Expression.CallExpression(callee, arguments)
    }

    private fun parsePrimary(): Expression {
        if (match(TokenType.NUMBER)) {
            val value = previous().value.toDouble()
            return Expression.NumberExpression(value)
        }

        if (match(TokenType.STRING)) {
            return Expression.StringExpression(previous().value)
        }

        if (match(TokenType.TRUE)) return Expression.BooleanExpression(true)
        if (match(TokenType.FALSE)) return Expression.BooleanExpression(false)

        if (match(TokenType.ID)) {
            return Expression.VariableExpression(previous().value)
        }

        if (match(TokenType.LPAREN)) {
            val expr = parseExpression()
            consume(TokenType.RPAREN, "Ожидается ')' после выражения.")
            return expr
        }

        if (match(TokenType.LBRACKET)) {
            val elements = mutableListOf<Expression>()
            if (!check(TokenType.RBRACKET)) {
                do { elements.add(parseExpression()) } while (match(TokenType.COMMA))
            }
            consume(TokenType.RBRACKET, "Ожидается ']' после элементов массива.")
            return Expression.ArrayExpression(elements)
        }

        val token = peek()
        throw Exception("Ошибка парсера: Line ${token.line}, Col ${token.column}: Ожидается выражение.")
    }

    private fun match(vararg types: TokenType): Boolean {
        for (type in types) {
            if (check(type)) {
                advance()
                return true
            }
        }
        return false
    }

    private fun check(type: TokenType): Boolean {
        if (isAtEnd()) return false
        return peek().type == type
    }

    private fun advance(): Token {
        if (!isAtEnd()) position++
        return previous()
    }

    private fun isAtEnd(): Boolean = peek().type == TokenType.EOF
    private fun peek(): Token = tokens[position]
    private fun previous(): Token = tokens[position - 1]

    private fun consume(type: TokenType, message: String): Token {
        if (check(type)) return advance()
        val token = peek()
        throw Exception("Ошибка парсера: Line ${token.line}, Col ${token.column}: $message")
    }

    private fun parseWhileStatement(): Statement {
        consume(TokenType.LPAREN, "Ожидается '(' после 'while'.")
        val condition = parseExpression()
        consume(TokenType.RPAREN, "Ожидается ')' после условия 'while'.")
        val body = parseStatement()
        return Statement.WhileStatement(condition, body)
    }

    private fun parsePrintStatement(): Statement {
        consume(TokenType.LPAREN, "Ожидается '(' после 'print'.")
        val expr = parseExpression()
        consume(TokenType.RPAREN, "Ожидается ')' после выражения в 'print'.")
        consume(TokenType.SEMICOLON, "Ожидается ';' после 'print(...)'.")
        return Statement.PrintStatement(expr)
    }
}