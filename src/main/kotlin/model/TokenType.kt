package model

enum class TokenType {

    //BASIC
    NUMBER,
    ID,
    STRING,

    //Keywords
    VAR,
    PRINT,
    IF,
    ELSE,
    WHILE,

    //Operators
    PLUS,
    MINUS,
    STAR,
    SLASH,
    EQ,
    EQEQ,
    EXCL,
    NEQ,
    LT,
    GT,
    LTEQ,
    GTEQ,
    AND,
    OR,

    //Grouping & Punctuation
    LPAREN,
    RPAREN,
    LBRACE,
    RBRACE,
    SEMICOLON,

    //конец файла
    EOF

}