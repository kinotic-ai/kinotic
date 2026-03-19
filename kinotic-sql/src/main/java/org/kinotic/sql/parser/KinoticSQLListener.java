// Generated from KinoticSQL.g4 by ANTLR 4.13.2
package org.kinotic.sql.parser;
import org.antlr.v4.runtime.tree.ParseTreeListener;

/**
 * This interface defines a complete listener for a parse tree produced by
 * {@link KinoticSQLParser}.
 */
public interface KinoticSQLListener extends ParseTreeListener {
	/**
	 * Enter a parse tree produced by {@link KinoticSQLParser#migrations}.
	 * @param ctx the parse tree
	 */
	void enterMigrations(KinoticSQLParser.MigrationsContext ctx);
	/**
	 * Exit a parse tree produced by {@link KinoticSQLParser#migrations}.
	 * @param ctx the parse tree
	 */
	void exitMigrations(KinoticSQLParser.MigrationsContext ctx);
	/**
	 * Enter a parse tree produced by {@link KinoticSQLParser#statement}.
	 * @param ctx the parse tree
	 */
	void enterStatement(KinoticSQLParser.StatementContext ctx);
	/**
	 * Exit a parse tree produced by {@link KinoticSQLParser#statement}.
	 * @param ctx the parse tree
	 */
	void exitStatement(KinoticSQLParser.StatementContext ctx);
	/**
	 * Enter a parse tree produced by {@link KinoticSQLParser#createTableStatement}.
	 * @param ctx the parse tree
	 */
	void enterCreateTableStatement(KinoticSQLParser.CreateTableStatementContext ctx);
	/**
	 * Exit a parse tree produced by {@link KinoticSQLParser#createTableStatement}.
	 * @param ctx the parse tree
	 */
	void exitCreateTableStatement(KinoticSQLParser.CreateTableStatementContext ctx);
	/**
	 * Enter a parse tree produced by {@link KinoticSQLParser#createComponentTemplateStatement}.
	 * @param ctx the parse tree
	 */
	void enterCreateComponentTemplateStatement(KinoticSQLParser.CreateComponentTemplateStatementContext ctx);
	/**
	 * Exit a parse tree produced by {@link KinoticSQLParser#createComponentTemplateStatement}.
	 * @param ctx the parse tree
	 */
	void exitCreateComponentTemplateStatement(KinoticSQLParser.CreateComponentTemplateStatementContext ctx);
	/**
	 * Enter a parse tree produced by {@link KinoticSQLParser#createIndexTemplateStatement}.
	 * @param ctx the parse tree
	 */
	void enterCreateIndexTemplateStatement(KinoticSQLParser.CreateIndexTemplateStatementContext ctx);
	/**
	 * Exit a parse tree produced by {@link KinoticSQLParser#createIndexTemplateStatement}.
	 * @param ctx the parse tree
	 */
	void exitCreateIndexTemplateStatement(KinoticSQLParser.CreateIndexTemplateStatementContext ctx);
	/**
	 * Enter a parse tree produced by {@link KinoticSQLParser#templatePart}.
	 * @param ctx the parse tree
	 */
	void enterTemplatePart(KinoticSQLParser.TemplatePartContext ctx);
	/**
	 * Exit a parse tree produced by {@link KinoticSQLParser#templatePart}.
	 * @param ctx the parse tree
	 */
	void exitTemplatePart(KinoticSQLParser.TemplatePartContext ctx);
	/**
	 * Enter a parse tree produced by {@link KinoticSQLParser#alterTableStatement}.
	 * @param ctx the parse tree
	 */
	void enterAlterTableStatement(KinoticSQLParser.AlterTableStatementContext ctx);
	/**
	 * Exit a parse tree produced by {@link KinoticSQLParser#alterTableStatement}.
	 * @param ctx the parse tree
	 */
	void exitAlterTableStatement(KinoticSQLParser.AlterTableStatementContext ctx);
	/**
	 * Enter a parse tree produced by {@link KinoticSQLParser#reindexStatement}.
	 * @param ctx the parse tree
	 */
	void enterReindexStatement(KinoticSQLParser.ReindexStatementContext ctx);
	/**
	 * Exit a parse tree produced by {@link KinoticSQLParser#reindexStatement}.
	 * @param ctx the parse tree
	 */
	void exitReindexStatement(KinoticSQLParser.ReindexStatementContext ctx);
	/**
	 * Enter a parse tree produced by {@link KinoticSQLParser#reindexOptions}.
	 * @param ctx the parse tree
	 */
	void enterReindexOptions(KinoticSQLParser.ReindexOptionsContext ctx);
	/**
	 * Exit a parse tree produced by {@link KinoticSQLParser#reindexOptions}.
	 * @param ctx the parse tree
	 */
	void exitReindexOptions(KinoticSQLParser.ReindexOptionsContext ctx);
	/**
	 * Enter a parse tree produced by {@link KinoticSQLParser#reindexOption}.
	 * @param ctx the parse tree
	 */
	void enterReindexOption(KinoticSQLParser.ReindexOptionContext ctx);
	/**
	 * Exit a parse tree produced by {@link KinoticSQLParser#reindexOption}.
	 * @param ctx the parse tree
	 */
	void exitReindexOption(KinoticSQLParser.ReindexOptionContext ctx);
	/**
	 * Enter a parse tree produced by {@link KinoticSQLParser#updateStatement}.
	 * @param ctx the parse tree
	 */
	void enterUpdateStatement(KinoticSQLParser.UpdateStatementContext ctx);
	/**
	 * Exit a parse tree produced by {@link KinoticSQLParser#updateStatement}.
	 * @param ctx the parse tree
	 */
	void exitUpdateStatement(KinoticSQLParser.UpdateStatementContext ctx);
	/**
	 * Enter a parse tree produced by {@link KinoticSQLParser#deleteStatement}.
	 * @param ctx the parse tree
	 */
	void enterDeleteStatement(KinoticSQLParser.DeleteStatementContext ctx);
	/**
	 * Exit a parse tree produced by {@link KinoticSQLParser#deleteStatement}.
	 * @param ctx the parse tree
	 */
	void exitDeleteStatement(KinoticSQLParser.DeleteStatementContext ctx);
	/**
	 * Enter a parse tree produced by {@link KinoticSQLParser#insertStatement}.
	 * @param ctx the parse tree
	 */
	void enterInsertStatement(KinoticSQLParser.InsertStatementContext ctx);
	/**
	 * Exit a parse tree produced by {@link KinoticSQLParser#insertStatement}.
	 * @param ctx the parse tree
	 */
	void exitInsertStatement(KinoticSQLParser.InsertStatementContext ctx);
	/**
	 * Enter a parse tree produced by {@link KinoticSQLParser#valueList}.
	 * @param ctx the parse tree
	 */
	void enterValueList(KinoticSQLParser.ValueListContext ctx);
	/**
	 * Exit a parse tree produced by {@link KinoticSQLParser#valueList}.
	 * @param ctx the parse tree
	 */
	void exitValueList(KinoticSQLParser.ValueListContext ctx);
	/**
	 * Enter a parse tree produced by {@link KinoticSQLParser#value}.
	 * @param ctx the parse tree
	 */
	void enterValue(KinoticSQLParser.ValueContext ctx);
	/**
	 * Exit a parse tree produced by {@link KinoticSQLParser#value}.
	 * @param ctx the parse tree
	 */
	void exitValue(KinoticSQLParser.ValueContext ctx);
	/**
	 * Enter a parse tree produced by {@link KinoticSQLParser#assignment}.
	 * @param ctx the parse tree
	 */
	void enterAssignment(KinoticSQLParser.AssignmentContext ctx);
	/**
	 * Exit a parse tree produced by {@link KinoticSQLParser#assignment}.
	 * @param ctx the parse tree
	 */
	void exitAssignment(KinoticSQLParser.AssignmentContext ctx);
	/**
	 * Enter a parse tree produced by {@link KinoticSQLParser#expression}.
	 * @param ctx the parse tree
	 */
	void enterExpression(KinoticSQLParser.ExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link KinoticSQLParser#expression}.
	 * @param ctx the parse tree
	 */
	void exitExpression(KinoticSQLParser.ExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link KinoticSQLParser#operator}.
	 * @param ctx the parse tree
	 */
	void enterOperator(KinoticSQLParser.OperatorContext ctx);
	/**
	 * Exit a parse tree produced by {@link KinoticSQLParser#operator}.
	 * @param ctx the parse tree
	 */
	void exitOperator(KinoticSQLParser.OperatorContext ctx);
	/**
	 * Enter a parse tree produced by {@link KinoticSQLParser#whereClause}.
	 * @param ctx the parse tree
	 */
	void enterWhereClause(KinoticSQLParser.WhereClauseContext ctx);
	/**
	 * Exit a parse tree produced by {@link KinoticSQLParser#whereClause}.
	 * @param ctx the parse tree
	 */
	void exitWhereClause(KinoticSQLParser.WhereClauseContext ctx);
	/**
	 * Enter a parse tree produced by {@link KinoticSQLParser#condition}.
	 * @param ctx the parse tree
	 */
	void enterCondition(KinoticSQLParser.ConditionContext ctx);
	/**
	 * Exit a parse tree produced by {@link KinoticSQLParser#condition}.
	 * @param ctx the parse tree
	 */
	void exitCondition(KinoticSQLParser.ConditionContext ctx);
	/**
	 * Enter a parse tree produced by {@link KinoticSQLParser#comparisonOperator}.
	 * @param ctx the parse tree
	 */
	void enterComparisonOperator(KinoticSQLParser.ComparisonOperatorContext ctx);
	/**
	 * Exit a parse tree produced by {@link KinoticSQLParser#comparisonOperator}.
	 * @param ctx the parse tree
	 */
	void exitComparisonOperator(KinoticSQLParser.ComparisonOperatorContext ctx);
	/**
	 * Enter a parse tree produced by {@link KinoticSQLParser#tableName}.
	 * @param ctx the parse tree
	 */
	void enterTableName(KinoticSQLParser.TableNameContext ctx);
	/**
	 * Exit a parse tree produced by {@link KinoticSQLParser#tableName}.
	 * @param ctx the parse tree
	 */
	void exitTableName(KinoticSQLParser.TableNameContext ctx);
	/**
	 * Enter a parse tree produced by {@link KinoticSQLParser#columnName}.
	 * @param ctx the parse tree
	 */
	void enterColumnName(KinoticSQLParser.ColumnNameContext ctx);
	/**
	 * Exit a parse tree produced by {@link KinoticSQLParser#columnName}.
	 * @param ctx the parse tree
	 */
	void exitColumnName(KinoticSQLParser.ColumnNameContext ctx);
	/**
	 * Enter a parse tree produced by {@link KinoticSQLParser#columnDefinition}.
	 * @param ctx the parse tree
	 */
	void enterColumnDefinition(KinoticSQLParser.ColumnDefinitionContext ctx);
	/**
	 * Exit a parse tree produced by {@link KinoticSQLParser#columnDefinition}.
	 * @param ctx the parse tree
	 */
	void exitColumnDefinition(KinoticSQLParser.ColumnDefinitionContext ctx);
	/**
	 * Enter a parse tree produced by {@link KinoticSQLParser#type}.
	 * @param ctx the parse tree
	 */
	void enterType(KinoticSQLParser.TypeContext ctx);
	/**
	 * Exit a parse tree produced by {@link KinoticSQLParser#type}.
	 * @param ctx the parse tree
	 */
	void exitType(KinoticSQLParser.TypeContext ctx);
	/**
	 * Enter a parse tree produced by {@link KinoticSQLParser#comment}.
	 * @param ctx the parse tree
	 */
	void enterComment(KinoticSQLParser.CommentContext ctx);
	/**
	 * Exit a parse tree produced by {@link KinoticSQLParser#comment}.
	 * @param ctx the parse tree
	 */
	void exitComment(KinoticSQLParser.CommentContext ctx);
}