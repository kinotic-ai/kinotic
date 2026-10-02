// Generated from KinoticSQL.g4 by ANTLR 4.13.2
package org.kinotic.sql.parser;
import org.antlr.v4.runtime.atn.*;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.misc.*;
import org.antlr.v4.runtime.tree.*;
import java.util.List;
import java.util.Iterator;
import java.util.ArrayList;

@SuppressWarnings({"all", "warnings", "unchecked", "unused", "cast", "CheckReturnValue", "this-escape"})
public class KinoticSQLParser extends Parser {
	static { RuntimeMetaData.checkVersion("4.13.2", RuntimeMetaData.VERSION); }

	protected static final DFA[] _decisionToDFA;
	protected static final PredictionContextCache _sharedContextCache =
		new PredictionContextCache();
	public static final int
		ABORT=1, ADD=2, ALTER=3, AND=4, AS=5, ASC=6, AUTO=7, BY=8, COLUMN=9, COMPONENT=10, 
		CONFLICTS=11, CREATE=12, DATA=13, DATA_RETENTION=14, DATE=15, DELETE=16, 
		DESC=17, DOCUMENT_ID=18, DOUBLE=19, EXISTS=20, FLOAT=21, FOR=22, FROM=23, 
		GROUP=24, IF=25, INDEX=26, INDEXED=27, INSERT=28, INTO=29, LIMIT=30, LONG=31, 
		MAX_DOCS=32, NOT=33, NUMBER_OF_REPLICAS=34, NUMBER_OF_SHARDS=35, OR=36, 
		ORDER=37, PROCEED=38, QUERY=39, REFRESH=40, REINDEX=41, ROUTING=42, SCRIPT=43, 
		SELECT=44, SET=45, SIZE=46, SLICES=47, SOURCE_FIELDS=48, STREAM=49, TABLE=50, 
		TEMPLATE=51, TIME_REFERENCE=52, UPDATE=53, USING=54, VALUES=55, WHERE=56, 
		WITH=57, WAIT=58, SKIP_IF_NO_SOURCE=59, BOOLEAN=60, INTEGER=61, KEYWORD=62, 
		NESTED=63, OBJECT=64, TEXT=65, JSON=66, BINARY=67, GEO_POINT=68, GEO_SHAPE=69, 
		UUID=70, DECIMAL=71, UNION=72, ASSIGN=73, COLON=74, COMMA=75, DIVIDE=76, 
		DOT=77, GREATER_THAN=78, GREATER_THAN_EQUALS=79, LBRACE=80, LBRACKET=81, 
		LESS_THAN=82, LESS_THAN_EQUALS=83, LPAREN=84, MINUS=85, MULTIPLY=86, NOT_EQUALS=87, 
		PLUS=88, RBRACE=89, RBRACKET=90, RPAREN=91, SEMICOLON=92, BOOLEAN_LITERAL=93, 
		DECIMAL_LITERAL=94, NULL_LITERAL=95, ID=96, QUOTED_ID=97, INTEGER_LITERAL=98, 
		STRING=99, COMMENT=100, WS=101;
	public static final int
		RULE_migrations = 0, RULE_statement = 1, RULE_createTableStatement = 2, 
		RULE_createDataStreamStatement = 3, RULE_dataStreamOption = 4, RULE_createComponentTemplateStatement = 5, 
		RULE_createIndexTemplateStatement = 6, RULE_templatePart = 7, RULE_alterTableStatement = 8, 
		RULE_reindexStatement = 9, RULE_reindexOptions = 10, RULE_reindexOption = 11, 
		RULE_updateStatement = 12, RULE_deleteStatement = 13, RULE_insertStatement = 14, 
		RULE_selectStatement = 15, RULE_selectList = 16, RULE_selectItem = 17, 
		RULE_selectExpression = 18, RULE_functionCall = 19, RULE_functionArgument = 20, 
		RULE_orderBy = 21, RULE_fieldPath = 22, RULE_insertOption = 23, RULE_valueList = 24, 
		RULE_value = 25, RULE_namedParameter = 26, RULE_objectLiteral = 27, RULE_objectField = 28, 
		RULE_arrayLiteral = 29, RULE_numberLiteral = 30, RULE_assignment = 31, 
		RULE_expression = 32, RULE_operator = 33, RULE_whereClause = 34, RULE_condition = 35, 
		RULE_comparisonOperator = 36, RULE_tableName = 37, RULE_columnName = 38, 
		RULE_columnDefinition = 39, RULE_unionVariant = 40, RULE_type = 41, RULE_comment = 42, 
		RULE_identifier = 43, RULE_nonReserved = 44;
	private static String[] makeRuleNames() {
		return new String[] {
			"migrations", "statement", "createTableStatement", "createDataStreamStatement", 
			"dataStreamOption", "createComponentTemplateStatement", "createIndexTemplateStatement", 
			"templatePart", "alterTableStatement", "reindexStatement", "reindexOptions", 
			"reindexOption", "updateStatement", "deleteStatement", "insertStatement", 
			"selectStatement", "selectList", "selectItem", "selectExpression", "functionCall", 
			"functionArgument", "orderBy", "fieldPath", "insertOption", "valueList", 
			"value", "namedParameter", "objectLiteral", "objectField", "arrayLiteral", 
			"numberLiteral", "assignment", "expression", "operator", "whereClause", 
			"condition", "comparisonOperator", "tableName", "columnName", "columnDefinition", 
			"unionVariant", "type", "comment", "identifier", "nonReserved"
		};
	}
	public static final String[] ruleNames = makeRuleNames();

	private static String[] makeLiteralNames() {
		return new String[] {
			null, "'ABORT'", "'ADD'", "'ALTER'", "'AND'", "'AS'", "'ASC'", "'AUTO'", 
			"'BY'", "'COLUMN'", "'COMPONENT'", "'CONFLICTS'", "'CREATE'", "'DATA'", 
			"'DATA_RETENTION'", "'DATE'", "'DELETE'", "'DESC'", "'DOCUMENT_ID'", 
			"'DOUBLE'", "'EXISTS'", "'FLOAT'", "'FOR'", "'FROM'", "'GROUP'", "'IF'", 
			"'INDEX'", "'INDEXED'", "'INSERT'", "'INTO'", "'LIMIT'", "'LONG'", "'MAX_DOCS'", 
			"'NOT'", "'NUMBER_OF_REPLICAS'", "'NUMBER_OF_SHARDS'", "'OR'", "'ORDER'", 
			"'PROCEED'", "'QUERY'", "'REFRESH'", "'REINDEX'", "'ROUTING'", "'SCRIPT'", 
			"'SELECT'", "'SET'", "'SIZE'", "'SLICES'", "'SOURCE_FIELDS'", "'STREAM'", 
			"'TABLE'", "'TEMPLATE'", "'TIME_REFERENCE'", "'UPDATE'", "'USING'", "'VALUES'", 
			"'WHERE'", "'WITH'", "'WAIT'", "'SKIP_IF_NO_SOURCE'", "'BOOLEAN'", "'INTEGER'", 
			"'KEYWORD'", "'NESTED'", "'OBJECT'", "'TEXT'", "'JSON'", "'BINARY'", 
			"'GEO_POINT'", "'GEO_SHAPE'", "'UUID'", "'DECIMAL'", "'UNION'", "'='", 
			"':'", "','", "'/'", "'.'", "'>'", "'>='", "'{'", "'['", "'<'", "'<='", 
			"'('", "'-'", "'*'", "'!='", "'+'", "'}'", "']'", "')'", "';'", null, 
			null, "'null'"
		};
	}
	private static final String[] _LITERAL_NAMES = makeLiteralNames();
	private static String[] makeSymbolicNames() {
		return new String[] {
			null, "ABORT", "ADD", "ALTER", "AND", "AS", "ASC", "AUTO", "BY", "COLUMN", 
			"COMPONENT", "CONFLICTS", "CREATE", "DATA", "DATA_RETENTION", "DATE", 
			"DELETE", "DESC", "DOCUMENT_ID", "DOUBLE", "EXISTS", "FLOAT", "FOR", 
			"FROM", "GROUP", "IF", "INDEX", "INDEXED", "INSERT", "INTO", "LIMIT", 
			"LONG", "MAX_DOCS", "NOT", "NUMBER_OF_REPLICAS", "NUMBER_OF_SHARDS", 
			"OR", "ORDER", "PROCEED", "QUERY", "REFRESH", "REINDEX", "ROUTING", "SCRIPT", 
			"SELECT", "SET", "SIZE", "SLICES", "SOURCE_FIELDS", "STREAM", "TABLE", 
			"TEMPLATE", "TIME_REFERENCE", "UPDATE", "USING", "VALUES", "WHERE", "WITH", 
			"WAIT", "SKIP_IF_NO_SOURCE", "BOOLEAN", "INTEGER", "KEYWORD", "NESTED", 
			"OBJECT", "TEXT", "JSON", "BINARY", "GEO_POINT", "GEO_SHAPE", "UUID", 
			"DECIMAL", "UNION", "ASSIGN", "COLON", "COMMA", "DIVIDE", "DOT", "GREATER_THAN", 
			"GREATER_THAN_EQUALS", "LBRACE", "LBRACKET", "LESS_THAN", "LESS_THAN_EQUALS", 
			"LPAREN", "MINUS", "MULTIPLY", "NOT_EQUALS", "PLUS", "RBRACE", "RBRACKET", 
			"RPAREN", "SEMICOLON", "BOOLEAN_LITERAL", "DECIMAL_LITERAL", "NULL_LITERAL", 
			"ID", "QUOTED_ID", "INTEGER_LITERAL", "STRING", "COMMENT", "WS"
		};
	}
	private static final String[] _SYMBOLIC_NAMES = makeSymbolicNames();
	public static final Vocabulary VOCABULARY = new VocabularyImpl(_LITERAL_NAMES, _SYMBOLIC_NAMES);

	/**
	 * @deprecated Use {@link #VOCABULARY} instead.
	 */
	@Deprecated
	public static final String[] tokenNames;
	static {
		tokenNames = new String[_SYMBOLIC_NAMES.length];
		for (int i = 0; i < tokenNames.length; i++) {
			tokenNames[i] = VOCABULARY.getLiteralName(i);
			if (tokenNames[i] == null) {
				tokenNames[i] = VOCABULARY.getSymbolicName(i);
			}

			if (tokenNames[i] == null) {
				tokenNames[i] = "<INVALID>";
			}
		}
	}

	@Override
	@Deprecated
	public String[] getTokenNames() {
		return tokenNames;
	}

	@Override

	public Vocabulary getVocabulary() {
		return VOCABULARY;
	}

	@Override
	public String getGrammarFileName() { return "KinoticSQL.g4"; }

	@Override
	public String[] getRuleNames() { return ruleNames; }

	@Override
	public String getSerializedATN() { return _serializedATN; }

	@Override
	public ATN getATN() { return _ATN; }

	public KinoticSQLParser(TokenStream input) {
		super(input);
		_interp = new ParserATNSimulator(this,_ATN,_decisionToDFA,_sharedContextCache);
	}

