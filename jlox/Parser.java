package jlox;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static jlox.TokenType.*;

class Parser {

    // This flag is used to appease the testing suite
    final boolean test = Global.test;

    private static class ParseError extends RuntimeException {}

    private final List<Token> tokens;
    private int current = 0;
    private int loopDepth = 0;

    Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    List<Stmt> parse() {
        List<Stmt> statements = new ArrayList<>();
        while (!isAtEnd()) {
            statements.add(declaration());
        }

        return statements;
    }

    private Stmt declaration() {
        try {
            if (match(CLASS)) return classDeclaration();
            if (match(VAR)) return varDeclaration();
            if (match(FUN)) return funcDeclaration("function");
            return statement();
        } catch (ParseError error) {
            synchronize();
            return null;
        }
    }

    private Stmt classDeclaration() {
        Token name = consume(IDENTIFIER, "Expect class name.");
        consume(LEFT_BRACE, "Expect '{' before class body.");

        List<Stmt.Function> methods = new ArrayList<>();
        List<Stmt.Function> staticMethods = new ArrayList<>();
        while (!check(RIGHT_BRACE) && !isAtEnd()) {
            (match(STATIC) ? staticMethods : methods ).add(funcDeclaration("method"));
        }

        consume(RIGHT_BRACE, "Expect '}' after class body.");

        return new Stmt.Class(name, methods, staticMethods);
    }

    private Stmt varDeclaration() {
        Token name = consume(IDENTIFIER, "Expect variable name.");

        Expr initializer = null;
        if (match(EQUAL)) {
            initializer = expression();
        }

        consume(SEMICOLON, "Expect ';' after variable declaration.");
        
        return new Stmt.Var(name, initializer);
    }

    private Stmt.Function funcDeclaration(String kind) {
        Token name = consume(IDENTIFIER, "Expect " + kind + " name.");
        
        consume(LEFT_PAREN, "Expect '(' after " + kind + " name.");
        
        List<Token> params = new ArrayList<>();
        if(!check(RIGHT_PAREN)) {
            do {
                if (params.size() >= 255)
                    error(peek(), "Can't have more than 255 parameters.");
                params.add(consume(IDENTIFIER, "Expect parameter name."));
            } while (match(COMMA));
        }

        consume(RIGHT_PAREN, "Expect ')' after parameters.");

        consume(LEFT_BRACE, "Expect '{' before " + kind + " body.");
        List<Stmt> body = block();
        return new Stmt.Function(name, params, body);
    }

    private Stmt statement() {
        if (match(IF)) return ifStatement();
        if (match(WHILE)) return whileStatement();
        if (match(FOR)) return forStatement();
        if (match(PRINT)) return printStatement();
        if (match(BREAK)) return breakStatement();
        if (match(RETURN)) return returnStatement();
        if (match(LEFT_BRACE)) return new Stmt.Block(block());
        return expressionStatement();
    }
    
    private Stmt ifStatement() {
        consume(LEFT_PAREN, "Expect '(' after 'if'.");
        Expr condition = expression();
        consume(RIGHT_PAREN, "Expect ')' after if condition.");
        
        Stmt thenBranch = statement();
        Stmt elseBranch = null;
        if (match(ELSE)) 
            elseBranch = statement();
        
        return new Stmt.If(condition, thenBranch, elseBranch);
    }

    private Stmt whileStatement() {
        consume(LEFT_PAREN, "Expect '(' after 'while'.");
        Expr condition = expression();
        consume(RIGHT_PAREN, "Expect ')' after while condition.");

        try {
            loopDepth++;
            Stmt body = statement();
            return new Stmt.While(condition, body);
        } finally {
            loopDepth--;
        }
    }

    private Stmt forStatement() {
        consume(LEFT_PAREN, "Expect '(' after 'for'.");

        Stmt initializer;
        if (match(SEMICOLON)) {
            initializer = null;
        } else if (match(VAR)) {
            initializer = varDeclaration();
        } else {
            initializer = expressionStatement();
        }

        Expr condition = null;
        if (!check(SEMICOLON))
            condition = expression();
        consume(SEMICOLON, "Expect ';' after loop condition.");

        Expr increment = null;
        if (!check(RIGHT_PAREN))
            increment = expression();
        consume(RIGHT_PAREN, "Expect ')' after for clauses.");

        try {
            loopDepth++;

            Stmt body = statement();
    
            if (increment != null)
                body = new Stmt.Block(Arrays.asList(body, new Stmt.Expression(increment)));
    
            if (condition == null)
                condition = new Expr.Literal(true);
    
            body = new Stmt.While(condition, body);
    
            if (initializer != null)
                body = new Stmt.Block(Arrays.asList(initializer, body));
    
            return body;
        } finally {
            loopDepth--;
        }
    }

