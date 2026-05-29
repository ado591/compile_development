package model

class Lexer(input: String?) {
    private val input: String = input ?: ""
    private var position: Int = 0
    private var line: Int = 1
    private var column: Int = 1

    companion object {
        private val keywords = mapOf(
            "var" to TokenType.VAR,
            "print" to TokenType.PRINT,
            "if" to TokenType.IF,
            "else" to TokenType.ELSE,
            "while" to TokenType.WHILE,
            "true" to TokenType.TRUE,
            "false" to TokenType.FALSE,
            "fun" to TokenType.FUN,
            "return" to TokenType.RETURN
        )

        private val operators = mapOf(
            "==" to TokenType.EQEQ,
            "!=" to TokenType.NEQ,
            "<=" to TokenType.LTEQ,
            ">=" to TokenType.GTEQ,
            "&&" to TokenType.AND,
            "||" to TokenType.OR,
            "+" to TokenType.PLUS,
            "-" to TokenType.MINUS,
            "*" to TokenType.STAR,
            "/" to TokenType.SLASH,
            "=" to TokenType.EQ,
            "<" to TokenType.LT,
            ">" to TokenType.GT,
            "!" to TokenType.EXCL,
            "(" to TokenType.LPAREN,
            ")" to TokenType.RPAREN,
            "{" to TokenType.LBRACE,
            "}" to TokenType.RBRACE,
            "[" to TokenType.LBRACKET,
            "]" to TokenType.RBRACKET,
            ";" to TokenType.SEMICOLON,
            "," to TokenType.COMMA,
            ":" to TokenType.COLON
        )
    }

    fun tokenize(): Sequence<Token> = sequence {
        while (position < input.length) {
            val current = peek()

            when {
                current.isWhitespace() -> {
                    next()
                }
                current == '"' -> {
                    yield(readString())
                }
                current.isDigit() -> {
                    yield(readNumber())
                }
                current.isLetter() -> {
                    yield(readWord())
                }
                else -> {
                    yield(readOperatorOrPunctuation())
                }
            }
        }

        yield(Token(TokenType.EOF, "\u0000", position, line, column))
    }

    private fun readString(): Token {
        val startPos = position
        val startLine = line
        val startCol = column
        next()
        val sb = StringBuilder()
        while (position < input.length && peek() != '"') {
            sb.append(next())
        }
        if (position >= input.length)
            throw Exception("Ошибка лексера: незакрытая строка на строке $startLine, колонке $startCol")
        next()
        return Token(TokenType.STRING, sb.toString(), startPos, startLine, startCol)
    }

    private fun readNumber(): Token {
        val startPos = position
        val startLine = line
        val startCol = column

        while (peek().isDigit()) {
            next()
        }

        val text = input.substring(startPos, position)

        return Token(TokenType.NUMBER, text, startPos, startLine, startCol)
    }

    private fun readWord(): Token {
        val startPos = position
        val startLine = line
        val startCol = column

        while (peek().isLetterOrDigit()) {
            next()
        }

        val text = input.substring(startPos, position)
        val type = keywords[text] ?: TokenType.ID

        return Token(type, text, startPos, startLine, startCol)
    }

    private fun readOperatorOrPunctuation(): Token {
        val startPos = position
        val startLine = line
        val startCol = column

        if (position + 1 < input.length) {
            val twoChars = input.substring(position, position + 2)
            val opType = operators[twoChars]

            if (opType != null) {
                next()
                next()
                return Token(opType, twoChars, startPos, startLine, startCol)
            }
        }

        val oneChar = input[position].toString()
        val type = operators[oneChar]

        if (type != null) {
            next()
            return Token(type, oneChar, startPos, startLine, startCol)
        }

        val badChar = peek()
        throw Exception("Ошибка лексера: Unexpected character '$badChar' at Line $startLine, Column $startCol")
    }

    private fun peek(): Char = if (position >= input.length) '\u0000' else input[position]

    private fun next(): Char {
        if (position >= input.length) return '\u0000'

        val current = input[position++]

        if (current == '\n') {
            line++
            column = 1
        } else {
            column++
        }

        return current
    }
}