// Generated from AbacPolicy.g4 by ANTLR 4.13.2
package org.kinotic.auth.parser;
import org.antlr.v4.runtime.tree.ParseTreeListener;

/**
 * This interface defines a complete listener for a parse tree produced by
 * {@link AbacPolicyParser}.
 */
public interface AbacPolicyListener extends ParseTreeListener {
	/**
	 * Enter a parse tree produced by {@link AbacPolicyParser#policy}.
	 * @param ctx the parse tree
	 */
	void enterPolicy(AbacPolicyParser.PolicyContext ctx);
	/**
	 * Exit a parse tree produced by {@link AbacPolicyParser#policy}.
	 * @param ctx the parse tree
	 */
	void exitPolicy(AbacPolicyParser.PolicyContext ctx);
	/**
	 * Enter a parse tree produced by the {@code notExpr}
	 * labeled alternative in {@link AbacPolicyParser#expression}.
	 * @param ctx the parse tree
	 */
	void enterNotExpr(AbacPolicyParser.NotExprContext ctx);
	/**
	 * Exit a parse tree produced by the {@code notExpr}
	 * labeled alternative in {@link AbacPolicyParser#expression}.
	 * @param ctx the parse tree
	 */
	void exitNotExpr(AbacPolicyParser.NotExprContext ctx);
	/**
	 * Enter a parse tree produced by the {@code orExpr}
	 * labeled alternative in {@link AbacPolicyParser#expression}.
	 * @param ctx the parse tree
	 */
	void enterOrExpr(AbacPolicyParser.OrExprContext ctx);
	/**
	 * Exit a parse tree produced by the {@code orExpr}
	 * labeled alternative in {@link AbacPolicyParser#expression}.
	 * @param ctx the parse tree
	 */
	void exitOrExpr(AbacPolicyParser.OrExprContext ctx);
	/**
	 * Enter a parse tree produced by the {@code comparisonExpr}
	 * labeled alternative in {@link AbacPolicyParser#expression}.
	 * @param ctx the parse tree
	 */
	void enterComparisonExpr(AbacPolicyParser.ComparisonExprContext ctx);
	/**
	 * Exit a parse tree produced by the {@code comparisonExpr}
	 * labeled alternative in {@link AbacPolicyParser#expression}.
	 * @param ctx the parse tree
	 */
	void exitComparisonExpr(AbacPolicyParser.ComparisonExprContext ctx);
	/**
	 * Enter a parse tree produced by the {@code parenExpr}
	 * labeled alternative in {@link AbacPolicyParser#expression}.
	 * @param ctx the parse tree
	 */
	void enterParenExpr(AbacPolicyParser.ParenExprContext ctx);
	/**
	 * Exit a parse tree produced by the {@code parenExpr}
	 * labeled alternative in {@link AbacPolicyParser#expression}.
	 * @param ctx the parse tree
	 */
	void exitParenExpr(AbacPolicyParser.ParenExprContext ctx);
	/**
	 * Enter a parse tree produced by the {@code andExpr}
	 * labeled alternative in {@link AbacPolicyParser#expression}.
	 * @param ctx the parse tree
	 */
	void enterAndExpr(AbacPolicyParser.AndExprContext ctx);
	/**
	 * Exit a parse tree produced by the {@code andExpr}
	 * labeled alternative in {@link AbacPolicyParser#expression}.
	 * @param ctx the parse tree
	 */
	void exitAndExpr(AbacPolicyParser.AndExprContext ctx);
	/**
	 * Enter a parse tree produced by the {@code pathComparison}
	 * labeled alternative in {@link AbacPolicyParser#comparison}.
	 * @param ctx the parse tree
	 */
	void enterPathComparison(AbacPolicyParser.PathComparisonContext ctx);
	/**
	 * Exit a parse tree produced by the {@code pathComparison}
	 * labeled alternative in {@link AbacPolicyParser#comparison}.
	 * @param ctx the parse tree
	 */
	void exitPathComparison(AbacPolicyParser.PathComparisonContext ctx);
	/**
	 * Enter a parse tree produced by the {@code literalComparison}
	 * labeled alternative in {@link AbacPolicyParser#comparison}.
	 * @param ctx the parse tree
	 */
	void enterLiteralComparison(AbacPolicyParser.LiteralComparisonContext ctx);
	/**
	 * Exit a parse tree produced by the {@code literalComparison}
	 * labeled alternative in {@link AbacPolicyParser#comparison}.
	 * @param ctx the parse tree
	 */
	void exitLiteralComparison(AbacPolicyParser.LiteralComparisonContext ctx);
	/**
	 * Enter a parse tree produced by the {@code inComparison}
	 * labeled alternative in {@link AbacPolicyParser#comparison}.
	 * @param ctx the parse tree
	 */
	void enterInComparison(AbacPolicyParser.InComparisonContext ctx);
	/**
	 * Exit a parse tree produced by the {@code inComparison}
	 * labeled alternative in {@link AbacPolicyParser#comparison}.
	 * @param ctx the parse tree
	 */
	void exitInComparison(AbacPolicyParser.InComparisonContext ctx);
	/**
	 * Enter a parse tree produced by the {@code containsComparison}
	 * labeled alternative in {@link AbacPolicyParser#comparison}.
	 * @param ctx the parse tree
	 */
	void enterContainsComparison(AbacPolicyParser.ContainsComparisonContext ctx);
	/**
	 * Exit a parse tree produced by the {@code containsComparison}
	 * labeled alternative in {@link AbacPolicyParser#comparison}.
	 * @param ctx the parse tree
	 */
	void exitContainsComparison(AbacPolicyParser.ContainsComparisonContext ctx);
	/**
	 * Enter a parse tree produced by the {@code existsComparison}
	 * labeled alternative in {@link AbacPolicyParser#comparison}.
	 * @param ctx the parse tree
	 */
	void enterExistsComparison(AbacPolicyParser.ExistsComparisonContext ctx);
	/**
	 * Exit a parse tree produced by the {@code existsComparison}
	 * labeled alternative in {@link AbacPolicyParser#comparison}.
	 * @param ctx the parse tree
	 */
	void exitExistsComparison(AbacPolicyParser.ExistsComparisonContext ctx);
	/**
	 * Enter a parse tree produced by the {@code likeComparison}
	 * labeled alternative in {@link AbacPolicyParser#comparison}.
	 * @param ctx the parse tree
	 */
	void enterLikeComparison(AbacPolicyParser.LikeComparisonContext ctx);
	/**
	 * Exit a parse tree produced by the {@code likeComparison}
	 * labeled alternative in {@link AbacPolicyParser#comparison}.
	 * @param ctx the parse tree
	 */
	void exitLikeComparison(AbacPolicyParser.LikeComparisonContext ctx);
	/**
	 * Enter a parse tree produced by {@link AbacPolicyParser#comparisonOp}.
	 * @param ctx the parse tree
	 */
	void enterComparisonOp(AbacPolicyParser.ComparisonOpContext ctx);
	/**
	 * Exit a parse tree produced by {@link AbacPolicyParser#comparisonOp}.
	 * @param ctx the parse tree
	 */
	void exitComparisonOp(AbacPolicyParser.ComparisonOpContext ctx);
	/**
	 * Enter a parse tree produced by {@link AbacPolicyParser#path}.
	 * @param ctx the parse tree
	 */
	void enterPath(AbacPolicyParser.PathContext ctx);
	/**
	 * Exit a parse tree produced by {@link AbacPolicyParser#path}.
	 * @param ctx the parse tree
	 */
	void exitPath(AbacPolicyParser.PathContext ctx);
	/**
	 * Enter a parse tree produced by {@link AbacPolicyParser#literal}.
	 * @param ctx the parse tree
	 */
	void enterLiteral(AbacPolicyParser.LiteralContext ctx);
	/**
	 * Exit a parse tree produced by {@link AbacPolicyParser#literal}.
	 * @param ctx the parse tree
	 */
	void exitLiteral(AbacPolicyParser.LiteralContext ctx);
	/**
	 * Enter a parse tree produced by {@link AbacPolicyParser#array}.
	 * @param ctx the parse tree
	 */
	void enterArray(AbacPolicyParser.ArrayContext ctx);
	/**
	 * Exit a parse tree produced by {@link AbacPolicyParser#array}.
	 * @param ctx the parse tree
	 */
	void exitArray(AbacPolicyParser.ArrayContext ctx);
}