	@SuppressWarnings("CheckReturnValue")
	public static class MigrationsContext extends ParserRuleContext {
		public TerminalNode EOF() { return getToken(KinoticSQLParser.EOF, 0); }
		public List<StatementContext> statement() {
			return getRuleContexts(StatementContext.class);
		}
		public StatementContext statement(int i) {
			return getRuleContext(StatementContext.class,i);
		}
		public MigrationsContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_migrations; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KinoticSQLListener ) ((KinoticSQLListener)listener).enterMigrations(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KinoticSQLListener ) ((KinoticSQLListener)listener).exitMigrations(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof KinoticSQLVisitor ) return ((KinoticSQLVisitor<? extends T>)visitor).visitMigrations(this);
			else return visitor.visitChildren(this);
		}
	}

	public final MigrationsContext migrations() throws RecognitionException {
		MigrationsContext _localctx = new MigrationsContext(_ctx, getState());
		enterRule(_localctx, 0, RULE_migrations);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(93);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while ((((_la) & ~0x3f) == 0 && ((1L << _la) & 9026990732546056L) != 0) || _la==COMMENT) {
				{
				{
				setState(90);
				statement();
				}
				}
				setState(95);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(96);
			match(EOF);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class StatementContext extends ParserRuleContext {
		public CreateTableStatementContext createTableStatement() {
			return getRuleContext(CreateTableStatementContext.class,0);
		}
		public CreateDataStreamStatementContext createDataStreamStatement() {
			return getRuleContext(CreateDataStreamStatementContext.class,0);
		}
		public CreateComponentTemplateStatementContext createComponentTemplateStatement() {
			return getRuleContext(CreateComponentTemplateStatementContext.class,0);
		}
		public CreateIndexTemplateStatementContext createIndexTemplateStatement() {
			return getRuleContext(CreateIndexTemplateStatementContext.class,0);
		}
		public AlterTableStatementContext alterTableStatement() {
			return getRuleContext(AlterTableStatementContext.class,0);
		}
		public ReindexStatementContext reindexStatement() {
			return getRuleContext(ReindexStatementContext.class,0);
		}
		public UpdateStatementContext updateStatement() {
			return getRuleContext(UpdateStatementContext.class,0);
		}
		public DeleteStatementContext deleteStatement() {
			return getRuleContext(DeleteStatementContext.class,0);
		}
		public InsertStatementContext insertStatement() {
			return getRuleContext(InsertStatementContext.class,0);
		}
		public SelectStatementContext selectStatement() {
			return getRuleContext(SelectStatementContext.class,0);
		}
		public CommentContext comment() {
			return getRuleContext(CommentContext.class,0);
		}
		public StatementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_statement; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KinoticSQLListener ) ((KinoticSQLListener)listener).enterStatement(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KinoticSQLListener ) ((KinoticSQLListener)listener).exitStatement(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof KinoticSQLVisitor ) return ((KinoticSQLVisitor<? extends T>)visitor).visitStatement(this);
			else return visitor.visitChildren(this);
		}
	}

	public final StatementContext statement() throws RecognitionException {
		StatementContext _localctx = new StatementContext(_ctx, getState());
		enterRule(_localctx, 2, RULE_statement);
		try {
			setState(109);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,1,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(98);
				createTableStatement();
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(99);
				createDataStreamStatement();
				}
				break;
			case 3:
				enterOuterAlt(_localctx, 3);
				{
				setState(100);
				createComponentTemplateStatement();
				}
				break;
			case 4:
				enterOuterAlt(_localctx, 4);
				{
				setState(101);
				createIndexTemplateStatement();
				}
				break;
			case 5:
				enterOuterAlt(_localctx, 5);
				{
				setState(102);
				alterTableStatement();
				}
				break;
			case 6:
				enterOuterAlt(_localctx, 6);
				{
				setState(103);
				reindexStatement();
				}
				break;
			case 7:
				enterOuterAlt(_localctx, 7);
				{
				setState(104);
				updateStatement();
				}
				break;
			case 8:
				enterOuterAlt(_localctx, 8);
				{
				setState(105);
				deleteStatement();
				}
				break;
			case 9:
				enterOuterAlt(_localctx, 9);
				{
				setState(106);
				insertStatement();
				}
				break;
			case 10:
				enterOuterAlt(_localctx, 10);
				{
				setState(107);
				selectStatement();
				}
				break;
			case 11:
				enterOuterAlt(_localctx, 11);
				{
				setState(108);
				comment();
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class CreateTableStatementContext extends ParserRuleContext {
		public TerminalNode CREATE() { return getToken(KinoticSQLParser.CREATE, 0); }
		public TerminalNode TABLE() { return getToken(KinoticSQLParser.TABLE, 0); }
		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class,0);
		}
		public TerminalNode LPAREN() { return getToken(KinoticSQLParser.LPAREN, 0); }
		public List<ColumnDefinitionContext> columnDefinition() {
			return getRuleContexts(ColumnDefinitionContext.class);
		}
		public ColumnDefinitionContext columnDefinition(int i) {
			return getRuleContext(ColumnDefinitionContext.class,i);
		}
		public TerminalNode RPAREN() { return getToken(KinoticSQLParser.RPAREN, 0); }
		public TerminalNode SEMICOLON() { return getToken(KinoticSQLParser.SEMICOLON, 0); }
		public TerminalNode IF() { return getToken(KinoticSQLParser.IF, 0); }
		public TerminalNode NOT() { return getToken(KinoticSQLParser.NOT, 0); }
		public TerminalNode EXISTS() { return getToken(KinoticSQLParser.EXISTS, 0); }
		public List<TerminalNode> COMMA() { return getTokens(KinoticSQLParser.COMMA); }
		public TerminalNode COMMA(int i) {
			return getToken(KinoticSQLParser.COMMA, i);
		}
		public CreateTableStatementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_createTableStatement; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KinoticSQLListener ) ((KinoticSQLListener)listener).enterCreateTableStatement(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KinoticSQLListener ) ((KinoticSQLListener)listener).exitCreateTableStatement(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof KinoticSQLVisitor ) return ((KinoticSQLVisitor<? extends T>)visitor).visitCreateTableStatement(this);
			else return visitor.visitChildren(this);
		}
	}

	public final CreateTableStatementContext createTableStatement() throws RecognitionException {
		CreateTableStatementContext _localctx = new CreateTableStatementContext(_ctx, getState());
		enterRule(_localctx, 4, RULE_createTableStatement);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(111);
			match(CREATE);
			setState(112);
			match(TABLE);
			setState(116);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,2,_ctx) ) {
			case 1:
				{
				setState(113);
				match(IF);
				setState(114);
				match(NOT);
				setState(115);
				match(EXISTS);
				}
				break;
			}
			setState(118);
			identifier();
			setState(119);
			match(LPAREN);
			setState(120);
			columnDefinition();
			setState(125);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==COMMA) {
				{
				{
				setState(121);
				match(COMMA);
				setState(122);
				columnDefinition();
				}
				}
				setState(127);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(128);
			match(RPAREN);
			setState(129);
			match(SEMICOLON);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class CreateDataStreamStatementContext extends ParserRuleContext {
		public TerminalNode CREATE() { return getToken(KinoticSQLParser.CREATE, 0); }
		public TerminalNode DATA() { return getToken(KinoticSQLParser.DATA, 0); }
		public TerminalNode STREAM() { return getToken(KinoticSQLParser.STREAM, 0); }
		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class,0);
		}
		public List<TerminalNode> LPAREN() { return getTokens(KinoticSQLParser.LPAREN); }
		public TerminalNode LPAREN(int i) {
			return getToken(KinoticSQLParser.LPAREN, i);
		}
		public List<ColumnDefinitionContext> columnDefinition() {
			return getRuleContexts(ColumnDefinitionContext.class);
		}
		public ColumnDefinitionContext columnDefinition(int i) {
			return getRuleContext(ColumnDefinitionContext.class,i);
		}
		public List<TerminalNode> RPAREN() { return getTokens(KinoticSQLParser.RPAREN); }
		public TerminalNode RPAREN(int i) {
			return getToken(KinoticSQLParser.RPAREN, i);
		}
		public TerminalNode SEMICOLON() { return getToken(KinoticSQLParser.SEMICOLON, 0); }
		public List<TerminalNode> COMMA() { return getTokens(KinoticSQLParser.COMMA); }
		public TerminalNode COMMA(int i) {
			return getToken(KinoticSQLParser.COMMA, i);
		}
		public TerminalNode WITH() { return getToken(KinoticSQLParser.WITH, 0); }
		public List<DataStreamOptionContext> dataStreamOption() {
			return getRuleContexts(DataStreamOptionContext.class);
		}
		public DataStreamOptionContext dataStreamOption(int i) {
			return getRuleContext(DataStreamOptionContext.class,i);
		}
		public CreateDataStreamStatementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_createDataStreamStatement; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KinoticSQLListener ) ((KinoticSQLListener)listener).enterCreateDataStreamStatement(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KinoticSQLListener ) ((KinoticSQLListener)listener).exitCreateDataStreamStatement(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof KinoticSQLVisitor ) return ((KinoticSQLVisitor<? extends T>)visitor).visitCreateDataStreamStatement(this);
			else return visitor.visitChildren(this);
		}
	}

	public final CreateDataStreamStatementContext createDataStreamStatement() throws RecognitionException {
		CreateDataStreamStatementContext _localctx = new CreateDataStreamStatementContext(_ctx, getState());
		enterRule(_localctx, 6, RULE_createDataStreamStatement);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(131);
			match(CREATE);
			setState(132);
			match(DATA);
			setState(133);
			match(STREAM);
			setState(134);
			identifier();
			setState(135);
			match(LPAREN);
			setState(136);
			columnDefinition();
			setState(141);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==COMMA) {
				{
				{
				setState(137);
				match(COMMA);
				setState(138);
				columnDefinition();
				}
				}
				setState(143);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(144);
			match(RPAREN);
			setState(157);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==WITH) {
				{
				setState(145);
				match(WITH);
				setState(146);
				match(LPAREN);
				setState(147);
				dataStreamOption();
				setState(152);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while (_la==COMMA) {
					{
					{
					setState(148);
					match(COMMA);
					setState(149);
					dataStreamOption();
					}
					}
					setState(154);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}
				setState(155);
				match(RPAREN);
				}
			}

			setState(159);
			match(SEMICOLON);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class DataStreamOptionContext extends ParserRuleContext {
		public TerminalNode DATA_RETENTION() { return getToken(KinoticSQLParser.DATA_RETENTION, 0); }
		public TerminalNode ASSIGN() { return getToken(KinoticSQLParser.ASSIGN, 0); }
		public TerminalNode STRING() { return getToken(KinoticSQLParser.STRING, 0); }
		public TerminalNode TIME_REFERENCE() { return getToken(KinoticSQLParser.TIME_REFERENCE, 0); }
		public DataStreamOptionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_dataStreamOption; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KinoticSQLListener ) ((KinoticSQLListener)listener).enterDataStreamOption(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KinoticSQLListener ) ((KinoticSQLListener)listener).exitDataStreamOption(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof KinoticSQLVisitor ) return ((KinoticSQLVisitor<? extends T>)visitor).visitDataStreamOption(this);
			else return visitor.visitChildren(this);
		}
	}

	public final DataStreamOptionContext dataStreamOption() throws RecognitionException {
		DataStreamOptionContext _localctx = new DataStreamOptionContext(_ctx, getState());
		enterRule(_localctx, 8, RULE_dataStreamOption);
		try {
			setState(167);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case DATA_RETENTION:
				enterOuterAlt(_localctx, 1);
				{
				setState(161);
				match(DATA_RETENTION);
				setState(162);
				match(ASSIGN);
				setState(163);
				match(STRING);
				}
				break;
			case TIME_REFERENCE:
				enterOuterAlt(_localctx, 2);
				{
				setState(164);
				match(TIME_REFERENCE);
				setState(165);
				match(ASSIGN);
				setState(166);
				match(STRING);
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class CreateComponentTemplateStatementContext extends ParserRuleContext {
		public TerminalNode CREATE() { return getToken(KinoticSQLParser.CREATE, 0); }
		public TerminalNode COMPONENT() { return getToken(KinoticSQLParser.COMPONENT, 0); }
		public TerminalNode TEMPLATE() { return getToken(KinoticSQLParser.TEMPLATE, 0); }
		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class,0);
		}
		public TerminalNode LPAREN() { return getToken(KinoticSQLParser.LPAREN, 0); }
		public List<TemplatePartContext> templatePart() {
			return getRuleContexts(TemplatePartContext.class);
		}
		public TemplatePartContext templatePart(int i) {
			return getRuleContext(TemplatePartContext.class,i);
		}
		public TerminalNode RPAREN() { return getToken(KinoticSQLParser.RPAREN, 0); }
		public TerminalNode SEMICOLON() { return getToken(KinoticSQLParser.SEMICOLON, 0); }
		public List<TerminalNode> COMMA() { return getTokens(KinoticSQLParser.COMMA); }
		public TerminalNode COMMA(int i) {
			return getToken(KinoticSQLParser.COMMA, i);
		}
		public CreateComponentTemplateStatementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_createComponentTemplateStatement; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KinoticSQLListener ) ((KinoticSQLListener)listener).enterCreateComponentTemplateStatement(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KinoticSQLListener ) ((KinoticSQLListener)listener).exitCreateComponentTemplateStatement(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof KinoticSQLVisitor ) return ((KinoticSQLVisitor<? extends T>)visitor).visitCreateComponentTemplateStatement(this);
			else return visitor.visitChildren(this);
		}
	}

	public final CreateComponentTemplateStatementContext createComponentTemplateStatement() throws RecognitionException {
		CreateComponentTemplateStatementContext _localctx = new CreateComponentTemplateStatementContext(_ctx, getState());
		enterRule(_localctx, 10, RULE_createComponentTemplateStatement);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(169);
			match(CREATE);
			setState(170);
			match(COMPONENT);
			setState(171);
			match(TEMPLATE);
			setState(172);
			identifier();
			setState(173);
			match(LPAREN);
			setState(174);
			templatePart();
			setState(179);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==COMMA) {
				{
				{
				setState(175);
				match(COMMA);
				setState(176);
				templatePart();
				}
				}
				setState(181);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(182);
			match(RPAREN);
			setState(183);
			match(SEMICOLON);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class CreateIndexTemplateStatementContext extends ParserRuleContext {
		public TerminalNode CREATE() { return getToken(KinoticSQLParser.CREATE, 0); }
		public TerminalNode INDEX() { return getToken(KinoticSQLParser.INDEX, 0); }
		public TerminalNode TEMPLATE() { return getToken(KinoticSQLParser.TEMPLATE, 0); }
		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class,0);
		}
		public TerminalNode FOR() { return getToken(KinoticSQLParser.FOR, 0); }
		public List<TerminalNode> STRING() { return getTokens(KinoticSQLParser.STRING); }
		public TerminalNode STRING(int i) {
			return getToken(KinoticSQLParser.STRING, i);
		}
		public TerminalNode USING() { return getToken(KinoticSQLParser.USING, 0); }
		public TerminalNode SEMICOLON() { return getToken(KinoticSQLParser.SEMICOLON, 0); }
		public TerminalNode WITH() { return getToken(KinoticSQLParser.WITH, 0); }
		public TerminalNode LPAREN() { return getToken(KinoticSQLParser.LPAREN, 0); }
		public List<TemplatePartContext> templatePart() {
			return getRuleContexts(TemplatePartContext.class);
		}
		public TemplatePartContext templatePart(int i) {
			return getRuleContext(TemplatePartContext.class,i);
		}
		public TerminalNode RPAREN() { return getToken(KinoticSQLParser.RPAREN, 0); }
		public List<TerminalNode> COMMA() { return getTokens(KinoticSQLParser.COMMA); }
		public TerminalNode COMMA(int i) {
			return getToken(KinoticSQLParser.COMMA, i);
		}
		public CreateIndexTemplateStatementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_createIndexTemplateStatement; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KinoticSQLListener ) ((KinoticSQLListener)listener).enterCreateIndexTemplateStatement(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KinoticSQLListener ) ((KinoticSQLListener)listener).exitCreateIndexTemplateStatement(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof KinoticSQLVisitor ) return ((KinoticSQLVisitor<? extends T>)visitor).visitCreateIndexTemplateStatement(this);
			else return visitor.visitChildren(this);
		}
	}

	public final CreateIndexTemplateStatementContext createIndexTemplateStatement() throws RecognitionException {
		CreateIndexTemplateStatementContext _localctx = new CreateIndexTemplateStatementContext(_ctx, getState());
		enterRule(_localctx, 12, RULE_createIndexTemplateStatement);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(185);
			match(CREATE);
			setState(186);
			match(INDEX);
			setState(187);
			match(TEMPLATE);
			setState(188);
			identifier();
			setState(189);
			match(FOR);
			setState(190);
			match(STRING);
			setState(191);
			match(USING);
			setState(192);
			match(STRING);
			setState(205);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==WITH) {
				{
				setState(193);
				match(WITH);
				setState(194);
				match(LPAREN);
				setState(195);
				templatePart();
				setState(200);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while (_la==COMMA) {
					{
					{
					setState(196);
					match(COMMA);
					setState(197);
					templatePart();
					}
					}
					setState(202);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}
				setState(203);
				match(RPAREN);
				}
			}

			setState(207);
			match(SEMICOLON);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class TemplatePartContext extends ParserRuleContext {
		public TerminalNode NUMBER_OF_SHARDS() { return getToken(KinoticSQLParser.NUMBER_OF_SHARDS, 0); }
		public TerminalNode ASSIGN() { return getToken(KinoticSQLParser.ASSIGN, 0); }
		public TerminalNode INTEGER_LITERAL() { return getToken(KinoticSQLParser.INTEGER_LITERAL, 0); }
		public TerminalNode NUMBER_OF_REPLICAS() { return getToken(KinoticSQLParser.NUMBER_OF_REPLICAS, 0); }
		public ColumnDefinitionContext columnDefinition() {
			return getRuleContext(ColumnDefinitionContext.class,0);
		}
		public TemplatePartContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_templatePart; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KinoticSQLListener ) ((KinoticSQLListener)listener).enterTemplatePart(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KinoticSQLListener ) ((KinoticSQLListener)listener).exitTemplatePart(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof KinoticSQLVisitor ) return ((KinoticSQLVisitor<? extends T>)visitor).visitTemplatePart(this);
			else return visitor.visitChildren(this);
		}
	}

	public final TemplatePartContext templatePart() throws RecognitionException {
		TemplatePartContext _localctx = new TemplatePartContext(_ctx, getState());
		enterRule(_localctx, 14, RULE_templatePart);
		try {
			setState(216);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,11,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(209);
				match(NUMBER_OF_SHARDS);
				setState(210);
				match(ASSIGN);
				setState(211);
				match(INTEGER_LITERAL);
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(212);
				match(NUMBER_OF_REPLICAS);
				setState(213);
				match(ASSIGN);
				setState(214);
				match(INTEGER_LITERAL);
				}
				break;
			case 3:
				enterOuterAlt(_localctx, 3);
				{
				setState(215);
				columnDefinition();
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class AlterTableStatementContext extends ParserRuleContext {
		public TerminalNode ALTER() { return getToken(KinoticSQLParser.ALTER, 0); }
		public TerminalNode TABLE() { return getToken(KinoticSQLParser.TABLE, 0); }
		public List<IdentifierContext> identifier() {
			return getRuleContexts(IdentifierContext.class);
		}
		public IdentifierContext identifier(int i) {
			return getRuleContext(IdentifierContext.class,i);
		}
		public TerminalNode ADD() { return getToken(KinoticSQLParser.ADD, 0); }
		public TerminalNode COLUMN() { return getToken(KinoticSQLParser.COLUMN, 0); }
		public TypeContext type() {
			return getRuleContext(TypeContext.class,0);
		}
		public TerminalNode SEMICOLON() { return getToken(KinoticSQLParser.SEMICOLON, 0); }
		public AlterTableStatementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_alterTableStatement; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KinoticSQLListener ) ((KinoticSQLListener)listener).enterAlterTableStatement(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KinoticSQLListener ) ((KinoticSQLListener)listener).exitAlterTableStatement(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof KinoticSQLVisitor ) return ((KinoticSQLVisitor<? extends T>)visitor).visitAlterTableStatement(this);
			else return visitor.visitChildren(this);
		}
	}

	public final AlterTableStatementContext alterTableStatement() throws RecognitionException {
		AlterTableStatementContext _localctx = new AlterTableStatementContext(_ctx, getState());
		enterRule(_localctx, 16, RULE_alterTableStatement);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(218);
			match(ALTER);
			setState(219);
			match(TABLE);
			setState(220);
			identifier();
			setState(221);
			match(ADD);
			setState(222);
			match(COLUMN);
			setState(223);
			identifier();
			setState(224);
			type();
			setState(225);
			match(SEMICOLON);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ReindexStatementContext extends ParserRuleContext {
		public TerminalNode REINDEX() { return getToken(KinoticSQLParser.REINDEX, 0); }
		public List<IdentifierContext> identifier() {
			return getRuleContexts(IdentifierContext.class);
		}
		public IdentifierContext identifier(int i) {
			return getRuleContext(IdentifierContext.class,i);
		}
		public TerminalNode INTO() { return getToken(KinoticSQLParser.INTO, 0); }
		public TerminalNode SEMICOLON() { return getToken(KinoticSQLParser.SEMICOLON, 0); }
		public ReindexOptionsContext reindexOptions() {
			return getRuleContext(ReindexOptionsContext.class,0);
		}
		public ReindexStatementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_reindexStatement; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KinoticSQLListener ) ((KinoticSQLListener)listener).enterReindexStatement(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KinoticSQLListener ) ((KinoticSQLListener)listener).exitReindexStatement(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof KinoticSQLVisitor ) return ((KinoticSQLVisitor<? extends T>)visitor).visitReindexStatement(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ReindexStatementContext reindexStatement() throws RecognitionException {
		ReindexStatementContext _localctx = new ReindexStatementContext(_ctx, getState());
		enterRule(_localctx, 18, RULE_reindexStatement);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(227);
			match(REINDEX);
			setState(228);
			identifier();
			setState(229);
			match(INTO);
			setState(230);
			identifier();
			setState(232);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==WITH) {
				{
				setState(231);
				reindexOptions();
				}
			}

			setState(234);
			match(SEMICOLON);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ReindexOptionsContext extends ParserRuleContext {
		public TerminalNode WITH() { return getToken(KinoticSQLParser.WITH, 0); }
		public TerminalNode LPAREN() { return getToken(KinoticSQLParser.LPAREN, 0); }
		public List<ReindexOptionContext> reindexOption() {
			return getRuleContexts(ReindexOptionContext.class);
		}
		public ReindexOptionContext reindexOption(int i) {
			return getRuleContext(ReindexOptionContext.class,i);
		}
		public TerminalNode RPAREN() { return getToken(KinoticSQLParser.RPAREN, 0); }
		public List<TerminalNode> COMMA() { return getTokens(KinoticSQLParser.COMMA); }
		public TerminalNode COMMA(int i) {
			return getToken(KinoticSQLParser.COMMA, i);
		}
		public ReindexOptionsContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_reindexOptions; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KinoticSQLListener ) ((KinoticSQLListener)listener).enterReindexOptions(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KinoticSQLListener ) ((KinoticSQLListener)listener).exitReindexOptions(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof KinoticSQLVisitor ) return ((KinoticSQLVisitor<? extends T>)visitor).visitReindexOptions(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ReindexOptionsContext reindexOptions() throws RecognitionException {
		ReindexOptionsContext _localctx = new ReindexOptionsContext(_ctx, getState());
		enterRule(_localctx, 20, RULE_reindexOptions);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(236);
			match(WITH);
			setState(237);
			match(LPAREN);
			setState(238);
			reindexOption();
			setState(243);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==COMMA) {
				{
				{
				setState(239);
				match(COMMA);
				setState(240);
				reindexOption();
				}
				}
				setState(245);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(246);
			match(RPAREN);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ReindexOptionContext extends ParserRuleContext {
		public TerminalNode CONFLICTS() { return getToken(KinoticSQLParser.CONFLICTS, 0); }
		public TerminalNode ASSIGN() { return getToken(KinoticSQLParser.ASSIGN, 0); }
		public TerminalNode ABORT() { return getToken(KinoticSQLParser.ABORT, 0); }
		public TerminalNode PROCEED() { return getToken(KinoticSQLParser.PROCEED, 0); }
		public TerminalNode MAX_DOCS() { return getToken(KinoticSQLParser.MAX_DOCS, 0); }
		public TerminalNode INTEGER_LITERAL() { return getToken(KinoticSQLParser.INTEGER_LITERAL, 0); }
		public TerminalNode SLICES() { return getToken(KinoticSQLParser.SLICES, 0); }
		public TerminalNode AUTO() { return getToken(KinoticSQLParser.AUTO, 0); }
		public TerminalNode SIZE() { return getToken(KinoticSQLParser.SIZE, 0); }
		public TerminalNode SOURCE_FIELDS() { return getToken(KinoticSQLParser.SOURCE_FIELDS, 0); }
		public TerminalNode STRING() { return getToken(KinoticSQLParser.STRING, 0); }
		public TerminalNode QUERY() { return getToken(KinoticSQLParser.QUERY, 0); }
		public TerminalNode SCRIPT() { return getToken(KinoticSQLParser.SCRIPT, 0); }
		public TerminalNode WAIT() { return getToken(KinoticSQLParser.WAIT, 0); }
		public TerminalNode BOOLEAN_LITERAL() { return getToken(KinoticSQLParser.BOOLEAN_LITERAL, 0); }
		public TerminalNode SKIP_IF_NO_SOURCE() { return getToken(KinoticSQLParser.SKIP_IF_NO_SOURCE, 0); }
		public ReindexOptionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_reindexOption; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KinoticSQLListener ) ((KinoticSQLListener)listener).enterReindexOption(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KinoticSQLListener ) ((KinoticSQLListener)listener).exitReindexOption(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof KinoticSQLVisitor ) return ((KinoticSQLVisitor<? extends T>)visitor).visitReindexOption(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ReindexOptionContext reindexOption() throws RecognitionException {
		ReindexOptionContext _localctx = new ReindexOptionContext(_ctx, getState());
		enterRule(_localctx, 22, RULE_reindexOption);
		int _la;
		try {
			setState(275);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case CONFLICTS:
				enterOuterAlt(_localctx, 1);
				{
				setState(248);
				match(CONFLICTS);
				setState(249);
				match(ASSIGN);
				setState(250);
				_la = _input.LA(1);
				if ( !(_la==ABORT || _la==PROCEED) ) {
				_errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				}
				break;
			case MAX_DOCS:
				enterOuterAlt(_localctx, 2);
				{
				setState(251);
				match(MAX_DOCS);
				setState(252);
				match(ASSIGN);
				setState(253);
				match(INTEGER_LITERAL);
				}
				break;
			case SLICES:
				enterOuterAlt(_localctx, 3);
				{
				setState(254);
				match(SLICES);
				setState(255);
				match(ASSIGN);
				setState(256);
				_la = _input.LA(1);
				if ( !(_la==AUTO || _la==INTEGER_LITERAL) ) {
				_errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				}
				break;
			case SIZE:
				enterOuterAlt(_localctx, 4);
				{
				setState(257);
				match(SIZE);
				setState(258);
				match(ASSIGN);
				setState(259);
				match(INTEGER_LITERAL);
				}
				break;
			case SOURCE_FIELDS:
				enterOuterAlt(_localctx, 5);
				{
				setState(260);
				match(SOURCE_FIELDS);
				setState(261);
				match(ASSIGN);
				setState(262);
				match(STRING);
				}
				break;
			case QUERY:
				enterOuterAlt(_localctx, 6);
				{
				setState(263);
				match(QUERY);
				setState(264);
				match(ASSIGN);
				setState(265);
				match(STRING);
				}
				break;
			case SCRIPT:
				enterOuterAlt(_localctx, 7);
				{
				setState(266);
				match(SCRIPT);
				setState(267);
				match(ASSIGN);
				setState(268);
				match(STRING);
				}
				break;
			case WAIT:
				enterOuterAlt(_localctx, 8);
				{
				setState(269);
				match(WAIT);
				setState(270);
				match(ASSIGN);
				setState(271);
				match(BOOLEAN_LITERAL);
				}
				break;
			case SKIP_IF_NO_SOURCE:
				enterOuterAlt(_localctx, 9);
				{
				setState(272);
				match(SKIP_IF_NO_SOURCE);
				setState(273);
				match(ASSIGN);
				setState(274);
				match(BOOLEAN_LITERAL);
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class UpdateStatementContext extends ParserRuleContext {
		public TerminalNode UPDATE() { return getToken(KinoticSQLParser.UPDATE, 0); }
		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class,0);
		}
		public TerminalNode SET() { return getToken(KinoticSQLParser.SET, 0); }
		public List<AssignmentContext> assignment() {
			return getRuleContexts(AssignmentContext.class);
		}
		public AssignmentContext assignment(int i) {
			return getRuleContext(AssignmentContext.class,i);
		}
		public TerminalNode WHERE() { return getToken(KinoticSQLParser.WHERE, 0); }
		public WhereClauseContext whereClause() {
			return getRuleContext(WhereClauseContext.class,0);
		}
		public TerminalNode SEMICOLON() { return getToken(KinoticSQLParser.SEMICOLON, 0); }
		public List<TerminalNode> COMMA() { return getTokens(KinoticSQLParser.COMMA); }
		public TerminalNode COMMA(int i) {
			return getToken(KinoticSQLParser.COMMA, i);
		}
		public TerminalNode WITH() { return getToken(KinoticSQLParser.WITH, 0); }
		public TerminalNode REFRESH() { return getToken(KinoticSQLParser.REFRESH, 0); }
		public UpdateStatementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_updateStatement; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KinoticSQLListener ) ((KinoticSQLListener)listener).enterUpdateStatement(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KinoticSQLListener ) ((KinoticSQLListener)listener).exitUpdateStatement(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof KinoticSQLVisitor ) return ((KinoticSQLVisitor<? extends T>)visitor).visitUpdateStatement(this);
			else return visitor.visitChildren(this);
		}
	}

	public final UpdateStatementContext updateStatement() throws RecognitionException {
		UpdateStatementContext _localctx = new UpdateStatementContext(_ctx, getState());
		enterRule(_localctx, 24, RULE_updateStatement);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(277);
			match(UPDATE);
			setState(278);
			identifier();
			setState(279);
			match(SET);
			setState(280);
			assignment();
			setState(285);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==COMMA) {
				{
				{
				setState(281);
				match(COMMA);
				setState(282);
				assignment();
				}
				}
				setState(287);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(288);
			match(WHERE);
			setState(289);
			whereClause(0);
			setState(292);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==WITH) {
				{
				setState(290);
				match(WITH);
				setState(291);
				match(REFRESH);
				}
			}

			setState(294);
			match(SEMICOLON);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class DeleteStatementContext extends ParserRuleContext {
		public TerminalNode DELETE() { return getToken(KinoticSQLParser.DELETE, 0); }
		public TerminalNode FROM() { return getToken(KinoticSQLParser.FROM, 0); }
		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class,0);
		}
		public TerminalNode WHERE() { return getToken(KinoticSQLParser.WHERE, 0); }
		public WhereClauseContext whereClause() {
			return getRuleContext(WhereClauseContext.class,0);
		}
		public TerminalNode SEMICOLON() { return getToken(KinoticSQLParser.SEMICOLON, 0); }
		public TerminalNode WITH() { return getToken(KinoticSQLParser.WITH, 0); }
		public TerminalNode REFRESH() { return getToken(KinoticSQLParser.REFRESH, 0); }
		public DeleteStatementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_deleteStatement; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KinoticSQLListener ) ((KinoticSQLListener)listener).enterDeleteStatement(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KinoticSQLListener ) ((KinoticSQLListener)listener).exitDeleteStatement(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof KinoticSQLVisitor ) return ((KinoticSQLVisitor<? extends T>)visitor).visitDeleteStatement(this);
			else return visitor.visitChildren(this);
		}
	}

	public final DeleteStatementContext deleteStatement() throws RecognitionException {
		DeleteStatementContext _localctx = new DeleteStatementContext(_ctx, getState());
		enterRule(_localctx, 26, RULE_deleteStatement);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(296);
			match(DELETE);
			setState(297);
			match(FROM);
			setState(298);
			identifier();
			setState(299);
			match(WHERE);
			setState(300);
			whereClause(0);
			setState(303);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==WITH) {
				{
				setState(301);
				match(WITH);
				setState(302);
				match(REFRESH);
				}
			}

			setState(305);
			match(SEMICOLON);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class InsertStatementContext extends ParserRuleContext {
		public TerminalNode INSERT() { return getToken(KinoticSQLParser.INSERT, 0); }
		public TerminalNode INTO() { return getToken(KinoticSQLParser.INTO, 0); }
		public TableNameContext tableName() {
			return getRuleContext(TableNameContext.class,0);
		}
		public TerminalNode VALUES() { return getToken(KinoticSQLParser.VALUES, 0); }
		public List<TerminalNode> LPAREN() { return getTokens(KinoticSQLParser.LPAREN); }
		public TerminalNode LPAREN(int i) {
			return getToken(KinoticSQLParser.LPAREN, i);
		}
		public ValueListContext valueList() {
			return getRuleContext(ValueListContext.class,0);
		}
		public List<TerminalNode> RPAREN() { return getTokens(KinoticSQLParser.RPAREN); }
		public TerminalNode RPAREN(int i) {
			return getToken(KinoticSQLParser.RPAREN, i);
		}
		public TerminalNode SEMICOLON() { return getToken(KinoticSQLParser.SEMICOLON, 0); }
		public List<ColumnNameContext> columnName() {
			return getRuleContexts(ColumnNameContext.class);
		}
		public ColumnNameContext columnName(int i) {
			return getRuleContext(ColumnNameContext.class,i);
		}
		public TerminalNode WITH() { return getToken(KinoticSQLParser.WITH, 0); }
		public List<InsertOptionContext> insertOption() {
			return getRuleContexts(InsertOptionContext.class);
		}
		public InsertOptionContext insertOption(int i) {
			return getRuleContext(InsertOptionContext.class,i);
		}
		public List<TerminalNode> COMMA() { return getTokens(KinoticSQLParser.COMMA); }
		public TerminalNode COMMA(int i) {
			return getToken(KinoticSQLParser.COMMA, i);
		}
		public InsertStatementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_insertStatement; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KinoticSQLListener ) ((KinoticSQLListener)listener).enterInsertStatement(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KinoticSQLListener ) ((KinoticSQLListener)listener).exitInsertStatement(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof KinoticSQLVisitor ) return ((KinoticSQLVisitor<? extends T>)visitor).visitInsertStatement(this);
			else return visitor.visitChildren(this);
		}
	}

	public final InsertStatementContext insertStatement() throws RecognitionException {
		InsertStatementContext _localctx = new InsertStatementContext(_ctx, getState());
		enterRule(_localctx, 28, RULE_insertStatement);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(307);
			match(INSERT);
			setState(308);
			match(INTO);
			setState(309);
			tableName();
			setState(321);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==LPAREN) {
				{
				setState(310);
				match(LPAREN);
				setState(311);
				columnName();
				setState(316);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while (_la==COMMA) {
					{
					{
					setState(312);
					match(COMMA);
					setState(313);
					columnName();
					}
					}
					setState(318);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}
				setState(319);
				match(RPAREN);
				}
			}

			setState(323);
			match(VALUES);
			setState(324);
			match(LPAREN);
			setState(325);
			valueList();
			setState(326);
			match(RPAREN);
			setState(336);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==WITH) {
				{
				setState(327);
				match(WITH);
				setState(328);
				insertOption();
				setState(333);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while (_la==COMMA) {
					{
					{
					setState(329);
					match(COMMA);
					setState(330);
					insertOption();
					}
					}
					setState(335);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}
				}
			}

			setState(338);
			match(SEMICOLON);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class SelectStatementContext extends ParserRuleContext {
		public TerminalNode SELECT() { return getToken(KinoticSQLParser.SELECT, 0); }
		public SelectListContext selectList() {
			return getRuleContext(SelectListContext.class,0);
		}
		public TerminalNode FROM() { return getToken(KinoticSQLParser.FROM, 0); }
		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class,0);
		}
		public TerminalNode SEMICOLON() { return getToken(KinoticSQLParser.SEMICOLON, 0); }
		public TerminalNode WHERE() { return getToken(KinoticSQLParser.WHERE, 0); }
		public WhereClauseContext whereClause() {
			return getRuleContext(WhereClauseContext.class,0);
		}
		public TerminalNode GROUP() { return getToken(KinoticSQLParser.GROUP, 0); }
		public List<TerminalNode> BY() { return getTokens(KinoticSQLParser.BY); }
		public TerminalNode BY(int i) {
			return getToken(KinoticSQLParser.BY, i);
		}
		public List<SelectExpressionContext> selectExpression() {
			return getRuleContexts(SelectExpressionContext.class);
		}
		public SelectExpressionContext selectExpression(int i) {
			return getRuleContext(SelectExpressionContext.class,i);
		}
		public TerminalNode ORDER() { return getToken(KinoticSQLParser.ORDER, 0); }
		public List<OrderByContext> orderBy() {
			return getRuleContexts(OrderByContext.class);
		}
		public OrderByContext orderBy(int i) {
			return getRuleContext(OrderByContext.class,i);
		}
		public TerminalNode LIMIT() { return getToken(KinoticSQLParser.LIMIT, 0); }
		public TerminalNode INTEGER_LITERAL() { return getToken(KinoticSQLParser.INTEGER_LITERAL, 0); }
		public List<TerminalNode> COMMA() { return getTokens(KinoticSQLParser.COMMA); }
		public TerminalNode COMMA(int i) {
			return getToken(KinoticSQLParser.COMMA, i);
		}
		public SelectStatementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_selectStatement; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KinoticSQLListener ) ((KinoticSQLListener)listener).enterSelectStatement(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KinoticSQLListener ) ((KinoticSQLListener)listener).exitSelectStatement(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof KinoticSQLVisitor ) return ((KinoticSQLVisitor<? extends T>)visitor).visitSelectStatement(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SelectStatementContext selectStatement() throws RecognitionException {
		SelectStatementContext _localctx = new SelectStatementContext(_ctx, getState());
		enterRule(_localctx, 30, RULE_selectStatement);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(340);
			match(SELECT);
			setState(341);
			selectList();
			setState(342);
			match(FROM);
			setState(343);
			identifier();
			setState(346);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==WHERE) {
				{
				setState(344);
				match(WHERE);
				setState(345);
				whereClause(0);
				}
			}

			setState(358);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==GROUP) {
				{
				setState(348);
				match(GROUP);
				setState(349);
				match(BY);
				setState(350);
				selectExpression();
				setState(355);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while (_la==COMMA) {
					{
					{
					setState(351);
					match(COMMA);
					setState(352);
					selectExpression();
					}
					}
					setState(357);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}
				}
			}

			setState(370);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==ORDER) {
				{
				setState(360);
				match(ORDER);
				setState(361);
				match(BY);
				setState(362);
				orderBy();
				setState(367);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while (_la==COMMA) {
					{
					{
					setState(363);
					match(COMMA);
					setState(364);
					orderBy();
					}
					}
					setState(369);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}
				}
			}

			setState(374);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==LIMIT) {
				{
				setState(372);
				match(LIMIT);
				setState(373);
				match(INTEGER_LITERAL);
				}
			}

			setState(376);
			match(SEMICOLON);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class SelectListContext extends ParserRuleContext {
		public TerminalNode MULTIPLY() { return getToken(KinoticSQLParser.MULTIPLY, 0); }
		public List<SelectItemContext> selectItem() {
			return getRuleContexts(SelectItemContext.class);
		}
		public SelectItemContext selectItem(int i) {
			return getRuleContext(SelectItemContext.class,i);
		}
		public List<TerminalNode> COMMA() { return getTokens(KinoticSQLParser.COMMA); }
		public TerminalNode COMMA(int i) {
			return getToken(KinoticSQLParser.COMMA, i);
		}
		public SelectListContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_selectList; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KinoticSQLListener ) ((KinoticSQLListener)listener).enterSelectList(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KinoticSQLListener ) ((KinoticSQLListener)listener).exitSelectList(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof KinoticSQLVisitor ) return ((KinoticSQLVisitor<? extends T>)visitor).visitSelectList(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SelectListContext selectList() throws RecognitionException {
		SelectListContext _localctx = new SelectListContext(_ctx, getState());
		enterRule(_localctx, 32, RULE_selectList);
		int _la;
		try {
			setState(387);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case MULTIPLY:
				enterOuterAlt(_localctx, 1);
				{
				setState(378);
				match(MULTIPLY);
				}
				break;
			case ABORT:
			case ADD:
			case ASC:
			case AUTO:
			case COLUMN:
			case COMPONENT:
			case CONFLICTS:
			case DATA:
			case DATA_RETENTION:
			case DATE:
			case DESC:
			case DOCUMENT_ID:
			case DOUBLE:
			case EXISTS:
			case FLOAT:
			case FOR:
			case IF:
			case INDEX:
			case INDEXED:
			case LONG:
			case MAX_DOCS:
			case NUMBER_OF_REPLICAS:
			case NUMBER_OF_SHARDS:
			case PROCEED:
			case QUERY:
			case REFRESH:
			case ROUTING:
			case SCRIPT:
			case SIZE:
			case SLICES:
			case SOURCE_FIELDS:
			case STREAM:
			case TABLE:
			case TEMPLATE:
			case TIME_REFERENCE:
			case USING:
			case WAIT:
			case SKIP_IF_NO_SOURCE:
			case BOOLEAN:
			case INTEGER:
			case KEYWORD:
			case NESTED:
			case OBJECT:
			case TEXT:
			case JSON:
			case BINARY:
			case GEO_POINT:
			case GEO_SHAPE:
			case UUID:
			case DECIMAL:
			case UNION:
			case ID:
			case QUOTED_ID:
				enterOuterAlt(_localctx, 2);
				{
				setState(379);
				selectItem();
				setState(384);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while (_la==COMMA) {
					{
					{
					setState(380);
					match(COMMA);
					setState(381);
					selectItem();
					}
					}
					setState(386);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class SelectItemContext extends ParserRuleContext {
		public SelectExpressionContext selectExpression() {
			return getRuleContext(SelectExpressionContext.class,0);
		}
		public TerminalNode AS() { return getToken(KinoticSQLParser.AS, 0); }
		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class,0);
		}
		public SelectItemContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_selectItem; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KinoticSQLListener ) ((KinoticSQLListener)listener).enterSelectItem(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KinoticSQLListener ) ((KinoticSQLListener)listener).exitSelectItem(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof KinoticSQLVisitor ) return ((KinoticSQLVisitor<? extends T>)visitor).visitSelectItem(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SelectItemContext selectItem() throws RecognitionException {
		SelectItemContext _localctx = new SelectItemContext(_ctx, getState());
		enterRule(_localctx, 34, RULE_selectItem);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(389);
			selectExpression();
			setState(392);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==AS) {
				{
				setState(390);
				match(AS);
				setState(391);
				identifier();
				}
			}

			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class SelectExpressionContext extends ParserRuleContext {
		public FunctionCallContext functionCall() {
			return getRuleContext(FunctionCallContext.class,0);
		}
		public FieldPathContext fieldPath() {
			return getRuleContext(FieldPathContext.class,0);
		}
		public SelectExpressionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_selectExpression; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KinoticSQLListener ) ((KinoticSQLListener)listener).enterSelectExpression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KinoticSQLListener ) ((KinoticSQLListener)listener).exitSelectExpression(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof KinoticSQLVisitor ) return ((KinoticSQLVisitor<? extends T>)visitor).visitSelectExpression(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SelectExpressionContext selectExpression() throws RecognitionException {
		SelectExpressionContext _localctx = new SelectExpressionContext(_ctx, getState());
		enterRule(_localctx, 36, RULE_selectExpression);
		try {
			setState(396);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,31,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(394);
				functionCall();
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(395);
				fieldPath();
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class FunctionCallContext extends ParserRuleContext {
		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class,0);
		}
		public TerminalNode LPAREN() { return getToken(KinoticSQLParser.LPAREN, 0); }
		public TerminalNode RPAREN() { return getToken(KinoticSQLParser.RPAREN, 0); }
		public TerminalNode MULTIPLY() { return getToken(KinoticSQLParser.MULTIPLY, 0); }
		public List<FunctionArgumentContext> functionArgument() {
			return getRuleContexts(FunctionArgumentContext.class);
		}
		public FunctionArgumentContext functionArgument(int i) {
			return getRuleContext(FunctionArgumentContext.class,i);
		}
		public List<TerminalNode> COMMA() { return getTokens(KinoticSQLParser.COMMA); }
		public TerminalNode COMMA(int i) {
			return getToken(KinoticSQLParser.COMMA, i);
		}
		public FunctionCallContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_functionCall; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KinoticSQLListener ) ((KinoticSQLListener)listener).enterFunctionCall(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KinoticSQLListener ) ((KinoticSQLListener)listener).exitFunctionCall(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof KinoticSQLVisitor ) return ((KinoticSQLVisitor<? extends T>)visitor).visitFunctionCall(this);
			else return visitor.visitChildren(this);
		}
	}

	public final FunctionCallContext functionCall() throws RecognitionException {
		FunctionCallContext _localctx = new FunctionCallContext(_ctx, getState());
		enterRule(_localctx, 38, RULE_functionCall);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(398);
			identifier();
			setState(399);
			match(LPAREN);
			setState(409);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case MULTIPLY:
				{
				setState(400);
				match(MULTIPLY);
				}
				break;
			case ABORT:
			case ADD:
			case ASC:
			case AUTO:
			case COLUMN:
			case COMPONENT:
			case CONFLICTS:
			case DATA:
			case DATA_RETENTION:
			case DATE:
			case DESC:
			case DOCUMENT_ID:
			case DOUBLE:
			case EXISTS:
			case FLOAT:
			case FOR:
			case IF:
			case INDEX:
			case INDEXED:
			case LONG:
			case MAX_DOCS:
			case NUMBER_OF_REPLICAS:
			case NUMBER_OF_SHARDS:
			case PROCEED:
			case QUERY:
			case REFRESH:
			case ROUTING:
			case SCRIPT:
			case SIZE:
			case SLICES:
			case SOURCE_FIELDS:
			case STREAM:
			case TABLE:
			case TEMPLATE:
			case TIME_REFERENCE:
			case USING:
			case WAIT:
			case SKIP_IF_NO_SOURCE:
			case BOOLEAN:
			case INTEGER:
			case KEYWORD:
			case NESTED:
			case OBJECT:
			case TEXT:
			case JSON:
			case BINARY:
			case GEO_POINT:
			case GEO_SHAPE:
			case UUID:
			case DECIMAL:
			case UNION:
			case COLON:
			case MINUS:
			case DECIMAL_LITERAL:
			case ID:
			case QUOTED_ID:
			case INTEGER_LITERAL:
			case STRING:
				{
				setState(401);
				functionArgument();
				setState(406);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while (_la==COMMA) {
					{
					{
					setState(402);
					match(COMMA);
					setState(403);
					functionArgument();
					}
					}
					setState(408);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}
				}
				break;
			case RPAREN:
				break;
			default:
				break;
			}
			setState(411);
			match(RPAREN);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class FunctionArgumentContext extends ParserRuleContext {
		public SelectExpressionContext selectExpression() {
			return getRuleContext(SelectExpressionContext.class,0);
		}
		public TerminalNode STRING() { return getToken(KinoticSQLParser.STRING, 0); }
		public NumberLiteralContext numberLiteral() {
			return getRuleContext(NumberLiteralContext.class,0);
		}
		public NamedParameterContext namedParameter() {
			return getRuleContext(NamedParameterContext.class,0);
		}
		public FunctionArgumentContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_functionArgument; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KinoticSQLListener ) ((KinoticSQLListener)listener).enterFunctionArgument(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KinoticSQLListener ) ((KinoticSQLListener)listener).exitFunctionArgument(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof KinoticSQLVisitor ) return ((KinoticSQLVisitor<? extends T>)visitor).visitFunctionArgument(this);
			else return visitor.visitChildren(this);
		}
	}

	public final FunctionArgumentContext functionArgument() throws RecognitionException {
		FunctionArgumentContext _localctx = new FunctionArgumentContext(_ctx, getState());
		enterRule(_localctx, 40, RULE_functionArgument);
		try {
			setState(417);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case ABORT:
			case ADD:
			case ASC:
			case AUTO:
			case COLUMN:
			case COMPONENT:
			case CONFLICTS:
			case DATA:
			case DATA_RETENTION:
			case DATE:
			case DESC:
			case DOCUMENT_ID:
			case DOUBLE:
			case EXISTS:
			case FLOAT:
			case FOR:
			case IF:
			case INDEX:
			case INDEXED:
			case LONG:
			case MAX_DOCS:
			case NUMBER_OF_REPLICAS:
			case NUMBER_OF_SHARDS:
			case PROCEED:
			case QUERY:
			case REFRESH:
			case ROUTING:
			case SCRIPT:
			case SIZE:
			case SLICES:
			case SOURCE_FIELDS:
			case STREAM:
			case TABLE:
			case TEMPLATE:
			case TIME_REFERENCE:
			case USING:
			case WAIT:
			case SKIP_IF_NO_SOURCE:
			case BOOLEAN:
			case INTEGER:
			case KEYWORD:
			case NESTED:
			case OBJECT:
			case TEXT:
			case JSON:
			case BINARY:
			case GEO_POINT:
			case GEO_SHAPE:
			case UUID:
			case DECIMAL:
			case UNION:
			case ID:
			case QUOTED_ID:
				enterOuterAlt(_localctx, 1);
				{
				setState(413);
				selectExpression();
				}
				break;
			case STRING:
				enterOuterAlt(_localctx, 2);
				{
				setState(414);
				match(STRING);
				}
				break;
			case MINUS:
			case DECIMAL_LITERAL:
			case INTEGER_LITERAL:
				enterOuterAlt(_localctx, 3);
				{
				setState(415);
				numberLiteral();
				}
				break;
			case COLON:
				enterOuterAlt(_localctx, 4);
				{
				setState(416);
				namedParameter();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class OrderByContext extends ParserRuleContext {
		public FieldPathContext fieldPath() {
			return getRuleContext(FieldPathContext.class,0);
		}
		public TerminalNode ASC() { return getToken(KinoticSQLParser.ASC, 0); }
		public TerminalNode DESC() { return getToken(KinoticSQLParser.DESC, 0); }
		public OrderByContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_orderBy; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KinoticSQLListener ) ((KinoticSQLListener)listener).enterOrderBy(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KinoticSQLListener ) ((KinoticSQLListener)listener).exitOrderBy(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof KinoticSQLVisitor ) return ((KinoticSQLVisitor<? extends T>)visitor).visitOrderBy(this);
			else return visitor.visitChildren(this);
		}
	}

	public final OrderByContext orderBy() throws RecognitionException {
		OrderByContext _localctx = new OrderByContext(_ctx, getState());
		enterRule(_localctx, 42, RULE_orderBy);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(419);
			fieldPath();
			setState(421);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==ASC || _la==DESC) {
				{
				setState(420);
				_la = _input.LA(1);
				if ( !(_la==ASC || _la==DESC) ) {
				_errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				}
			}

			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class FieldPathContext extends ParserRuleContext {
		public List<IdentifierContext> identifier() {
			return getRuleContexts(IdentifierContext.class);
		}
		public IdentifierContext identifier(int i) {
			return getRuleContext(IdentifierContext.class,i);
		}
		public List<TerminalNode> DOT() { return getTokens(KinoticSQLParser.DOT); }
		public TerminalNode DOT(int i) {
			return getToken(KinoticSQLParser.DOT, i);
		}
		public FieldPathContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_fieldPath; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KinoticSQLListener ) ((KinoticSQLListener)listener).enterFieldPath(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KinoticSQLListener ) ((KinoticSQLListener)listener).exitFieldPath(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof KinoticSQLVisitor ) return ((KinoticSQLVisitor<? extends T>)visitor).visitFieldPath(this);
			else return visitor.visitChildren(this);
		}
	}

	public final FieldPathContext fieldPath() throws RecognitionException {
		FieldPathContext _localctx = new FieldPathContext(_ctx, getState());
		enterRule(_localctx, 44, RULE_fieldPath);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(423);
			identifier();
			setState(428);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==DOT) {
				{
				{
				setState(424);
				match(DOT);
				setState(425);
				identifier();
				}
				}
				setState(430);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class InsertOptionContext extends ParserRuleContext {
		public TerminalNode REFRESH() { return getToken(KinoticSQLParser.REFRESH, 0); }
		public TerminalNode ROUTING() { return getToken(KinoticSQLParser.ROUTING, 0); }
		public TerminalNode STRING() { return getToken(KinoticSQLParser.STRING, 0); }
		public TerminalNode DOCUMENT_ID() { return getToken(KinoticSQLParser.DOCUMENT_ID, 0); }
		public InsertOptionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_insertOption; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KinoticSQLListener ) ((KinoticSQLListener)listener).enterInsertOption(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KinoticSQLListener ) ((KinoticSQLListener)listener).exitInsertOption(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof KinoticSQLVisitor ) return ((KinoticSQLVisitor<? extends T>)visitor).visitInsertOption(this);
			else return visitor.visitChildren(this);
		}
	}

	public final InsertOptionContext insertOption() throws RecognitionException {
		InsertOptionContext _localctx = new InsertOptionContext(_ctx, getState());
		enterRule(_localctx, 46, RULE_insertOption);
		try {
			setState(436);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case REFRESH:
				enterOuterAlt(_localctx, 1);
				{
				setState(431);
				match(REFRESH);
				}
				break;
			case ROUTING:
				enterOuterAlt(_localctx, 2);
				{
				setState(432);
				match(ROUTING);
				setState(433);
				match(STRING);
				}
				break;
			case DOCUMENT_ID:
				enterOuterAlt(_localctx, 3);
				{
				setState(434);
				match(DOCUMENT_ID);
				setState(435);
				match(STRING);
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ValueListContext extends ParserRuleContext {
		public List<ValueContext> value() {
			return getRuleContexts(ValueContext.class);
		}
		public ValueContext value(int i) {
			return getRuleContext(ValueContext.class,i);
		}
		public List<TerminalNode> COMMA() { return getTokens(KinoticSQLParser.COMMA); }
		public TerminalNode COMMA(int i) {
			return getToken(KinoticSQLParser.COMMA, i);
		}
		public ValueListContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_valueList; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KinoticSQLListener ) ((KinoticSQLListener)listener).enterValueList(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KinoticSQLListener ) ((KinoticSQLListener)listener).exitValueList(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof KinoticSQLVisitor ) return ((KinoticSQLVisitor<? extends T>)visitor).visitValueList(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ValueListContext valueList() throws RecognitionException {
		ValueListContext _localctx = new ValueListContext(_ctx, getState());
		enterRule(_localctx, 48, RULE_valueList);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(438);
			value();
			setState(443);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==COMMA) {
				{
				{
				setState(439);
				match(COMMA);
				setState(440);
				value();
				}
				}
				setState(445);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ValueContext extends ParserRuleContext {
		public TerminalNode STRING() { return getToken(KinoticSQLParser.STRING, 0); }
		public NumberLiteralContext numberLiteral() {
			return getRuleContext(NumberLiteralContext.class,0);
		}
		public TerminalNode BOOLEAN_LITERAL() { return getToken(KinoticSQLParser.BOOLEAN_LITERAL, 0); }
		public TerminalNode NULL_LITERAL() { return getToken(KinoticSQLParser.NULL_LITERAL, 0); }
		public NamedParameterContext namedParameter() {
			return getRuleContext(NamedParameterContext.class,0);
		}
		public ObjectLiteralContext objectLiteral() {
			return getRuleContext(ObjectLiteralContext.class,0);
		}
		public ArrayLiteralContext arrayLiteral() {
			return getRuleContext(ArrayLiteralContext.class,0);
		}
		public ValueContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_value; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KinoticSQLListener ) ((KinoticSQLListener)listener).enterValue(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KinoticSQLListener ) ((KinoticSQLListener)listener).exitValue(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof KinoticSQLVisitor ) return ((KinoticSQLVisitor<? extends T>)visitor).visitValue(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ValueContext value() throws RecognitionException {
		ValueContext _localctx = new ValueContext(_ctx, getState());
		enterRule(_localctx, 50, RULE_value);
		try {
			setState(453);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case STRING:
				enterOuterAlt(_localctx, 1);
				{
				setState(446);
				match(STRING);
				}
				break;
			case MINUS:
			case DECIMAL_LITERAL:
			case INTEGER_LITERAL:
				enterOuterAlt(_localctx, 2);
				{
				setState(447);
				numberLiteral();
				}
				break;
			case BOOLEAN_LITERAL:
				enterOuterAlt(_localctx, 3);
				{
				setState(448);
				match(BOOLEAN_LITERAL);
				}
				break;
			case NULL_LITERAL:
				enterOuterAlt(_localctx, 4);
				{
				setState(449);
				match(NULL_LITERAL);
				}
				break;
			case COLON:
				enterOuterAlt(_localctx, 5);
				{
				setState(450);
				namedParameter();
				}
				break;
			case LBRACE:
				enterOuterAlt(_localctx, 6);
				{
				setState(451);
				objectLiteral();
				}
				break;
			case LBRACKET:
				enterOuterAlt(_localctx, 7);
				{
				setState(452);
				arrayLiteral();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class NamedParameterContext extends ParserRuleContext {
		public TerminalNode COLON() { return getToken(KinoticSQLParser.COLON, 0); }
		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class,0);
		}
		public NamedParameterContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_namedParameter; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KinoticSQLListener ) ((KinoticSQLListener)listener).enterNamedParameter(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KinoticSQLListener ) ((KinoticSQLListener)listener).exitNamedParameter(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof KinoticSQLVisitor ) return ((KinoticSQLVisitor<? extends T>)visitor).visitNamedParameter(this);
			else return visitor.visitChildren(this);
		}
	}

	public final NamedParameterContext namedParameter() throws RecognitionException {
		NamedParameterContext _localctx = new NamedParameterContext(_ctx, getState());
		enterRule(_localctx, 52, RULE_namedParameter);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(455);
			match(COLON);
			setState(456);
			identifier();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ObjectLiteralContext extends ParserRuleContext {
		public TerminalNode LBRACE() { return getToken(KinoticSQLParser.LBRACE, 0); }
		public TerminalNode RBRACE() { return getToken(KinoticSQLParser.RBRACE, 0); }
		public List<ObjectFieldContext> objectField() {
			return getRuleContexts(ObjectFieldContext.class);
		}
		public ObjectFieldContext objectField(int i) {
			return getRuleContext(ObjectFieldContext.class,i);
		}
		public List<TerminalNode> COMMA() { return getTokens(KinoticSQLParser.COMMA); }
		public TerminalNode COMMA(int i) {
			return getToken(KinoticSQLParser.COMMA, i);
		}
		public ObjectLiteralContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_objectLiteral; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KinoticSQLListener ) ((KinoticSQLListener)listener).enterObjectLiteral(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KinoticSQLListener ) ((KinoticSQLListener)listener).exitObjectLiteral(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof KinoticSQLVisitor ) return ((KinoticSQLVisitor<? extends T>)visitor).visitObjectLiteral(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ObjectLiteralContext objectLiteral() throws RecognitionException {
		ObjectLiteralContext _localctx = new ObjectLiteralContext(_ctx, getState());
		enterRule(_localctx, 54, RULE_objectLiteral);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(458);
			match(LBRACE);
			setState(467);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if ((((_la) & ~0x3f) == 0 && ((1L << _la) & -261263970621526330L) != 0) || ((((_la - 64)) & ~0x3f) == 0 && ((1L << (_la - 64)) & 47244640767L) != 0)) {
				{
				setState(459);
				objectField();
				setState(464);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while (_la==COMMA) {
					{
					{
					setState(460);
					match(COMMA);
					setState(461);
					objectField();
					}
					}
					setState(466);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}
				}
			}

			setState(469);
			match(RBRACE);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ObjectFieldContext extends ParserRuleContext {
		public TerminalNode COLON() { return getToken(KinoticSQLParser.COLON, 0); }
		public ValueContext value() {
			return getRuleContext(ValueContext.class,0);
		}
		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class,0);
		}
		public TerminalNode STRING() { return getToken(KinoticSQLParser.STRING, 0); }
		public ObjectFieldContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_objectField; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KinoticSQLListener ) ((KinoticSQLListener)listener).enterObjectField(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KinoticSQLListener ) ((KinoticSQLListener)listener).exitObjectField(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof KinoticSQLVisitor ) return ((KinoticSQLVisitor<? extends T>)visitor).visitObjectField(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ObjectFieldContext objectField() throws RecognitionException {
		ObjectFieldContext _localctx = new ObjectFieldContext(_ctx, getState());
		enterRule(_localctx, 56, RULE_objectField);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(473);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case ABORT:
			case ADD:
			case ASC:
			case AUTO:
			case COLUMN:
			case COMPONENT:
			case CONFLICTS:
			case DATA:
			case DATA_RETENTION:
			case DATE:
			case DESC:
			case DOCUMENT_ID:
			case DOUBLE:
			case EXISTS:
			case FLOAT:
			case FOR:
			case IF:
			case INDEX:
			case INDEXED:
			case LONG:
			case MAX_DOCS:
			case NUMBER_OF_REPLICAS:
			case NUMBER_OF_SHARDS:
			case PROCEED:
			case QUERY:
			case REFRESH:
			case ROUTING:
			case SCRIPT:
			case SIZE:
			case SLICES:
			case SOURCE_FIELDS:
			case STREAM:
			case TABLE:
			case TEMPLATE:
			case TIME_REFERENCE:
			case USING:
			case WAIT:
			case SKIP_IF_NO_SOURCE:
			case BOOLEAN:
			case INTEGER:
			case KEYWORD:
			case NESTED:
			case OBJECT:
			case TEXT:
			case JSON:
			case BINARY:
			case GEO_POINT:
			case GEO_SHAPE:
			case UUID:
			case DECIMAL:
			case UNION:
			case ID:
			case QUOTED_ID:
				{
				setState(471);
				identifier();
				}
				break;
			case STRING:
				{
				setState(472);
				match(STRING);
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
			setState(475);
			match(COLON);
			setState(476);
			value();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ArrayLiteralContext extends ParserRuleContext {
		public TerminalNode LBRACKET() { return getToken(KinoticSQLParser.LBRACKET, 0); }
		public TerminalNode RBRACKET() { return getToken(KinoticSQLParser.RBRACKET, 0); }
		public List<ValueContext> value() {
			return getRuleContexts(ValueContext.class);
		}
		public ValueContext value(int i) {
			return getRuleContext(ValueContext.class,i);
		}
		public List<TerminalNode> COMMA() { return getTokens(KinoticSQLParser.COMMA); }
		public TerminalNode COMMA(int i) {
			return getToken(KinoticSQLParser.COMMA, i);
		}
		public ArrayLiteralContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_arrayLiteral; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KinoticSQLListener ) ((KinoticSQLListener)listener).enterArrayLiteral(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KinoticSQLListener ) ((KinoticSQLListener)listener).exitArrayLiteral(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof KinoticSQLVisitor ) return ((KinoticSQLVisitor<? extends T>)visitor).visitArrayLiteral(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ArrayLiteralContext arrayLiteral() throws RecognitionException {
		ArrayLiteralContext _localctx = new ArrayLiteralContext(_ctx, getState());
		enterRule(_localctx, 58, RULE_arrayLiteral);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(478);
			match(LBRACKET);
			setState(487);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (((((_la - 74)) & ~0x3f) == 0 && ((1L << (_la - 74)) & 54003905L) != 0)) {
				{
				setState(479);
				value();
				setState(484);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while (_la==COMMA) {
					{
					{
					setState(480);
					match(COMMA);
					setState(481);
					value();
					}
					}
					setState(486);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}
				}
			}

			setState(489);
			match(RBRACKET);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class NumberLiteralContext extends ParserRuleContext {
		public TerminalNode INTEGER_LITERAL() { return getToken(KinoticSQLParser.INTEGER_LITERAL, 0); }
		public TerminalNode DECIMAL_LITERAL() { return getToken(KinoticSQLParser.DECIMAL_LITERAL, 0); }
		public TerminalNode MINUS() { return getToken(KinoticSQLParser.MINUS, 0); }
		public NumberLiteralContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_numberLiteral; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KinoticSQLListener ) ((KinoticSQLListener)listener).enterNumberLiteral(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KinoticSQLListener ) ((KinoticSQLListener)listener).exitNumberLiteral(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof KinoticSQLVisitor ) return ((KinoticSQLVisitor<? extends T>)visitor).visitNumberLiteral(this);
			else return visitor.visitChildren(this);
		}
	}

	public final NumberLiteralContext numberLiteral() throws RecognitionException {
		NumberLiteralContext _localctx = new NumberLiteralContext(_ctx, getState());
		enterRule(_localctx, 60, RULE_numberLiteral);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(492);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==MINUS) {
				{
				setState(491);
				match(MINUS);
				}
			}

			setState(494);
			_la = _input.LA(1);
			if ( !(_la==DECIMAL_LITERAL || _la==INTEGER_LITERAL) ) {
			_errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class AssignmentContext extends ParserRuleContext {
		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class,0);
		}
		public TerminalNode ASSIGN() { return getToken(KinoticSQLParser.ASSIGN, 0); }
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public AssignmentContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_assignment; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KinoticSQLListener ) ((KinoticSQLListener)listener).enterAssignment(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KinoticSQLListener ) ((KinoticSQLListener)listener).exitAssignment(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof KinoticSQLVisitor ) return ((KinoticSQLVisitor<? extends T>)visitor).visitAssignment(this);
			else return visitor.visitChildren(this);
		}
	}

	public final AssignmentContext assignment() throws RecognitionException {
		AssignmentContext _localctx = new AssignmentContext(_ctx, getState());
		enterRule(_localctx, 62, RULE_assignment);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(496);
			identifier();
			setState(497);
			match(ASSIGN);
			setState(498);
			expression();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ExpressionContext extends ParserRuleContext {
		public ValueContext value() {
			return getRuleContext(ValueContext.class,0);
		}
		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class,0);
		}
		public OperatorContext operator() {
			return getRuleContext(OperatorContext.class,0);
		}
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public TerminalNode LPAREN() { return getToken(KinoticSQLParser.LPAREN, 0); }
		public TerminalNode RPAREN() { return getToken(KinoticSQLParser.RPAREN, 0); }
		public ExpressionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_expression; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KinoticSQLListener ) ((KinoticSQLListener)listener).enterExpression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KinoticSQLListener ) ((KinoticSQLListener)listener).exitExpression(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof KinoticSQLVisitor ) return ((KinoticSQLVisitor<? extends T>)visitor).visitExpression(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ExpressionContext expression() throws RecognitionException {
		ExpressionContext _localctx = new ExpressionContext(_ctx, getState());
		enterRule(_localctx, 64, RULE_expression);
		try {
			setState(509);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case COLON:
			case LBRACE:
			case LBRACKET:
			case MINUS:
			case BOOLEAN_LITERAL:
			case DECIMAL_LITERAL:
			case NULL_LITERAL:
			case INTEGER_LITERAL:
			case STRING:
				enterOuterAlt(_localctx, 1);
				{
				setState(500);
				value();
				}
				break;
			case ABORT:
			case ADD:
			case ASC:
			case AUTO:
			case COLUMN:
			case COMPONENT:
			case CONFLICTS:
			case DATA:
			case DATA_RETENTION:
			case DATE:
			case DESC:
			case DOCUMENT_ID:
			case DOUBLE:
			case EXISTS:
			case FLOAT:
			case FOR:
			case IF:
			case INDEX:
			case INDEXED:
			case LONG:
			case MAX_DOCS:
			case NUMBER_OF_REPLICAS:
			case NUMBER_OF_SHARDS:
			case PROCEED:
			case QUERY:
			case REFRESH:
			case ROUTING:
			case SCRIPT:
			case SIZE:
			case SLICES:
			case SOURCE_FIELDS:
			case STREAM:
			case TABLE:
			case TEMPLATE:
			case TIME_REFERENCE:
			case USING:
			case WAIT:
			case SKIP_IF_NO_SOURCE:
			case BOOLEAN:
			case INTEGER:
			case KEYWORD:
			case NESTED:
			case OBJECT:
			case TEXT:
			case JSON:
			case BINARY:
			case GEO_POINT:
			case GEO_SHAPE:
			case UUID:
			case DECIMAL:
			case UNION:
			case ID:
			case QUOTED_ID:
				enterOuterAlt(_localctx, 2);
				{
				setState(501);
				identifier();
				setState(502);
				operator();
				setState(503);
				expression();
				}
				break;
			case LPAREN:
				enterOuterAlt(_localctx, 3);
				{
				setState(505);
				match(LPAREN);
				setState(506);
				expression();
				setState(507);
				match(RPAREN);
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class OperatorContext extends ParserRuleContext {
		public TerminalNode PLUS() { return getToken(KinoticSQLParser.PLUS, 0); }
		public TerminalNode MINUS() { return getToken(KinoticSQLParser.MINUS, 0); }
		public TerminalNode MULTIPLY() { return getToken(KinoticSQLParser.MULTIPLY, 0); }
		public TerminalNode DIVIDE() { return getToken(KinoticSQLParser.DIVIDE, 0); }
		public OperatorContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_operator; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KinoticSQLListener ) ((KinoticSQLListener)listener).enterOperator(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KinoticSQLListener ) ((KinoticSQLListener)listener).exitOperator(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof KinoticSQLVisitor ) return ((KinoticSQLVisitor<? extends T>)visitor).visitOperator(this);
			else return visitor.visitChildren(this);
		}
	}

	public final OperatorContext operator() throws RecognitionException {
		OperatorContext _localctx = new OperatorContext(_ctx, getState());
		enterRule(_localctx, 66, RULE_operator);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(511);
			_la = _input.LA(1);
			if ( !(((((_la - 76)) & ~0x3f) == 0 && ((1L << (_la - 76)) & 5633L) != 0)) ) {
			_errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class WhereClauseContext extends ParserRuleContext {
		public ConditionContext condition() {
			return getRuleContext(ConditionContext.class,0);
		}
		public TerminalNode LPAREN() { return getToken(KinoticSQLParser.LPAREN, 0); }
		public List<WhereClauseContext> whereClause() {
			return getRuleContexts(WhereClauseContext.class);
		}
		public WhereClauseContext whereClause(int i) {
			return getRuleContext(WhereClauseContext.class,i);
		}
		public TerminalNode RPAREN() { return getToken(KinoticSQLParser.RPAREN, 0); }
		public TerminalNode AND() { return getToken(KinoticSQLParser.AND, 0); }
		public TerminalNode OR() { return getToken(KinoticSQLParser.OR, 0); }
		public WhereClauseContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_whereClause; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KinoticSQLListener ) ((KinoticSQLListener)listener).enterWhereClause(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KinoticSQLListener ) ((KinoticSQLListener)listener).exitWhereClause(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof KinoticSQLVisitor ) return ((KinoticSQLVisitor<? extends T>)visitor).visitWhereClause(this);
			else return visitor.visitChildren(this);
		}
	}

	public final WhereClauseContext whereClause() throws RecognitionException {
		return whereClause(0);
	}

	private WhereClauseContext whereClause(int _p) throws RecognitionException {
		ParserRuleContext _parentctx = _ctx;
		int _parentState = getState();
		WhereClauseContext _localctx = new WhereClauseContext(_ctx, _parentState);
		WhereClauseContext _prevctx = _localctx;
		int _startState = 68;
		enterRecursionRule(_localctx, 68, RULE_whereClause, _p);
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(519);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case ABORT:
			case ADD:
			case ASC:
			case AUTO:
			case COLUMN:
			case COMPONENT:
			case CONFLICTS:
			case DATA:
			case DATA_RETENTION:
			case DATE:
			case DESC:
			case DOCUMENT_ID:
			case DOUBLE:
			case EXISTS:
			case FLOAT:
			case FOR:
			case IF:
			case INDEX:
			case INDEXED:
			case LONG:
			case MAX_DOCS:
			case NUMBER_OF_REPLICAS:
			case NUMBER_OF_SHARDS:
			case PROCEED:
			case QUERY:
			case REFRESH:
			case ROUTING:
			case SCRIPT:
			case SIZE:
			case SLICES:
			case SOURCE_FIELDS:
			case STREAM:
			case TABLE:
			case TEMPLATE:
			case TIME_REFERENCE:
			case USING:
			case WAIT:
			case SKIP_IF_NO_SOURCE:
			case BOOLEAN:
			case INTEGER:
			case KEYWORD:
			case NESTED:
			case OBJECT:
			case TEXT:
			case JSON:
			case BINARY:
			case GEO_POINT:
			case GEO_SHAPE:
			case UUID:
			case DECIMAL:
			case UNION:
			case ID:
			case QUOTED_ID:
				{
				setState(514);
				condition();
				}
				break;
			case LPAREN:
				{
				setState(515);
				match(LPAREN);
				setState(516);
				whereClause(0);
				setState(517);
				match(RPAREN);
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
			_ctx.stop = _input.LT(-1);
			setState(529);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,49,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					if ( _parseListeners!=null ) triggerExitRuleEvent();
					_prevctx = _localctx;
					{
					setState(527);
					_errHandler.sync(this);
					switch ( getInterpreter().adaptivePredict(_input,48,_ctx) ) {
					case 1:
						{
						_localctx = new WhereClauseContext(_parentctx, _parentState);
						pushNewRecursionContext(_localctx, _startState, RULE_whereClause);
						setState(521);
						if (!(precpred(_ctx, 2))) throw new FailedPredicateException(this, "precpred(_ctx, 2)");
						setState(522);
						match(AND);
						setState(523);
						whereClause(3);
						}
						break;
					case 2:
						{
						_localctx = new WhereClauseContext(_parentctx, _parentState);
						pushNewRecursionContext(_localctx, _startState, RULE_whereClause);
						setState(524);
						if (!(precpred(_ctx, 1))) throw new FailedPredicateException(this, "precpred(_ctx, 1)");
						setState(525);
						match(OR);
						setState(526);
						whereClause(2);
						}
						break;
					}
					} 
				}
				setState(531);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,49,_ctx);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			unrollRecursionContexts(_parentctx);
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ConditionContext extends ParserRuleContext {
		public FieldPathContext fieldPath() {
			return getRuleContext(FieldPathContext.class,0);
		}
		public ComparisonOperatorContext comparisonOperator() {
			return getRuleContext(ComparisonOperatorContext.class,0);
		}
		public NamedParameterContext namedParameter() {
			return getRuleContext(NamedParameterContext.class,0);
		}
		public TerminalNode STRING() { return getToken(KinoticSQLParser.STRING, 0); }
		public NumberLiteralContext numberLiteral() {
			return getRuleContext(NumberLiteralContext.class,0);
		}
		public TerminalNode BOOLEAN_LITERAL() { return getToken(KinoticSQLParser.BOOLEAN_LITERAL, 0); }
		public ConditionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_condition; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KinoticSQLListener ) ((KinoticSQLListener)listener).enterCondition(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KinoticSQLListener ) ((KinoticSQLListener)listener).exitCondition(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof KinoticSQLVisitor ) return ((KinoticSQLVisitor<? extends T>)visitor).visitCondition(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ConditionContext condition() throws RecognitionException {
		ConditionContext _localctx = new ConditionContext(_ctx, getState());
		enterRule(_localctx, 70, RULE_condition);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(532);
			fieldPath();
			setState(533);
			comparisonOperator();
			setState(538);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case COLON:
				{
				setState(534);
				namedParameter();
				}
				break;
			case STRING:
				{
				setState(535);
				match(STRING);
				}
				break;
			case MINUS:
			case DECIMAL_LITERAL:
			case INTEGER_LITERAL:
				{
				setState(536);
				numberLiteral();
				}
				break;
			case BOOLEAN_LITERAL:
				{
				setState(537);
				match(BOOLEAN_LITERAL);
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ComparisonOperatorContext extends ParserRuleContext {
		public TerminalNode ASSIGN() { return getToken(KinoticSQLParser.ASSIGN, 0); }
		public TerminalNode NOT_EQUALS() { return getToken(KinoticSQLParser.NOT_EQUALS, 0); }
		public TerminalNode LESS_THAN() { return getToken(KinoticSQLParser.LESS_THAN, 0); }
		public TerminalNode GREATER_THAN() { return getToken(KinoticSQLParser.GREATER_THAN, 0); }
		public TerminalNode LESS_THAN_EQUALS() { return getToken(KinoticSQLParser.LESS_THAN_EQUALS, 0); }
		public TerminalNode GREATER_THAN_EQUALS() { return getToken(KinoticSQLParser.GREATER_THAN_EQUALS, 0); }
		public ComparisonOperatorContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_comparisonOperator; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KinoticSQLListener ) ((KinoticSQLListener)listener).enterComparisonOperator(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KinoticSQLListener ) ((KinoticSQLListener)listener).exitComparisonOperator(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof KinoticSQLVisitor ) return ((KinoticSQLVisitor<? extends T>)visitor).visitComparisonOperator(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ComparisonOperatorContext comparisonOperator() throws RecognitionException {
		ComparisonOperatorContext _localctx = new ComparisonOperatorContext(_ctx, getState());
		enterRule(_localctx, 72, RULE_comparisonOperator);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(540);
			_la = _input.LA(1);
			if ( !(((((_la - 73)) & ~0x3f) == 0 && ((1L << (_la - 73)) & 18017L) != 0)) ) {
			_errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class TableNameContext extends ParserRuleContext {
		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class,0);
		}
		public TableNameContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_tableName; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KinoticSQLListener ) ((KinoticSQLListener)listener).enterTableName(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KinoticSQLListener ) ((KinoticSQLListener)listener).exitTableName(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof KinoticSQLVisitor ) return ((KinoticSQLVisitor<? extends T>)visitor).visitTableName(this);
			else return visitor.visitChildren(this);
		}
	}

	public final TableNameContext tableName() throws RecognitionException {
		TableNameContext _localctx = new TableNameContext(_ctx, getState());
		enterRule(_localctx, 74, RULE_tableName);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(542);
			identifier();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ColumnNameContext extends ParserRuleContext {
		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class,0);
		}
		public ColumnNameContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_columnName; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KinoticSQLListener ) ((KinoticSQLListener)listener).enterColumnName(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KinoticSQLListener ) ((KinoticSQLListener)listener).exitColumnName(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof KinoticSQLVisitor ) return ((KinoticSQLVisitor<? extends T>)visitor).visitColumnName(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ColumnNameContext columnName() throws RecognitionException {
		ColumnNameContext _localctx = new ColumnNameContext(_ctx, getState());
		enterRule(_localctx, 76, RULE_columnName);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(544);
			identifier();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ColumnDefinitionContext extends ParserRuleContext {
		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class,0);
		}
		public TypeContext type() {
			return getRuleContext(TypeContext.class,0);
		}
		public ColumnDefinitionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_columnDefinition; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KinoticSQLListener ) ((KinoticSQLListener)listener).enterColumnDefinition(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KinoticSQLListener ) ((KinoticSQLListener)listener).exitColumnDefinition(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof KinoticSQLVisitor ) return ((KinoticSQLVisitor<? extends T>)visitor).visitColumnDefinition(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ColumnDefinitionContext columnDefinition() throws RecognitionException {
		ColumnDefinitionContext _localctx = new ColumnDefinitionContext(_ctx, getState());
		enterRule(_localctx, 78, RULE_columnDefinition);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(546);
			identifier();
			setState(547);
			type();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class UnionVariantContext extends ParserRuleContext {
		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class,0);
		}
		public TerminalNode LPAREN() { return getToken(KinoticSQLParser.LPAREN, 0); }
		public List<ColumnDefinitionContext> columnDefinition() {
			return getRuleContexts(ColumnDefinitionContext.class);
		}
		public ColumnDefinitionContext columnDefinition(int i) {
			return getRuleContext(ColumnDefinitionContext.class,i);
		}
		public TerminalNode RPAREN() { return getToken(KinoticSQLParser.RPAREN, 0); }
		public List<TerminalNode> COMMA() { return getTokens(KinoticSQLParser.COMMA); }
		public TerminalNode COMMA(int i) {
			return getToken(KinoticSQLParser.COMMA, i);
		}
		public UnionVariantContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_unionVariant; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KinoticSQLListener ) ((KinoticSQLListener)listener).enterUnionVariant(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KinoticSQLListener ) ((KinoticSQLListener)listener).exitUnionVariant(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof KinoticSQLVisitor ) return ((KinoticSQLVisitor<? extends T>)visitor).visitUnionVariant(this);
			else return visitor.visitChildren(this);
		}
	}

	public final UnionVariantContext unionVariant() throws RecognitionException {
		UnionVariantContext _localctx = new UnionVariantContext(_ctx, getState());
		enterRule(_localctx, 80, RULE_unionVariant);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(549);
			identifier();
			setState(550);
			match(LPAREN);
			setState(551);
			columnDefinition();
			setState(556);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==COMMA) {
				{
				{
				setState(552);
				match(COMMA);
				setState(553);
				columnDefinition();
				}
				}
				setState(558);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(559);
			match(RPAREN);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class TypeContext extends ParserRuleContext {
		public TerminalNode TEXT() { return getToken(KinoticSQLParser.TEXT, 0); }
		public TerminalNode KEYWORD() { return getToken(KinoticSQLParser.KEYWORD, 0); }
		public TerminalNode NOT() { return getToken(KinoticSQLParser.NOT, 0); }
		public TerminalNode INDEXED() { return getToken(KinoticSQLParser.INDEXED, 0); }
		public TerminalNode INTEGER() { return getToken(KinoticSQLParser.INTEGER, 0); }
		public TerminalNode LONG() { return getToken(KinoticSQLParser.LONG, 0); }
		public TerminalNode FLOAT() { return getToken(KinoticSQLParser.FLOAT, 0); }
		public TerminalNode DOUBLE() { return getToken(KinoticSQLParser.DOUBLE, 0); }
		public TerminalNode BOOLEAN() { return getToken(KinoticSQLParser.BOOLEAN, 0); }
		public TerminalNode DATE() { return getToken(KinoticSQLParser.DATE, 0); }
		public TerminalNode JSON() { return getToken(KinoticSQLParser.JSON, 0); }
		public TerminalNode BINARY() { return getToken(KinoticSQLParser.BINARY, 0); }
		public TerminalNode GEO_POINT() { return getToken(KinoticSQLParser.GEO_POINT, 0); }
		public TerminalNode GEO_SHAPE() { return getToken(KinoticSQLParser.GEO_SHAPE, 0); }
		public TerminalNode UUID() { return getToken(KinoticSQLParser.UUID, 0); }
		public TerminalNode DECIMAL() { return getToken(KinoticSQLParser.DECIMAL, 0); }
		public TerminalNode OBJECT() { return getToken(KinoticSQLParser.OBJECT, 0); }
		public TerminalNode LPAREN() { return getToken(KinoticSQLParser.LPAREN, 0); }
		public List<ColumnDefinitionContext> columnDefinition() {
			return getRuleContexts(ColumnDefinitionContext.class);
		}
		public ColumnDefinitionContext columnDefinition(int i) {
			return getRuleContext(ColumnDefinitionContext.class,i);
		}
		public TerminalNode RPAREN() { return getToken(KinoticSQLParser.RPAREN, 0); }
		public List<TerminalNode> COMMA() { return getTokens(KinoticSQLParser.COMMA); }
		public TerminalNode COMMA(int i) {
			return getToken(KinoticSQLParser.COMMA, i);
		}
		public TerminalNode NESTED() { return getToken(KinoticSQLParser.NESTED, 0); }
		public TerminalNode UNION() { return getToken(KinoticSQLParser.UNION, 0); }
		public List<UnionVariantContext> unionVariant() {
			return getRuleContexts(UnionVariantContext.class);
		}
		public UnionVariantContext unionVariant(int i) {
			return getRuleContext(UnionVariantContext.class,i);
		}
		public TypeContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_type; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KinoticSQLListener ) ((KinoticSQLListener)listener).enterType(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KinoticSQLListener ) ((KinoticSQLListener)listener).exitType(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof KinoticSQLVisitor ) return ((KinoticSQLVisitor<? extends T>)visitor).visitType(this);
			else return visitor.visitChildren(this);
		}
	}

	public final TypeContext type() throws RecognitionException {
		TypeContext _localctx = new TypeContext(_ctx, getState());
		enterRule(_localctx, 82, RULE_type);
		int _la;
		try {
			setState(657);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case TEXT:
				enterOuterAlt(_localctx, 1);
				{
				setState(561);
				match(TEXT);
				}
				break;
			case KEYWORD:
				enterOuterAlt(_localctx, 2);
				{
				setState(562);
				match(KEYWORD);
				setState(565);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==NOT) {
					{
					setState(563);
					match(NOT);
					setState(564);
					match(INDEXED);
					}
				}

				}
				break;
			case INTEGER:
				enterOuterAlt(_localctx, 3);
				{
				setState(567);
				match(INTEGER);
				setState(570);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==NOT) {
					{
					setState(568);
					match(NOT);
					setState(569);
					match(INDEXED);
					}
				}

				}
				break;
			case LONG:
				enterOuterAlt(_localctx, 4);
				{
				setState(572);
				match(LONG);
				setState(575);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==NOT) {
					{
					setState(573);
					match(NOT);
					setState(574);
					match(INDEXED);
					}
				}

				}
				break;
			case FLOAT:
				enterOuterAlt(_localctx, 5);
				{
				setState(577);
				match(FLOAT);
				setState(580);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==NOT) {
					{
					setState(578);
					match(NOT);
					setState(579);
					match(INDEXED);
					}
				}

				}
				break;
			case DOUBLE:
				enterOuterAlt(_localctx, 6);
				{
				setState(582);
				match(DOUBLE);
				setState(585);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==NOT) {
					{
					setState(583);
					match(NOT);
					setState(584);
					match(INDEXED);
					}
				}

				}
				break;
			case BOOLEAN:
				enterOuterAlt(_localctx, 7);
				{
				setState(587);
				match(BOOLEAN);
				setState(590);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==NOT) {
					{
					setState(588);
					match(NOT);
					setState(589);
					match(INDEXED);
					}
				}

				}
				break;
			case DATE:
				enterOuterAlt(_localctx, 8);
				{
				setState(592);
				match(DATE);
				setState(595);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==NOT) {
					{
					setState(593);
					match(NOT);
					setState(594);
					match(INDEXED);
					}
				}

				}
				break;
			case JSON:
				enterOuterAlt(_localctx, 9);
				{
				setState(597);
				match(JSON);
				setState(600);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==NOT) {
					{
					setState(598);
					match(NOT);
					setState(599);
					match(INDEXED);
					}
				}

				}
				break;
			case BINARY:
				enterOuterAlt(_localctx, 10);
				{
				setState(602);
				match(BINARY);
				}
				break;
			case GEO_POINT:
				enterOuterAlt(_localctx, 11);
				{
				setState(603);
				match(GEO_POINT);
				}
				break;
			case GEO_SHAPE:
				enterOuterAlt(_localctx, 12);
				{
				setState(604);
				match(GEO_SHAPE);
				}
				break;
			case UUID:
				enterOuterAlt(_localctx, 13);
				{
				setState(605);
				match(UUID);
				setState(608);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==NOT) {
					{
					setState(606);
					match(NOT);
					setState(607);
					match(INDEXED);
					}
				}

				}
				break;
			case DECIMAL:
				enterOuterAlt(_localctx, 14);
				{
				setState(610);
				match(DECIMAL);
				setState(613);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==NOT) {
					{
					setState(611);
					match(NOT);
					setState(612);
					match(INDEXED);
					}
				}

				}
				break;
			case OBJECT:
				enterOuterAlt(_localctx, 15);
				{
				setState(615);
				match(OBJECT);
				setState(616);
				match(LPAREN);
				setState(617);
				columnDefinition();
				setState(622);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while (_la==COMMA) {
					{
					{
					setState(618);
					match(COMMA);
					setState(619);
					columnDefinition();
					}
					}
					setState(624);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}
				setState(625);
				match(RPAREN);
				setState(628);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==NOT) {
					{
					setState(626);
					match(NOT);
					setState(627);
					match(INDEXED);
					}
				}

				}
				break;
			case NESTED:
				enterOuterAlt(_localctx, 16);
				{
				setState(630);
				match(NESTED);
				setState(631);
				match(LPAREN);
				setState(632);
				columnDefinition();
				setState(637);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while (_la==COMMA) {
					{
					{
					setState(633);
					match(COMMA);
					setState(634);
					columnDefinition();
					}
					}
					setState(639);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}
				setState(640);
				match(RPAREN);
				}
				break;
			case UNION:
				enterOuterAlt(_localctx, 17);
				{
				setState(642);
				match(UNION);
				setState(643);
				match(LPAREN);
				setState(644);
				unionVariant();
				setState(649);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while (_la==COMMA) {
					{
					{
					setState(645);
					match(COMMA);
					setState(646);
					unionVariant();
					}
					}
					setState(651);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}
				setState(652);
				match(RPAREN);
				setState(655);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==NOT) {
					{
					setState(653);
					match(NOT);
					setState(654);
					match(INDEXED);
					}
				}

				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class CommentContext extends ParserRuleContext {
		public TerminalNode COMMENT() { return getToken(KinoticSQLParser.COMMENT, 0); }
		public CommentContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_comment; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KinoticSQLListener ) ((KinoticSQLListener)listener).enterComment(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KinoticSQLListener ) ((KinoticSQLListener)listener).exitComment(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof KinoticSQLVisitor ) return ((KinoticSQLVisitor<? extends T>)visitor).visitComment(this);
			else return visitor.visitChildren(this);
		}
	}

	public final CommentContext comment() throws RecognitionException {
		CommentContext _localctx = new CommentContext(_ctx, getState());
		enterRule(_localctx, 84, RULE_comment);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(659);
			match(COMMENT);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class IdentifierContext extends ParserRuleContext {
		public TerminalNode ID() { return getToken(KinoticSQLParser.ID, 0); }
		public TerminalNode QUOTED_ID() { return getToken(KinoticSQLParser.QUOTED_ID, 0); }
		public NonReservedContext nonReserved() {
			return getRuleContext(NonReservedContext.class,0);
		}
		public IdentifierContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_identifier; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KinoticSQLListener ) ((KinoticSQLListener)listener).enterIdentifier(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KinoticSQLListener ) ((KinoticSQLListener)listener).exitIdentifier(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof KinoticSQLVisitor ) return ((KinoticSQLVisitor<? extends T>)visitor).visitIdentifier(this);
			else return visitor.visitChildren(this);
		}
	}

	public final IdentifierContext identifier() throws RecognitionException {
		IdentifierContext _localctx = new IdentifierContext(_ctx, getState());
		enterRule(_localctx, 86, RULE_identifier);
		try {
			setState(664);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case ID:
				enterOuterAlt(_localctx, 1);
				{
				setState(661);
				match(ID);
				}
				break;
			case QUOTED_ID:
				enterOuterAlt(_localctx, 2);
				{
				setState(662);
				match(QUOTED_ID);
				}
				break;
			case ABORT:
			case ADD:
			case ASC:
			case AUTO:
			case COLUMN:
			case COMPONENT:
			case CONFLICTS:
			case DATA:
			case DATA_RETENTION:
			case DATE:
			case DESC:
			case DOCUMENT_ID:
			case DOUBLE:
			case EXISTS:
			case FLOAT:
			case FOR:
			case IF:
			case INDEX:
			case INDEXED:
			case LONG:
			case MAX_DOCS:
			case NUMBER_OF_REPLICAS:
			case NUMBER_OF_SHARDS:
			case PROCEED:
			case QUERY:
			case REFRESH:
			case ROUTING:
			case SCRIPT:
			case SIZE:
			case SLICES:
			case SOURCE_FIELDS:
			case STREAM:
			case TABLE:
			case TEMPLATE:
			case TIME_REFERENCE:
			case USING:
			case WAIT:
			case SKIP_IF_NO_SOURCE:
			case BOOLEAN:
			case INTEGER:
			case KEYWORD:
			case NESTED:
			case OBJECT:
			case TEXT:
			case JSON:
			case BINARY:
			case GEO_POINT:
			case GEO_SHAPE:
			case UUID:
			case DECIMAL:
			case UNION:
				enterOuterAlt(_localctx, 3);
				{
				setState(663);
				nonReserved();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class NonReservedContext extends ParserRuleContext {
		public TerminalNode ABORT() { return getToken(KinoticSQLParser.ABORT, 0); }
		public TerminalNode ADD() { return getToken(KinoticSQLParser.ADD, 0); }
		public TerminalNode ASC() { return getToken(KinoticSQLParser.ASC, 0); }
		public TerminalNode AUTO() { return getToken(KinoticSQLParser.AUTO, 0); }
		public TerminalNode COLUMN() { return getToken(KinoticSQLParser.COLUMN, 0); }
		public TerminalNode COMPONENT() { return getToken(KinoticSQLParser.COMPONENT, 0); }
		public TerminalNode CONFLICTS() { return getToken(KinoticSQLParser.CONFLICTS, 0); }
		public TerminalNode DATA() { return getToken(KinoticSQLParser.DATA, 0); }
		public TerminalNode DATA_RETENTION() { return getToken(KinoticSQLParser.DATA_RETENTION, 0); }
		public TerminalNode DATE() { return getToken(KinoticSQLParser.DATE, 0); }
		public TerminalNode DESC() { return getToken(KinoticSQLParser.DESC, 0); }
		public TerminalNode DOCUMENT_ID() { return getToken(KinoticSQLParser.DOCUMENT_ID, 0); }
		public TerminalNode DOUBLE() { return getToken(KinoticSQLParser.DOUBLE, 0); }
		public TerminalNode EXISTS() { return getToken(KinoticSQLParser.EXISTS, 0); }
		public TerminalNode FLOAT() { return getToken(KinoticSQLParser.FLOAT, 0); }
		public TerminalNode FOR() { return getToken(KinoticSQLParser.FOR, 0); }
		public TerminalNode IF() { return getToken(KinoticSQLParser.IF, 0); }
		public TerminalNode INDEX() { return getToken(KinoticSQLParser.INDEX, 0); }
		public TerminalNode INDEXED() { return getToken(KinoticSQLParser.INDEXED, 0); }
		public TerminalNode LONG() { return getToken(KinoticSQLParser.LONG, 0); }
		public TerminalNode MAX_DOCS() { return getToken(KinoticSQLParser.MAX_DOCS, 0); }
		public TerminalNode NUMBER_OF_REPLICAS() { return getToken(KinoticSQLParser.NUMBER_OF_REPLICAS, 0); }
		public TerminalNode NUMBER_OF_SHARDS() { return getToken(KinoticSQLParser.NUMBER_OF_SHARDS, 0); }
		public TerminalNode PROCEED() { return getToken(KinoticSQLParser.PROCEED, 0); }
		public TerminalNode QUERY() { return getToken(KinoticSQLParser.QUERY, 0); }
		public TerminalNode REFRESH() { return getToken(KinoticSQLParser.REFRESH, 0); }
		public TerminalNode ROUTING() { return getToken(KinoticSQLParser.ROUTING, 0); }
		public TerminalNode SCRIPT() { return getToken(KinoticSQLParser.SCRIPT, 0); }
		public TerminalNode SIZE() { return getToken(KinoticSQLParser.SIZE, 0); }
		public TerminalNode SLICES() { return getToken(KinoticSQLParser.SLICES, 0); }
		public TerminalNode SOURCE_FIELDS() { return getToken(KinoticSQLParser.SOURCE_FIELDS, 0); }
		public TerminalNode STREAM() { return getToken(KinoticSQLParser.STREAM, 0); }
		public TerminalNode TABLE() { return getToken(KinoticSQLParser.TABLE, 0); }
		public TerminalNode TEMPLATE() { return getToken(KinoticSQLParser.TEMPLATE, 0); }
		public TerminalNode TIME_REFERENCE() { return getToken(KinoticSQLParser.TIME_REFERENCE, 0); }
		public TerminalNode USING() { return getToken(KinoticSQLParser.USING, 0); }
		public TerminalNode WAIT() { return getToken(KinoticSQLParser.WAIT, 0); }
		public TerminalNode SKIP_IF_NO_SOURCE() { return getToken(KinoticSQLParser.SKIP_IF_NO_SOURCE, 0); }
		public TerminalNode BOOLEAN() { return getToken(KinoticSQLParser.BOOLEAN, 0); }
		public TerminalNode INTEGER() { return getToken(KinoticSQLParser.INTEGER, 0); }
		public TerminalNode KEYWORD() { return getToken(KinoticSQLParser.KEYWORD, 0); }
		public TerminalNode NESTED() { return getToken(KinoticSQLParser.NESTED, 0); }
		public TerminalNode OBJECT() { return getToken(KinoticSQLParser.OBJECT, 0); }
		public TerminalNode TEXT() { return getToken(KinoticSQLParser.TEXT, 0); }
		public TerminalNode JSON() { return getToken(KinoticSQLParser.JSON, 0); }
		public TerminalNode BINARY() { return getToken(KinoticSQLParser.BINARY, 0); }
		public TerminalNode GEO_POINT() { return getToken(KinoticSQLParser.GEO_POINT, 0); }
		public TerminalNode GEO_SHAPE() { return getToken(KinoticSQLParser.GEO_SHAPE, 0); }
		public TerminalNode UUID() { return getToken(KinoticSQLParser.UUID, 0); }
		public TerminalNode DECIMAL() { return getToken(KinoticSQLParser.DECIMAL, 0); }
		public TerminalNode UNION() { return getToken(KinoticSQLParser.UNION, 0); }
		public NonReservedContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_nonReserved; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof KinoticSQLListener ) ((KinoticSQLListener)listener).enterNonReserved(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof KinoticSQLListener ) ((KinoticSQLListener)listener).exitNonReserved(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof KinoticSQLVisitor ) return ((KinoticSQLVisitor<? extends T>)visitor).visitNonReserved(this);
			else return visitor.visitChildren(this);
		}
	}

	public final NonReservedContext nonReserved() throws RecognitionException {
		NonReservedContext _localctx = new NonReservedContext(_ctx, getState());
		enterRule(_localctx, 88, RULE_nonReserved);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(666);
			_la = _input.LA(1);
			if ( !((((_la) & ~0x3f) == 0 && ((1L << _la) & -261263970621526330L) != 0) || ((((_la - 64)) & ~0x3f) == 0 && ((1L << (_la - 64)) & 511L) != 0)) ) {
			_errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public boolean sempred(RuleContext _localctx, int ruleIndex, int predIndex) {
		switch (ruleIndex) {
		case 34:
			return whereClause_sempred((WhereClauseContext)_localctx, predIndex);
		}
		return true;
	}
	private boolean whereClause_sempred(WhereClauseContext _localctx, int predIndex) {
		switch (predIndex) {
		case 0:
			return precpred(_ctx, 2);
		case 1:
			return precpred(_ctx, 1);
		}
		return true;
	}

	public static final String _serializedATN =
		"\u0004\u0001e\u029d\u0002\u0000\u0007\u0000\u0002\u0001\u0007\u0001\u0002"+
		"\u0002\u0007\u0002\u0002\u0003\u0007\u0003\u0002\u0004\u0007\u0004\u0002"+
		"\u0005\u0007\u0005\u0002\u0006\u0007\u0006\u0002\u0007\u0007\u0007\u0002"+
		"\b\u0007\b\u0002\t\u0007\t\u0002\n\u0007\n\u0002\u000b\u0007\u000b\u0002"+
		"\f\u0007\f\u0002\r\u0007\r\u0002\u000e\u0007\u000e\u0002\u000f\u0007\u000f"+
		"\u0002\u0010\u0007\u0010\u0002\u0011\u0007\u0011\u0002\u0012\u0007\u0012"+
		"\u0002\u0013\u0007\u0013\u0002\u0014\u0007\u0014\u0002\u0015\u0007\u0015"+
		"\u0002\u0016\u0007\u0016\u0002\u0017\u0007\u0017\u0002\u0018\u0007\u0018"+
		"\u0002\u0019\u0007\u0019\u0002\u001a\u0007\u001a\u0002\u001b\u0007\u001b"+
		"\u0002\u001c\u0007\u001c\u0002\u001d\u0007\u001d\u0002\u001e\u0007\u001e"+
		"\u0002\u001f\u0007\u001f\u0002 \u0007 \u0002!\u0007!\u0002\"\u0007\"\u0002"+
		"#\u0007#\u0002$\u0007$\u0002%\u0007%\u0002&\u0007&\u0002\'\u0007\'\u0002"+
		"(\u0007(\u0002)\u0007)\u0002*\u0007*\u0002+\u0007+\u0002,\u0007,\u0001"+
		"\u0000\u0005\u0000\\\b\u0000\n\u0000\f\u0000_\t\u0000\u0001\u0000\u0001"+
		"\u0000\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001"+
		"\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0003"+
		"\u0001n\b\u0001\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001"+
		"\u0002\u0003\u0002u\b\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001"+
		"\u0002\u0001\u0002\u0005\u0002|\b\u0002\n\u0002\f\u0002\u007f\t\u0002"+
		"\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0003\u0001\u0003\u0001\u0003"+
		"\u0001\u0003\u0001\u0003\u0001\u0003\u0001\u0003\u0001\u0003\u0005\u0003"+
		"\u008c\b\u0003\n\u0003\f\u0003\u008f\t\u0003\u0001\u0003\u0001\u0003\u0001"+
		"\u0003\u0001\u0003\u0001\u0003\u0001\u0003\u0005\u0003\u0097\b\u0003\n"+
		"\u0003\f\u0003\u009a\t\u0003\u0001\u0003\u0001\u0003\u0003\u0003\u009e"+
		"\b\u0003\u0001\u0003\u0001\u0003\u0001\u0004\u0001\u0004\u0001\u0004\u0001"+
		"\u0004\u0001\u0004\u0001\u0004\u0003\u0004\u00a8\b\u0004\u0001\u0005\u0001"+
		"\u0005\u0001\u0005\u0001\u0005\u0001\u0005\u0001\u0005\u0001\u0005\u0001"+
		"\u0005\u0005\u0005\u00b2\b\u0005\n\u0005\f\u0005\u00b5\t\u0005\u0001\u0005"+
		"\u0001\u0005\u0001\u0005\u0001\u0006\u0001\u0006\u0001\u0006\u0001\u0006"+
		"\u0001\u0006\u0001\u0006\u0001\u0006\u0001\u0006\u0001\u0006\u0001\u0006"+
		"\u0001\u0006\u0001\u0006\u0001\u0006\u0005\u0006\u00c7\b\u0006\n\u0006"+
		"\f\u0006\u00ca\t\u0006\u0001\u0006\u0001\u0006\u0003\u0006\u00ce\b\u0006"+
		"\u0001\u0006\u0001\u0006\u0001\u0007\u0001\u0007\u0001\u0007\u0001\u0007"+
		"\u0001\u0007\u0001\u0007\u0001\u0007\u0003\u0007\u00d9\b\u0007\u0001\b"+
		"\u0001\b\u0001\b\u0001\b\u0001\b\u0001\b\u0001\b\u0001\b\u0001\b\u0001"+
		"\t\u0001\t\u0001\t\u0001\t\u0001\t\u0003\t\u00e9\b\t\u0001\t\u0001\t\u0001"+
		"\n\u0001\n\u0001\n\u0001\n\u0001\n\u0005\n\u00f2\b\n\n\n\f\n\u00f5\t\n"+
		"\u0001\n\u0001\n\u0001\u000b\u0001\u000b\u0001\u000b\u0001\u000b\u0001"+
		"\u000b\u0001\u000b\u0001\u000b\u0001\u000b\u0001\u000b\u0001\u000b\u0001"+
		"\u000b\u0001\u000b\u0001\u000b\u0001\u000b\u0001\u000b\u0001\u000b\u0001"+
		"\u000b\u0001\u000b\u0001\u000b\u0001\u000b\u0001\u000b\u0001\u000b\u0001"+
		"\u000b\u0001\u000b\u0001\u000b\u0001\u000b\u0001\u000b\u0003\u000b\u0114"+
		"\b\u000b\u0001\f\u0001\f\u0001\f\u0001\f\u0001\f\u0001\f\u0005\f\u011c"+
		"\b\f\n\f\f\f\u011f\t\f\u0001\f\u0001\f\u0001\f\u0001\f\u0003\f\u0125\b"+
		"\f\u0001\f\u0001\f\u0001\r\u0001\r\u0001\r\u0001\r\u0001\r\u0001\r\u0001"+
		"\r\u0003\r\u0130\b\r\u0001\r\u0001\r\u0001\u000e\u0001\u000e\u0001\u000e"+
		"\u0001\u000e\u0001\u000e\u0001\u000e\u0001\u000e\u0005\u000e\u013b\b\u000e"+
		"\n\u000e\f\u000e\u013e\t\u000e\u0001\u000e\u0001\u000e\u0003\u000e\u0142"+
		"\b\u000e\u0001\u000e\u0001\u000e\u0001\u000e\u0001\u000e\u0001\u000e\u0001"+
		"\u000e\u0001\u000e\u0001\u000e\u0005\u000e\u014c\b\u000e\n\u000e\f\u000e"+
		"\u014f\t\u000e\u0003\u000e\u0151\b\u000e\u0001\u000e\u0001\u000e\u0001"+
		"\u000f\u0001\u000f\u0001\u000f\u0001\u000f\u0001\u000f\u0001\u000f\u0003"+
		"\u000f\u015b\b\u000f\u0001\u000f\u0001\u000f\u0001\u000f\u0001\u000f\u0001"+
		"\u000f\u0005\u000f\u0162\b\u000f\n\u000f\f\u000f\u0165\t\u000f\u0003\u000f"+
		"\u0167\b\u000f\u0001\u000f\u0001\u000f\u0001\u000f\u0001\u000f\u0001\u000f"+
		"\u0005\u000f\u016e\b\u000f\n\u000f\f\u000f\u0171\t\u000f\u0003\u000f\u0173"+
		"\b\u000f\u0001\u000f\u0001\u000f\u0003\u000f\u0177\b\u000f\u0001\u000f"+
		"\u0001\u000f\u0001\u0010\u0001\u0010\u0001\u0010\u0001\u0010\u0005\u0010"+
		"\u017f\b\u0010\n\u0010\f\u0010\u0182\t\u0010\u0003\u0010\u0184\b\u0010"+
		"\u0001\u0011\u0001\u0011\u0001\u0011\u0003\u0011\u0189\b\u0011\u0001\u0012"+
		"\u0001\u0012\u0003\u0012\u018d\b\u0012\u0001\u0013\u0001\u0013\u0001\u0013"+
		"\u0001\u0013\u0001\u0013\u0001\u0013\u0005\u0013\u0195\b\u0013\n\u0013"+
		"\f\u0013\u0198\t\u0013\u0003\u0013\u019a\b\u0013\u0001\u0013\u0001\u0013"+
		"\u0001\u0014\u0001\u0014\u0001\u0014\u0001\u0014\u0003\u0014\u01a2\b\u0014"+
		"\u0001\u0015\u0001\u0015\u0003\u0015\u01a6\b\u0015\u0001\u0016\u0001\u0016"+
		"\u0001\u0016\u0005\u0016\u01ab\b\u0016\n\u0016\f\u0016\u01ae\t\u0016\u0001"+
		"\u0017\u0001\u0017\u0001\u0017\u0001\u0017\u0001\u0017\u0003\u0017\u01b5"+
		"\b\u0017\u0001\u0018\u0001\u0018\u0001\u0018\u0005\u0018\u01ba\b\u0018"+
		"\n\u0018\f\u0018\u01bd\t\u0018\u0001\u0019\u0001\u0019\u0001\u0019\u0001"+
		"\u0019\u0001\u0019\u0001\u0019\u0001\u0019\u0003\u0019\u01c6\b\u0019\u0001"+
		"\u001a\u0001\u001a\u0001\u001a\u0001\u001b\u0001\u001b\u0001\u001b\u0001"+
		"\u001b\u0005\u001b\u01cf\b\u001b\n\u001b\f\u001b\u01d2\t\u001b\u0003\u001b"+
		"\u01d4\b\u001b\u0001\u001b\u0001\u001b\u0001\u001c\u0001\u001c\u0003\u001c"+
		"\u01da\b\u001c\u0001\u001c\u0001\u001c\u0001\u001c\u0001\u001d\u0001\u001d"+
		"\u0001\u001d\u0001\u001d\u0005\u001d\u01e3\b\u001d\n\u001d\f\u001d\u01e6"+
		"\t\u001d\u0003\u001d\u01e8\b\u001d\u0001\u001d\u0001\u001d\u0001\u001e"+
		"\u0003\u001e\u01ed\b\u001e\u0001\u001e\u0001\u001e\u0001\u001f\u0001\u001f"+
		"\u0001\u001f\u0001\u001f\u0001 \u0001 \u0001 \u0001 \u0001 \u0001 \u0001"+
		" \u0001 \u0001 \u0003 \u01fe\b \u0001!\u0001!\u0001\"\u0001\"\u0001\""+
		"\u0001\"\u0001\"\u0001\"\u0003\"\u0208\b\"\u0001\"\u0001\"\u0001\"\u0001"+
		"\"\u0001\"\u0001\"\u0005\"\u0210\b\"\n\"\f\"\u0213\t\"\u0001#\u0001#\u0001"+
		"#\u0001#\u0001#\u0001#\u0003#\u021b\b#\u0001$\u0001$\u0001%\u0001%\u0001"+
		"&\u0001&\u0001\'\u0001\'\u0001\'\u0001(\u0001(\u0001(\u0001(\u0001(\u0005"+
		"(\u022b\b(\n(\f(\u022e\t(\u0001(\u0001(\u0001)\u0001)\u0001)\u0001)\u0003"+
		")\u0236\b)\u0001)\u0001)\u0001)\u0003)\u023b\b)\u0001)\u0001)\u0001)\u0003"+
		")\u0240\b)\u0001)\u0001)\u0001)\u0003)\u0245\b)\u0001)\u0001)\u0001)\u0003"+
		")\u024a\b)\u0001)\u0001)\u0001)\u0003)\u024f\b)\u0001)\u0001)\u0001)\u0003"+
		")\u0254\b)\u0001)\u0001)\u0001)\u0003)\u0259\b)\u0001)\u0001)\u0001)\u0001"+
		")\u0001)\u0001)\u0003)\u0261\b)\u0001)\u0001)\u0001)\u0003)\u0266\b)\u0001"+
		")\u0001)\u0001)\u0001)\u0001)\u0005)\u026d\b)\n)\f)\u0270\t)\u0001)\u0001"+
		")\u0001)\u0003)\u0275\b)\u0001)\u0001)\u0001)\u0001)\u0001)\u0005)\u027c"+
		"\b)\n)\f)\u027f\t)\u0001)\u0001)\u0001)\u0001)\u0001)\u0001)\u0001)\u0005"+
		")\u0288\b)\n)\f)\u028b\t)\u0001)\u0001)\u0001)\u0003)\u0290\b)\u0003)"+
		"\u0292\b)\u0001*\u0001*\u0001+\u0001+\u0001+\u0003+\u0299\b+\u0001,\u0001"+
		",\u0001,\u0000\u0001D-\u0000\u0002\u0004\u0006\b\n\f\u000e\u0010\u0012"+
		"\u0014\u0016\u0018\u001a\u001c\u001e \"$&(*,.02468:<>@BDFHJLNPRTVX\u0000"+
		"\u0007\u0002\u0000\u0001\u0001&&\u0002\u0000\u0007\u0007bb\u0002\u0000"+
		"\u0006\u0006\u0011\u0011\u0002\u0000^^bb\u0003\u0000LLUVXX\u0004\u0000"+
		"IINORSWW\r\u0000\u0001\u0002\u0006\u0007\t\u000b\r\u000f\u0011\u0016\u0019"+
		"\u001b\u001f \"#&(*+.466:H\u02e1\u0000]\u0001\u0000\u0000\u0000\u0002"+
		"m\u0001\u0000\u0000\u0000\u0004o\u0001\u0000\u0000\u0000\u0006\u0083\u0001"+
		"\u0000\u0000\u0000\b\u00a7\u0001\u0000\u0000\u0000\n\u00a9\u0001\u0000"+
		"\u0000\u0000\f\u00b9\u0001\u0000\u0000\u0000\u000e\u00d8\u0001\u0000\u0000"+
		"\u0000\u0010\u00da\u0001\u0000\u0000\u0000\u0012\u00e3\u0001\u0000\u0000"+
		"\u0000\u0014\u00ec\u0001\u0000\u0000\u0000\u0016\u0113\u0001\u0000\u0000"+
		"\u0000\u0018\u0115\u0001\u0000\u0000\u0000\u001a\u0128\u0001\u0000\u0000"+
		"\u0000\u001c\u0133\u0001\u0000\u0000\u0000\u001e\u0154\u0001\u0000\u0000"+
		"\u0000 \u0183\u0001\u0000\u0000\u0000\"\u0185\u0001\u0000\u0000\u0000"+
		"$\u018c\u0001\u0000\u0000\u0000&\u018e\u0001\u0000\u0000\u0000(\u01a1"+
		"\u0001\u0000\u0000\u0000*\u01a3\u0001\u0000\u0000\u0000,\u01a7\u0001\u0000"+
		"\u0000\u0000.\u01b4\u0001\u0000\u0000\u00000\u01b6\u0001\u0000\u0000\u0000"+
		"2\u01c5\u0001\u0000\u0000\u00004\u01c7\u0001\u0000\u0000\u00006\u01ca"+
		"\u0001\u0000\u0000\u00008\u01d9\u0001\u0000\u0000\u0000:\u01de\u0001\u0000"+
		"\u0000\u0000<\u01ec\u0001\u0000\u0000\u0000>\u01f0\u0001\u0000\u0000\u0000"+
		"@\u01fd\u0001\u0000\u0000\u0000B\u01ff\u0001\u0000\u0000\u0000D\u0207"+
		"\u0001\u0000\u0000\u0000F\u0214\u0001\u0000\u0000\u0000H\u021c\u0001\u0000"+
		"\u0000\u0000J\u021e\u0001\u0000\u0000\u0000L\u0220\u0001\u0000\u0000\u0000"+
		"N\u0222\u0001\u0000\u0000\u0000P\u0225\u0001\u0000\u0000\u0000R\u0291"+
		"\u0001\u0000\u0000\u0000T\u0293\u0001\u0000\u0000\u0000V\u0298\u0001\u0000"+
		"\u0000\u0000X\u029a\u0001\u0000\u0000\u0000Z\\\u0003\u0002\u0001\u0000"+
		"[Z\u0001\u0000\u0000\u0000\\_\u0001\u0000\u0000\u0000][\u0001\u0000\u0000"+
		"\u0000]^\u0001\u0000\u0000\u0000^`\u0001\u0000\u0000\u0000_]\u0001\u0000"+
		"\u0000\u0000`a\u0005\u0000\u0000\u0001a\u0001\u0001\u0000\u0000\u0000"+
		"bn\u0003\u0004\u0002\u0000cn\u0003\u0006\u0003\u0000dn\u0003\n\u0005\u0000"+
		"en\u0003\f\u0006\u0000fn\u0003\u0010\b\u0000gn\u0003\u0012\t\u0000hn\u0003"+
		"\u0018\f\u0000in\u0003\u001a\r\u0000jn\u0003\u001c\u000e\u0000kn\u0003"+
		"\u001e\u000f\u0000ln\u0003T*\u0000mb\u0001\u0000\u0000\u0000mc\u0001\u0000"+
		"\u0000\u0000md\u0001\u0000\u0000\u0000me\u0001\u0000\u0000\u0000mf\u0001"+
		"\u0000\u0000\u0000mg\u0001\u0000\u0000\u0000mh\u0001\u0000\u0000\u0000"+
		"mi\u0001\u0000\u0000\u0000mj\u0001\u0000\u0000\u0000mk\u0001\u0000\u0000"+
		"\u0000ml\u0001\u0000\u0000\u0000n\u0003\u0001\u0000\u0000\u0000op\u0005"+
		"\f\u0000\u0000pt\u00052\u0000\u0000qr\u0005\u0019\u0000\u0000rs\u0005"+
		"!\u0000\u0000su\u0005\u0014\u0000\u0000tq\u0001\u0000\u0000\u0000tu\u0001"+
		"\u0000\u0000\u0000uv\u0001\u0000\u0000\u0000vw\u0003V+\u0000wx\u0005T"+
		"\u0000\u0000x}\u0003N\'\u0000yz\u0005K\u0000\u0000z|\u0003N\'\u0000{y"+
		"\u0001\u0000\u0000\u0000|\u007f\u0001\u0000\u0000\u0000}{\u0001\u0000"+
		"\u0000\u0000}~\u0001\u0000\u0000\u0000~\u0080\u0001\u0000\u0000\u0000"+
		"\u007f}\u0001\u0000\u0000\u0000\u0080\u0081\u0005[\u0000\u0000\u0081\u0082"+
		"\u0005\\\u0000\u0000\u0082\u0005\u0001\u0000\u0000\u0000\u0083\u0084\u0005"+
		"\f\u0000\u0000\u0084\u0085\u0005\r\u0000\u0000\u0085\u0086\u00051\u0000"+
		"\u0000\u0086\u0087\u0003V+\u0000\u0087\u0088\u0005T\u0000\u0000\u0088"+
		"\u008d\u0003N\'\u0000\u0089\u008a\u0005K\u0000\u0000\u008a\u008c\u0003"+
		"N\'\u0000\u008b\u0089\u0001\u0000\u0000\u0000\u008c\u008f\u0001\u0000"+
		"\u0000\u0000\u008d\u008b\u0001\u0000\u0000\u0000\u008d\u008e\u0001\u0000"+
		"\u0000\u0000\u008e\u0090\u0001\u0000\u0000\u0000\u008f\u008d\u0001\u0000"+
		"\u0000\u0000\u0090\u009d\u0005[\u0000\u0000\u0091\u0092\u00059\u0000\u0000"+
		"\u0092\u0093\u0005T\u0000\u0000\u0093\u0098\u0003\b\u0004\u0000\u0094"+
		"\u0095\u0005K\u0000\u0000\u0095\u0097\u0003\b\u0004\u0000\u0096\u0094"+
		"\u0001\u0000\u0000\u0000\u0097\u009a\u0001\u0000\u0000\u0000\u0098\u0096"+
		"\u0001\u0000\u0000\u0000\u0098\u0099\u0001\u0000\u0000\u0000\u0099\u009b"+
		"\u0001\u0000\u0000\u0000\u009a\u0098\u0001\u0000\u0000\u0000\u009b\u009c"+
		"\u0005[\u0000\u0000\u009c\u009e\u0001\u0000\u0000\u0000\u009d\u0091\u0001"+
		"\u0000\u0000\u0000\u009d\u009e\u0001\u0000\u0000\u0000\u009e\u009f\u0001"+
		"\u0000\u0000\u0000\u009f\u00a0\u0005\\\u0000\u0000\u00a0\u0007\u0001\u0000"+
		"\u0000\u0000\u00a1\u00a2\u0005\u000e\u0000\u0000\u00a2\u00a3\u0005I\u0000"+
		"\u0000\u00a3\u00a8\u0005c\u0000\u0000\u00a4\u00a5\u00054\u0000\u0000\u00a5"+
		"\u00a6\u0005I\u0000\u0000\u00a6\u00a8\u0005c\u0000\u0000\u00a7\u00a1\u0001"+
		"\u0000\u0000\u0000\u00a7\u00a4\u0001\u0000\u0000\u0000\u00a8\t\u0001\u0000"+
		"\u0000\u0000\u00a9\u00aa\u0005\f\u0000\u0000\u00aa\u00ab\u0005\n\u0000"+
		"\u0000\u00ab\u00ac\u00053\u0000\u0000\u00ac\u00ad\u0003V+\u0000\u00ad"+
		"\u00ae\u0005T\u0000\u0000\u00ae\u00b3\u0003\u000e\u0007\u0000\u00af\u00b0"+
		"\u0005K\u0000\u0000\u00b0\u00b2\u0003\u000e\u0007\u0000\u00b1\u00af\u0001"+
		"\u0000\u0000\u0000\u00b2\u00b5\u0001\u0000\u0000\u0000\u00b3\u00b1\u0001"+
		"\u0000\u0000\u0000\u00b3\u00b4\u0001\u0000\u0000\u0000\u00b4\u00b6\u0001"+
		"\u0000\u0000\u0000\u00b5\u00b3\u0001\u0000\u0000\u0000\u00b6\u00b7\u0005"+
		"[\u0000\u0000\u00b7\u00b8\u0005\\\u0000\u0000\u00b8\u000b\u0001\u0000"+
		"\u0000\u0000\u00b9\u00ba\u0005\f\u0000\u0000\u00ba\u00bb\u0005\u001a\u0000"+
		"\u0000\u00bb\u00bc\u00053\u0000\u0000\u00bc\u00bd\u0003V+\u0000\u00bd"+
		"\u00be\u0005\u0016\u0000\u0000\u00be\u00bf\u0005c\u0000\u0000\u00bf\u00c0"+
		"\u00056\u0000\u0000\u00c0\u00cd\u0005c\u0000\u0000\u00c1\u00c2\u00059"+
		"\u0000\u0000\u00c2\u00c3\u0005T\u0000\u0000\u00c3\u00c8\u0003\u000e\u0007"+
		"\u0000\u00c4\u00c5\u0005K\u0000\u0000\u00c5\u00c7\u0003\u000e\u0007\u0000"+
		"\u00c6\u00c4\u0001\u0000\u0000\u0000\u00c7\u00ca\u0001\u0000\u0000\u0000"+
		"\u00c8\u00c6\u0001\u0000\u0000\u0000\u00c8\u00c9\u0001\u0000\u0000\u0000"+
		"\u00c9\u00cb\u0001\u0000\u0000\u0000\u00ca\u00c8\u0001\u0000\u0000\u0000"+
		"\u00cb\u00cc\u0005[\u0000\u0000\u00cc\u00ce\u0001\u0000\u0000\u0000\u00cd"+
		"\u00c1\u0001\u0000\u0000\u0000\u00cd\u00ce\u0001\u0000\u0000\u0000\u00ce"+
		"\u00cf\u0001\u0000\u0000\u0000\u00cf\u00d0\u0005\\\u0000\u0000\u00d0\r"+
		"\u0001\u0000\u0000\u0000\u00d1\u00d2\u0005#\u0000\u0000\u00d2\u00d3\u0005"+
		"I\u0000\u0000\u00d3\u00d9\u0005b\u0000\u0000\u00d4\u00d5\u0005\"\u0000"+
		"\u0000\u00d5\u00d6\u0005I\u0000\u0000\u00d6\u00d9\u0005b\u0000\u0000\u00d7"+
		"\u00d9\u0003N\'\u0000\u00d8\u00d1\u0001\u0000\u0000\u0000\u00d8\u00d4"+
		"\u0001\u0000\u0000\u0000\u00d8\u00d7\u0001\u0000\u0000\u0000\u00d9\u000f"+
		"\u0001\u0000\u0000\u0000\u00da\u00db\u0005\u0003\u0000\u0000\u00db\u00dc"+
		"\u00052\u0000\u0000\u00dc\u00dd\u0003V+\u0000\u00dd\u00de\u0005\u0002"+
		"\u0000\u0000\u00de\u00df\u0005\t\u0000\u0000\u00df\u00e0\u0003V+\u0000"+
		"\u00e0\u00e1\u0003R)\u0000\u00e1\u00e2\u0005\\\u0000\u0000\u00e2\u0011"+
		"\u0001\u0000\u0000\u0000\u00e3\u00e4\u0005)\u0000\u0000\u00e4\u00e5\u0003"+
		"V+\u0000\u00e5\u00e6\u0005\u001d\u0000\u0000\u00e6\u00e8\u0003V+\u0000"+
		"\u00e7\u00e9\u0003\u0014\n\u0000\u00e8\u00e7\u0001\u0000\u0000\u0000\u00e8"+
		"\u00e9\u0001\u0000\u0000\u0000\u00e9\u00ea\u0001\u0000\u0000\u0000\u00ea"+
		"\u00eb\u0005\\\u0000\u0000\u00eb\u0013\u0001\u0000\u0000\u0000\u00ec\u00ed"+
		"\u00059\u0000\u0000\u00ed\u00ee\u0005T\u0000\u0000\u00ee\u00f3\u0003\u0016"+
		"\u000b\u0000\u00ef\u00f0\u0005K\u0000\u0000\u00f0\u00f2\u0003\u0016\u000b"+
		"\u0000\u00f1\u00ef\u0001\u0000\u0000\u0000\u00f2\u00f5\u0001\u0000\u0000"+
		"\u0000\u00f3\u00f1\u0001\u0000\u0000\u0000\u00f3\u00f4\u0001\u0000\u0000"+
		"\u0000\u00f4\u00f6\u0001\u0000\u0000\u0000\u00f5\u00f3\u0001\u0000\u0000"+
		"\u0000\u00f6\u00f7\u0005[\u0000\u0000\u00f7\u0015\u0001\u0000\u0000\u0000"+
		"\u00f8\u00f9\u0005\u000b\u0000\u0000\u00f9\u00fa\u0005I\u0000\u0000\u00fa"+
		"\u0114\u0007\u0000\u0000\u0000\u00fb\u00fc\u0005 \u0000\u0000\u00fc\u00fd"+
		"\u0005I\u0000\u0000\u00fd\u0114\u0005b\u0000\u0000\u00fe\u00ff\u0005/"+
		"\u0000\u0000\u00ff\u0100\u0005I\u0000\u0000\u0100\u0114\u0007\u0001\u0000"+
		"\u0000\u0101\u0102\u0005.\u0000\u0000\u0102\u0103\u0005I\u0000\u0000\u0103"+
		"\u0114\u0005b\u0000\u0000\u0104\u0105\u00050\u0000\u0000\u0105\u0106\u0005"+
		"I\u0000\u0000\u0106\u0114\u0005c\u0000\u0000\u0107\u0108\u0005\'\u0000"+
		"\u0000\u0108\u0109\u0005I\u0000\u0000\u0109\u0114\u0005c\u0000\u0000\u010a"+
		"\u010b\u0005+\u0000\u0000\u010b\u010c\u0005I\u0000\u0000\u010c\u0114\u0005"+
		"c\u0000\u0000\u010d\u010e\u0005:\u0000\u0000\u010e\u010f\u0005I\u0000"+
		"\u0000\u010f\u0114\u0005]\u0000\u0000\u0110\u0111\u0005;\u0000\u0000\u0111"+
		"\u0112\u0005I\u0000\u0000\u0112\u0114\u0005]\u0000\u0000\u0113\u00f8\u0001"+
		"\u0000\u0000\u0000\u0113\u00fb\u0001\u0000\u0000\u0000\u0113\u00fe\u0001"+
		"\u0000\u0000\u0000\u0113\u0101\u0001\u0000\u0000\u0000\u0113\u0104\u0001"+
		"\u0000\u0000\u0000\u0113\u0107\u0001\u0000\u0000\u0000\u0113\u010a\u0001"+
		"\u0000\u0000\u0000\u0113\u010d\u0001\u0000\u0000\u0000\u0113\u0110\u0001"+
		"\u0000\u0000\u0000\u0114\u0017\u0001\u0000\u0000\u0000\u0115\u0116\u0005"+
		"5\u0000\u0000\u0116\u0117\u0003V+\u0000\u0117\u0118\u0005-\u0000\u0000"+
		"\u0118\u011d\u0003>\u001f\u0000\u0119\u011a\u0005K\u0000\u0000\u011a\u011c"+
		"\u0003>\u001f\u0000\u011b\u0119\u0001\u0000\u0000\u0000\u011c\u011f\u0001"+
		"\u0000\u0000\u0000\u011d\u011b\u0001\u0000\u0000\u0000\u011d\u011e\u0001"+
		"\u0000\u0000\u0000\u011e\u0120\u0001\u0000\u0000\u0000\u011f\u011d\u0001"+
		"\u0000\u0000\u0000\u0120\u0121\u00058\u0000\u0000\u0121\u0124\u0003D\""+
		"\u0000\u0122\u0123\u00059\u0000\u0000\u0123\u0125\u0005(\u0000\u0000\u0124"+
		"\u0122\u0001\u0000\u0000\u0000\u0124\u0125\u0001\u0000\u0000\u0000\u0125"+
		"\u0126\u0001\u0000\u0000\u0000\u0126\u0127\u0005\\\u0000\u0000\u0127\u0019"+
		"\u0001\u0000\u0000\u0000\u0128\u0129\u0005\u0010\u0000\u0000\u0129\u012a"+
		"\u0005\u0017\u0000\u0000\u012a\u012b\u0003V+\u0000\u012b\u012c\u00058"+
		"\u0000\u0000\u012c\u012f\u0003D\"\u0000\u012d\u012e\u00059\u0000\u0000"+
		"\u012e\u0130\u0005(\u0000\u0000\u012f\u012d\u0001\u0000\u0000\u0000\u012f"+
		"\u0130\u0001\u0000\u0000\u0000\u0130\u0131\u0001\u0000\u0000\u0000\u0131"+
		"\u0132\u0005\\\u0000\u0000\u0132\u001b\u0001\u0000\u0000\u0000\u0133\u0134"+
		"\u0005\u001c\u0000\u0000\u0134\u0135\u0005\u001d\u0000\u0000\u0135\u0141"+
		"\u0003J%\u0000\u0136\u0137\u0005T\u0000\u0000\u0137\u013c\u0003L&\u0000"+
		"\u0138\u0139\u0005K\u0000\u0000\u0139\u013b\u0003L&\u0000\u013a\u0138"+
		"\u0001\u0000\u0000\u0000\u013b\u013e\u0001\u0000\u0000\u0000\u013c\u013a"+
		"\u0001\u0000\u0000\u0000\u013c\u013d\u0001\u0000\u0000\u0000\u013d\u013f"+
		"\u0001\u0000\u0000\u0000\u013e\u013c\u0001\u0000\u0000\u0000\u013f\u0140"+
		"\u0005[\u0000\u0000\u0140\u0142\u0001\u0000\u0000\u0000\u0141\u0136\u0001"+
		"\u0000\u0000\u0000\u0141\u0142\u0001\u0000\u0000\u0000\u0142\u0143\u0001"+
		"\u0000\u0000\u0000\u0143\u0144\u00057\u0000\u0000\u0144\u0145\u0005T\u0000"+
		"\u0000\u0145\u0146\u00030\u0018\u0000\u0146\u0150\u0005[\u0000\u0000\u0147"+
		"\u0148\u00059\u0000\u0000\u0148\u014d\u0003.\u0017\u0000\u0149\u014a\u0005"+
		"K\u0000\u0000\u014a\u014c\u0003.\u0017\u0000\u014b\u0149\u0001\u0000\u0000"+
		"\u0000\u014c\u014f\u0001\u0000\u0000\u0000\u014d\u014b\u0001\u0000\u0000"+
		"\u0000\u014d\u014e\u0001\u0000\u0000\u0000\u014e\u0151\u0001\u0000\u0000"+
		"\u0000\u014f\u014d\u0001\u0000\u0000\u0000\u0150\u0147\u0001\u0000\u0000"+
		"\u0000\u0150\u0151\u0001\u0000\u0000\u0000\u0151\u0152\u0001\u0000\u0000"+
		"\u0000\u0152\u0153\u0005\\\u0000\u0000\u0153\u001d\u0001\u0000\u0000\u0000"+
		"\u0154\u0155\u0005,\u0000\u0000\u0155\u0156\u0003 \u0010\u0000\u0156\u0157"+
		"\u0005\u0017\u0000\u0000\u0157\u015a\u0003V+\u0000\u0158\u0159\u00058"+
		"\u0000\u0000\u0159\u015b\u0003D\"\u0000\u015a\u0158\u0001\u0000\u0000"+
		"\u0000\u015a\u015b\u0001\u0000\u0000\u0000\u015b\u0166\u0001\u0000\u0000"+
		"\u0000\u015c\u015d\u0005\u0018\u0000\u0000\u015d\u015e\u0005\b\u0000\u0000"+
		"\u015e\u0163\u0003$\u0012\u0000\u015f\u0160\u0005K\u0000\u0000\u0160\u0162"+
		"\u0003$\u0012\u0000\u0161\u015f\u0001\u0000\u0000\u0000\u0162\u0165\u0001"+
		"\u0000\u0000\u0000\u0163\u0161\u0001\u0000\u0000\u0000\u0163\u0164\u0001"+
		"\u0000\u0000\u0000\u0164\u0167\u0001\u0000\u0000\u0000\u0165\u0163\u0001"+
		"\u0000\u0000\u0000\u0166\u015c\u0001\u0000\u0000\u0000\u0166\u0167\u0001"+
		"\u0000\u0000\u0000\u0167\u0172\u0001\u0000\u0000\u0000\u0168\u0169\u0005"+
		"%\u0000\u0000\u0169\u016a\u0005\b\u0000\u0000\u016a\u016f\u0003*\u0015"+
		"\u0000\u016b\u016c\u0005K\u0000\u0000\u016c\u016e\u0003*\u0015\u0000\u016d"+
		"\u016b\u0001\u0000\u0000\u0000\u016e\u0171\u0001\u0000\u0000\u0000\u016f"+
		"\u016d\u0001\u0000\u0000\u0000\u016f\u0170\u0001\u0000\u0000\u0000\u0170"+
		"\u0173\u0001\u0000\u0000\u0000\u0171\u016f\u0001\u0000\u0000\u0000\u0172"+
		"\u0168\u0001\u0000\u0000\u0000\u0172\u0173\u0001\u0000\u0000\u0000\u0173"+
		"\u0176\u0001\u0000\u0000\u0000\u0174\u0175\u0005\u001e\u0000\u0000\u0175"+
		"\u0177\u0005b\u0000\u0000\u0176\u0174\u0001\u0000\u0000\u0000\u0176\u0177"+
		"\u0001\u0000\u0000\u0000\u0177\u0178\u0001\u0000\u0000\u0000\u0178\u0179"+
		"\u0005\\\u0000\u0000\u0179\u001f\u0001\u0000\u0000\u0000\u017a\u0184\u0005"+
		"V\u0000\u0000\u017b\u0180\u0003\"\u0011\u0000\u017c\u017d\u0005K\u0000"+
		"\u0000\u017d\u017f\u0003\"\u0011\u0000\u017e\u017c\u0001\u0000\u0000\u0000"+
		"\u017f\u0182\u0001\u0000\u0000\u0000\u0180\u017e\u0001\u0000\u0000\u0000"+
		"\u0180\u0181\u0001\u0000\u0000\u0000\u0181\u0184\u0001\u0000\u0000\u0000"+
		"\u0182\u0180\u0001\u0000\u0000\u0000\u0183\u017a\u0001\u0000\u0000\u0000"+
		"\u0183\u017b\u0001\u0000\u0000\u0000\u0184!\u0001\u0000\u0000\u0000\u0185"+
		"\u0188\u0003$\u0012\u0000\u0186\u0187\u0005\u0005\u0000\u0000\u0187\u0189"+
		"\u0003V+\u0000\u0188\u0186\u0001\u0000\u0000\u0000\u0188\u0189\u0001\u0000"+
		"\u0000\u0000\u0189#\u0001\u0000\u0000\u0000\u018a\u018d\u0003&\u0013\u0000"+
		"\u018b\u018d\u0003,\u0016\u0000\u018c\u018a\u0001\u0000\u0000\u0000\u018c"+
		"\u018b\u0001\u0000\u0000\u0000\u018d%\u0001\u0000\u0000\u0000\u018e\u018f"+
		"\u0003V+\u0000\u018f\u0199\u0005T\u0000\u0000\u0190\u019a\u0005V\u0000"+
		"\u0000\u0191\u0196\u0003(\u0014\u0000\u0192\u0193\u0005K\u0000\u0000\u0193"+
		"\u0195\u0003(\u0014\u0000\u0194\u0192\u0001\u0000\u0000\u0000\u0195\u0198"+
		"\u0001\u0000\u0000\u0000\u0196\u0194\u0001\u0000\u0000\u0000\u0196\u0197"+
		"\u0001\u0000\u0000\u0000\u0197\u019a\u0001\u0000\u0000\u0000\u0198\u0196"+
		"\u0001\u0000\u0000\u0000\u0199\u0190\u0001\u0000\u0000\u0000\u0199\u0191"+
		"\u0001\u0000\u0000\u0000\u0199\u019a\u0001\u0000\u0000\u0000\u019a\u019b"+
		"\u0001\u0000\u0000\u0000\u019b\u019c\u0005[\u0000\u0000\u019c\'\u0001"+
		"\u0000\u0000\u0000\u019d\u01a2\u0003$\u0012\u0000\u019e\u01a2\u0005c\u0000"+
		"\u0000\u019f\u01a2\u0003<\u001e\u0000\u01a0\u01a2\u00034\u001a\u0000\u01a1"+
		"\u019d\u0001\u0000\u0000\u0000\u01a1\u019e\u0001\u0000\u0000\u0000\u01a1"+
		"\u019f\u0001\u0000\u0000\u0000\u01a1\u01a0\u0001\u0000\u0000\u0000\u01a2"+
		")\u0001\u0000\u0000\u0000\u01a3\u01a5\u0003,\u0016\u0000\u01a4\u01a6\u0007"+
		"\u0002\u0000\u0000\u01a5\u01a4\u0001\u0000\u0000\u0000\u01a5\u01a6\u0001"+
		"\u0000\u0000\u0000\u01a6+\u0001\u0000\u0000\u0000\u01a7\u01ac\u0003V+"+
		"\u0000\u01a8\u01a9\u0005M\u0000\u0000\u01a9\u01ab\u0003V+\u0000\u01aa"+
		"\u01a8\u0001\u0000\u0000\u0000\u01ab\u01ae\u0001\u0000\u0000\u0000\u01ac"+
		"\u01aa\u0001\u0000\u0000\u0000\u01ac\u01ad\u0001\u0000\u0000\u0000\u01ad"+
		"-\u0001\u0000\u0000\u0000\u01ae\u01ac\u0001\u0000\u0000\u0000\u01af\u01b5"+
		"\u0005(\u0000\u0000\u01b0\u01b1\u0005*\u0000\u0000\u01b1\u01b5\u0005c"+
		"\u0000\u0000\u01b2\u01b3\u0005\u0012\u0000\u0000\u01b3\u01b5\u0005c\u0000"+
		"\u0000\u01b4\u01af\u0001\u0000\u0000\u0000\u01b4\u01b0\u0001\u0000\u0000"+
		"\u0000\u01b4\u01b2\u0001\u0000\u0000\u0000\u01b5/\u0001\u0000\u0000\u0000"+
		"\u01b6\u01bb\u00032\u0019\u0000\u01b7\u01b8\u0005K\u0000\u0000\u01b8\u01ba"+
		"\u00032\u0019\u0000\u01b9\u01b7\u0001\u0000\u0000\u0000\u01ba\u01bd\u0001"+
		"\u0000\u0000\u0000\u01bb\u01b9\u0001\u0000\u0000\u0000\u01bb\u01bc\u0001"+
		"\u0000\u0000\u0000\u01bc1\u0001\u0000\u0000\u0000\u01bd\u01bb\u0001\u0000"+
		"\u0000\u0000\u01be\u01c6\u0005c\u0000\u0000\u01bf\u01c6\u0003<\u001e\u0000"+
		"\u01c0\u01c6\u0005]\u0000\u0000\u01c1\u01c6\u0005_\u0000\u0000\u01c2\u01c6"+
		"\u00034\u001a\u0000\u01c3\u01c6\u00036\u001b\u0000\u01c4\u01c6\u0003:"+
		"\u001d\u0000\u01c5\u01be\u0001\u0000\u0000\u0000\u01c5\u01bf\u0001\u0000"+
		"\u0000\u0000\u01c5\u01c0\u0001\u0000\u0000\u0000\u01c5\u01c1\u0001\u0000"+
		"\u0000\u0000\u01c5\u01c2\u0001\u0000\u0000\u0000\u01c5\u01c3\u0001\u0000"+
		"\u0000\u0000\u01c5\u01c4\u0001\u0000\u0000\u0000\u01c63\u0001\u0000\u0000"+
		"\u0000\u01c7\u01c8\u0005J\u0000\u0000\u01c8\u01c9\u0003V+\u0000\u01c9"+
		"5\u0001\u0000\u0000\u0000\u01ca\u01d3\u0005P\u0000\u0000\u01cb\u01d0\u0003"+
		"8\u001c\u0000\u01cc\u01cd\u0005K\u0000\u0000\u01cd\u01cf\u00038\u001c"+
		"\u0000\u01ce\u01cc\u0001\u0000\u0000\u0000\u01cf\u01d2\u0001\u0000\u0000"+
		"\u0000\u01d0\u01ce\u0001\u0000\u0000\u0000\u01d0\u01d1\u0001\u0000\u0000"+
		"\u0000\u01d1\u01d4\u0001\u0000\u0000\u0000\u01d2\u01d0\u0001\u0000\u0000"+
		"\u0000\u01d3\u01cb\u0001\u0000\u0000\u0000\u01d3\u01d4\u0001\u0000\u0000"+
		"\u0000\u01d4\u01d5\u0001\u0000\u0000\u0000\u01d5\u01d6\u0005Y\u0000\u0000"+
		"\u01d67\u0001\u0000\u0000\u0000\u01d7\u01da\u0003V+\u0000\u01d8\u01da"+
		"\u0005c\u0000\u0000\u01d9\u01d7\u0001\u0000\u0000\u0000\u01d9\u01d8\u0001"+
		"\u0000\u0000\u0000\u01da\u01db\u0001\u0000\u0000\u0000\u01db\u01dc\u0005"+
		"J\u0000\u0000\u01dc\u01dd\u00032\u0019\u0000\u01dd9\u0001\u0000\u0000"+
		"\u0000\u01de\u01e7\u0005Q\u0000\u0000\u01df\u01e4\u00032\u0019\u0000\u01e0"+
		"\u01e1\u0005K\u0000\u0000\u01e1\u01e3\u00032\u0019\u0000\u01e2\u01e0\u0001"+
		"\u0000\u0000\u0000\u01e3\u01e6\u0001\u0000\u0000\u0000\u01e4\u01e2\u0001"+
		"\u0000\u0000\u0000\u01e4\u01e5\u0001\u0000\u0000\u0000\u01e5\u01e8\u0001"+
		"\u0000\u0000\u0000\u01e6\u01e4\u0001\u0000\u0000\u0000\u01e7\u01df\u0001"+
		"\u0000\u0000\u0000\u01e7\u01e8\u0001\u0000\u0000\u0000\u01e8\u01e9\u0001"+
		"\u0000\u0000\u0000\u01e9\u01ea\u0005Z\u0000\u0000\u01ea;\u0001\u0000\u0000"+
		"\u0000\u01eb\u01ed\u0005U\u0000\u0000\u01ec\u01eb\u0001\u0000\u0000\u0000"+
		"\u01ec\u01ed\u0001\u0000\u0000\u0000\u01ed\u01ee\u0001\u0000\u0000\u0000"+
		"\u01ee\u01ef\u0007\u0003\u0000\u0000\u01ef=\u0001\u0000\u0000\u0000\u01f0"+
		"\u01f1\u0003V+\u0000\u01f1\u01f2\u0005I\u0000\u0000\u01f2\u01f3\u0003"+
		"@ \u0000\u01f3?\u0001\u0000\u0000\u0000\u01f4\u01fe\u00032\u0019\u0000"+
		"\u01f5\u01f6\u0003V+\u0000\u01f6\u01f7\u0003B!\u0000\u01f7\u01f8\u0003"+
		"@ \u0000\u01f8\u01fe\u0001\u0000\u0000\u0000\u01f9\u01fa\u0005T\u0000"+
		"\u0000\u01fa\u01fb\u0003@ \u0000\u01fb\u01fc\u0005[\u0000\u0000\u01fc"+
		"\u01fe\u0001\u0000\u0000\u0000\u01fd\u01f4\u0001\u0000\u0000\u0000\u01fd"+
		"\u01f5\u0001\u0000\u0000\u0000\u01fd\u01f9\u0001\u0000\u0000\u0000\u01fe"+
		"A\u0001\u0000\u0000\u0000\u01ff\u0200\u0007\u0004\u0000\u0000\u0200C\u0001"+
		"\u0000\u0000\u0000\u0201\u0202\u0006\"\uffff\uffff\u0000\u0202\u0208\u0003"+
		"F#\u0000\u0203\u0204\u0005T\u0000\u0000\u0204\u0205\u0003D\"\u0000\u0205"+
		"\u0206\u0005[\u0000\u0000\u0206\u0208\u0001\u0000\u0000\u0000\u0207\u0201"+
		"\u0001\u0000\u0000\u0000\u0207\u0203\u0001\u0000\u0000\u0000\u0208\u0211"+
		"\u0001\u0000\u0000\u0000\u0209\u020a\n\u0002\u0000\u0000\u020a\u020b\u0005"+
		"\u0004\u0000\u0000\u020b\u0210\u0003D\"\u0003\u020c\u020d\n\u0001\u0000"+
		"\u0000\u020d\u020e\u0005$\u0000\u0000\u020e\u0210\u0003D\"\u0002\u020f"+
		"\u0209\u0001\u0000\u0000\u0000\u020f\u020c\u0001\u0000\u0000\u0000\u0210"+
		"\u0213\u0001\u0000\u0000\u0000\u0211\u020f\u0001\u0000\u0000\u0000\u0211"+
		"\u0212\u0001\u0000\u0000\u0000\u0212E\u0001\u0000\u0000\u0000\u0213\u0211"+
		"\u0001\u0000\u0000\u0000\u0214\u0215\u0003,\u0016\u0000\u0215\u021a\u0003"+
		"H$\u0000\u0216\u021b\u00034\u001a\u0000\u0217\u021b\u0005c\u0000\u0000"+
		"\u0218\u021b\u0003<\u001e\u0000\u0219\u021b\u0005]\u0000\u0000\u021a\u0216"+
		"\u0001\u0000\u0000\u0000\u021a\u0217\u0001\u0000\u0000\u0000\u021a\u0218"+
		"\u0001\u0000\u0000\u0000\u021a\u0219\u0001\u0000\u0000\u0000\u021bG\u0001"+
		"\u0000\u0000\u0000\u021c\u021d\u0007\u0005\u0000\u0000\u021dI\u0001\u0000"+
		"\u0000\u0000\u021e\u021f\u0003V+\u0000\u021fK\u0001\u0000\u0000\u0000"+
		"\u0220\u0221\u0003V+\u0000\u0221M\u0001\u0000\u0000\u0000\u0222\u0223"+
		"\u0003V+\u0000\u0223\u0224\u0003R)\u0000\u0224O\u0001\u0000\u0000\u0000"+
		"\u0225\u0226\u0003V+\u0000\u0226\u0227\u0005T\u0000\u0000\u0227\u022c"+
		"\u0003N\'\u0000\u0228\u0229\u0005K\u0000\u0000\u0229\u022b\u0003N\'\u0000"+
		"\u022a\u0228\u0001\u0000\u0000\u0000\u022b\u022e\u0001\u0000\u0000\u0000"+
		"\u022c\u022a\u0001\u0000\u0000\u0000\u022c\u022d\u0001\u0000\u0000\u0000"+
		"\u022d\u022f\u0001\u0000\u0000\u0000\u022e\u022c\u0001\u0000\u0000\u0000"+
		"\u022f\u0230\u0005[\u0000\u0000\u0230Q\u0001\u0000\u0000\u0000\u0231\u0292"+
		"\u0005A\u0000\u0000\u0232\u0235\u0005>\u0000\u0000\u0233\u0234\u0005!"+
		"\u0000\u0000\u0234\u0236\u0005\u001b\u0000\u0000\u0235\u0233\u0001\u0000"+
		"\u0000\u0000\u0235\u0236\u0001\u0000\u0000\u0000\u0236\u0292\u0001\u0000"+
		"\u0000\u0000\u0237\u023a\u0005=\u0000\u0000\u0238\u0239\u0005!\u0000\u0000"+
		"\u0239\u023b\u0005\u001b\u0000\u0000\u023a\u0238\u0001\u0000\u0000\u0000"+
		"\u023a\u023b\u0001\u0000\u0000\u0000\u023b\u0292\u0001\u0000\u0000\u0000"+
		"\u023c\u023f\u0005\u001f\u0000\u0000\u023d\u023e\u0005!\u0000\u0000\u023e"+
		"\u0240\u0005\u001b\u0000\u0000\u023f\u023d\u0001\u0000\u0000\u0000\u023f"+
		"\u0240\u0001\u0000\u0000\u0000\u0240\u0292\u0001\u0000\u0000\u0000\u0241"+
		"\u0244\u0005\u0015\u0000\u0000\u0242\u0243\u0005!\u0000\u0000\u0243\u0245"+
		"\u0005\u001b\u0000\u0000\u0244\u0242\u0001\u0000\u0000\u0000\u0244\u0245"+
		"\u0001\u0000\u0000\u0000\u0245\u0292\u0001\u0000\u0000\u0000\u0246\u0249"+
		"\u0005\u0013\u0000\u0000\u0247\u0248\u0005!\u0000\u0000\u0248\u024a\u0005"+
		"\u001b\u0000\u0000\u0249\u0247\u0001\u0000\u0000\u0000\u0249\u024a\u0001"+
		"\u0000\u0000\u0000\u024a\u0292\u0001\u0000\u0000\u0000\u024b\u024e\u0005"+
		"<\u0000\u0000\u024c\u024d\u0005!\u0000\u0000\u024d\u024f\u0005\u001b\u0000"+
		"\u0000\u024e\u024c\u0001\u0000\u0000\u0000\u024e\u024f\u0001\u0000\u0000"+
		"\u0000\u024f\u0292\u0001\u0000\u0000\u0000\u0250\u0253\u0005\u000f\u0000"+
		"\u0000\u0251\u0252\u0005!\u0000\u0000\u0252\u0254\u0005\u001b\u0000\u0000"+
		"\u0253\u0251\u0001\u0000\u0000\u0000\u0253\u0254\u0001\u0000\u0000\u0000"+
		"\u0254\u0292\u0001\u0000\u0000\u0000\u0255\u0258\u0005B\u0000\u0000\u0256"+
		"\u0257\u0005!\u0000\u0000\u0257\u0259\u0005\u001b\u0000\u0000\u0258\u0256"+
		"\u0001\u0000\u0000\u0000\u0258\u0259\u0001\u0000\u0000\u0000\u0259\u0292"+
		"\u0001\u0000\u0000\u0000\u025a\u0292\u0005C\u0000\u0000\u025b\u0292\u0005"+
		"D\u0000\u0000\u025c\u0292\u0005E\u0000\u0000\u025d\u0260\u0005F\u0000"+
		"\u0000\u025e\u025f\u0005!\u0000\u0000\u025f\u0261\u0005\u001b\u0000\u0000"+
		"\u0260\u025e\u0001\u0000\u0000\u0000\u0260\u0261\u0001\u0000\u0000\u0000"+
		"\u0261\u0292\u0001\u0000\u0000\u0000\u0262\u0265\u0005G\u0000\u0000\u0263"+
		"\u0264\u0005!\u0000\u0000\u0264\u0266\u0005\u001b\u0000\u0000\u0265\u0263"+
		"\u0001\u0000\u0000\u0000\u0265\u0266\u0001\u0000\u0000\u0000\u0266\u0292"+
		"\u0001\u0000\u0000\u0000\u0267\u0268\u0005@\u0000\u0000\u0268\u0269\u0005"+
		"T\u0000\u0000\u0269\u026e\u0003N\'\u0000\u026a\u026b\u0005K\u0000\u0000"+
		"\u026b\u026d\u0003N\'\u0000\u026c\u026a\u0001\u0000\u0000\u0000\u026d"+
		"\u0270\u0001\u0000\u0000\u0000\u026e\u026c\u0001\u0000\u0000\u0000\u026e"+
		"\u026f\u0001\u0000\u0000\u0000\u026f\u0271\u0001\u0000\u0000\u0000\u0270"+
		"\u026e\u0001\u0000\u0000\u0000\u0271\u0274\u0005[\u0000\u0000\u0272\u0273"+
		"\u0005!\u0000\u0000\u0273\u0275\u0005\u001b\u0000\u0000\u0274\u0272\u0001"+
		"\u0000\u0000\u0000\u0274\u0275\u0001\u0000\u0000\u0000\u0275\u0292\u0001"+
		"\u0000\u0000\u0000\u0276\u0277\u0005?\u0000\u0000\u0277\u0278\u0005T\u0000"+
		"\u0000\u0278\u027d\u0003N\'\u0000\u0279\u027a\u0005K\u0000\u0000\u027a"+
		"\u027c\u0003N\'\u0000\u027b\u0279\u0001\u0000\u0000\u0000\u027c\u027f"+
		"\u0001\u0000\u0000\u0000\u027d\u027b\u0001\u0000\u0000\u0000\u027d\u027e"+
		"\u0001\u0000\u0000\u0000\u027e\u0280\u0001\u0000\u0000\u0000\u027f\u027d"+
		"\u0001\u0000\u0000\u0000\u0280\u0281\u0005[\u0000\u0000\u0281\u0292\u0001"+
		"\u0000\u0000\u0000\u0282\u0283\u0005H\u0000\u0000\u0283\u0284\u0005T\u0000"+
		"\u0000\u0284\u0289\u0003P(\u0000\u0285\u0286\u0005K\u0000\u0000\u0286"+
		"\u0288\u0003P(\u0000\u0287\u0285\u0001\u0000\u0000\u0000\u0288\u028b\u0001"+
		"\u0000\u0000\u0000\u0289\u0287\u0001\u0000\u0000\u0000\u0289\u028a\u0001"+
		"\u0000\u0000\u0000\u028a\u028c\u0001\u0000\u0000\u0000\u028b\u0289\u0001"+
		"\u0000\u0000\u0000\u028c\u028f\u0005[\u0000\u0000\u028d\u028e\u0005!\u0000"+
		"\u0000\u028e\u0290\u0005\u001b\u0000\u0000\u028f\u028d\u0001\u0000\u0000"+
		"\u0000\u028f\u0290\u0001\u0000\u0000\u0000\u0290\u0292\u0001\u0000\u0000"+
		"\u0000\u0291\u0231\u0001\u0000\u0000\u0000\u0291\u0232\u0001\u0000\u0000"+
		"\u0000\u0291\u0237\u0001\u0000\u0000\u0000\u0291\u023c\u0001\u0000\u0000"+
		"\u0000\u0291\u0241\u0001\u0000\u0000\u0000\u0291\u0246\u0001\u0000\u0000"+
		"\u0000\u0291\u024b\u0001\u0000\u0000\u0000\u0291\u0250\u0001\u0000\u0000"+
		"\u0000\u0291\u0255\u0001\u0000\u0000\u0000\u0291\u025a\u0001\u0000\u0000"+
		"\u0000\u0291\u025b\u0001\u0000\u0000\u0000\u0291\u025c\u0001\u0000\u0000"+
		"\u0000\u0291\u025d\u0001\u0000\u0000\u0000\u0291\u0262\u0001\u0000\u0000"+
		"\u0000\u0291\u0267\u0001\u0000\u0000\u0000\u0291\u0276\u0001\u0000\u0000"+
		"\u0000\u0291\u0282\u0001\u0000\u0000\u0000\u0292S\u0001\u0000\u0000\u0000"+
		"\u0293\u0294\u0005d\u0000\u0000\u0294U\u0001\u0000\u0000\u0000\u0295\u0299"+
		"\u0005`\u0000\u0000\u0296\u0299\u0005a\u0000\u0000\u0297\u0299\u0003X"+
		",\u0000\u0298\u0295\u0001\u0000\u0000\u0000\u0298\u0296\u0001\u0000\u0000"+
		"\u0000\u0298\u0297\u0001\u0000\u0000\u0000\u0299W\u0001\u0000\u0000\u0000"+
		"\u029a\u029b\u0007\u0006\u0000\u0000\u029bY\u0001\u0000\u0000\u0000E]"+
		"mt}\u008d\u0098\u009d\u00a7\u00b3\u00c8\u00cd\u00d8\u00e8\u00f3\u0113"+
		"\u011d\u0124\u012f\u013c\u0141\u014d\u0150\u015a\u0163\u0166\u016f\u0172"+
		"\u0176\u0180\u0183\u0188\u018c\u0196\u0199\u01a1\u01a5\u01ac\u01b4\u01bb"+
		"\u01c5\u01d0\u01d3\u01d9\u01e4\u01e7\u01ec\u01fd\u0207\u020f\u0211\u021a"+
		"\u022c\u0235\u023a\u023f\u0244\u0249\u024e\u0253\u0258\u0260\u0265\u026e"+
		"\u0274\u027d\u0289\u028f\u0291\u0298";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}