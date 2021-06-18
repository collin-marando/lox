package jlox;

enum TokenType {
    // Single-character tokens.
    LEFT_PAREN, RIGHT_PAREN, LEFT_BRACE, RIGHT_BRACE,
    COMMA, DOT, SEMICOLON, SLASH, STAR, QUESTION, COLON,
  
    // One or two character tokens.
    MINUS, MINUS_MINUS, MINUS_EQUAL,
    PLUS, PLUS_PLUS, PLUS_EQUAL,
    BANG, BANG_EQUAL,
    EQUAL, EQUAL_EQUAL,
    GREATER, GREATER_EQUAL,
    LESS, LESS_EQUAL,
  
    // Literals.
    IDENTIFIER, STRING, NUMBER,
  
    // Keywords.
    AND, BREAK, CLASS, ELSE, FALSE, FUN, FOR, IF, NIL, OR,
    PRINT, RETURN, STATIC, SUPER, THIS, TRUE, VAR, WHILE,
  
    EOF
}