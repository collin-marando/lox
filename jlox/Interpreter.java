package jlox;

import java.util.List;

public class Interpreter implements Expr.Visitor<Object>, Stmt.Visitor<Void> {

    private Environment environment = new Environment();

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
                if (left instanceof String || right instanceof String)
                    return stringify(left) + stringify(right);
                if (left instanceof Double && right instanceof Double)
                    return (double)left + (double)right;
                throw new RuntimeError(expr.operator, "One of the operands must be a string, or both numbers");
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
    public Object visit(Expr.Grouping expr) {
        return evaluate(expr.expression);
    }

    @Override
    public Object visit(Expr.Literal expr) {
        return expr.value;
    }

    @Override
    public Object visit(Expr.Ternary expr) {
        Object condition = evaluate(expr.ifClause);
        return evaluate(isTruthy(condition) ? expr.thenClause : expr.elseClause);
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
    public Void visit(Stmt.Print stmt) {
        Object value = evaluate(stmt.expression);
        System.out.println(stringify(value));
        return null;
    }

    @Override
    public Void visit(Stmt.Expression stmt) {
        evaluate(stmt.expression);
        return null;
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
        if (object instanceof Double) return (double)object != 0;
        if (object instanceof String) return ((String)object).length() != 0;
        return true; // Since all primitives are covered, this should be unreachable 
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

    private void executeBlock(List<Stmt> statements, Environment environment) {
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
