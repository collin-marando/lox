package jlox;

import java.util.List;

abstract class Expr {
	interface Visitor<R> {
		R visit(Assign expr);
		R visit(Binary expr);
		R visit(Call expr);
		R visit(Grouping expr);
		R visit(Literal expr);
		R visit(Logical expr);
		R visit(Ternary expr);
		R visit(Unary expr);
		R visit(Var expr);
	}

	static class Assign extends Expr {
		final Token name;
		final Expr value;

		Assign(Token name, Expr value) {
			this.name = name;
			this.value = value;
		}

		@Override
		<R> R accept(Visitor<R> visitor) {
			return visitor.visit(this);
		}
	}

	static class Binary extends Expr {
		final Expr left;
		final Token operator;
		final Expr right;

		Binary(Expr left, Token operator, Expr right) {
			this.left = left;
			this.operator = operator;
			this.right = right;
		}

		@Override
		<R> R accept(Visitor<R> visitor) {
			return visitor.visit(this);
		}
	}

	static class Call extends Expr {
		final Expr callee;
		final Token paren;
		final List<Expr> arguments;

		Call(Expr callee, Token paren, List<Expr> arguments) {
			this.callee = callee;
			this.paren = paren;
			this.arguments = arguments;
		}

		@Override
		<R> R accept(Visitor<R> visitor) {
			return visitor.visit(this);
		}
	}

	static class Grouping extends Expr {
		final Expr expression;

		Grouping(Expr expression) {
			this.expression = expression;
		}

		@Override
		<R> R accept(Visitor<R> visitor) {
			return visitor.visit(this);
		}
	}

	static class Literal extends Expr {
		final Object value;

		Literal(Object value) {
			this.value = value;
		}

		@Override
		<R> R accept(Visitor<R> visitor) {
			return visitor.visit(this);
		}
	}

	static class Logical extends Expr {
		final Expr left;
		final Token operator;
		final Expr right;

		Logical(Expr left, Token operator, Expr right) {
			this.left = left;
			this.operator = operator;
			this.right = right;
		}

		@Override
		<R> R accept(Visitor<R> visitor) {
			return visitor.visit(this);
		}
	}

	static class Ternary extends Expr {
		final Expr condition;
		final Expr thenBranch;
		final Expr elseClause;

		Ternary(Expr condition, Expr thenBranch, Expr elseClause) {
			this.condition = condition;
			this.thenBranch = thenBranch;
			this.elseClause = elseClause;
		}

		@Override
		<R> R accept(Visitor<R> visitor) {
			return visitor.visit(this);
		}
	}

	static class Unary extends Expr {
		final Token operator;
		final Expr right;

		Unary(Token operator, Expr right) {
			this.operator = operator;
			this.right = right;
		}

		@Override
		<R> R accept(Visitor<R> visitor) {
			return visitor.visit(this);
		}
	}

	static class Var extends Expr {
		final Token name;

		Var(Token name) {
			this.name = name;
		}

		@Override
		<R> R accept(Visitor<R> visitor) {
			return visitor.visit(this);
		}
	}

	abstract <R> R accept(Visitor<R> visitor);
}
