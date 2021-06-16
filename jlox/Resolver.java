package jlox;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

public class Resolver implements Expr.Visitor<Void>, Stmt.Visitor<Void> {
    
    // This flag is used to appease the testing suite
    boolean test = Global.test; 

    private final Interpreter interpreter;
    private final Stack<Map<String, Variable>> scopes = new Stack<>();
    
    Resolver(Interpreter interpreter) {
        this.interpreter = interpreter;
    }

    private enum FunctionType { NONE, FUNCTION, INIT, METHOD }
    private FunctionType currentFunction = FunctionType.NONE;
    
    private enum ClassType { NONE, CLASS }
    private ClassType currentClass = ClassType.NONE;

    private enum VariableState { DECLARED, DEFINED, READ }

    private static class Variable {
        final Token name;
        VariableState state;

        private Variable(Token name, VariableState state) {
            this.name = name;
            this.state = state;
        }
    }

    @Override
    public Void visit(Stmt.Block stmt) {
        beginScope();
        resolve(stmt.statements);
        endScope();
        return null;
    }

    @Override
    public Void visit(Stmt.Break stmt) {
        return null;
    }

    @Override
    public Void visit(Stmt.Class stmt) {
        ClassType enclosingClass = currentClass;
        currentClass = ClassType.CLASS;

        declare(stmt.name);
        define(stmt.name);

        beginScope();
        scopes.peek().put("this", 
            new Variable(
                new Token(TokenType.THIS, "this", null, -1), 
                VariableState.DEFINED));

        for (Stmt.Function method : stmt.methods) {
            FunctionType declaration = FunctionType.METHOD;
            if (method.name.lexeme.equals("init")) {
                declaration = FunctionType.INIT;
            }
        
            resolveFunction(method, declaration);
        }
        endScope();

        currentClass = enclosingClass;
        return null;
    }

    @Override
    public Void visit(Stmt.Expression stmt) {
        resolve(stmt.expression);
        return null;
    }

    @Override
    public Void visit(Stmt.Function stmt) {
        declare(stmt.name);
        define(stmt.name);

        resolveFunction(stmt, FunctionType.FUNCTION);
        return null;
    }

    @Override
    public Void visit(Stmt.If stmt) {
        resolve(stmt.condition);
        resolve(stmt.thenBranch);
        if (stmt.elseBranch != null) 
            resolve(stmt.elseBranch);
        return null;
    }

    @Override
    public Void visit(Stmt.Print stmt) {
        resolve(stmt.expression);
        return null;
    }

    @Override
    public Void visit(Stmt.Return stmt) {
        if (currentFunction == FunctionType.NONE) {
            Lox.error(stmt.keyword, "Can't return from top-level code.");
        }

        if (stmt.value != null) {
            if (currentFunction == FunctionType.INIT)
                Lox.error(stmt.keyword, "Can't return a value from an initializer.");
            resolve(stmt.value);
        }
        return null;
    }

    @Override
    public Void visit(Stmt.Var stmt) {
        declare(stmt.name);
        if(stmt.initializer != null) {
            resolve(stmt.initializer);
        }
        define(stmt.name);
        return null;
    }

    @Override
    public Void visit(Stmt.While stmt) {
        resolve(stmt.condition);
        resolve(stmt.body);
        return null;
    }

    @Override
    public Void visit(Expr.Assign expr) {
        resolve(expr.value);
        resolveLocal(expr, expr.name, false);
        return null;
    }

    @Override
    public Void visit(Expr.Binary expr) {
        resolve(expr.left);
        resolve(expr.right);
        return null;
    }

    @Override
    public Void visit(Expr.Call expr) {
        resolve(expr.callee);
        for (Expr argument : expr.arguments)
            resolve(argument);
        return null;
    }

    @Override
    public Void visit(Expr.Grouping expr) {
        resolve(expr.expression);
        return null;
    }

    @Override
    public Void visit(Expr.Get expr) {
        resolve(expr.object);
        return null;
    }

    @Override
    public Void visit(Expr.Literal expr) {
        return null;
    }

    @Override
    public Void visit(Expr.Logical expr) {
        resolve(expr.left);
        resolve(expr.right);
        return null;
    }

    @Override
    public Void visit(Expr.Set expr) {
        resolve(expr.value);
        resolve(expr.object);
        return null;
    }

    @Override
    public Void visit(Expr.Ternary expr) {
        resolve(expr.condition);
        resolve(expr.thenBranch);
        resolve(expr.elseClause);
        return null;
    }

    @Override
    public Void visit(Expr.This expr) {
        // TODO: It might possible that 'this' can be assigned to
        // I'm not sure if that'd pass, will have to see
        // For now, we default to true -> VariableState.READ
        if (currentClass == ClassType.NONE) {
            Lox.error(expr.keyword,
                "Can't use 'this' outside of a class.");
            return null;
        }

        resolveLocal(expr, expr.keyword, true);
        return null;
    }

    @Override
    public Void visit(Expr.Unary expr) {
        resolve(expr.right);
        return null;
    }

    @Override
    public Void visit(Expr.Var expr) {
        if(!scopes.isEmpty() 
         && scopes.peek().containsKey(expr.name.lexeme)
         && scopes.peek().get(expr.name.lexeme).state == VariableState.DECLARED) {
            Lox.error(expr.name, "Can't read local variable in its own initializer.");
        }

        resolveLocal(expr, expr.name, true);
        return null;
    }

    private void beginScope() {
        scopes.push(new HashMap<String, Variable>());
    }

    private void endScope() {
        Map<String, Variable> scope = scopes.pop();
        if(test) return; 
        
        for (Map.Entry<String, Variable> entry : scope.entrySet()) {
            if (entry.getKey() == "this") continue;
            if (entry.getValue().state == VariableState.DEFINED) {
                Lox.warning(entry.getValue().name, "Local variable is not used.");
            }
        }
    }

    private void declare(Token name) {
        if (scopes.isEmpty()) return;

        Map<String, Variable> scope = scopes.peek();
        if (scope.containsKey(name.lexeme)) {
            Lox.error(name, "Already variable with this name in this scope.");
        }

        scope.put(name.lexeme, new Variable(name, VariableState.DECLARED));
    }

    private void define(Token name) {
        if (scopes.isEmpty()) return;
        scopes.peek().get(name.lexeme).state = VariableState.DEFINED;
    }

    private void resolveLocal(Expr expr, Token name, boolean isRead) {
        for (int i = scopes.size() - 1; i >= 0; i--) {
            if (scopes.get(i).containsKey(name.lexeme)) {
                interpreter.resolve(expr, scopes.size() - 1 - i);

                // Mark it used.
                if (isRead) {
                    scopes.get(i).get(name.lexeme).state = VariableState.READ;
                }
                return;
            }
        }
    }

    private void resolveFunction(Stmt.Function function, FunctionType type) {
        FunctionType enclosingFunction = currentFunction;
        currentFunction = type;

        beginScope();
        for (Token param : function.params) {
            declare(param);
            define(param);
        }
        resolve(function.body);
        endScope();
        currentFunction = enclosingFunction;
    }

    void resolve(List<Stmt> statements) {
        for (Stmt statement : statements) {
            resolve(statement);
        }
    }

    private void resolve(Stmt stmt) {
        stmt.accept(this);
    }

    private void resolve(Expr expr) {
        expr.accept(this);
    }
}
