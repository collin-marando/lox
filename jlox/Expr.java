package jlox;

abstract class Expr {
	interface Visitor<R> {
		R visit(Binary expr);
		R visit(Grouping expr);
		R visit(Literal expr);
		R visit(Ternary expr);
		R visit(Unary expr);
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

	static class Ternary extends Expr {
		final Expr ifClause;
		final Expr thenClause;
		final Expr elseClause;

		Ternary(Expr ifClause, Expr thenClause, Expr elseClause) {
			this.ifClause = ifClause;
			this.thenClause = thenClause;
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

	abstract <R> R accept(Visitor<R> visitor);
}