    private Stmt printStatement() {
        Expr expression = expression();
        consume(SEMICOLON, "Expect ';' after value.");
        return new Stmt.Print(expression);
    }

    private Stmt breakStatement() {
        if (loopDepth == 0)
            error(previous(), "Must be inside a loop to use 'break'.");
        consume(SEMICOLON, "Expect ';' after break statement.");
        return new Stmt.Break();
    }

    private Stmt returnStatement() {
        Token keyword = previous();
        Expr value = null;
        if(!check(SEMICOLON))
            value = expression();
        consume(SEMICOLON, "Expect ';' after return value.");
        return new Stmt.Return(keyword, value);
    }

    private Stmt expressionStatement () {
        Expr expression = expression();
        consume(SEMICOLON, "Expect ';' after expression.");
        return new Stmt.Expression(expression);
    }

    private List<Stmt> block() {
        List<Stmt> statements = new ArrayList<>();

        while (!check(RIGHT_BRACE) && !isAtEnd()) {
            statements.add(declaration());
        }

        consume(RIGHT_BRACE, "Expect '}' after block.");

        return statements;
    }

    private Expr expression() {
        return multi();
    }

    private Expr multi() {
        Expr expr = assignment();

        while(match(COMMA)) {
            Token operator = previous();
            Expr right = assignment();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr assignment() {
        Expr expr = ternary();
        
        if (match(EQUAL, PLUS_EQUAL, MINUS_EQUAL)) {
            Token operator = previous();
            Expr value = assignment();

            if (expr instanceof Expr.Var) {
                Token name = ((Expr.Var)expr).name;
                return new Expr.Assign(name, operator, value);
            } else if (expr instanceof Expr.Get) {
                Expr.Get get = (Expr.Get)expr;
                return new Expr.Set(get.object, get.name, value);
            }

            error(operator, "Invalid assignment target.");
        }

        return expr;
    }

    private Expr ternary() {
        Expr expr = or();

        if(match(QUESTION)) {
            // According to the C precendence table, the then clause takes precedence
            // over the whole ternary, as if it were parenthesized. Therefore,
            // this portion should be read as an expression, and not an equality
            Expr thenBranch = expression();
            consume(COLON, "Expect ':' after then branch of ternary statement.");
            Expr elseClause = ternary();
            expr = new Expr.Ternary(expr, thenBranch, elseClause);
        }

        return expr;
    }

    private Expr or() {
        Expr expr = and();

        while (match(OR)) {
            Token operator = previous();
            Expr right = and();
            expr = new Expr.Logical(expr, operator, right);
        }

        return expr;
    }

    private Expr and() {
        Expr expr = equality();

        while (match(AND)) {
            Token operator = previous();
            Expr right = equality();
            expr = new Expr.Logical(expr, operator, right);
        }

        return expr;
    }

    private Expr equality() {
        Expr expr = comparison();

        while(match(BANG_EQUAL, EQUAL_EQUAL)) {
            Token operator = previous();
            Expr right = comparison();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr comparison() {
        Expr expr = term();

        while(match(GREATER, GREATER_EQUAL, LESS, LESS_EQUAL)) {
            Token operator = previous();
            Expr right = term();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr term() {
        Expr expr = factor();

        while(match(MINUS, PLUS)) {
            Token operator = previous();
            Expr right = factor();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr factor() {
        Expr expr = unary();

        while(match(SLASH, STAR)) {
            Token operator = previous();
            Expr right = unary();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr unary() {
        if (match(BANG, MINUS, MINUS_MINUS, PLUS_PLUS)) {
            Token operator = previous();
            Expr right = unary();
            switch (operator.type) {
                case BANG:
                case MINUS:
                    return new Expr.Unary(operator, right);
                case MINUS_MINUS:
                case PLUS_PLUS:
                    // NOTE: This causes failure on double negation; maybe make test patch later
                    // Temp fix is return right expr here instead
                    return buildIncDec(operator, right);
                default:
            }
        }
        
        return post();    
    }

    private Expr post(){
        Expr expr = primary();
        if (match(MINUS_MINUS)) {
            Token operator = previous();
            return new Expr.Binary(
                new Expr.Grouping(buildIncDec(operator, expr)), 
                new Token(PLUS, "+", null, operator.line), 
                new Expr.Literal(1.0));
        } else if (match(PLUS_PLUS)) {
            Token operator = previous();
            // (x += 1) - 1
            return new Expr.Binary(
                new Expr.Grouping(buildIncDec(operator, expr)), 
                new Token(MINUS, "-", null, operator.line), 
                new Expr.Literal(1.0));
        } else while (true) {
            if(match(LEFT_PAREN)){
                expr = finishCall(expr);
            } else if (match(DOT)) {
                Token name = consume(IDENTIFIER, "Expect property name after '.'.");
                expr = new Expr.Get(expr, name);
            } else {
                // TODO: Catch error here?
                break;
            }
        }

        return expr;
    }

    private Expr buildIncDec(Token operator, Expr expr) {
        if (expr instanceof Expr.Var) {
            Token name = ((Expr.Var)expr).name;
            Token op = operator.type == MINUS_MINUS ? 
                new Token(MINUS_EQUAL, "-=", null, operator.line):
                new Token(PLUS_EQUAL, "+=", null, operator.line);
            return new Expr.Assign(name, op, new Expr.Literal(1.0));
        }
        error(operator, "Invalid assignment target.");
        return null;
    }

    private Expr finishCall(Expr callee) {
        List<Expr> arguments = new ArrayList<>();
        if (!check(RIGHT_PAREN)) {
            do {
                if (arguments.size() >= 255)
                    error(peek(), "Can't have more than 255 arguments.");
                arguments.add(assignment());
            } while (match(COMMA));
        }
        
        Token paren = consume(RIGHT_PAREN, "Expect ')' after arguments.");
        
        return new Expr.Call(callee, paren, arguments);
    }

    private Expr primary() {
        if (match(FALSE)) return new Expr.Literal(false);
        if (match(TRUE)) return new Expr.Literal(true);
        if (match(NIL)) return new Expr.Literal(null);

        if (match(NUMBER, STRING)) {
            return new Expr.Literal(previous().literal);
        }

        if (match(THIS)) {
            return new Expr.This(previous());
        }

        if (match(LEFT_PAREN)) {
            Expr expr = expression();
            consume(RIGHT_PAREN, "Expect ')' after expression.");
            return new Expr.Grouping(expr);
        }

        if (match(IDENTIFIER)) {
            return new Expr.Var(previous());
        }

        // ----- Error Productions -----
        
        // Multi
        if (match(COMMA)) {
            error(previous(), "Missing left-hand expression");
            multi();
            return null;
        }

        // Assignment
        if (match(EQUAL, MINUS_EQUAL, PLUS_EQUAL)) {
            error(previous(), "Missing left-hand assignment target");
            assignment();
            return null;
        }

        // Ternary (QUESTION only, a leading COLON will get passed to default error)
        if (match(QUESTION)) {
            error(previous(), "Missing left-hand condition");
            expression();
            consume(COLON, "Expect ':' after then branch of ternary statement.");
            ternary();
            return null;
        }

        // Or
        if (match(OR)) {
            error(previous(), "Missing left-hand operand");
            or();
            return null;
        }

        // And
        if (match(AND)) {
            error(previous(), "Missing left-hand operand");
            and();
            return null;
        }
        
        // Equality
        if (match(BANG_EQUAL, EQUAL_EQUAL)) {
            error(previous(), "Missing left-hand operand");
            equality();
            return null;
        }

        // Comparison
        if (match(GREATER, GREATER_EQUAL, LESS, LESS_EQUAL)) {
            error(previous(), "Missing left-hand operand");
            term();
            return null;
        }

        // Term
        if (match(PLUS)) {
            error(previous(), "Missing left-hand operand");
            term();
            return null;
        }

        // Factor
        if (match(SLASH, STAR)) {
            error(previous(), "Missing left-hand operand");
            factor();
            return null;
        }

        throw error(peek(), "Expect expression.");
    }

    private boolean match(TokenType... types) {
        for (TokenType type : types) {
            if(check(type)) {
                advance();
                return true;
            }
        }
        return false;
    }

    private Token consume(TokenType type, String message) {
        if(check(type)) return advance();

        throw error(peek(), message);
    }

    private boolean check(TokenType... types) {
        if (isAtEnd()) return false;
        for (TokenType type : types) {
            if (peek().type == type)
                return true;
        }
        return false;
    }

    private Token advance() {
        if (!isAtEnd()) current++;
        return previous();
    }
    
    private boolean isAtEnd() {
        return peek().type == EOF;
    }

    private Token peek() {
        return tokens.get(current);
    }

    private Token previous() {
        return tokens.get(current - 1);
    }

    private ParseError error(Token token, String message) {
        Lox.error(token, message);
        return new ParseError();
    }

    private void synchronize() {
        advance();
        
        while(!isAtEnd()) {
            if (previous().type == SEMICOLON) return;

            switch (peek().type) {
                case CLASS:
                case FUN:
                case VAR:
                case FOR:
                case IF:
                case WHILE:
                case PRINT:
                case RETURN:
                    return;
                default:
            }

            advance();
        }
    }

}