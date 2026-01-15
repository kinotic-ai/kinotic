// Generated from StructuresSQL.g4 by ANTLR 4.13.2
package org.mindignited.structures.sql.parser;
import org.antlr.v4.runtime.tree.ParseTreeVisitor;

/**
 * This interface defines a complete generic visitor for a parse tree produced
 * by {@link StructuresSQLParser}.
 *
 * @param <T> The return type of the visit operation. Use {@link Void} for
 * operations with no return type.
 */
public interface StructuresSQLVisitor<T> extends ParseTreeVisitor<T> {
	/**
	 * Visit a parse tree produced by {@link StructuresSQLParser#migrations}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitMigrations(StructuresSQLParser.MigrationsContext ctx);
	/**
	 * Visit a parse tree produced by {@link StructuresSQLParser#statement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitStatement(StructuresSQLParser.StatementContext ctx);
	/**
	 * Visit a parse tree produced by {@link StructuresSQLParser#createTableStatement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCreateTableStatement(StructuresSQLParser.CreateTableStatementContext ctx);
	/**
	 * Visit a parse tree produced by {@link StructuresSQLParser#createComponentTemplateStatement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCreateComponentTemplateStatement(StructuresSQLParser.CreateComponentTemplateStatementContext ctx);
	/**
	 * Visit a parse tree produced by {@link StructuresSQLParser#createIndexTemplateStatement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCreateIndexTemplateStatement(StructuresSQLParser.CreateIndexTemplateStatementContext ctx);
	/**
	 * Visit a parse tree produced by {@link StructuresSQLParser#templatePart}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitTemplatePart(StructuresSQLParser.TemplatePartContext ctx);
	/**
	 * Visit a parse tree produced by {@link StructuresSQLParser#alterTableStatement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitAlterTableStatement(StructuresSQLParser.AlterTableStatementContext ctx);
	/**
	 * Visit a parse tree produced by {@link StructuresSQLParser#reindexStatement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitReindexStatement(StructuresSQLParser.ReindexStatementContext ctx);
	/**
	 * Visit a parse tree produced by {@link StructuresSQLParser#reindexOptions}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitReindexOptions(StructuresSQLParser.ReindexOptionsContext ctx);
	/**
	 * Visit a parse tree produced by {@link StructuresSQLParser#reindexOption}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitReindexOption(StructuresSQLParser.ReindexOptionContext ctx);
	/**
	 * Visit a parse tree produced by {@link StructuresSQLParser#updateStatement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitUpdateStatement(StructuresSQLParser.UpdateStatementContext ctx);
	/**
	 * Visit a parse tree produced by {@link StructuresSQLParser#deleteStatement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitDeleteStatement(StructuresSQLParser.DeleteStatementContext ctx);
	/**
	 * Visit a parse tree produced by {@link StructuresSQLParser#insertStatement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitInsertStatement(StructuresSQLParser.InsertStatementContext ctx);
	/**
	 * Visit a parse tree produced by {@link StructuresSQLParser#valueList}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitValueList(StructuresSQLParser.ValueListContext ctx);
	/**
	 * Visit a parse tree produced by {@link StructuresSQLParser#value}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitValue(StructuresSQLParser.ValueContext ctx);
	/**
	 * Visit a parse tree produced by {@link StructuresSQLParser#assignment}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitAssignment(StructuresSQLParser.AssignmentContext ctx);
	/**
	 * Visit a parse tree produced by {@link StructuresSQLParser#expression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitExpression(StructuresSQLParser.ExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link StructuresSQLParser#operator}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitOperator(StructuresSQLParser.OperatorContext ctx);
	/**
	 * Visit a parse tree produced by {@link StructuresSQLParser#whereClause}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitWhereClause(StructuresSQLParser.WhereClauseContext ctx);
	/**
	 * Visit a parse tree produced by {@link StructuresSQLParser#condition}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCondition(StructuresSQLParser.ConditionContext ctx);
	/**
	 * Visit a parse tree produced by {@link StructuresSQLParser#comparisonOperator}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitComparisonOperator(StructuresSQLParser.ComparisonOperatorContext ctx);
	/**
	 * Visit a parse tree produced by {@link StructuresSQLParser#tableName}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitTableName(StructuresSQLParser.TableNameContext ctx);
	/**
	 * Visit a parse tree produced by {@link StructuresSQLParser#columnName}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitColumnName(StructuresSQLParser.ColumnNameContext ctx);
	/**
	 * Visit a parse tree produced by {@link StructuresSQLParser#columnDefinition}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitColumnDefinition(StructuresSQLParser.ColumnDefinitionContext ctx);
	/**
	 * Visit a parse tree produced by {@link StructuresSQLParser#type}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitType(StructuresSQLParser.TypeContext ctx);
	/**
	 * Visit a parse tree produced by {@link StructuresSQLParser#comment}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitComment(StructuresSQLParser.CommentContext ctx);
}