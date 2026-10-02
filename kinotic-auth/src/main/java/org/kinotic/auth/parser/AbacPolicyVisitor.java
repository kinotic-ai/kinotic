// Generated from AbacPolicy.g4 by ANTLR 4.13.2
package org.kinotic.auth.parser;
import org.antlr.v4.runtime.tree.ParseTreeVisitor;

/**
 * This interface defines a complete generic visitor for a parse tree produced
 * by {@link AbacPolicyParser}.
 *
 * @param <T> The return type of the visit operation. Use {@link Void} for
 * operations with no return type.
 */
public interface AbacPolicyVisitor<T> extends ParseTreeVisitor<T> {
	/**
	 * Visit a parse tree produced by {@link AbacPolicyParser#policy}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitPolicy(AbacPolicyParser.PolicyContext ctx);
	/**
	 * Visit a parse tree produced by the {@code notExpr}
	 * labeled alternative in {@link AbacPolicyParser#expression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitNotExpr(AbacPolicyParser.NotExprContext ctx);
	/**
	 * Visit a parse tree produced by the {@code orExpr}
	 * labeled alternative in {@link AbacPolicyParser#expression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitOrExpr(AbacPolicyParser.OrExprContext ctx);
	/**
	 * Visit a parse tree produced by the {@code comparisonExpr}
	 * labeled alternative in {@link AbacPolicyParser#expression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitComparisonExpr(AbacPolicyParser.ComparisonExprContext ctx);
	/**
	 * Visit a parse tree produced by the {@code parenExpr}
	 * labeled alternative in {@link AbacPolicyParser#expression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitParenExpr(AbacPolicyParser.ParenExprContext ctx);
	/**
	 * Visit a parse tree produced by the {@code andExpr}
	 * labeled alternative in {@link AbacPolicyParser#expression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitAndExpr(AbacPolicyParser.AndExprContext ctx);
	/**
	 * Visit a parse tree produced by the {@code pathComparison}
	 * labeled alternative in {@link AbacPolicyParser#comparison}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitPathComparison(AbacPolicyParser.PathComparisonContext ctx);
	/**
	 * Visit a parse tree produced by the {@code literalComparison}
	 * labeled alternative in {@link AbacPolicyParser#comparison}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitLiteralComparison(AbacPolicyParser.LiteralComparisonContext ctx);
	/**
	 * Visit a parse tree produced by the {@code inComparison}
	 * labeled alternative in {@link AbacPolicyParser#comparison}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitInComparison(AbacPolicyParser.InComparisonContext ctx);
	/**
	 * Visit a parse tree produced by the {@code containsComparison}
	 * labeled alternative in {@link AbacPolicyParser#comparison}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitContainsComparison(AbacPolicyParser.ContainsComparisonContext ctx);
	/**
	 * Visit a parse tree produced by the {@code existsComparison}
	 * labeled alternative in {@link AbacPolicyParser#comparison}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitExistsComparison(AbacPolicyParser.ExistsComparisonContext ctx);
	/**
	 * Visit a parse tree produced by the {@code likeComparison}
	 * labeled alternative in {@link AbacPolicyParser#comparison}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitLikeComparison(AbacPolicyParser.LikeComparisonContext ctx);
	/**
	 * Visit a parse tree produced by {@link AbacPolicyParser#comparisonOp}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitComparisonOp(AbacPolicyParser.ComparisonOpContext ctx);
	/**
	 * Visit a parse tree produced by {@link AbacPolicyParser#path}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitPath(AbacPolicyParser.PathContext ctx);
	/**
	 * Visit a parse tree produced by {@link AbacPolicyParser#literal}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitLiteral(AbacPolicyParser.LiteralContext ctx);
	/**
	 * Visit a parse tree produced by {@link AbacPolicyParser#array}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitArray(AbacPolicyParser.ArrayContext ctx);
}