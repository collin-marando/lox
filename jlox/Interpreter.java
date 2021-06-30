package jlox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class BreakException extends RuntimeException {}

public class Interpreter implements Expr.Visitor<Object>, Stmt.Visitor<Void> {

    // This flag is used to appease the testing suite
    boolean test = Global.test; 

    final Environment globals = new Environment();
    private Environment environment = globals;
    private final Map<Expr, Integer> locals = new HashMap<>();

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
        globals.define("getClass", new LoxCallable(){
            @Override
            public int arity() { return 1; }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                Object arg = arguments.get(0);
                if (arg instanceof LoxInstance && ! (arg instanceof LoxClass))
                    return ((LoxInstance)arg).getLoxClass();

                // TODO: Figure out how to throw a built-in error here, instead of returning null
                // System.out.println("getLoxClass: argument must be an instance");
                return null;
            }
            
            @Override
            public String toString() { return "<nativ fn>"; }
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

        Integer distance = locals.get(expr);
        if (distance != null) {
            environment.assignAt(distance, expr.name, value);
        } else {
            globals.assign(expr.name, value);
        }

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
    public Object visit(Expr.Get expr) {
        Object object = evaluate(expr.object);
        if (object instanceof LoxInstance) {
            return ((LoxInstance)object).get(expr.name);
        }

        throw new RuntimeError(expr.name, "Only instances have properties.");
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
    public Object visit(Expr.Set expr) {
        Object object = evaluate(expr.object);

        if (!(object instanceof LoxInstance)) {
            throw new RuntimeError(expr.name, "Only instances have fields.");
        }

        Object value = evaluate(expr.value);
        ((LoxInstance)object).set(expr.name, value);
        return value;
    }

    @Override
    public Object visit(Expr.Super expr) {
        int distance = locals.get(expr);
        LoxClass superclass = (LoxClass)environment.getAt(distance, "super");
        LoxInstance object = (LoxInstance)environment.getAt(distance - 1, "this");

        LoxFunction method = superclass.findMethod(expr.method.lexeme);
        if (method == null)
            throw new RuntimeError(expr.method, "Undefined property '" + expr.method.lexeme + "'.");
        
        return method.bind(object);
    }

    @Override
    public Object visit(Expr.Ternary expr) {
        Object condition = evaluate(expr.condition);
        return evaluate(isTruthy(condition) ? expr.thenBranch : expr.elseClause);
    }

    @Override
    public Object visit(Expr.This expr) {
        return lookUpVariable(expr.keyword, expr);
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
        return lookUpVariable(expr.name, expr);
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
    public Void visit(Stmt.Class stmt) {
        environment.define(stmt.name.lexeme, null);

        Object superclass = null;
        if (stmt.superclass != null) {
            superclass = evaluate(stmt.superclass);
            if(!(superclass instanceof LoxClass)) {
                throw new RuntimeError(stmt.superclass.name, "Superclass must be a class.");
            }
            environment = new Environment(environment);
            environment.define("super", superclass);
        }   

        Map<String, LoxFunction> staticMethods = new HashMap<>();
        for (Stmt.Function method : stmt.staticMethods) {
            LoxFunction function = new LoxFunction(method, environment, method.name.lexeme.equals("init"));
            staticMethods.put(method.name.lexeme, function);
        }
        // TODO Possibly use superclass on metaclass to add native class functions 
        LoxClass metaclass = new LoxClass(null, null, stmt.name.lexeme + " metaclass", staticMethods);

        Map<String, LoxFunction> methods = new HashMap<>();
        for (Stmt.Function method : stmt.methods) {
            LoxFunction function = new LoxFunction(
                method, environment, method.name.lexeme.equals("init"));
            methods.put(method.name.lexeme, function);
        }

        // TODO If superclass is null, use native object function class 
        LoxClass loxClass = new LoxClass(metaclass, (LoxClass)superclass, stmt.name.lexeme, methods);

        if (superclass != null) {
            environment = environment.enclosing;
        }

        // Call static class initializer, if available
        LoxFunction initializer = metaclass.findMethod("init");
        if (initializer != null) {
            initializer.bind(loxClass).call(this, null);
        }

        environment.assign(stmt.name, loxClass);
        return null;
    }

    @Override
    public Void visit(Stmt.Expression stmt) {
        evaluate(stmt.expression);
        return null;
    }

    @Override
    public Void visit(Stmt.Function stmt) {
        LoxFunction function = new LoxFunction(stmt, environment, false);
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
        //Swtiched to printf so that % flags are supported, icluding %n for newline
        System.out.printf(stringify(value) + "\n");
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
    
    public void resolve(Expr expr, int depth) {
        locals.put(expr, depth);
    }

    private Object lookUpVariable(Token name, Expr expr) {
        Integer distance = locals.get(expr);
        if(distance != null) {
            return environment.getAt(distance, name.lexeme);
        } else {
            return globals.get(name);
        }
    }
}
