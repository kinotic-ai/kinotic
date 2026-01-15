// Generated from StructuresSQL.g4 by ANTLR 4.13.2
package org.mindignited.structures.sql.parser;
import org.antlr.v4.runtime.tree.ParseTreeListener;

/**
 * This interface defines a complete listener for a parse tree produced by
 * {@link StructuresSQLParser}.
 */
public interface StructuresSQLListener extends ParseTreeListener {
	/**
	 * Enter a parse tree produced by {@link StructuresSQLParser#migrations}.
	 * @param ctx the parse tree
	 */
	void enterMigrations(StructuresSQLParser.MigrationsContext ctx);
	/**
	 * Exit a parse tree produced by {@link StructuresSQLParser#migrations}.
	 * @param ctx the parse tree
	 */
	void exitMigrations(StructuresSQLParser.MigrationsContext ctx);
	/**
	 * Enter a parse tree produced by {@link StructuresSQLParser#statement}.
	 * @param ctx the parse tree
	 */
	void enterStatement(StructuresSQLParser.StatementContext ctx);
	/**
	 * Exit a parse tree produced by {@link StructuresSQLParser#statement}.
	 * @param ctx the parse tree
	 */
	void exitStatement(StructuresSQLParser.StatementContext ctx);
	/**
	 * Enter a parse tree produced by {@link StructuresSQLParser#createTableStatement}.
	 * @param ctx the parse tree
	 */
	void enterCreateTableStatement(StructuresSQLParser.CreateTableStatementContext ctx);
	/**
	 * Exit a parse tree produced by {@link StructuresSQLParser#createTableStatement}.
	 * @param ctx the parse tree
	 */
	void exitCreateTableStatement(StructuresSQLParser.CreateTableStatementContext ctx);
	/**
	 * Enter a parse tree produced by {@link StructuresSQLParser#createComponentTemplateStatement}.
	 * @param ctx the parse tree
	 */
	void enterCreateComponentTemplateStatement(StructuresSQLParser.CreateComponentTemplateStatementContext ctx);
	/**
	 * Exit a parse tree produced by {@link StructuresSQLParser#createComponentTemplateStatement}.
	 * @param ctx the parse tree
	 */
	void exitCreateComponentTemplateStatement(StructuresSQLParser.CreateComponentTemplateStatementContext ctx);
	/**
	 * Enter a parse tree produced by {@link StructuresSQLParser#createIndexTemplateStatement}.
	 * @param ctx the parse tree
	 */
	void enterCreateIndexTemplateStatement(StructuresSQLParser.CreateIndexTemplateStatementContext ctx);
	/**
	 * Exit a parse tree produced by {@link StructuresSQLParser#createIndexTemplateStatement}.
	 * @param ctx the parse tree
	 */
	void exitCreateIndexTemplateStatement(StructuresSQLParser.CreateIndexTemplateStatementContext ctx);
	/**
	 * Enter a parse tree produced by {@link StructuresSQLParser#templatePart}.
	 * @param ctx the parse tree
	 */
	void enterTemplatePart(StructuresSQLParser.TemplatePartContext ctx);
	/**
	 * Exit a parse tree produced by {@link StructuresSQLParser#templatePart}.
	 * @param ctx the parse tree
	 */
	void exitTemplatePart(StructuresSQLParser.TemplatePartContext ctx);
	/**
	 * Enter a parse tree produced by {@link StructuresSQLParser#alterTableStatement}.
	 * @param ctx the parse tree
	 */
	void enterAlterTableStatement(StructuresSQLParser.AlterTableStatementContext ctx);
	/**
	 * Exit a parse tree produced by {@link StructuresSQLParser#alterTableStatement}.
	 * @param ctx the parse tree
	 */
	void exitAlterTableStatement(StructuresSQLParser.AlterTableStatementContext ctx);
	/**
	 * Enter a parse tree produced by {@link StructuresSQLParser#reindexStatement}.
	 * @param ctx the parse tree
	 */
	void enterReindexStatement(StructuresSQLParser.ReindexStatementContext ctx);
	/**
	 * Exit a parse tree produced by {@link StructuresSQLParser#reindexStatement}.
	 * @param ctx the parse tree
	 */
	void exitReindexStatement(StructuresSQLParser.ReindexStatementContext ctx);
	/**
	 * Enter a parse tree produced by {@link StructuresSQLParser#reindexOptions}.
	 * @param ctx the parse tree
	 */
	void enterReindexOptions(StructuresSQLParser.ReindexOptionsContext ctx);
	/**
	 * Exit a parse tree produced by {@link StructuresSQLParser#reindexOptions}.
	 * @param ctx the parse tree
	 */
	void exitReindexOptions(StructuresSQLParser.ReindexOptionsContext ctx);
	/**
	 * Enter a parse tree produced by {@link StructuresSQLParser#reindexOption}.
	 * @param ctx the parse tree
	 */
	void enterReindexOption(StructuresSQLParser.ReindexOptionContext ctx);
	/**
	 * Exit a parse tree produced by {@link StructuresSQLParser#reindexOption}.
	 * @param ctx the parse tree
	 */
	void exitReindexOption(StructuresSQLParser.ReindexOptionContext ctx);
	/**
	 * Enter a parse tree produced by {@link StructuresSQLParser#updateStatement}.
	 * @param ctx the parse tree
	 */
	void enterUpdateStatement(StructuresSQLParser.UpdateStatementContext ctx);
	/**
	 * Exit a parse tree produced by {@link StructuresSQLParser#updateStatement}.
	 * @param ctx the parse tree
	 */
	void exitUpdateStatement(StructuresSQLParser.UpdateStatementContext ctx);
	/**
	 * Enter a parse tree produced by {@link StructuresSQLParser#deleteStatement}.
	 * @param ctx the parse tree
	 */
	void enterDeleteStatement(StructuresSQLParser.DeleteStatementContext ctx);
	/**
	 * Exit a parse tree produced by {@link StructuresSQLParser#deleteStatement}.
	 * @param ctx the parse tree
	 */
	void exitDeleteStatement(StructuresSQLParser.DeleteStatementContext ctx);
	/**
	 * Enter a parse tree produced by {@link StructuresSQLParser#insertStatement}.
	 * @param ctx the parse tree
	 */
	void enterInsertStatement(StructuresSQLParser.InsertStatementContext ctx);
	/**
	 * Exit a parse tree produced by {@link StructuresSQLParser#insertStatement}.
	 * @param ctx the parse tree
	 */
	void exitInsertStatement(StructuresSQLParser.InsertStatementContext ctx);
	/**
	 * Enter a parse tree produced by {@link StructuresSQLParser#valueList}.
	 * @param ctx the parse tree
	 */
	void enterValueList(StructuresSQLParser.ValueListContext ctx);
	/**
	 * Exit a parse tree produced by {@link StructuresSQLParser#valueList}.
	 * @param ctx the parse tree
	 */
	void exitValueList(StructuresSQLParser.ValueListContext ctx);
	/**
	 * Enter a parse tree produced by {@link StructuresSQLParser#value}.
	 * @param ctx the parse tree
	 */
	void enterValue(StructuresSQLParser.ValueContext ctx);
	/**
	 * Exit a parse tree produced by {@link StructuresSQLParser#value}.
	 * @param ctx the parse tree
	 */
	void exitValue(StructuresSQLParser.ValueContext ctx);
	/**
	 * Enter a parse tree produced by {@link StructuresSQLParser#assignment}.
	 * @param ctx the parse tree
	 */
	void enterAssignment(StructuresSQLParser.AssignmentContext ctx);
	/**
	 * Exit a parse tree produced by {@link StructuresSQLParser#assignment}.
	 * @param ctx the parse tree
	 */
	void exitAssignment(StructuresSQLParser.AssignmentContext ctx);
	/**
	 * Enter a parse tree produced by {@link StructuresSQLParser#expression}.
	 * @param ctx the parse tree
	 */
	void enterExpression(StructuresSQLParser.ExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link StructuresSQLParser#expression}.
	 * @param ctx the parse tree
	 */
	void exitExpression(StructuresSQLParser.ExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link StructuresSQLParser#operator}.
	 * @param ctx the parse tree
	 */
	void enterOperator(StructuresSQLParser.OperatorContext ctx);
	/**
	 * Exit a parse tree produced by {@link StructuresSQLParser#operator}.
	 * @param ctx the parse tree
	 */
	void exitOperator(StructuresSQLParser.OperatorContext ctx);
	/**
	 * Enter a parse tree produced by {@link StructuresSQLParser#whereClause}.
	 * @param ctx the parse tree
	 */
	void enterWhereClause(StructuresSQLParser.WhereClauseContext ctx);
	/**
	 * Exit a parse tree produced by {@link StructuresSQLParser#whereClause}.
	 * @param ctx the parse tree
	 */
	void exitWhereClause(StructuresSQLParser.WhereClauseContext ctx);
	/**
	 * Enter a parse tree produced by {@link StructuresSQLParser#condition}.
	 * @param ctx the parse tree
	 */
	void enterCondition(StructuresSQLParser.ConditionContext ctx);
	/**
	 * Exit a parse tree produced by {@link StructuresSQLParser#condition}.
	 * @param ctx the parse tree
	 */
	void exitCondition(StructuresSQLParser.ConditionContext ctx);
	/**
	 * Enter a parse tree produced by {@link StructuresSQLParser#comparisonOperator}.
	 * @param ctx the parse tree
	 */
	void enterComparisonOperator(StructuresSQLParser.ComparisonOperatorContext ctx);
	/**
	 * Exit a parse tree produced by {@link StructuresSQLParser#comparisonOperator}.
	 * @param ctx the parse tree
	 */
	void exitComparisonOperator(StructuresSQLParser.ComparisonOperatorContext ctx);
	/**
	 * Enter a parse tree produced by {@link StructuresSQLParser#tableName}.
	 * @param ctx the parse tree
	 */
	void enterTableName(StructuresSQLParser.TableNameContext ctx);
	/**
	 * Exit a parse tree produced by {@link StructuresSQLParser#tableName}.
	 * @param ctx the parse tree
	 */
	void exitTableName(StructuresSQLParser.TableNameContext ctx);
	/**
	 * Enter a parse tree produced by {@link StructuresSQLParser#columnName}.
	 * @param ctx the parse tree
	 */
	void enterColumnName(StructuresSQLParser.ColumnNameContext ctx);
	/**
	 * Exit a parse tree produced by {@link StructuresSQLParser#columnName}.
	 * @param ctx the parse tree
	 */
	void exitColumnName(StructuresSQLParser.ColumnNameContext ctx);
	/**
	 * Enter a parse tree produced by {@link StructuresSQLParser#columnDefinition}.
	 * @param ctx the parse tree
	 */
	void enterColumnDefinition(StructuresSQLParser.ColumnDefinitionContext ctx);
	/**
	 * Exit a parse tree produced by {@link StructuresSQLParser#columnDefinition}.
	 * @param ctx the parse tree
	 */
	void exitColumnDefinition(StructuresSQLParser.ColumnDefinitionContext ctx);
	/**
	 * Enter a parse tree produced by {@link StructuresSQLParser#type}.
	 * @param ctx the parse tree
	 */
	void enterType(StructuresSQLParser.TypeContext ctx);
	/**
	 * Exit a parse tree produced by {@link StructuresSQLParser#type}.
	 * @param ctx the parse tree
	 */
	void exitType(StructuresSQLParser.TypeContext ctx);
	/**
	 * Enter a parse tree produced by {@link StructuresSQLParser#comment}.
	 * @param ctx the parse tree
	 */
	void enterComment(StructuresSQLParser.CommentContext ctx);
	/**
	 * Exit a parse tree produced by {@link StructuresSQLParser#comment}.
	 * @param ctx the parse tree
	 */
	void exitComment(StructuresSQLParser.CommentContext ctx);
}