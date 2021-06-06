package jlox;

import java.util.List;

abstract class Stmt {
	interface Visitor<R> {
		R visit(Block stmt);
		R visit(Expression stmt);
		R visit(Print stmt);
		R visit(Var stmt);
	}

	static class Block extends Stmt {
		final List<Stmt> statements;

		Block(List<Stmt> statements) {
			this.statements = statements;
		}

		@Override
		<R> R accept(Visitor<R> visitor) {
			return visitor.visit(this);
		}
	}

	static class Expression extends Stmt {
		final Expr expression;

		Expression(Expr expression) {
			this.expression = expression;
		}

		@Override
		<R> R accept(Visitor<R> visitor) {
			return visitor.visit(this);
		}
	}

	static class Print extends Stmt {
		final Expr expression;

		Print(Expr expression) {
			this.expression = expression;
		}

		@Override
		<R> R accept(Visitor<R> visitor) {
			return visitor.visit(this);
		}
	}

	static class Var extends Stmt {
		final Token name;
		final Expr initializer;

		Var(Token name, Expr initializer) {
			this.name = name;
			this.initializer = initializer;
		}

		@Override
		<R> R accept(Visitor<R> visitor) {
			return visitor.visit(this);
		}
	}

	abstract <R> R accept(Visitor<R> visitor);
}
