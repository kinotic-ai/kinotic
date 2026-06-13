// Generated from KinoticSQL.g4 by ANTLR 4.13.2
package org.kinotic.sql.parser;
import org.antlr.v4.runtime.tree.ParseTreeVisitor;

/**
 * This interface defines a complete generic visitor for a parse tree produced
 * by {@link KinoticSQLParser}.
 *
 * @param <T> The return type of the visit operation. Use {@link Void} for
 * operations with no return type.
 */
public interface KinoticSQLVisitor<T> extends ParseTreeVisitor<T> {
	/**
	 * Visit a parse tree produced by {@link KinoticSQLParser#migrations}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitMigrations(KinoticSQLParser.MigrationsContext ctx);
	/**
	 * Visit a parse tree produced by {@link KinoticSQLParser#statement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitStatement(KinoticSQLParser.StatementContext ctx);
	/**
	 * Visit a parse tree produced by {@link KinoticSQLParser#createTableStatement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCreateTableStatement(KinoticSQLParser.CreateTableStatementContext ctx);
	/**
	 * Visit a parse tree produced by {@link KinoticSQLParser#createComponentTemplateStatement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCreateComponentTemplateStatement(KinoticSQLParser.CreateComponentTemplateStatementContext ctx);
	/**
	 * Visit a parse tree produced by {@link KinoticSQLParser#createIndexTemplateStatement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCreateIndexTemplateStatement(KinoticSQLParser.CreateIndexTemplateStatementContext ctx);
	/**
	 * Visit a parse tree produced by {@link KinoticSQLParser#templatePart}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitTemplatePart(KinoticSQLParser.TemplatePartContext ctx);
	/**
	 * Visit a parse tree produced by {@link KinoticSQLParser#alterTableStatement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitAlterTableStatement(KinoticSQLParser.AlterTableStatementContext ctx);
	/**
	 * Visit a parse tree produced by {@link KinoticSQLParser#reindexStatement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitReindexStatement(KinoticSQLParser.ReindexStatementContext ctx);
	/**
	 * Visit a parse tree produced by {@link KinoticSQLParser#reindexOptions}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitReindexOptions(KinoticSQLParser.ReindexOptionsContext ctx);
	/**
	 * Visit a parse tree produced by {@link KinoticSQLParser#reindexOption}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitReindexOption(KinoticSQLParser.ReindexOptionContext ctx);
	/**
	 * Visit a parse tree produced by {@link KinoticSQLParser#updateStatement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitUpdateStatement(KinoticSQLParser.UpdateStatementContext ctx);
	/**
	 * Visit a parse tree produced by {@link KinoticSQLParser#deleteStatement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitDeleteStatement(KinoticSQLParser.DeleteStatementContext ctx);
	/**
	 * Visit a parse tree produced by {@link KinoticSQLParser#insertStatement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitInsertStatement(KinoticSQLParser.InsertStatementContext ctx);
	/**
	 * Visit a parse tree produced by {@link KinoticSQLParser#valueList}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitValueList(KinoticSQLParser.ValueListContext ctx);
	/**
	 * Visit a parse tree produced by {@link KinoticSQLParser#value}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitValue(KinoticSQLParser.ValueContext ctx);
	/**
	 * Visit a parse tree produced by {@link KinoticSQLParser#assignment}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitAssignment(KinoticSQLParser.AssignmentContext ctx);
	/**
	 * Visit a parse tree produced by {@link KinoticSQLParser#expression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitExpression(KinoticSQLParser.ExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link KinoticSQLParser#operator}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitOperator(KinoticSQLParser.OperatorContext ctx);
	/**
	 * Visit a parse tree produced by {@link KinoticSQLParser#whereClause}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitWhereClause(KinoticSQLParser.WhereClauseContext ctx);
	/**
	 * Visit a parse tree produced by {@link KinoticSQLParser#condition}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCondition(KinoticSQLParser.ConditionContext ctx);
	/**
	 * Visit a parse tree produced by {@link KinoticSQLParser#comparisonOperator}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitComparisonOperator(KinoticSQLParser.ComparisonOperatorContext ctx);
	/**
	 * Visit a parse tree produced by {@link KinoticSQLParser#tableName}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitTableName(KinoticSQLParser.TableNameContext ctx);
	/**
	 * Visit a parse tree produced by {@link KinoticSQLParser#columnName}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitColumnName(KinoticSQLParser.ColumnNameContext ctx);
	/**
	 * Visit a parse tree produced by {@link KinoticSQLParser#columnDefinition}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitColumnDefinition(KinoticSQLParser.ColumnDefinitionContext ctx);
	/**
	 * Visit a parse tree produced by {@link KinoticSQLParser#unionVariant}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitUnionVariant(KinoticSQLParser.UnionVariantContext ctx);
	/**
	 * Visit a parse tree produced by {@link KinoticSQLParser#type}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitType(KinoticSQLParser.TypeContext ctx);
	/**
	 * Visit a parse tree produced by {@link KinoticSQLParser#comment}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitComment(KinoticSQLParser.CommentContext ctx);
}