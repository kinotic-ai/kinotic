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
		ABORT=1, ADD=2, ALTER=3, AND=4, AUTO=5, COLUMN=6, COMPONENT=7, CONFLICTS=8, 
		CREATE=9, DATA=10, DATA_RETENTION=11, DATE=12, DELETE=13, DOUBLE=14, EXISTS=15, 
		FLOAT=16, FOR=17, FROM=18, IF=19, INDEX=20, INDEXED=21, INSERT=22, INTO=23, 
		LONG=24, MAX_DOCS=25, NOT=26, NUMBER_OF_REPLICAS=27, NUMBER_OF_SHARDS=28, 
		OR=29, PROCEED=30, QUERY=31, REFRESH=32, REINDEX=33, SCRIPT=34, SET=35, 
		SIZE=36, SLICES=37, SOURCE_FIELDS=38, STREAM=39, TABLE=40, TEMPLATE=41, 
		TIME_REFERENCE=42, UPDATE=43, USING=44, VALUES=45, WHERE=46, WITH=47, 
		WAIT=48, TRUE=49, FALSE=50, SKIP_IF_NO_SOURCE=51, BOOLEAN=52, INTEGER=53, 
		KEYWORD=54, NESTED=55, OBJECT=56, TEXT=57, JSON=58, BINARY=59, GEO_POINT=60, 
		GEO_SHAPE=61, UUID=62, DECIMAL=63, UNION=64, ASSIGN=65, COLON=66, COMMA=67, 
		DIVIDE=68, EQUALS=69, GREATER_THAN=70, GREATER_THAN_EQUALS=71, LBRACE=72, 
		LBRACKET=73, LESS_THAN=74, LESS_THAN_EQUALS=75, LPAREN=76, MINUS=77, MULTIPLY=78, 
		NOT_EQUALS=79, PLUS=80, RBRACE=81, RBRACKET=82, RPAREN=83, SEMICOLON=84, 
		BOOLEAN_LITERAL=85, DECIMAL_LITERAL=86, NULL_LITERAL=87, ID=88, INTEGER_LITERAL=89, 
		STRING=90, COMMENT=91, WS=92;
	public static final int
		RULE_migrations = 0, RULE_statement = 1, RULE_createTableStatement = 2, 
		RULE_createDataStreamStatement = 3, RULE_dataStreamOption = 4, RULE_createComponentTemplateStatement = 5, 
		RULE_createIndexTemplateStatement = 6, RULE_templatePart = 7, RULE_alterTableStatement = 8, 
		RULE_reindexStatement = 9, RULE_reindexOptions = 10, RULE_reindexOption = 11, 
		RULE_updateStatement = 12, RULE_deleteStatement = 13, RULE_insertStatement = 14, 
		RULE_valueList = 15, RULE_value = 16, RULE_namedParameter = 17, RULE_objectLiteral = 18, 
		RULE_objectField = 19, RULE_arrayLiteral = 20, RULE_numberLiteral = 21, 
		RULE_assignment = 22, RULE_expression = 23, RULE_operator = 24, RULE_whereClause = 25, 
		RULE_condition = 26, RULE_comparisonOperator = 27, RULE_tableName = 28, 
		RULE_columnName = 29, RULE_columnDefinition = 30, RULE_unionVariant = 31, 
		RULE_type = 32, RULE_comment = 33;
	private static String[] makeRuleNames() {
		return new String[] {
			"migrations", "statement", "createTableStatement", "createDataStreamStatement", 
			"dataStreamOption", "createComponentTemplateStatement", "createIndexTemplateStatement", 
			"templatePart", "alterTableStatement", "reindexStatement", "reindexOptions", 
			"reindexOption", "updateStatement", "deleteStatement", "insertStatement", 
			"valueList", "value", "namedParameter", "objectLiteral", "objectField", 
			"arrayLiteral", "numberLiteral", "assignment", "expression", "operator", 
			"whereClause", "condition", "comparisonOperator", "tableName", "columnName", 
			"columnDefinition", "unionVariant", "type", "comment"
		};
	}
	public static final String[] ruleNames = makeRuleNames();

	private static String[] makeLiteralNames() {
		return new String[] {
			null, "'ABORT'", "'ADD'", "'ALTER'", "'AND'", "'AUTO'", "'COLUMN'", "'COMPONENT'", 
			"'CONFLICTS'", "'CREATE'", "'DATA'", "'DATA_RETENTION'", "'DATE'", "'DELETE'", 
			"'DOUBLE'", "'EXISTS'", "'FLOAT'", "'FOR'", "'FROM'", "'IF'", "'INDEX'", 
			"'INDEXED'", "'INSERT'", "'INTO'", "'LONG'", "'MAX_DOCS'", "'NOT'", "'NUMBER_OF_REPLICAS'", 
			"'NUMBER_OF_SHARDS'", "'OR'", "'PROCEED'", "'QUERY'", "'REFRESH'", "'REINDEX'", 
			"'SCRIPT'", "'SET'", "'SIZE'", "'SLICES'", "'SOURCE_FIELDS'", "'STREAM'", 
			"'TABLE'", "'TEMPLATE'", "'TIME_REFERENCE'", "'UPDATE'", "'USING'", "'VALUES'", 
			"'WHERE'", "'WITH'", "'WAIT'", "'TRUE'", "'FALSE'", "'SKIP_IF_NO_SOURCE'", 
			"'BOOLEAN'", "'INTEGER'", "'KEYWORD'", "'NESTED'", "'OBJECT'", "'TEXT'", 
			"'JSON'", "'BINARY'", "'GEO_POINT'", "'GEO_SHAPE'", "'UUID'", "'DECIMAL'", 
			"'UNION'", "'='", "':'", "','", "'/'", "'=='", "'>'", "'>='", "'{'", 
			"'['", "'<'", "'<='", "'('", "'-'", "'*'", "'!='", "'+'", "'}'", "']'", 
			"')'", "';'", null, null, "'null'"
		};
	}
	private static final String[] _LITERAL_NAMES = makeLiteralNames();
	private static String[] makeSymbolicNames() {
		return new String[] {
			null, "ABORT", "ADD", "ALTER", "AND", "AUTO", "COLUMN", "COMPONENT", 
			"CONFLICTS", "CREATE", "DATA", "DATA_RETENTION", "DATE", "DELETE", "DOUBLE", 
			"EXISTS", "FLOAT", "FOR", "FROM", "IF", "INDEX", "INDEXED", "INSERT", 
			"INTO", "LONG", "MAX_DOCS", "NOT", "NUMBER_OF_REPLICAS", "NUMBER_OF_SHARDS", 
			"OR", "PROCEED", "QUERY", "REFRESH", "REINDEX", "SCRIPT", "SET", "SIZE", 
			"SLICES", "SOURCE_FIELDS", "STREAM", "TABLE", "TEMPLATE", "TIME_REFERENCE", 
			"UPDATE", "USING", "VALUES", "WHERE", "WITH", "WAIT", "TRUE", "FALSE", 
			"SKIP_IF_NO_SOURCE", "BOOLEAN", "INTEGER", "KEYWORD", "NESTED", "OBJECT", 
			"TEXT", "JSON", "BINARY", "GEO_POINT", "GEO_SHAPE", "UUID", "DECIMAL", 
			"UNION", "ASSIGN", "COLON", "COMMA", "DIVIDE", "EQUALS", "GREATER_THAN", 
			"GREATER_THAN_EQUALS", "LBRACE", "LBRACKET", "LESS_THAN", "LESS_THAN_EQUALS", 
			"LPAREN", "MINUS", "MULTIPLY", "NOT_EQUALS", "PLUS", "RBRACE", "RBRACKET", 
			"RPAREN", "SEMICOLON", "BOOLEAN_LITERAL", "DECIMAL_LITERAL", "NULL_LITERAL", 
			"ID", "INTEGER_LITERAL", "STRING", "COMMENT", "WS"
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
			setState(71);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while ((((_la) & ~0x3f) == 0 && ((1L << _la) & 8804687159816L) != 0) || _la==COMMENT) {
				{
				{
				setState(68);
				statement();
				}
				}
				setState(73);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(74);
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
			setState(86);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,1,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(76);
				createTableStatement();
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(77);
				createDataStreamStatement();
				}
				break;
			case 3:
				enterOuterAlt(_localctx, 3);
				{
				setState(78);
				createComponentTemplateStatement();
				}
				break;
			case 4:
				enterOuterAlt(_localctx, 4);
				{
				setState(79);
				createIndexTemplateStatement();
				}
				break;
			case 5:
				enterOuterAlt(_localctx, 5);
				{
				setState(80);
				alterTableStatement();
				}
				break;
			case 6:
				enterOuterAlt(_localctx, 6);
				{
				setState(81);
				reindexStatement();
				}
				break;
			case 7:
				enterOuterAlt(_localctx, 7);
				{
				setState(82);
				updateStatement();
				}
				break;
			case 8:
				enterOuterAlt(_localctx, 8);
				{
				setState(83);
				deleteStatement();
				}
				break;
			case 9:
				enterOuterAlt(_localctx, 9);
				{
				setState(84);
				insertStatement();
				}
				break;
			case 10:
				enterOuterAlt(_localctx, 10);
				{
				setState(85);
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
		public TerminalNode ID() { return getToken(KinoticSQLParser.ID, 0); }
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
			setState(88);
			match(CREATE);
			setState(89);
			match(TABLE);
			setState(93);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==IF) {
				{
				setState(90);
				match(IF);
				setState(91);
				match(NOT);
				setState(92);
				match(EXISTS);
				}
			}

			setState(95);
			match(ID);
			setState(96);
			match(LPAREN);
			setState(97);
			columnDefinition();
			setState(102);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==COMMA) {
				{
				{
				setState(98);
				match(COMMA);
				setState(99);
				columnDefinition();
				}
				}
				setState(104);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(105);
			match(RPAREN);
			setState(106);
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
		public TerminalNode ID() { return getToken(KinoticSQLParser.ID, 0); }
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
			setState(108);
			match(CREATE);
			setState(109);
			match(DATA);
			setState(110);
			match(STREAM);
			setState(111);
			match(ID);
			setState(112);
			match(LPAREN);
			setState(113);
			columnDefinition();
			setState(118);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==COMMA) {
				{
				{
				setState(114);
				match(COMMA);
				setState(115);
				columnDefinition();
				}
				}
				setState(120);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(121);
			match(RPAREN);
			setState(134);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==WITH) {
				{
				setState(122);
				match(WITH);
				setState(123);
				match(LPAREN);
				setState(124);
				dataStreamOption();
				setState(129);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while (_la==COMMA) {
					{
					{
					setState(125);
					match(COMMA);
					setState(126);
					dataStreamOption();
					}
					}
					setState(131);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}
				setState(132);
				match(RPAREN);
				}
			}

			setState(136);
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
			setState(144);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case DATA_RETENTION:
				enterOuterAlt(_localctx, 1);
				{
				setState(138);
				match(DATA_RETENTION);
				setState(139);
				match(ASSIGN);
				setState(140);
				match(STRING);
				}
				break;
			case TIME_REFERENCE:
				enterOuterAlt(_localctx, 2);
				{
				setState(141);
				match(TIME_REFERENCE);
				setState(142);
				match(ASSIGN);
				setState(143);
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
		public TerminalNode ID() { return getToken(KinoticSQLParser.ID, 0); }
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
			setState(146);
			match(CREATE);
			setState(147);
			match(COMPONENT);
			setState(148);
			match(TEMPLATE);
			setState(149);
			match(ID);
			setState(150);
			match(LPAREN);
			setState(151);
			templatePart();
			setState(156);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==COMMA) {
				{
				{
				setState(152);
				match(COMMA);
				setState(153);
				templatePart();
				}
				}
				setState(158);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(159);
			match(RPAREN);
			setState(160);
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
		public TerminalNode ID() { return getToken(KinoticSQLParser.ID, 0); }
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
			setState(162);
			match(CREATE);
			setState(163);
			match(INDEX);
			setState(164);
			match(TEMPLATE);
			setState(165);
			match(ID);
			setState(166);
			match(FOR);
			setState(167);
			match(STRING);
			setState(168);
			match(USING);
			setState(169);
			match(STRING);
			setState(182);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==WITH) {
				{
				setState(170);
				match(WITH);
				setState(171);
				match(LPAREN);
				setState(172);
				templatePart();
				setState(177);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while (_la==COMMA) {
					{
					{
					setState(173);
					match(COMMA);
					setState(174);
					templatePart();
					}
					}
					setState(179);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}
				setState(180);
				match(RPAREN);
				}
			}

			setState(184);
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
			setState(193);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case NUMBER_OF_SHARDS:
				enterOuterAlt(_localctx, 1);
				{
				setState(186);
				match(NUMBER_OF_SHARDS);
				setState(187);
				match(ASSIGN);
				setState(188);
				match(INTEGER_LITERAL);
				}
				break;
			case NUMBER_OF_REPLICAS:
				enterOuterAlt(_localctx, 2);
				{
				setState(189);
				match(NUMBER_OF_REPLICAS);
				setState(190);
				match(ASSIGN);
				setState(191);
				match(INTEGER_LITERAL);
				}
				break;
			case ID:
				enterOuterAlt(_localctx, 3);
				{
				setState(192);
				columnDefinition();
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
	public static class AlterTableStatementContext extends ParserRuleContext {
		public TerminalNode ALTER() { return getToken(KinoticSQLParser.ALTER, 0); }
		public TerminalNode TABLE() { return getToken(KinoticSQLParser.TABLE, 0); }
		public List<TerminalNode> ID() { return getTokens(KinoticSQLParser.ID); }
		public TerminalNode ID(int i) {
			return getToken(KinoticSQLParser.ID, i);
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
			setState(195);
			match(ALTER);
			setState(196);
			match(TABLE);
			setState(197);
			match(ID);
			setState(198);
			match(ADD);
			setState(199);
			match(COLUMN);
			setState(200);
			match(ID);
			setState(201);
			type();
			setState(202);
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
		public List<TerminalNode> ID() { return getTokens(KinoticSQLParser.ID); }
		public TerminalNode ID(int i) {
			return getToken(KinoticSQLParser.ID, i);
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
			setState(204);
			match(REINDEX);
			setState(205);
			match(ID);
			setState(206);
			match(INTO);
			setState(207);
			match(ID);
			setState(209);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==WITH) {
				{
				setState(208);
				reindexOptions();
				}
			}

			setState(211);
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
			setState(213);
			match(WITH);
			setState(214);
			match(LPAREN);
			setState(215);
			reindexOption();
			setState(220);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==COMMA) {
				{
				{
				setState(216);
				match(COMMA);
				setState(217);
				reindexOption();
				}
				}
				setState(222);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(223);
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
		public TerminalNode TRUE() { return getToken(KinoticSQLParser.TRUE, 0); }
		public TerminalNode FALSE() { return getToken(KinoticSQLParser.FALSE, 0); }
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
			setState(252);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case CONFLICTS:
				enterOuterAlt(_localctx, 1);
				{
				setState(225);
				match(CONFLICTS);
				setState(226);
				match(ASSIGN);
				setState(227);
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
				setState(228);
				match(MAX_DOCS);
				setState(229);
				match(ASSIGN);
				setState(230);
				match(INTEGER_LITERAL);
				}
				break;
			case SLICES:
				enterOuterAlt(_localctx, 3);
				{
				setState(231);
				match(SLICES);
				setState(232);
				match(ASSIGN);
				setState(233);
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
				setState(234);
				match(SIZE);
				setState(235);
				match(ASSIGN);
				setState(236);
				match(INTEGER_LITERAL);
				}
				break;
			case SOURCE_FIELDS:
				enterOuterAlt(_localctx, 5);
				{
				setState(237);
				match(SOURCE_FIELDS);
				setState(238);
				match(ASSIGN);
				setState(239);
				match(STRING);
				}
				break;
			case QUERY:
				enterOuterAlt(_localctx, 6);
				{
				setState(240);
				match(QUERY);
				setState(241);
				match(ASSIGN);
				setState(242);
				match(STRING);
				}
				break;
			case SCRIPT:
				enterOuterAlt(_localctx, 7);
				{
				setState(243);
				match(SCRIPT);
				setState(244);
				match(ASSIGN);
				setState(245);
				match(STRING);
				}
				break;
			case WAIT:
				enterOuterAlt(_localctx, 8);
				{
				setState(246);
				match(WAIT);
				setState(247);
				match(ASSIGN);
				setState(248);
				_la = _input.LA(1);
				if ( !(_la==TRUE || _la==FALSE) ) {
				_errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				}
				break;
			case SKIP_IF_NO_SOURCE:
				enterOuterAlt(_localctx, 9);
				{
				setState(249);
				match(SKIP_IF_NO_SOURCE);
				setState(250);
				match(ASSIGN);
				setState(251);
				_la = _input.LA(1);
				if ( !(_la==TRUE || _la==FALSE) ) {
				_errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
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
	public static class UpdateStatementContext extends ParserRuleContext {
		public TerminalNode UPDATE() { return getToken(KinoticSQLParser.UPDATE, 0); }
		public TerminalNode ID() { return getToken(KinoticSQLParser.ID, 0); }
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
			setState(254);
			match(UPDATE);
			setState(255);
			match(ID);
			setState(256);
			match(SET);
			setState(257);
			assignment();
			setState(262);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==COMMA) {
				{
				{
				setState(258);
				match(COMMA);
				setState(259);
				assignment();
				}
				}
				setState(264);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(265);
			match(WHERE);
			setState(266);
			whereClause(0);
			setState(269);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==WITH) {
				{
				setState(267);
				match(WITH);
				setState(268);
				match(REFRESH);
				}
			}

			setState(271);
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
		public TerminalNode ID() { return getToken(KinoticSQLParser.ID, 0); }
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
			setState(273);
			match(DELETE);
			setState(274);
			match(FROM);
			setState(275);
			match(ID);
			setState(276);
			match(WHERE);
			setState(277);
			whereClause(0);
			setState(280);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==WITH) {
				{
				setState(278);
				match(WITH);
				setState(279);
				match(REFRESH);
				}
			}

			setState(282);
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
		public TerminalNode REFRESH() { return getToken(KinoticSQLParser.REFRESH, 0); }
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
			setState(284);
			match(INSERT);
			setState(285);
			match(INTO);
			setState(286);
			tableName();
			setState(298);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==LPAREN) {
				{
				setState(287);
				match(LPAREN);
				setState(288);
				columnName();
				setState(293);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while (_la==COMMA) {
					{
					{
					setState(289);
					match(COMMA);
					setState(290);
					columnName();
					}
					}
					setState(295);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}
				setState(296);
				match(RPAREN);
				}
			}

			setState(300);
			match(VALUES);
			setState(301);
			match(LPAREN);
			setState(302);
			valueList();
			setState(303);
			match(RPAREN);
			setState(306);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==WITH) {
				{
				setState(304);
				match(WITH);
				setState(305);
				match(REFRESH);
				}
			}

			setState(308);
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
		enterRule(_localctx, 30, RULE_valueList);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(310);
			value();
			setState(315);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==COMMA) {
				{
				{
				setState(311);
				match(COMMA);
				setState(312);
				value();
				}
				}
				setState(317);
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
		enterRule(_localctx, 32, RULE_value);
		try {
			setState(325);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case STRING:
				enterOuterAlt(_localctx, 1);
				{
				setState(318);
				match(STRING);
				}
				break;
			case MINUS:
			case DECIMAL_LITERAL:
			case INTEGER_LITERAL:
				enterOuterAlt(_localctx, 2);
				{
				setState(319);
				numberLiteral();
				}
				break;
			case BOOLEAN_LITERAL:
				enterOuterAlt(_localctx, 3);
				{
				setState(320);
				match(BOOLEAN_LITERAL);
				}
				break;
			case NULL_LITERAL:
				enterOuterAlt(_localctx, 4);
				{
				setState(321);
				match(NULL_LITERAL);
				}
				break;
			case COLON:
				enterOuterAlt(_localctx, 5);
				{
				setState(322);
				namedParameter();
				}
				break;
			case LBRACE:
				enterOuterAlt(_localctx, 6);
				{
				setState(323);
				objectLiteral();
				}
				break;
			case LBRACKET:
				enterOuterAlt(_localctx, 7);
				{
				setState(324);
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
		public TerminalNode ID() { return getToken(KinoticSQLParser.ID, 0); }
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
		enterRule(_localctx, 34, RULE_namedParameter);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(327);
			match(COLON);
			setState(328);
			match(ID);
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
		enterRule(_localctx, 36, RULE_objectLiteral);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(330);
			match(LBRACE);
			setState(339);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==ID || _la==STRING) {
				{
				setState(331);
				objectField();
				setState(336);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while (_la==COMMA) {
					{
					{
					setState(332);
					match(COMMA);
					setState(333);
					objectField();
					}
					}
					setState(338);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}
				}
			}

			setState(341);
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
		public TerminalNode ID() { return getToken(KinoticSQLParser.ID, 0); }
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
		enterRule(_localctx, 38, RULE_objectField);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(343);
			_la = _input.LA(1);
			if ( !(_la==ID || _la==STRING) ) {
			_errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			setState(344);
			match(COLON);
			setState(345);
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
		enterRule(_localctx, 40, RULE_arrayLiteral);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(347);
			match(LBRACKET);
			setState(356);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (((((_la - 66)) & ~0x3f) == 0 && ((1L << (_la - 66)) & 28838081L) != 0)) {
				{
				setState(348);
				value();
				setState(353);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while (_la==COMMA) {
					{
					{
					setState(349);
					match(COMMA);
					setState(350);
					value();
					}
					}
					setState(355);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}
				}
			}

			setState(358);
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
		enterRule(_localctx, 42, RULE_numberLiteral);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(361);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==MINUS) {
				{
				setState(360);
				match(MINUS);
				}
			}

			setState(363);
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
		public TerminalNode ID() { return getToken(KinoticSQLParser.ID, 0); }
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
		enterRule(_localctx, 44, RULE_assignment);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(365);
			match(ID);
			setState(366);
			match(ASSIGN);
			setState(367);
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
		public TerminalNode ID() { return getToken(KinoticSQLParser.ID, 0); }
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
		enterRule(_localctx, 46, RULE_expression);
		try {
			setState(378);
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
				setState(369);
				value();
				}
				break;
			case ID:
				enterOuterAlt(_localctx, 2);
				{
				setState(370);
				match(ID);
				setState(371);
				operator();
				setState(372);
				expression();
				}
				break;
			case LPAREN:
				enterOuterAlt(_localctx, 3);
				{
				setState(374);
				match(LPAREN);
				setState(375);
				expression();
				setState(376);
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
		public TerminalNode EQUALS() { return getToken(KinoticSQLParser.EQUALS, 0); }
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
		enterRule(_localctx, 48, RULE_operator);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(380);
			_la = _input.LA(1);
			if ( !(((((_la - 68)) & ~0x3f) == 0 && ((1L << (_la - 68)) & 5635L) != 0)) ) {
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
		int _startState = 50;
		enterRecursionRule(_localctx, 50, RULE_whereClause, _p);
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(388);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case ID:
				{
				setState(383);
				condition();
				}
				break;
			case LPAREN:
				{
				setState(384);
				match(LPAREN);
				setState(385);
				whereClause(0);
				setState(386);
				match(RPAREN);
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
			_ctx.stop = _input.LT(-1);
			setState(398);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,31,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					if ( _parseListeners!=null ) triggerExitRuleEvent();
					_prevctx = _localctx;
					{
					setState(396);
					_errHandler.sync(this);
					switch ( getInterpreter().adaptivePredict(_input,30,_ctx) ) {
					case 1:
						{
						_localctx = new WhereClauseContext(_parentctx, _parentState);
						pushNewRecursionContext(_localctx, _startState, RULE_whereClause);
						setState(390);
						if (!(precpred(_ctx, 2))) throw new FailedPredicateException(this, "precpred(_ctx, 2)");
						setState(391);
						match(AND);
						setState(392);
						whereClause(3);
						}
						break;
					case 2:
						{
						_localctx = new WhereClauseContext(_parentctx, _parentState);
						pushNewRecursionContext(_localctx, _startState, RULE_whereClause);
						setState(393);
						if (!(precpred(_ctx, 1))) throw new FailedPredicateException(this, "precpred(_ctx, 1)");
						setState(394);
						match(OR);
						setState(395);
						whereClause(2);
						}
						break;
					}
					} 
				}
				setState(400);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,31,_ctx);
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
		public TerminalNode ID() { return getToken(KinoticSQLParser.ID, 0); }
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
		enterRule(_localctx, 52, RULE_condition);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(401);
			match(ID);
			setState(402);
			comparisonOperator();
			setState(407);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case COLON:
				{
				setState(403);
				namedParameter();
				}
				break;
			case STRING:
				{
				setState(404);
				match(STRING);
				}
				break;
			case MINUS:
			case DECIMAL_LITERAL:
			case INTEGER_LITERAL:
				{
				setState(405);
				numberLiteral();
				}
				break;
			case BOOLEAN_LITERAL:
				{
				setState(406);
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
		public TerminalNode EQUALS() { return getToken(KinoticSQLParser.EQUALS, 0); }
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
		enterRule(_localctx, 54, RULE_comparisonOperator);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(409);
			_la = _input.LA(1);
			if ( !(((((_la - 69)) & ~0x3f) == 0 && ((1L << (_la - 69)) & 1127L) != 0)) ) {
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
		public TerminalNode ID() { return getToken(KinoticSQLParser.ID, 0); }
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
		enterRule(_localctx, 56, RULE_tableName);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(411);
			match(ID);
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
		public TerminalNode ID() { return getToken(KinoticSQLParser.ID, 0); }
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
		enterRule(_localctx, 58, RULE_columnName);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(413);
			match(ID);
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
		public TerminalNode ID() { return getToken(KinoticSQLParser.ID, 0); }
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
		enterRule(_localctx, 60, RULE_columnDefinition);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(415);
			match(ID);
			setState(416);
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
		public TerminalNode ID() { return getToken(KinoticSQLParser.ID, 0); }
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
		enterRule(_localctx, 62, RULE_unionVariant);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(418);
			match(ID);
			setState(419);
			match(LPAREN);
			setState(420);
			columnDefinition();
			setState(425);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==COMMA) {
				{
				{
				setState(421);
				match(COMMA);
				setState(422);
				columnDefinition();
				}
				}
				setState(427);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(428);
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
		enterRule(_localctx, 64, RULE_type);
		int _la;
		try {
			setState(526);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case TEXT:
				enterOuterAlt(_localctx, 1);
				{
				setState(430);
				match(TEXT);
				}
				break;
			case KEYWORD:
				enterOuterAlt(_localctx, 2);
				{
				setState(431);
				match(KEYWORD);
				setState(434);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==NOT) {
					{
					setState(432);
					match(NOT);
					setState(433);
					match(INDEXED);
					}
				}

				}
				break;
			case INTEGER:
				enterOuterAlt(_localctx, 3);
				{
				setState(436);
				match(INTEGER);
				setState(439);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==NOT) {
					{
					setState(437);
					match(NOT);
					setState(438);
					match(INDEXED);
					}
				}

				}
				break;
			case LONG:
				enterOuterAlt(_localctx, 4);
				{
				setState(441);
				match(LONG);
				setState(444);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==NOT) {
					{
					setState(442);
					match(NOT);
					setState(443);
					match(INDEXED);
					}
				}

				}
				break;
			case FLOAT:
				enterOuterAlt(_localctx, 5);
				{
				setState(446);
				match(FLOAT);
				setState(449);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==NOT) {
					{
					setState(447);
					match(NOT);
					setState(448);
					match(INDEXED);
					}
				}

				}
				break;
			case DOUBLE:
				enterOuterAlt(_localctx, 6);
				{
				setState(451);
				match(DOUBLE);
				setState(454);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==NOT) {
					{
					setState(452);
					match(NOT);
					setState(453);
					match(INDEXED);
					}
				}

				}
				break;
			case BOOLEAN:
				enterOuterAlt(_localctx, 7);
				{
				setState(456);
				match(BOOLEAN);
				setState(459);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==NOT) {
					{
					setState(457);
					match(NOT);
					setState(458);
					match(INDEXED);
					}
				}

				}
				break;
			case DATE:
				enterOuterAlt(_localctx, 8);
				{
				setState(461);
				match(DATE);
				setState(464);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==NOT) {
					{
					setState(462);
					match(NOT);
					setState(463);
					match(INDEXED);
					}
				}

				}
				break;
			case JSON:
				enterOuterAlt(_localctx, 9);
				{
				setState(466);
				match(JSON);
				setState(469);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==NOT) {
					{
					setState(467);
					match(NOT);
					setState(468);
					match(INDEXED);
					}
				}

				}
				break;
			case BINARY:
				enterOuterAlt(_localctx, 10);
				{
				setState(471);
				match(BINARY);
				}
				break;
			case GEO_POINT:
				enterOuterAlt(_localctx, 11);
				{
				setState(472);
				match(GEO_POINT);
				}
				break;
			case GEO_SHAPE:
				enterOuterAlt(_localctx, 12);
				{
				setState(473);
				match(GEO_SHAPE);
				}
				break;
			case UUID:
				enterOuterAlt(_localctx, 13);
				{
				setState(474);
				match(UUID);
				setState(477);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==NOT) {
					{
					setState(475);
					match(NOT);
					setState(476);
					match(INDEXED);
					}
				}

				}
				break;
			case DECIMAL:
				enterOuterAlt(_localctx, 14);
				{
				setState(479);
				match(DECIMAL);
				setState(482);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==NOT) {
					{
					setState(480);
					match(NOT);
					setState(481);
					match(INDEXED);
					}
				}

				}
				break;
			case OBJECT:
				enterOuterAlt(_localctx, 15);
				{
				setState(484);
				match(OBJECT);
				setState(485);
				match(LPAREN);
				setState(486);
				columnDefinition();
				setState(491);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while (_la==COMMA) {
					{
					{
					setState(487);
					match(COMMA);
					setState(488);
					columnDefinition();
					}
					}
					setState(493);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}
				setState(494);
				match(RPAREN);
				setState(497);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==NOT) {
					{
					setState(495);
					match(NOT);
					setState(496);
					match(INDEXED);
					}
				}

				}
				break;
			case NESTED:
				enterOuterAlt(_localctx, 16);
				{
				setState(499);
				match(NESTED);
				setState(500);
				match(LPAREN);
				setState(501);
				columnDefinition();
				setState(506);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while (_la==COMMA) {
					{
					{
					setState(502);
					match(COMMA);
					setState(503);
					columnDefinition();
					}
					}
					setState(508);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}
				setState(509);
				match(RPAREN);
				}
				break;
			case UNION:
				enterOuterAlt(_localctx, 17);
				{
				setState(511);
				match(UNION);
				setState(512);
				match(LPAREN);
				setState(513);
				unionVariant();
				setState(518);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while (_la==COMMA) {
					{
					{
					setState(514);
					match(COMMA);
					setState(515);
					unionVariant();
					}
					}
					setState(520);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}
				setState(521);
				match(RPAREN);
				setState(524);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==NOT) {
					{
					setState(522);
					match(NOT);
					setState(523);
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
		enterRule(_localctx, 66, RULE_comment);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(528);
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

	public boolean sempred(RuleContext _localctx, int ruleIndex, int predIndex) {
		switch (ruleIndex) {
		case 25:
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
		"\u0004\u0001\\\u0213\u0002\u0000\u0007\u0000\u0002\u0001\u0007\u0001\u0002"+
		"\u0002\u0007\u0002\u0002\u0003\u0007\u0003\u0002\u0004\u0007\u0004\u0002"+
		"\u0005\u0007\u0005\u0002\u0006\u0007\u0006\u0002\u0007\u0007\u0007\u0002"+
		"\b\u0007\b\u0002\t\u0007\t\u0002\n\u0007\n\u0002\u000b\u0007\u000b\u0002"+
		"\f\u0007\f\u0002\r\u0007\r\u0002\u000e\u0007\u000e\u0002\u000f\u0007\u000f"+
		"\u0002\u0010\u0007\u0010\u0002\u0011\u0007\u0011\u0002\u0012\u0007\u0012"+
		"\u0002\u0013\u0007\u0013\u0002\u0014\u0007\u0014\u0002\u0015\u0007\u0015"+
		"\u0002\u0016\u0007\u0016\u0002\u0017\u0007\u0017\u0002\u0018\u0007\u0018"+
		"\u0002\u0019\u0007\u0019\u0002\u001a\u0007\u001a\u0002\u001b\u0007\u001b"+
		"\u0002\u001c\u0007\u001c\u0002\u001d\u0007\u001d\u0002\u001e\u0007\u001e"+
		"\u0002\u001f\u0007\u001f\u0002 \u0007 \u0002!\u0007!\u0001\u0000\u0005"+
		"\u0000F\b\u0000\n\u0000\f\u0000I\t\u0000\u0001\u0000\u0001\u0000\u0001"+
		"\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001"+
		"\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0003\u0001W\b\u0001\u0001"+
		"\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0003\u0002^\b"+
		"\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0005"+
		"\u0002e\b\u0002\n\u0002\f\u0002h\t\u0002\u0001\u0002\u0001\u0002\u0001"+
		"\u0002\u0001\u0003\u0001\u0003\u0001\u0003\u0001\u0003\u0001\u0003\u0001"+
		"\u0003\u0001\u0003\u0001\u0003\u0005\u0003u\b\u0003\n\u0003\f\u0003x\t"+
		"\u0003\u0001\u0003\u0001\u0003\u0001\u0003\u0001\u0003\u0001\u0003\u0001"+
		"\u0003\u0005\u0003\u0080\b\u0003\n\u0003\f\u0003\u0083\t\u0003\u0001\u0003"+
		"\u0001\u0003\u0003\u0003\u0087\b\u0003\u0001\u0003\u0001\u0003\u0001\u0004"+
		"\u0001\u0004\u0001\u0004\u0001\u0004\u0001\u0004\u0001\u0004\u0003\u0004"+
		"\u0091\b\u0004\u0001\u0005\u0001\u0005\u0001\u0005\u0001\u0005\u0001\u0005"+
		"\u0001\u0005\u0001\u0005\u0001\u0005\u0005\u0005\u009b\b\u0005\n\u0005"+
		"\f\u0005\u009e\t\u0005\u0001\u0005\u0001\u0005\u0001\u0005\u0001\u0006"+
		"\u0001\u0006\u0001\u0006\u0001\u0006\u0001\u0006\u0001\u0006\u0001\u0006"+
		"\u0001\u0006\u0001\u0006\u0001\u0006\u0001\u0006\u0001\u0006\u0001\u0006"+
		"\u0005\u0006\u00b0\b\u0006\n\u0006\f\u0006\u00b3\t\u0006\u0001\u0006\u0001"+
		"\u0006\u0003\u0006\u00b7\b\u0006\u0001\u0006\u0001\u0006\u0001\u0007\u0001"+
		"\u0007\u0001\u0007\u0001\u0007\u0001\u0007\u0001\u0007\u0001\u0007\u0003"+
		"\u0007\u00c2\b\u0007\u0001\b\u0001\b\u0001\b\u0001\b\u0001\b\u0001\b\u0001"+
		"\b\u0001\b\u0001\b\u0001\t\u0001\t\u0001\t\u0001\t\u0001\t\u0003\t\u00d2"+
		"\b\t\u0001\t\u0001\t\u0001\n\u0001\n\u0001\n\u0001\n\u0001\n\u0005\n\u00db"+
		"\b\n\n\n\f\n\u00de\t\n\u0001\n\u0001\n\u0001\u000b\u0001\u000b\u0001\u000b"+
		"\u0001\u000b\u0001\u000b\u0001\u000b\u0001\u000b\u0001\u000b\u0001\u000b"+
		"\u0001\u000b\u0001\u000b\u0001\u000b\u0001\u000b\u0001\u000b\u0001\u000b"+
		"\u0001\u000b\u0001\u000b\u0001\u000b\u0001\u000b\u0001\u000b\u0001\u000b"+
		"\u0001\u000b\u0001\u000b\u0001\u000b\u0001\u000b\u0001\u000b\u0001\u000b"+
		"\u0003\u000b\u00fd\b\u000b\u0001\f\u0001\f\u0001\f\u0001\f\u0001\f\u0001"+
		"\f\u0005\f\u0105\b\f\n\f\f\f\u0108\t\f\u0001\f\u0001\f\u0001\f\u0001\f"+
		"\u0003\f\u010e\b\f\u0001\f\u0001\f\u0001\r\u0001\r\u0001\r\u0001\r\u0001"+
		"\r\u0001\r\u0001\r\u0003\r\u0119\b\r\u0001\r\u0001\r\u0001\u000e\u0001"+
		"\u000e\u0001\u000e\u0001\u000e\u0001\u000e\u0001\u000e\u0001\u000e\u0005"+
		"\u000e\u0124\b\u000e\n\u000e\f\u000e\u0127\t\u000e\u0001\u000e\u0001\u000e"+
		"\u0003\u000e\u012b\b\u000e\u0001\u000e\u0001\u000e\u0001\u000e\u0001\u000e"+
		"\u0001\u000e\u0001\u000e\u0003\u000e\u0133\b\u000e\u0001\u000e\u0001\u000e"+
		"\u0001\u000f\u0001\u000f\u0001\u000f\u0005\u000f\u013a\b\u000f\n\u000f"+
		"\f\u000f\u013d\t\u000f\u0001\u0010\u0001\u0010\u0001\u0010\u0001\u0010"+
		"\u0001\u0010\u0001\u0010\u0001\u0010\u0003\u0010\u0146\b\u0010\u0001\u0011"+
		"\u0001\u0011\u0001\u0011\u0001\u0012\u0001\u0012\u0001\u0012\u0001\u0012"+
		"\u0005\u0012\u014f\b\u0012\n\u0012\f\u0012\u0152\t\u0012\u0003\u0012\u0154"+
		"\b\u0012\u0001\u0012\u0001\u0012\u0001\u0013\u0001\u0013\u0001\u0013\u0001"+
		"\u0013\u0001\u0014\u0001\u0014\u0001\u0014\u0001\u0014\u0005\u0014\u0160"+
		"\b\u0014\n\u0014\f\u0014\u0163\t\u0014\u0003\u0014\u0165\b\u0014\u0001"+
		"\u0014\u0001\u0014\u0001\u0015\u0003\u0015\u016a\b\u0015\u0001\u0015\u0001"+
		"\u0015\u0001\u0016\u0001\u0016\u0001\u0016\u0001\u0016\u0001\u0017\u0001"+
		"\u0017\u0001\u0017\u0001\u0017\u0001\u0017\u0001\u0017\u0001\u0017\u0001"+
		"\u0017\u0001\u0017\u0003\u0017\u017b\b\u0017\u0001\u0018\u0001\u0018\u0001"+
		"\u0019\u0001\u0019\u0001\u0019\u0001\u0019\u0001\u0019\u0001\u0019\u0003"+
		"\u0019\u0185\b\u0019\u0001\u0019\u0001\u0019\u0001\u0019\u0001\u0019\u0001"+
		"\u0019\u0001\u0019\u0005\u0019\u018d\b\u0019\n\u0019\f\u0019\u0190\t\u0019"+
		"\u0001\u001a\u0001\u001a\u0001\u001a\u0001\u001a\u0001\u001a\u0001\u001a"+
		"\u0003\u001a\u0198\b\u001a\u0001\u001b\u0001\u001b\u0001\u001c\u0001\u001c"+
		"\u0001\u001d\u0001\u001d\u0001\u001e\u0001\u001e\u0001\u001e\u0001\u001f"+
		"\u0001\u001f\u0001\u001f\u0001\u001f\u0001\u001f\u0005\u001f\u01a8\b\u001f"+
		"\n\u001f\f\u001f\u01ab\t\u001f\u0001\u001f\u0001\u001f\u0001 \u0001 \u0001"+
		" \u0001 \u0003 \u01b3\b \u0001 \u0001 \u0001 \u0003 \u01b8\b \u0001 \u0001"+
		" \u0001 \u0003 \u01bd\b \u0001 \u0001 \u0001 \u0003 \u01c2\b \u0001 \u0001"+
		" \u0001 \u0003 \u01c7\b \u0001 \u0001 \u0001 \u0003 \u01cc\b \u0001 \u0001"+
		" \u0001 \u0003 \u01d1\b \u0001 \u0001 \u0001 \u0003 \u01d6\b \u0001 \u0001"+
		" \u0001 \u0001 \u0001 \u0001 \u0003 \u01de\b \u0001 \u0001 \u0001 \u0003"+
		" \u01e3\b \u0001 \u0001 \u0001 \u0001 \u0001 \u0005 \u01ea\b \n \f \u01ed"+
		"\t \u0001 \u0001 \u0001 \u0003 \u01f2\b \u0001 \u0001 \u0001 \u0001 \u0001"+
		" \u0005 \u01f9\b \n \f \u01fc\t \u0001 \u0001 \u0001 \u0001 \u0001 \u0001"+
		" \u0001 \u0005 \u0205\b \n \f \u0208\t \u0001 \u0001 \u0001 \u0003 \u020d"+
		"\b \u0003 \u020f\b \u0001!\u0001!\u0001!\u0000\u00012\"\u0000\u0002\u0004"+
		"\u0006\b\n\f\u000e\u0010\u0012\u0014\u0016\u0018\u001a\u001c\u001e \""+
		"$&(*,.02468:<>@B\u0000\u0007\u0002\u0000\u0001\u0001\u001e\u001e\u0002"+
		"\u0000\u0005\u0005YY\u0001\u000012\u0002\u0000XXZZ\u0002\u0000VVYY\u0003"+
		"\u0000DEMNPP\u0003\u0000EGJKOO\u0249\u0000G\u0001\u0000\u0000\u0000\u0002"+
		"V\u0001\u0000\u0000\u0000\u0004X\u0001\u0000\u0000\u0000\u0006l\u0001"+
		"\u0000\u0000\u0000\b\u0090\u0001\u0000\u0000\u0000\n\u0092\u0001\u0000"+
		"\u0000\u0000\f\u00a2\u0001\u0000\u0000\u0000\u000e\u00c1\u0001\u0000\u0000"+
		"\u0000\u0010\u00c3\u0001\u0000\u0000\u0000\u0012\u00cc\u0001\u0000\u0000"+
		"\u0000\u0014\u00d5\u0001\u0000\u0000\u0000\u0016\u00fc\u0001\u0000\u0000"+
		"\u0000\u0018\u00fe\u0001\u0000\u0000\u0000\u001a\u0111\u0001\u0000\u0000"+
		"\u0000\u001c\u011c\u0001\u0000\u0000\u0000\u001e\u0136\u0001\u0000\u0000"+
		"\u0000 \u0145\u0001\u0000\u0000\u0000\"\u0147\u0001\u0000\u0000\u0000"+
		"$\u014a\u0001\u0000\u0000\u0000&\u0157\u0001\u0000\u0000\u0000(\u015b"+
		"\u0001\u0000\u0000\u0000*\u0169\u0001\u0000\u0000\u0000,\u016d\u0001\u0000"+
		"\u0000\u0000.\u017a\u0001\u0000\u0000\u00000\u017c\u0001\u0000\u0000\u0000"+
		"2\u0184\u0001\u0000\u0000\u00004\u0191\u0001\u0000\u0000\u00006\u0199"+
		"\u0001\u0000\u0000\u00008\u019b\u0001\u0000\u0000\u0000:\u019d\u0001\u0000"+
		"\u0000\u0000<\u019f\u0001\u0000\u0000\u0000>\u01a2\u0001\u0000\u0000\u0000"+
		"@\u020e\u0001\u0000\u0000\u0000B\u0210\u0001\u0000\u0000\u0000DF\u0003"+
		"\u0002\u0001\u0000ED\u0001\u0000\u0000\u0000FI\u0001\u0000\u0000\u0000"+
		"GE\u0001\u0000\u0000\u0000GH\u0001\u0000\u0000\u0000HJ\u0001\u0000\u0000"+
		"\u0000IG\u0001\u0000\u0000\u0000JK\u0005\u0000\u0000\u0001K\u0001\u0001"+
		"\u0000\u0000\u0000LW\u0003\u0004\u0002\u0000MW\u0003\u0006\u0003\u0000"+
		"NW\u0003\n\u0005\u0000OW\u0003\f\u0006\u0000PW\u0003\u0010\b\u0000QW\u0003"+
		"\u0012\t\u0000RW\u0003\u0018\f\u0000SW\u0003\u001a\r\u0000TW\u0003\u001c"+
		"\u000e\u0000UW\u0003B!\u0000VL\u0001\u0000\u0000\u0000VM\u0001\u0000\u0000"+
		"\u0000VN\u0001\u0000\u0000\u0000VO\u0001\u0000\u0000\u0000VP\u0001\u0000"+
		"\u0000\u0000VQ\u0001\u0000\u0000\u0000VR\u0001\u0000\u0000\u0000VS\u0001"+
		"\u0000\u0000\u0000VT\u0001\u0000\u0000\u0000VU\u0001\u0000\u0000\u0000"+
		"W\u0003\u0001\u0000\u0000\u0000XY\u0005\t\u0000\u0000Y]\u0005(\u0000\u0000"+
		"Z[\u0005\u0013\u0000\u0000[\\\u0005\u001a\u0000\u0000\\^\u0005\u000f\u0000"+
		"\u0000]Z\u0001\u0000\u0000\u0000]^\u0001\u0000\u0000\u0000^_\u0001\u0000"+
		"\u0000\u0000_`\u0005X\u0000\u0000`a\u0005L\u0000\u0000af\u0003<\u001e"+
		"\u0000bc\u0005C\u0000\u0000ce\u0003<\u001e\u0000db\u0001\u0000\u0000\u0000"+
		"eh\u0001\u0000\u0000\u0000fd\u0001\u0000\u0000\u0000fg\u0001\u0000\u0000"+
		"\u0000gi\u0001\u0000\u0000\u0000hf\u0001\u0000\u0000\u0000ij\u0005S\u0000"+
		"\u0000jk\u0005T\u0000\u0000k\u0005\u0001\u0000\u0000\u0000lm\u0005\t\u0000"+
		"\u0000mn\u0005\n\u0000\u0000no\u0005\'\u0000\u0000op\u0005X\u0000\u0000"+
		"pq\u0005L\u0000\u0000qv\u0003<\u001e\u0000rs\u0005C\u0000\u0000su\u0003"+
		"<\u001e\u0000tr\u0001\u0000\u0000\u0000ux\u0001\u0000\u0000\u0000vt\u0001"+
		"\u0000\u0000\u0000vw\u0001\u0000\u0000\u0000wy\u0001\u0000\u0000\u0000"+
		"xv\u0001\u0000\u0000\u0000y\u0086\u0005S\u0000\u0000z{\u0005/\u0000\u0000"+
		"{|\u0005L\u0000\u0000|\u0081\u0003\b\u0004\u0000}~\u0005C\u0000\u0000"+
		"~\u0080\u0003\b\u0004\u0000\u007f}\u0001\u0000\u0000\u0000\u0080\u0083"+
		"\u0001\u0000\u0000\u0000\u0081\u007f\u0001\u0000\u0000\u0000\u0081\u0082"+
		"\u0001\u0000\u0000\u0000\u0082\u0084\u0001\u0000\u0000\u0000\u0083\u0081"+
		"\u0001\u0000\u0000\u0000\u0084\u0085\u0005S\u0000\u0000\u0085\u0087\u0001"+
		"\u0000\u0000\u0000\u0086z\u0001\u0000\u0000\u0000\u0086\u0087\u0001\u0000"+
		"\u0000\u0000\u0087\u0088\u0001\u0000\u0000\u0000\u0088\u0089\u0005T\u0000"+
		"\u0000\u0089\u0007\u0001\u0000\u0000\u0000\u008a\u008b\u0005\u000b\u0000"+
		"\u0000\u008b\u008c\u0005A\u0000\u0000\u008c\u0091\u0005Z\u0000\u0000\u008d"+
		"\u008e\u0005*\u0000\u0000\u008e\u008f\u0005A\u0000\u0000\u008f\u0091\u0005"+
		"Z\u0000\u0000\u0090\u008a\u0001\u0000\u0000\u0000\u0090\u008d\u0001\u0000"+
		"\u0000\u0000\u0091\t\u0001\u0000\u0000\u0000\u0092\u0093\u0005\t\u0000"+
		"\u0000\u0093\u0094\u0005\u0007\u0000\u0000\u0094\u0095\u0005)\u0000\u0000"+
		"\u0095\u0096\u0005X\u0000\u0000\u0096\u0097\u0005L\u0000\u0000\u0097\u009c"+
		"\u0003\u000e\u0007\u0000\u0098\u0099\u0005C\u0000\u0000\u0099\u009b\u0003"+
		"\u000e\u0007\u0000\u009a\u0098\u0001\u0000\u0000\u0000\u009b\u009e\u0001"+
		"\u0000\u0000\u0000\u009c\u009a\u0001\u0000\u0000\u0000\u009c\u009d\u0001"+
		"\u0000\u0000\u0000\u009d\u009f\u0001\u0000\u0000\u0000\u009e\u009c\u0001"+
		"\u0000\u0000\u0000\u009f\u00a0\u0005S\u0000\u0000\u00a0\u00a1\u0005T\u0000"+
		"\u0000\u00a1\u000b\u0001\u0000\u0000\u0000\u00a2\u00a3\u0005\t\u0000\u0000"+
		"\u00a3\u00a4\u0005\u0014\u0000\u0000\u00a4\u00a5\u0005)\u0000\u0000\u00a5"+
		"\u00a6\u0005X\u0000\u0000\u00a6\u00a7\u0005\u0011\u0000\u0000\u00a7\u00a8"+
		"\u0005Z\u0000\u0000\u00a8\u00a9\u0005,\u0000\u0000\u00a9\u00b6\u0005Z"+
		"\u0000\u0000\u00aa\u00ab\u0005/\u0000\u0000\u00ab\u00ac\u0005L\u0000\u0000"+
		"\u00ac\u00b1\u0003\u000e\u0007\u0000\u00ad\u00ae\u0005C\u0000\u0000\u00ae"+
		"\u00b0\u0003\u000e\u0007\u0000\u00af\u00ad\u0001\u0000\u0000\u0000\u00b0"+
		"\u00b3\u0001\u0000\u0000\u0000\u00b1\u00af\u0001\u0000\u0000\u0000\u00b1"+
		"\u00b2\u0001\u0000\u0000\u0000\u00b2\u00b4\u0001\u0000\u0000\u0000\u00b3"+
		"\u00b1\u0001\u0000\u0000\u0000\u00b4\u00b5\u0005S\u0000\u0000\u00b5\u00b7"+
		"\u0001\u0000\u0000\u0000\u00b6\u00aa\u0001\u0000\u0000\u0000\u00b6\u00b7"+
		"\u0001\u0000\u0000\u0000\u00b7\u00b8\u0001\u0000\u0000\u0000\u00b8\u00b9"+
		"\u0005T\u0000\u0000\u00b9\r\u0001\u0000\u0000\u0000\u00ba\u00bb\u0005"+
		"\u001c\u0000\u0000\u00bb\u00bc\u0005A\u0000\u0000\u00bc\u00c2\u0005Y\u0000"+
		"\u0000\u00bd\u00be\u0005\u001b\u0000\u0000\u00be\u00bf\u0005A\u0000\u0000"+
		"\u00bf\u00c2\u0005Y\u0000\u0000\u00c0\u00c2\u0003<\u001e\u0000\u00c1\u00ba"+
		"\u0001\u0000\u0000\u0000\u00c1\u00bd\u0001\u0000\u0000\u0000\u00c1\u00c0"+
		"\u0001\u0000\u0000\u0000\u00c2\u000f\u0001\u0000\u0000\u0000\u00c3\u00c4"+
		"\u0005\u0003\u0000\u0000\u00c4\u00c5\u0005(\u0000\u0000\u00c5\u00c6\u0005"+
		"X\u0000\u0000\u00c6\u00c7\u0005\u0002\u0000\u0000\u00c7\u00c8\u0005\u0006"+
		"\u0000\u0000\u00c8\u00c9\u0005X\u0000\u0000\u00c9\u00ca\u0003@ \u0000"+
		"\u00ca\u00cb\u0005T\u0000\u0000\u00cb\u0011\u0001\u0000\u0000\u0000\u00cc"+
		"\u00cd\u0005!\u0000\u0000\u00cd\u00ce\u0005X\u0000\u0000\u00ce\u00cf\u0005"+
		"\u0017\u0000\u0000\u00cf\u00d1\u0005X\u0000\u0000\u00d0\u00d2\u0003\u0014"+
		"\n\u0000\u00d1\u00d0\u0001\u0000\u0000\u0000\u00d1\u00d2\u0001\u0000\u0000"+
		"\u0000\u00d2\u00d3\u0001\u0000\u0000\u0000\u00d3\u00d4\u0005T\u0000\u0000"+
		"\u00d4\u0013\u0001\u0000\u0000\u0000\u00d5\u00d6\u0005/\u0000\u0000\u00d6"+
		"\u00d7\u0005L\u0000\u0000\u00d7\u00dc\u0003\u0016\u000b\u0000\u00d8\u00d9"+
		"\u0005C\u0000\u0000\u00d9\u00db\u0003\u0016\u000b\u0000\u00da\u00d8\u0001"+
		"\u0000\u0000\u0000\u00db\u00de\u0001\u0000\u0000\u0000\u00dc\u00da\u0001"+
		"\u0000\u0000\u0000\u00dc\u00dd\u0001\u0000\u0000\u0000\u00dd\u00df\u0001"+
		"\u0000\u0000\u0000\u00de\u00dc\u0001\u0000\u0000\u0000\u00df\u00e0\u0005"+
		"S\u0000\u0000\u00e0\u0015\u0001\u0000\u0000\u0000\u00e1\u00e2\u0005\b"+
		"\u0000\u0000\u00e2\u00e3\u0005A\u0000\u0000\u00e3\u00fd\u0007\u0000\u0000"+
		"\u0000\u00e4\u00e5\u0005\u0019\u0000\u0000\u00e5\u00e6\u0005A\u0000\u0000"+
		"\u00e6\u00fd\u0005Y\u0000\u0000\u00e7\u00e8\u0005%\u0000\u0000\u00e8\u00e9"+
		"\u0005A\u0000\u0000\u00e9\u00fd\u0007\u0001\u0000\u0000\u00ea\u00eb\u0005"+
		"$\u0000\u0000\u00eb\u00ec\u0005A\u0000\u0000\u00ec\u00fd\u0005Y\u0000"+
		"\u0000\u00ed\u00ee\u0005&\u0000\u0000\u00ee\u00ef\u0005A\u0000\u0000\u00ef"+
		"\u00fd\u0005Z\u0000\u0000\u00f0\u00f1\u0005\u001f\u0000\u0000\u00f1\u00f2"+
		"\u0005A\u0000\u0000\u00f2\u00fd\u0005Z\u0000\u0000\u00f3\u00f4\u0005\""+
		"\u0000\u0000\u00f4\u00f5\u0005A\u0000\u0000\u00f5\u00fd\u0005Z\u0000\u0000"+
		"\u00f6\u00f7\u00050\u0000\u0000\u00f7\u00f8\u0005A\u0000\u0000\u00f8\u00fd"+
		"\u0007\u0002\u0000\u0000\u00f9\u00fa\u00053\u0000\u0000\u00fa\u00fb\u0005"+
		"A\u0000\u0000\u00fb\u00fd\u0007\u0002\u0000\u0000\u00fc\u00e1\u0001\u0000"+
		"\u0000\u0000\u00fc\u00e4\u0001\u0000\u0000\u0000\u00fc\u00e7\u0001\u0000"+
		"\u0000\u0000\u00fc\u00ea\u0001\u0000\u0000\u0000\u00fc\u00ed\u0001\u0000"+
		"\u0000\u0000\u00fc\u00f0\u0001\u0000\u0000\u0000\u00fc\u00f3\u0001\u0000"+
		"\u0000\u0000\u00fc\u00f6\u0001\u0000\u0000\u0000\u00fc\u00f9\u0001\u0000"+
		"\u0000\u0000\u00fd\u0017\u0001\u0000\u0000\u0000\u00fe\u00ff\u0005+\u0000"+
		"\u0000\u00ff\u0100\u0005X\u0000\u0000\u0100\u0101\u0005#\u0000\u0000\u0101"+
		"\u0106\u0003,\u0016\u0000\u0102\u0103\u0005C\u0000\u0000\u0103\u0105\u0003"+
		",\u0016\u0000\u0104\u0102\u0001\u0000\u0000\u0000\u0105\u0108\u0001\u0000"+
		"\u0000\u0000\u0106\u0104\u0001\u0000\u0000\u0000\u0106\u0107\u0001\u0000"+
		"\u0000\u0000\u0107\u0109\u0001\u0000\u0000\u0000\u0108\u0106\u0001\u0000"+
		"\u0000\u0000\u0109\u010a\u0005.\u0000\u0000\u010a\u010d\u00032\u0019\u0000"+
		"\u010b\u010c\u0005/\u0000\u0000\u010c\u010e\u0005 \u0000\u0000\u010d\u010b"+
		"\u0001\u0000\u0000\u0000\u010d\u010e\u0001\u0000\u0000\u0000\u010e\u010f"+
		"\u0001\u0000\u0000\u0000\u010f\u0110\u0005T\u0000\u0000\u0110\u0019\u0001"+
		"\u0000\u0000\u0000\u0111\u0112\u0005\r\u0000\u0000\u0112\u0113\u0005\u0012"+
		"\u0000\u0000\u0113\u0114\u0005X\u0000\u0000\u0114\u0115\u0005.\u0000\u0000"+
		"\u0115\u0118\u00032\u0019\u0000\u0116\u0117\u0005/\u0000\u0000\u0117\u0119"+
		"\u0005 \u0000\u0000\u0118\u0116\u0001\u0000\u0000\u0000\u0118\u0119\u0001"+
		"\u0000\u0000\u0000\u0119\u011a\u0001\u0000\u0000\u0000\u011a\u011b\u0005"+
		"T\u0000\u0000\u011b\u001b\u0001\u0000\u0000\u0000\u011c\u011d\u0005\u0016"+
		"\u0000\u0000\u011d\u011e\u0005\u0017\u0000\u0000\u011e\u012a\u00038\u001c"+
		"\u0000\u011f\u0120\u0005L\u0000\u0000\u0120\u0125\u0003:\u001d\u0000\u0121"+
		"\u0122\u0005C\u0000\u0000\u0122\u0124\u0003:\u001d\u0000\u0123\u0121\u0001"+
		"\u0000\u0000\u0000\u0124\u0127\u0001\u0000\u0000\u0000\u0125\u0123\u0001"+
		"\u0000\u0000\u0000\u0125\u0126\u0001\u0000\u0000\u0000\u0126\u0128\u0001"+
		"\u0000\u0000\u0000\u0127\u0125\u0001\u0000\u0000\u0000\u0128\u0129\u0005"+
		"S\u0000\u0000\u0129\u012b\u0001\u0000\u0000\u0000\u012a\u011f\u0001\u0000"+
		"\u0000\u0000\u012a\u012b\u0001\u0000\u0000\u0000\u012b\u012c\u0001\u0000"+
		"\u0000\u0000\u012c\u012d\u0005-\u0000\u0000\u012d\u012e\u0005L\u0000\u0000"+
		"\u012e\u012f\u0003\u001e\u000f\u0000\u012f\u0132\u0005S\u0000\u0000\u0130"+
		"\u0131\u0005/\u0000\u0000\u0131\u0133\u0005 \u0000\u0000\u0132\u0130\u0001"+
		"\u0000\u0000\u0000\u0132\u0133\u0001\u0000\u0000\u0000\u0133\u0134\u0001"+
		"\u0000\u0000\u0000\u0134\u0135\u0005T\u0000\u0000\u0135\u001d\u0001\u0000"+
		"\u0000\u0000\u0136\u013b\u0003 \u0010\u0000\u0137\u0138\u0005C\u0000\u0000"+
		"\u0138\u013a\u0003 \u0010\u0000\u0139\u0137\u0001\u0000\u0000\u0000\u013a"+
		"\u013d\u0001\u0000\u0000\u0000\u013b\u0139\u0001\u0000\u0000\u0000\u013b"+
		"\u013c\u0001\u0000\u0000\u0000\u013c\u001f\u0001\u0000\u0000\u0000\u013d"+
		"\u013b\u0001\u0000\u0000\u0000\u013e\u0146\u0005Z\u0000\u0000\u013f\u0146"+
		"\u0003*\u0015\u0000\u0140\u0146\u0005U\u0000\u0000\u0141\u0146\u0005W"+
		"\u0000\u0000\u0142\u0146\u0003\"\u0011\u0000\u0143\u0146\u0003$\u0012"+
		"\u0000\u0144\u0146\u0003(\u0014\u0000\u0145\u013e\u0001\u0000\u0000\u0000"+
		"\u0145\u013f\u0001\u0000\u0000\u0000\u0145\u0140\u0001\u0000\u0000\u0000"+
		"\u0145\u0141\u0001\u0000\u0000\u0000\u0145\u0142\u0001\u0000\u0000\u0000"+
		"\u0145\u0143\u0001\u0000\u0000\u0000\u0145\u0144\u0001\u0000\u0000\u0000"+
		"\u0146!\u0001\u0000\u0000\u0000\u0147\u0148\u0005B\u0000\u0000\u0148\u0149"+
		"\u0005X\u0000\u0000\u0149#\u0001\u0000\u0000\u0000\u014a\u0153\u0005H"+
		"\u0000\u0000\u014b\u0150\u0003&\u0013\u0000\u014c\u014d\u0005C\u0000\u0000"+
		"\u014d\u014f\u0003&\u0013\u0000\u014e\u014c\u0001\u0000\u0000\u0000\u014f"+
		"\u0152\u0001\u0000\u0000\u0000\u0150\u014e\u0001\u0000\u0000\u0000\u0150"+
		"\u0151\u0001\u0000\u0000\u0000\u0151\u0154\u0001\u0000\u0000\u0000\u0152"+
		"\u0150\u0001\u0000\u0000\u0000\u0153\u014b\u0001\u0000\u0000\u0000\u0153"+
		"\u0154\u0001\u0000\u0000\u0000\u0154\u0155\u0001\u0000\u0000\u0000\u0155"+
		"\u0156\u0005Q\u0000\u0000\u0156%\u0001\u0000\u0000\u0000\u0157\u0158\u0007"+
		"\u0003\u0000\u0000\u0158\u0159\u0005B\u0000\u0000\u0159\u015a\u0003 \u0010"+
		"\u0000\u015a\'\u0001\u0000\u0000\u0000\u015b\u0164\u0005I\u0000\u0000"+
		"\u015c\u0161\u0003 \u0010\u0000\u015d\u015e\u0005C\u0000\u0000\u015e\u0160"+
		"\u0003 \u0010\u0000\u015f\u015d\u0001\u0000\u0000\u0000\u0160\u0163\u0001"+
		"\u0000\u0000\u0000\u0161\u015f\u0001\u0000\u0000\u0000\u0161\u0162\u0001"+
		"\u0000\u0000\u0000\u0162\u0165\u0001\u0000\u0000\u0000\u0163\u0161\u0001"+
		"\u0000\u0000\u0000\u0164\u015c\u0001\u0000\u0000\u0000\u0164\u0165\u0001"+
		"\u0000\u0000\u0000\u0165\u0166\u0001\u0000\u0000\u0000\u0166\u0167\u0005"+
		"R\u0000\u0000\u0167)\u0001\u0000\u0000\u0000\u0168\u016a\u0005M\u0000"+
		"\u0000\u0169\u0168\u0001\u0000\u0000\u0000\u0169\u016a\u0001\u0000\u0000"+
		"\u0000\u016a\u016b\u0001\u0000\u0000\u0000\u016b\u016c\u0007\u0004\u0000"+
		"\u0000\u016c+\u0001\u0000\u0000\u0000\u016d\u016e\u0005X\u0000\u0000\u016e"+
		"\u016f\u0005A\u0000\u0000\u016f\u0170\u0003.\u0017\u0000\u0170-\u0001"+
		"\u0000\u0000\u0000\u0171\u017b\u0003 \u0010\u0000\u0172\u0173\u0005X\u0000"+
		"\u0000\u0173\u0174\u00030\u0018\u0000\u0174\u0175\u0003.\u0017\u0000\u0175"+
		"\u017b\u0001\u0000\u0000\u0000\u0176\u0177\u0005L\u0000\u0000\u0177\u0178"+
		"\u0003.\u0017\u0000\u0178\u0179\u0005S\u0000\u0000\u0179\u017b\u0001\u0000"+
		"\u0000\u0000\u017a\u0171\u0001\u0000\u0000\u0000\u017a\u0172\u0001\u0000"+
		"\u0000\u0000\u017a\u0176\u0001\u0000\u0000\u0000\u017b/\u0001\u0000\u0000"+
		"\u0000\u017c\u017d\u0007\u0005\u0000\u0000\u017d1\u0001\u0000\u0000\u0000"+
		"\u017e\u017f\u0006\u0019\uffff\uffff\u0000\u017f\u0185\u00034\u001a\u0000"+
		"\u0180\u0181\u0005L\u0000\u0000\u0181\u0182\u00032\u0019\u0000\u0182\u0183"+
		"\u0005S\u0000\u0000\u0183\u0185\u0001\u0000\u0000\u0000\u0184\u017e\u0001"+
		"\u0000\u0000\u0000\u0184\u0180\u0001\u0000\u0000\u0000\u0185\u018e\u0001"+
		"\u0000\u0000\u0000\u0186\u0187\n\u0002\u0000\u0000\u0187\u0188\u0005\u0004"+
		"\u0000\u0000\u0188\u018d\u00032\u0019\u0003\u0189\u018a\n\u0001\u0000"+
		"\u0000\u018a\u018b\u0005\u001d\u0000\u0000\u018b\u018d\u00032\u0019\u0002"+
		"\u018c\u0186\u0001\u0000\u0000\u0000\u018c\u0189\u0001\u0000\u0000\u0000"+
		"\u018d\u0190\u0001\u0000\u0000\u0000\u018e\u018c\u0001\u0000\u0000\u0000"+
		"\u018e\u018f\u0001\u0000\u0000\u0000\u018f3\u0001\u0000\u0000\u0000\u0190"+
		"\u018e\u0001\u0000\u0000\u0000\u0191\u0192\u0005X\u0000\u0000\u0192\u0197"+
		"\u00036\u001b\u0000\u0193\u0198\u0003\"\u0011\u0000\u0194\u0198\u0005"+
		"Z\u0000\u0000\u0195\u0198\u0003*\u0015\u0000\u0196\u0198\u0005U\u0000"+
		"\u0000\u0197\u0193\u0001\u0000\u0000\u0000\u0197\u0194\u0001\u0000\u0000"+
		"\u0000\u0197\u0195\u0001\u0000\u0000\u0000\u0197\u0196\u0001\u0000\u0000"+
		"\u0000\u01985\u0001\u0000\u0000\u0000\u0199\u019a\u0007\u0006\u0000\u0000"+
		"\u019a7\u0001\u0000\u0000\u0000\u019b\u019c\u0005X\u0000\u0000\u019c9"+
		"\u0001\u0000\u0000\u0000\u019d\u019e\u0005X\u0000\u0000\u019e;\u0001\u0000"+
		"\u0000\u0000\u019f\u01a0\u0005X\u0000\u0000\u01a0\u01a1\u0003@ \u0000"+
		"\u01a1=\u0001\u0000\u0000\u0000\u01a2\u01a3\u0005X\u0000\u0000\u01a3\u01a4"+
		"\u0005L\u0000\u0000\u01a4\u01a9\u0003<\u001e\u0000\u01a5\u01a6\u0005C"+
		"\u0000\u0000\u01a6\u01a8\u0003<\u001e\u0000\u01a7\u01a5\u0001\u0000\u0000"+
		"\u0000\u01a8\u01ab\u0001\u0000\u0000\u0000\u01a9\u01a7\u0001\u0000\u0000"+
		"\u0000\u01a9\u01aa\u0001\u0000\u0000\u0000\u01aa\u01ac\u0001\u0000\u0000"+
		"\u0000\u01ab\u01a9\u0001\u0000\u0000\u0000\u01ac\u01ad\u0005S\u0000\u0000"+
		"\u01ad?\u0001\u0000\u0000\u0000\u01ae\u020f\u00059\u0000\u0000\u01af\u01b2"+
		"\u00056\u0000\u0000\u01b0\u01b1\u0005\u001a\u0000\u0000\u01b1\u01b3\u0005"+
		"\u0015\u0000\u0000\u01b2\u01b0\u0001\u0000\u0000\u0000\u01b2\u01b3\u0001"+
		"\u0000\u0000\u0000\u01b3\u020f\u0001\u0000\u0000\u0000\u01b4\u01b7\u0005"+
		"5\u0000\u0000\u01b5\u01b6\u0005\u001a\u0000\u0000\u01b6\u01b8\u0005\u0015"+
		"\u0000\u0000\u01b7\u01b5\u0001\u0000\u0000\u0000\u01b7\u01b8\u0001\u0000"+
		"\u0000\u0000\u01b8\u020f\u0001\u0000\u0000\u0000\u01b9\u01bc\u0005\u0018"+
		"\u0000\u0000\u01ba\u01bb\u0005\u001a\u0000\u0000\u01bb\u01bd\u0005\u0015"+
		"\u0000\u0000\u01bc\u01ba\u0001\u0000\u0000\u0000\u01bc\u01bd\u0001\u0000"+
		"\u0000\u0000\u01bd\u020f\u0001\u0000\u0000\u0000\u01be\u01c1\u0005\u0010"+
		"\u0000\u0000\u01bf\u01c0\u0005\u001a\u0000\u0000\u01c0\u01c2\u0005\u0015"+
		"\u0000\u0000\u01c1\u01bf\u0001\u0000\u0000\u0000\u01c1\u01c2\u0001\u0000"+
		"\u0000\u0000\u01c2\u020f\u0001\u0000\u0000\u0000\u01c3\u01c6\u0005\u000e"+
		"\u0000\u0000\u01c4\u01c5\u0005\u001a\u0000\u0000\u01c5\u01c7\u0005\u0015"+
		"\u0000\u0000\u01c6\u01c4\u0001\u0000\u0000\u0000\u01c6\u01c7\u0001\u0000"+
		"\u0000\u0000\u01c7\u020f\u0001\u0000\u0000\u0000\u01c8\u01cb\u00054\u0000"+
		"\u0000\u01c9\u01ca\u0005\u001a\u0000\u0000\u01ca\u01cc\u0005\u0015\u0000"+
		"\u0000\u01cb\u01c9\u0001\u0000\u0000\u0000\u01cb\u01cc\u0001\u0000\u0000"+
		"\u0000\u01cc\u020f\u0001\u0000\u0000\u0000\u01cd\u01d0\u0005\f\u0000\u0000"+
		"\u01ce\u01cf\u0005\u001a\u0000\u0000\u01cf\u01d1\u0005\u0015\u0000\u0000"+
		"\u01d0\u01ce\u0001\u0000\u0000\u0000\u01d0\u01d1\u0001\u0000\u0000\u0000"+
		"\u01d1\u020f\u0001\u0000\u0000\u0000\u01d2\u01d5\u0005:\u0000\u0000\u01d3"+
		"\u01d4\u0005\u001a\u0000\u0000\u01d4\u01d6\u0005\u0015\u0000\u0000\u01d5"+
		"\u01d3\u0001\u0000\u0000\u0000\u01d5\u01d6\u0001\u0000\u0000\u0000\u01d6"+
		"\u020f\u0001\u0000\u0000\u0000\u01d7\u020f\u0005;\u0000\u0000\u01d8\u020f"+
		"\u0005<\u0000\u0000\u01d9\u020f\u0005=\u0000\u0000\u01da\u01dd\u0005>"+
		"\u0000\u0000\u01db\u01dc\u0005\u001a\u0000\u0000\u01dc\u01de\u0005\u0015"+
		"\u0000\u0000\u01dd\u01db\u0001\u0000\u0000\u0000\u01dd\u01de\u0001\u0000"+
		"\u0000\u0000\u01de\u020f\u0001\u0000\u0000\u0000\u01df\u01e2\u0005?\u0000"+
		"\u0000\u01e0\u01e1\u0005\u001a\u0000\u0000\u01e1\u01e3\u0005\u0015\u0000"+
		"\u0000\u01e2\u01e0\u0001\u0000\u0000\u0000\u01e2\u01e3\u0001\u0000\u0000"+
		"\u0000\u01e3\u020f\u0001\u0000\u0000\u0000\u01e4\u01e5\u00058\u0000\u0000"+
		"\u01e5\u01e6\u0005L\u0000\u0000\u01e6\u01eb\u0003<\u001e\u0000\u01e7\u01e8"+
		"\u0005C\u0000\u0000\u01e8\u01ea\u0003<\u001e\u0000\u01e9\u01e7\u0001\u0000"+
		"\u0000\u0000\u01ea\u01ed\u0001\u0000\u0000\u0000\u01eb\u01e9\u0001\u0000"+
		"\u0000\u0000\u01eb\u01ec\u0001\u0000\u0000\u0000\u01ec\u01ee\u0001\u0000"+
		"\u0000\u0000\u01ed\u01eb\u0001\u0000\u0000\u0000\u01ee\u01f1\u0005S\u0000"+
		"\u0000\u01ef\u01f0\u0005\u001a\u0000\u0000\u01f0\u01f2\u0005\u0015\u0000"+
		"\u0000\u01f1\u01ef\u0001\u0000\u0000\u0000\u01f1\u01f2\u0001\u0000\u0000"+
		"\u0000\u01f2\u020f\u0001\u0000\u0000\u0000\u01f3\u01f4\u00057\u0000\u0000"+
		"\u01f4\u01f5\u0005L\u0000\u0000\u01f5\u01fa\u0003<\u001e\u0000\u01f6\u01f7"+
		"\u0005C\u0000\u0000\u01f7\u01f9\u0003<\u001e\u0000\u01f8\u01f6\u0001\u0000"+
		"\u0000\u0000\u01f9\u01fc\u0001\u0000\u0000\u0000\u01fa\u01f8\u0001\u0000"+
		"\u0000\u0000\u01fa\u01fb\u0001\u0000\u0000\u0000\u01fb\u01fd\u0001\u0000"+
		"\u0000\u0000\u01fc\u01fa\u0001\u0000\u0000\u0000\u01fd\u01fe\u0005S\u0000"+
		"\u0000\u01fe\u020f\u0001\u0000\u0000\u0000\u01ff\u0200\u0005@\u0000\u0000"+
		"\u0200\u0201\u0005L\u0000\u0000\u0201\u0206\u0003>\u001f\u0000\u0202\u0203"+
		"\u0005C\u0000\u0000\u0203\u0205\u0003>\u001f\u0000\u0204\u0202\u0001\u0000"+
		"\u0000\u0000\u0205\u0208\u0001\u0000\u0000\u0000\u0206\u0204\u0001\u0000"+
		"\u0000\u0000\u0206\u0207\u0001\u0000\u0000\u0000\u0207\u0209\u0001\u0000"+
		"\u0000\u0000\u0208\u0206\u0001\u0000\u0000\u0000\u0209\u020c\u0005S\u0000"+
		"\u0000\u020a\u020b\u0005\u001a\u0000\u0000\u020b\u020d\u0005\u0015\u0000"+
		"\u0000\u020c\u020a\u0001\u0000\u0000\u0000\u020c\u020d\u0001\u0000\u0000"+
		"\u0000\u020d\u020f\u0001\u0000\u0000\u0000\u020e\u01ae\u0001\u0000\u0000"+
		"\u0000\u020e\u01af\u0001\u0000\u0000\u0000\u020e\u01b4\u0001\u0000\u0000"+
		"\u0000\u020e\u01b9\u0001\u0000\u0000\u0000\u020e\u01be\u0001\u0000\u0000"+
		"\u0000\u020e\u01c3\u0001\u0000\u0000\u0000\u020e\u01c8\u0001\u0000\u0000"+
		"\u0000\u020e\u01cd\u0001\u0000\u0000\u0000\u020e\u01d2\u0001\u0000\u0000"+
		"\u0000\u020e\u01d7\u0001\u0000\u0000\u0000\u020e\u01d8\u0001\u0000\u0000"+
		"\u0000\u020e\u01d9\u0001\u0000\u0000\u0000\u020e\u01da\u0001\u0000\u0000"+
		"\u0000\u020e\u01df\u0001\u0000\u0000\u0000\u020e\u01e4\u0001\u0000\u0000"+
		"\u0000\u020e\u01f3\u0001\u0000\u0000\u0000\u020e\u01ff\u0001\u0000\u0000"+
		"\u0000\u020fA\u0001\u0000\u0000\u0000\u0210\u0211\u0005[\u0000\u0000\u0211"+
		"C\u0001\u0000\u0000\u00002GV]fv\u0081\u0086\u0090\u009c\u00b1\u00b6\u00c1"+
		"\u00d1\u00dc\u00fc\u0106\u010d\u0118\u0125\u012a\u0132\u013b\u0145\u0150"+
		"\u0153\u0161\u0164\u0169\u017a\u0184\u018c\u018e\u0197\u01a9\u01b2\u01b7"+
		"\u01bc\u01c1\u01c6\u01cb\u01d0\u01d5\u01dd\u01e2\u01eb\u01f1\u01fa\u0206"+
		"\u020c\u020e";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}