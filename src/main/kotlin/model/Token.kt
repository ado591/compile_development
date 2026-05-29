package model

data class Token(var type: TokenType, var value: String, var position: Int, var line: Int, var column: Int)