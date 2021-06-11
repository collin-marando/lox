package jlox;

import java.util.ArrayList;
import java.util.List;

final class BreakException extends RuntimeException {}

public class Interpreter implements Expr.Visitor<Object>, Stmt.Visitor<Void> {

    // This flag is used to appease the testing suite
    final boolean test = false; 

    final Environment globals = new Environment();
    private Environment environment = globals;

    Interpreter() {
        globals.define("clock", new LoxCallable(){
            @Override
            public int arity() { return 0; }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                return (double)System.currentTimeMillis() / 1000.0;
            }

            @Override
            public String toString() { return "<native fn>"; }
        });
    }

    void interpret(List<Stmt> statements) {
        try {
            for (Stmt statement : statements) {
                execute(statement);
            }
        } catch (RuntimeError error) {
            Lox.runtimeError(error);
        }
    }

    @Override
    public Object visit(Expr.Assign expr) {
        Object value = evaluate(expr.value);

        Object targetVal = environment.get(expr.name);
        switch (expr.operator.type) {
            case PLUS_EQUAL:
                checkNumberOperands(expr.operator, environment.get(expr.name), value);
                value = (double)targetVal + (double)value;
                break;
            case MINUS_EQUAL:
                checkNumberOperands(expr.operator, environment.get(expr.name), value);
                value = (double)targetVal - (double)value;
                break;
            default:
        }
        
        environment.assign(expr.name, value);
        return value;
    }

    @Override
    public Object visit(Expr.Binary expr) {
        Object left = evaluate(expr.left);
        Object right = evaluate(expr.right);

        switch (expr.operator.type) {
            case BANG_EQUAL: return !isEqual(left, right);
            case COMMA: return right;
            case EQUAL_EQUAL: return isEqual(left, right);
            case GREATER:
                checkNumberOperands(expr.operator, left, right);
                return (double)left > (double)right;
            case GREATER_EQUAL:
                checkNumberOperands(expr.operator, left, right);
                return (double)left >= (double)right;
            case LESS:
                checkNumberOperands(expr.operator, left, right);
                return (double)left < (double)right;
            case LESS_EQUAL:
                checkNumberOperands(expr.operator, left, right);
                return (double)left <= (double)right;
            case MINUS:
                checkNumberOperands(expr.operator, left, right);
                return (double)left - (double)right;
            case PLUS:
                if (left instanceof Double && right instanceof Double)
                    return (double)left + (double)right;

                if (test) {
                    if (left instanceof String && right instanceof String)
                        return (String)left + (String)right;
                    throw new RuntimeError(expr.operator, "Operands must be two numbers or two strings.");
                } else {
                    if (left instanceof String || right instanceof String)
                        return stringify(left) + stringify(right);
                    throw new RuntimeError(expr.operator, "One of the operands must be a string, or both numbers");
                }
            case SLASH:
                checkNumberOperands(expr.operator, left, right);
                if ((double)right == 0) throw new RuntimeError(expr.operator, "Divide by zero");
                return (double)left / (double)right;
            case STAR:
                checkNumberOperands(expr.operator, left, right);
                return (double)left * (double)right;
            default:
        }

        return null; // This should be unreachable
    }

    @Override
    public Object visit(Expr.Call expr) {
        Object callee = evaluate(expr.callee);

        List<Object> arguments = new ArrayList<>();
        for (Expr argument : expr.arguments) {
            arguments.add(evaluate(argument));
        }

        if (!(callee instanceof LoxCallable)) {
            throw new RuntimeError(expr.paren, "Can only call functions and classes.");
        }

        LoxCallable function = (LoxCallable)callee;

        if (arguments.size() != function.arity()) {
            throw new RuntimeError(expr.paren, "Expected " + function.arity() + " arguments but got " + arguments.size() + ".");
        }

        return function.call(this, arguments);
    }

    @Override
    public Object visit(Expr.Grouping expr) {
        return evaluate(expr.expression);
    }

    @Override
    public Object visit(Expr.Literal expr) {
        return expr.value;
    }

    @Override 
    public Object visit(Expr.Logical expr) {
        Object left = evaluate(expr.left);

        if (expr.operator.type == TokenType.OR && isTruthy(left) 
         || expr.operator.type == TokenType.AND && !isTruthy(left)) {
            return left;
        }

        return evaluate(expr.right);
    }

    @Override
    public Object visit(Expr.Ternary expr) {
        Object condition = evaluate(expr.condition);
        return evaluate(isTruthy(condition) ? expr.thenBranch : expr.elseClause);
    }

    @Override
    public Object visit(Expr.Unary expr) {
        Object right = evaluate(expr.right);

        switch (expr.operator.type) {
            case BANG:
                return !isTruthy(right);
            case MINUS:
                checkNumberOperand(expr.operator, right);
                return -(double)right;
            default:
        }

        return null; // This should be unreachable
    }

    @Override 
    public Object visit(Expr.Var expr) {
        return environment.get(expr.name);
    }

    @Override
    public Void visit(Stmt.Block stmt) {
        executeBlock(stmt.statements, new Environment(environment));
        return null;
    }

    @Override
    public Void visit(Stmt.Break stmt) {
        throw new BreakException();
    }

    @Override
    public Void visit(Stmt.Expression stmt) {
        evaluate(stmt.expression);
        return null;
    }

    @Override
    public Void visit(Stmt.Function stmt) {
        LoxFunction function = new LoxFunction(stmt, environment);
        environment.define(stmt.name.lexeme, function);
        return null;
    }

    @Override
    public Void visit(Stmt.If stmt) {
        if (isTruthy(evaluate(stmt.condition))) {
            execute(stmt.thenBranch);
        } else if (stmt.elseBranch != null) {
            execute(stmt.elseBranch);
        }
        return null;
    }

    @Override
    public Void visit(Stmt.Print stmt) {
        Object value = evaluate(stmt.expression);
        System.out.println(stringify(value));
        return null;
    }

    @Override
    public Void visit(Stmt.Return stmt) {
        Object value = null;
        if(stmt.value != null)
            value = evaluate(stmt.value);
        throw new Return(value);
    }

    @Override
    public Void visit(Stmt.Var stmt) {
        Object value = null;
        if (stmt.initializer != null) {
            value = evaluate(stmt.initializer);
        }
        
        environment.define(stmt.name.lexeme, value);
        return null;
    }
    
    @Override
    public Void visit(Stmt.While stmt) {
        try {
            while (isTruthy(evaluate(stmt.condition))) {
                execute(stmt.body);
            }
        } catch (BreakException e) {
            // Loop exited
        }
        return null;
    }
    
    private void checkNumberOperand(Token operator, Object operand) {
        if (operand instanceof Double) return;
        throw new RuntimeError(operator, "Operand must be a number.");
    }

    private void checkNumberOperands(Token operator, Object left, Object right) {
        if (left instanceof Double && right instanceof Double) return;
        throw new RuntimeError(operator, "Operands must be numbers.");
    }

    private boolean isTruthy(Object object) {
        if (object == null) return false;
        if (object instanceof Boolean) return (boolean)object;
        
        if (test) {
            return true;
        } else {
            if (object instanceof Double) return (double)object != 0;
            if (object instanceof String) return ((String)object).length() != 0;
            return true; // Since all primitives are covered, this should be unreachable 
        }
    }

    private boolean isEqual(Object a, Object b) {
        if (a == null) return b == null;
        return a.equals(b);
    }

    private String stringify(Object object) {
        if (object == null) return "nil";

        if (object instanceof Double) {
            String text = object.toString();
            if (text.endsWith(".0")) {
                text = text.substring(0, text.length() - 2);
            }
            return text;
        }

        return object.toString();
    }

    private Object evaluate(Expr expr) {
        return expr.accept(this);
    }

    private Void execute(Stmt stmt) {
        return stmt.accept(this);
    }

    public void executeBlock(List<Stmt> statements, Environment environment) {
        Environment prev = this.environment;
        try {
            this.environment = environment;
            for(Stmt statement : statements) {
                execute(statement);
            }
        } finally {
            this.environment = prev;
        }
    }
}
