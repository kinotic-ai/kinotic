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
		GEO_SHAPE=61, UUID=62, DECIMAL=63, UNION=64, COMMA=65, DIVIDE=66, EQUALS=67, 
		GREATER_THAN=68, GREATER_THAN_EQUALS=69, LESS_THAN=70, LESS_THAN_EQUALS=71, 
		LPAREN=72, MINUS=73, MULTIPLY=74, NOT_EQUALS=75, PARAMETER=76, PLUS=77, 
		RPAREN=78, SEMICOLON=79, BOOLEAN_LITERAL=80, ID=81, INTEGER_LITERAL=82, 
		STRING=83, COMMENT=84, WS=85;
	public static final int
		RULE_migrations = 0, RULE_statement = 1, RULE_createTableStatement = 2, 
		RULE_createDataStreamStatement = 3, RULE_dataStreamOption = 4, RULE_createComponentTemplateStatement = 5, 
		RULE_createIndexTemplateStatement = 6, RULE_templatePart = 7, RULE_alterTableStatement = 8, 
		RULE_reindexStatement = 9, RULE_reindexOptions = 10, RULE_reindexOption = 11, 
		RULE_updateStatement = 12, RULE_deleteStatement = 13, RULE_insertStatement = 14, 
		RULE_valueList = 15, RULE_value = 16, RULE_assignment = 17, RULE_expression = 18, 
		RULE_operator = 19, RULE_whereClause = 20, RULE_condition = 21, RULE_comparisonOperator = 22, 
		RULE_tableName = 23, RULE_columnName = 24, RULE_columnDefinition = 25, 
		RULE_unionVariant = 26, RULE_type = 27, RULE_comment = 28;
	private static String[] makeRuleNames() {
		return new String[] {
			"migrations", "statement", "createTableStatement", "createDataStreamStatement", 
			"dataStreamOption", "createComponentTemplateStatement", "createIndexTemplateStatement", 
			"templatePart", "alterTableStatement", "reindexStatement", "reindexOptions", 
			"reindexOption", "updateStatement", "deleteStatement", "insertStatement", 
			"valueList", "value", "assignment", "expression", "operator", "whereClause", 
			"condition", "comparisonOperator", "tableName", "columnName", "columnDefinition", 
			"unionVariant", "type", "comment"
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
			"'UNION'", "','", "'/'", "'=='", "'>'", "'>='", "'<'", "'<='", "'('", 
			"'-'", "'*'", "'!='", "'?'", "'+'", "')'", "';'"
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
			"UNION", "COMMA", "DIVIDE", "EQUALS", "GREATER_THAN", "GREATER_THAN_EQUALS", 
			"LESS_THAN", "LESS_THAN_EQUALS", "LPAREN", "MINUS", "MULTIPLY", "NOT_EQUALS", 
			"PARAMETER", "PLUS", "RPAREN", "SEMICOLON", "BOOLEAN_LITERAL", "ID", 
			"INTEGER_LITERAL", "STRING", "COMMENT", "WS"
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
			setState(61);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while ((((_la) & ~0x3f) == 0 && ((1L << _la) & 8804687159816L) != 0) || _la==COMMENT) {
				{
				{
				setState(58);
				statement();
				}
				}
				setState(63);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(64);
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
			setState(76);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,1,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(66);
				createTableStatement();
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(67);
				createDataStreamStatement();
				}
				break;
			case 3:
				enterOuterAlt(_localctx, 3);
				{
				setState(68);
				createComponentTemplateStatement();
				}
				break;
			case 4:
				enterOuterAlt(_localctx, 4);
				{
				setState(69);
				createIndexTemplateStatement();
				}
				break;
			case 5:
				enterOuterAlt(_localctx, 5);
				{
				setState(70);
				alterTableStatement();
				}
				break;
			case 6:
				enterOuterAlt(_localctx, 6);
				{
				setState(71);
				reindexStatement();
				}
				break;
			case 7:
				enterOuterAlt(_localctx, 7);
				{
				setState(72);
				updateStatement();
				}
				break;
			case 8:
				enterOuterAlt(_localctx, 8);
				{
				setState(73);
				deleteStatement();
				}
				break;
			case 9:
				enterOuterAlt(_localctx, 9);
				{
				setState(74);
				insertStatement();
				}
				break;
			case 10:
				enterOuterAlt(_localctx, 10);
				{
				setState(75);
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
			setState(78);
			match(CREATE);
			setState(79);
			match(TABLE);
			setState(83);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==IF) {
				{
				setState(80);
				match(IF);
				setState(81);
				match(NOT);
				setState(82);
				match(EXISTS);
				}
			}

			setState(85);
			match(ID);
			setState(86);
			match(LPAREN);
			setState(87);
			columnDefinition();
			setState(92);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==COMMA) {
				{
				{
				setState(88);
				match(COMMA);
				setState(89);
				columnDefinition();
				}
				}
				setState(94);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(95);
			match(RPAREN);
			setState(96);
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
			setState(98);
			match(CREATE);
			setState(99);
			match(DATA);
			setState(100);
			match(STREAM);
			setState(101);
			match(ID);
			setState(102);
			match(LPAREN);
			setState(103);
			columnDefinition();
			setState(108);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==COMMA) {
				{
				{
				setState(104);
				match(COMMA);
				setState(105);
				columnDefinition();
				}
				}
				setState(110);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(111);
			match(RPAREN);
			setState(124);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==WITH) {
				{
				setState(112);
				match(WITH);
				setState(113);
				match(LPAREN);
				setState(114);
				dataStreamOption();
				setState(119);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while (_la==COMMA) {
					{
					{
					setState(115);
					match(COMMA);
					setState(116);
					dataStreamOption();
					}
					}
					setState(121);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}
				setState(122);
				match(RPAREN);
				}
			}

			setState(126);
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
		public TerminalNode EQUALS() { return getToken(KinoticSQLParser.EQUALS, 0); }
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
			setState(134);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case DATA_RETENTION:
				enterOuterAlt(_localctx, 1);
				{
				setState(128);
				match(DATA_RETENTION);
				setState(129);
				match(EQUALS);
				setState(130);
				match(STRING);
				}
				break;
			case TIME_REFERENCE:
				enterOuterAlt(_localctx, 2);
				{
				setState(131);
				match(TIME_REFERENCE);
				setState(132);
				match(EQUALS);
				setState(133);
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
			setState(136);
			match(CREATE);
			setState(137);
			match(COMPONENT);
			setState(138);
			match(TEMPLATE);
			setState(139);
			match(ID);
			setState(140);
			match(LPAREN);
			setState(141);
			templatePart();
			setState(146);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==COMMA) {
				{
				{
				setState(142);
				match(COMMA);
				setState(143);
				templatePart();
				}
				}
				setState(148);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(149);
			match(RPAREN);
			setState(150);
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
			setState(152);
			match(CREATE);
			setState(153);
			match(INDEX);
			setState(154);
			match(TEMPLATE);
			setState(155);
			match(ID);
			setState(156);
			match(FOR);
			setState(157);
			match(STRING);
			setState(158);
			match(USING);
			setState(159);
			match(STRING);
			setState(172);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==WITH) {
				{
				setState(160);
				match(WITH);
				setState(161);
				match(LPAREN);
				setState(162);
				templatePart();
				setState(167);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while (_la==COMMA) {
					{
					{
					setState(163);
					match(COMMA);
					setState(164);
					templatePart();
					}
					}
					setState(169);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}
				setState(170);
				match(RPAREN);
				}
			}

			setState(174);
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
		public TerminalNode EQUALS() { return getToken(KinoticSQLParser.EQUALS, 0); }
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
			setState(183);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case NUMBER_OF_SHARDS:
				enterOuterAlt(_localctx, 1);
				{
				setState(176);
				match(NUMBER_OF_SHARDS);
				setState(177);
				match(EQUALS);
				setState(178);
				match(INTEGER_LITERAL);
				}
				break;
			case NUMBER_OF_REPLICAS:
				enterOuterAlt(_localctx, 2);
				{
				setState(179);
				match(NUMBER_OF_REPLICAS);
				setState(180);
				match(EQUALS);
				setState(181);
				match(INTEGER_LITERAL);
				}
				break;
			case ID:
				enterOuterAlt(_localctx, 3);
				{
				setState(182);
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
			setState(185);
			match(ALTER);
			setState(186);
			match(TABLE);
			setState(187);
			match(ID);
			setState(188);
			match(ADD);
			setState(189);
			match(COLUMN);
			setState(190);
			match(ID);
			setState(191);
			type();
			setState(192);
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
			setState(194);
			match(REINDEX);
			setState(195);
			match(ID);
			setState(196);
			match(INTO);
			setState(197);
			match(ID);
			setState(199);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==WITH) {
				{
				setState(198);
				reindexOptions();
				}
			}

			setState(201);
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
			setState(203);
			match(WITH);
			setState(204);
			match(LPAREN);
			setState(205);
			reindexOption();
			setState(210);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==COMMA) {
				{
				{
				setState(206);
				match(COMMA);
				setState(207);
				reindexOption();
				}
				}
				setState(212);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(213);
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
		public TerminalNode EQUALS() { return getToken(KinoticSQLParser.EQUALS, 0); }
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
			setState(242);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case CONFLICTS:
				enterOuterAlt(_localctx, 1);
				{
				setState(215);
				match(CONFLICTS);
				setState(216);
				match(EQUALS);
				setState(217);
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
				setState(218);
				match(MAX_DOCS);
				setState(219);
				match(EQUALS);
				setState(220);
				match(INTEGER_LITERAL);
				}
				break;
			case SLICES:
				enterOuterAlt(_localctx, 3);
				{
				setState(221);
				match(SLICES);
				setState(222);
				match(EQUALS);
				setState(223);
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
				setState(224);
				match(SIZE);
				setState(225);
				match(EQUALS);
				setState(226);
				match(INTEGER_LITERAL);
				}
				break;
			case SOURCE_FIELDS:
				enterOuterAlt(_localctx, 5);
				{
				setState(227);
				match(SOURCE_FIELDS);
				setState(228);
				match(EQUALS);
				setState(229);
				match(STRING);
				}
				break;
			case QUERY:
				enterOuterAlt(_localctx, 6);
				{
				setState(230);
				match(QUERY);
				setState(231);
				match(EQUALS);
				setState(232);
				match(STRING);
				}
				break;
			case SCRIPT:
				enterOuterAlt(_localctx, 7);
				{
				setState(233);
				match(SCRIPT);
				setState(234);
				match(EQUALS);
				setState(235);
				match(STRING);
				}
				break;
			case WAIT:
				enterOuterAlt(_localctx, 8);
				{
				setState(236);
				match(WAIT);
				setState(237);
				match(EQUALS);
				setState(238);
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
				setState(239);
				match(SKIP_IF_NO_SOURCE);
				setState(240);
				match(EQUALS);
				setState(241);
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
			setState(244);
			match(UPDATE);
			setState(245);
			match(ID);
			setState(246);
			match(SET);
			setState(247);
			assignment();
			setState(252);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==COMMA) {
				{
				{
				setState(248);
				match(COMMA);
				setState(249);
				assignment();
				}
				}
				setState(254);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(255);
			match(WHERE);
			setState(256);
			whereClause(0);
			setState(259);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==WITH) {
				{
				setState(257);
				match(WITH);
				setState(258);
				match(REFRESH);
				}
			}

			setState(261);
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
			setState(263);
			match(DELETE);
			setState(264);
			match(FROM);
			setState(265);
			match(ID);
			setState(266);
			match(WHERE);
			setState(267);
			whereClause(0);
			setState(270);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==WITH) {
				{
				setState(268);
				match(WITH);
				setState(269);
				match(REFRESH);
				}
			}

			setState(272);
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
			setState(274);
			match(INSERT);
			setState(275);
			match(INTO);
			setState(276);
			tableName();
			setState(288);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==LPAREN) {
				{
				setState(277);
				match(LPAREN);
				setState(278);
				columnName();
				setState(283);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while (_la==COMMA) {
					{
					{
					setState(279);
					match(COMMA);
					setState(280);
					columnName();
					}
					}
					setState(285);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}
				setState(286);
				match(RPAREN);
				}
			}

			setState(290);
			match(VALUES);
			setState(291);
			match(LPAREN);
			setState(292);
			valueList();
			setState(293);
			match(RPAREN);
			setState(296);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==WITH) {
				{
				setState(294);
				match(WITH);
				setState(295);
				match(REFRESH);
				}
			}

			setState(298);
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
			setState(300);
			value();
			setState(305);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==COMMA) {
				{
				{
				setState(301);
				match(COMMA);
				setState(302);
				value();
				}
				}
				setState(307);
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
		public TerminalNode INTEGER_LITERAL() { return getToken(KinoticSQLParser.INTEGER_LITERAL, 0); }
		public TerminalNode BOOLEAN_LITERAL() { return getToken(KinoticSQLParser.BOOLEAN_LITERAL, 0); }
		public TerminalNode PARAMETER() { return getToken(KinoticSQLParser.PARAMETER, 0); }
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
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(308);
			_la = _input.LA(1);
			if ( !(((((_la - 76)) & ~0x3f) == 0 && ((1L << (_la - 76)) & 209L) != 0)) ) {
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
		public TerminalNode EQUALS() { return getToken(KinoticSQLParser.EQUALS, 0); }
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
		enterRule(_localctx, 34, RULE_assignment);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(310);
			match(ID);
			setState(311);
			match(EQUALS);
			setState(312);
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
		public TerminalNode PARAMETER() { return getToken(KinoticSQLParser.PARAMETER, 0); }
		public TerminalNode STRING() { return getToken(KinoticSQLParser.STRING, 0); }
		public TerminalNode INTEGER_LITERAL() { return getToken(KinoticSQLParser.INTEGER_LITERAL, 0); }
		public TerminalNode BOOLEAN_LITERAL() { return getToken(KinoticSQLParser.BOOLEAN_LITERAL, 0); }
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
		enterRule(_localctx, 36, RULE_expression);
		try {
			setState(326);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case PARAMETER:
				enterOuterAlt(_localctx, 1);
				{
				setState(314);
				match(PARAMETER);
				}
				break;
			case STRING:
				enterOuterAlt(_localctx, 2);
				{
				setState(315);
				match(STRING);
				}
				break;
			case INTEGER_LITERAL:
				enterOuterAlt(_localctx, 3);
				{
				setState(316);
				match(INTEGER_LITERAL);
				}
				break;
			case BOOLEAN_LITERAL:
				enterOuterAlt(_localctx, 4);
				{
				setState(317);
				match(BOOLEAN_LITERAL);
				}
				break;
			case ID:
				enterOuterAlt(_localctx, 5);
				{
				setState(318);
				match(ID);
				setState(319);
				operator();
				setState(320);
				expression();
				}
				break;
			case LPAREN:
				enterOuterAlt(_localctx, 6);
				{
				setState(322);
				match(LPAREN);
				setState(323);
				expression();
				setState(324);
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
		enterRule(_localctx, 38, RULE_operator);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(328);
			_la = _input.LA(1);
			if ( !(((((_la - 66)) & ~0x3f) == 0 && ((1L << (_la - 66)) & 2435L) != 0)) ) {
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
		int _startState = 40;
		enterRecursionRule(_localctx, 40, RULE_whereClause, _p);
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(336);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case ID:
				{
				setState(331);
				condition();
				}
				break;
			case LPAREN:
				{
				setState(332);
				match(LPAREN);
				setState(333);
				whereClause(0);
				setState(334);
				match(RPAREN);
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
			_ctx.stop = _input.LT(-1);
			setState(346);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,25,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					if ( _parseListeners!=null ) triggerExitRuleEvent();
					_prevctx = _localctx;
					{
					setState(344);
					_errHandler.sync(this);
					switch ( getInterpreter().adaptivePredict(_input,24,_ctx) ) {
					case 1:
						{
						_localctx = new WhereClauseContext(_parentctx, _parentState);
						pushNewRecursionContext(_localctx, _startState, RULE_whereClause);
						setState(338);
						if (!(precpred(_ctx, 2))) throw new FailedPredicateException(this, "precpred(_ctx, 2)");
						setState(339);
						match(AND);
						setState(340);
						whereClause(3);
						}
						break;
					case 2:
						{
						_localctx = new WhereClauseContext(_parentctx, _parentState);
						pushNewRecursionContext(_localctx, _startState, RULE_whereClause);
						setState(341);
						if (!(precpred(_ctx, 1))) throw new FailedPredicateException(this, "precpred(_ctx, 1)");
						setState(342);
						match(OR);
						setState(343);
						whereClause(2);
						}
						break;
					}
					} 
				}
				setState(348);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,25,_ctx);
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
		public TerminalNode PARAMETER() { return getToken(KinoticSQLParser.PARAMETER, 0); }
		public TerminalNode STRING() { return getToken(KinoticSQLParser.STRING, 0); }
		public TerminalNode INTEGER_LITERAL() { return getToken(KinoticSQLParser.INTEGER_LITERAL, 0); }
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
		enterRule(_localctx, 42, RULE_condition);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(349);
			match(ID);
			setState(350);
			comparisonOperator();
			setState(351);
			_la = _input.LA(1);
			if ( !(((((_la - 76)) & ~0x3f) == 0 && ((1L << (_la - 76)) & 209L) != 0)) ) {
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
		enterRule(_localctx, 44, RULE_comparisonOperator);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(353);
			_la = _input.LA(1);
			if ( !(((((_la - 67)) & ~0x3f) == 0 && ((1L << (_la - 67)) & 287L) != 0)) ) {
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
		enterRule(_localctx, 46, RULE_tableName);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(355);
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
		enterRule(_localctx, 48, RULE_columnName);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(357);
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
		enterRule(_localctx, 50, RULE_columnDefinition);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(359);
			match(ID);
			setState(360);
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
		enterRule(_localctx, 52, RULE_unionVariant);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(362);
			match(ID);
			setState(363);
			match(LPAREN);
			setState(364);
			columnDefinition();
			setState(369);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==COMMA) {
				{
				{
				setState(365);
				match(COMMA);
				setState(366);
				columnDefinition();
				}
				}
				setState(371);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(372);
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
		enterRule(_localctx, 54, RULE_type);
		int _la;
		try {
			setState(470);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case TEXT:
				enterOuterAlt(_localctx, 1);
				{
				setState(374);
				match(TEXT);
				}
				break;
			case KEYWORD:
				enterOuterAlt(_localctx, 2);
				{
				setState(375);
				match(KEYWORD);
				setState(378);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==NOT) {
					{
					setState(376);
					match(NOT);
					setState(377);
					match(INDEXED);
					}
				}

				}
				break;
			case INTEGER:
				enterOuterAlt(_localctx, 3);
				{
				setState(380);
				match(INTEGER);
				setState(383);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==NOT) {
					{
					setState(381);
					match(NOT);
					setState(382);
					match(INDEXED);
					}
				}

				}
				break;
			case LONG:
				enterOuterAlt(_localctx, 4);
				{
				setState(385);
				match(LONG);
				setState(388);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==NOT) {
					{
					setState(386);
					match(NOT);
					setState(387);
					match(INDEXED);
					}
				}

				}
				break;
			case FLOAT:
				enterOuterAlt(_localctx, 5);
				{
				setState(390);
				match(FLOAT);
				setState(393);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==NOT) {
					{
					setState(391);
					match(NOT);
					setState(392);
					match(INDEXED);
					}
				}

				}
				break;
			case DOUBLE:
				enterOuterAlt(_localctx, 6);
				{
				setState(395);
				match(DOUBLE);
				setState(398);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==NOT) {
					{
					setState(396);
					match(NOT);
					setState(397);
					match(INDEXED);
					}
				}

				}
				break;
			case BOOLEAN:
				enterOuterAlt(_localctx, 7);
				{
				setState(400);
				match(BOOLEAN);
				setState(403);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==NOT) {
					{
					setState(401);
					match(NOT);
					setState(402);
					match(INDEXED);
					}
				}

				}
				break;
			case DATE:
				enterOuterAlt(_localctx, 8);
				{
				setState(405);
				match(DATE);
				setState(408);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==NOT) {
					{
					setState(406);
					match(NOT);
					setState(407);
					match(INDEXED);
					}
				}

				}
				break;
			case JSON:
				enterOuterAlt(_localctx, 9);
				{
				setState(410);
				match(JSON);
				setState(413);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==NOT) {
					{
					setState(411);
					match(NOT);
					setState(412);
					match(INDEXED);
					}
				}

				}
				break;
			case BINARY:
				enterOuterAlt(_localctx, 10);
				{
				setState(415);
				match(BINARY);
				}
				break;
			case GEO_POINT:
				enterOuterAlt(_localctx, 11);
				{
				setState(416);
				match(GEO_POINT);
				}
				break;
			case GEO_SHAPE:
				enterOuterAlt(_localctx, 12);
				{
				setState(417);
				match(GEO_SHAPE);
				}
				break;
			case UUID:
				enterOuterAlt(_localctx, 13);
				{
				setState(418);
				match(UUID);
				setState(421);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==NOT) {
					{
					setState(419);
					match(NOT);
					setState(420);
					match(INDEXED);
					}
				}

				}
				break;
			case DECIMAL:
				enterOuterAlt(_localctx, 14);
				{
				setState(423);
				match(DECIMAL);
				setState(426);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==NOT) {
					{
					setState(424);
					match(NOT);
					setState(425);
					match(INDEXED);
					}
				}

				}
				break;
			case OBJECT:
				enterOuterAlt(_localctx, 15);
				{
				setState(428);
				match(OBJECT);
				setState(429);
				match(LPAREN);
				setState(430);
				columnDefinition();
				setState(435);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while (_la==COMMA) {
					{
					{
					setState(431);
					match(COMMA);
					setState(432);
					columnDefinition();
					}
					}
					setState(437);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}
				setState(438);
				match(RPAREN);
				setState(441);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==NOT) {
					{
					setState(439);
					match(NOT);
					setState(440);
					match(INDEXED);
					}
				}

				}
				break;
			case NESTED:
				enterOuterAlt(_localctx, 16);
				{
				setState(443);
				match(NESTED);
				setState(444);
				match(LPAREN);
				setState(445);
				columnDefinition();
				setState(450);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while (_la==COMMA) {
					{
					{
					setState(446);
					match(COMMA);
					setState(447);
					columnDefinition();
					}
					}
					setState(452);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}
				setState(453);
				match(RPAREN);
				}
				break;
			case UNION:
				enterOuterAlt(_localctx, 17);
				{
				setState(455);
				match(UNION);
				setState(456);
				match(LPAREN);
				setState(457);
				unionVariant();
				setState(462);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while (_la==COMMA) {
					{
					{
					setState(458);
					match(COMMA);
					setState(459);
					unionVariant();
					}
					}
					setState(464);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}
				setState(465);
				match(RPAREN);
				setState(468);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==NOT) {
					{
					setState(466);
					match(NOT);
					setState(467);
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
		enterRule(_localctx, 56, RULE_comment);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(472);
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
		case 20:
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
		"\u0004\u0001U\u01db\u0002\u0000\u0007\u0000\u0002\u0001\u0007\u0001\u0002"+
		"\u0002\u0007\u0002\u0002\u0003\u0007\u0003\u0002\u0004\u0007\u0004\u0002"+
		"\u0005\u0007\u0005\u0002\u0006\u0007\u0006\u0002\u0007\u0007\u0007\u0002"+
		"\b\u0007\b\u0002\t\u0007\t\u0002\n\u0007\n\u0002\u000b\u0007\u000b\u0002"+
		"\f\u0007\f\u0002\r\u0007\r\u0002\u000e\u0007\u000e\u0002\u000f\u0007\u000f"+
		"\u0002\u0010\u0007\u0010\u0002\u0011\u0007\u0011\u0002\u0012\u0007\u0012"+
		"\u0002\u0013\u0007\u0013\u0002\u0014\u0007\u0014\u0002\u0015\u0007\u0015"+
		"\u0002\u0016\u0007\u0016\u0002\u0017\u0007\u0017\u0002\u0018\u0007\u0018"+
		"\u0002\u0019\u0007\u0019\u0002\u001a\u0007\u001a\u0002\u001b\u0007\u001b"+
		"\u0002\u001c\u0007\u001c\u0001\u0000\u0005\u0000<\b\u0000\n\u0000\f\u0000"+
		"?\t\u0000\u0001\u0000\u0001\u0000\u0001\u0001\u0001\u0001\u0001\u0001"+
		"\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001"+
		"\u0001\u0001\u0003\u0001M\b\u0001\u0001\u0002\u0001\u0002\u0001\u0002"+
		"\u0001\u0002\u0001\u0002\u0003\u0002T\b\u0002\u0001\u0002\u0001\u0002"+
		"\u0001\u0002\u0001\u0002\u0001\u0002\u0005\u0002[\b\u0002\n\u0002\f\u0002"+
		"^\t\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0003\u0001\u0003"+
		"\u0001\u0003\u0001\u0003\u0001\u0003\u0001\u0003\u0001\u0003\u0001\u0003"+
		"\u0005\u0003k\b\u0003\n\u0003\f\u0003n\t\u0003\u0001\u0003\u0001\u0003"+
		"\u0001\u0003\u0001\u0003\u0001\u0003\u0001\u0003\u0005\u0003v\b\u0003"+
		"\n\u0003\f\u0003y\t\u0003\u0001\u0003\u0001\u0003\u0003\u0003}\b\u0003"+
		"\u0001\u0003\u0001\u0003\u0001\u0004\u0001\u0004\u0001\u0004\u0001\u0004"+
		"\u0001\u0004\u0001\u0004\u0003\u0004\u0087\b\u0004\u0001\u0005\u0001\u0005"+
		"\u0001\u0005\u0001\u0005\u0001\u0005\u0001\u0005\u0001\u0005\u0001\u0005"+
		"\u0005\u0005\u0091\b\u0005\n\u0005\f\u0005\u0094\t\u0005\u0001\u0005\u0001"+
		"\u0005\u0001\u0005\u0001\u0006\u0001\u0006\u0001\u0006\u0001\u0006\u0001"+
		"\u0006\u0001\u0006\u0001\u0006\u0001\u0006\u0001\u0006\u0001\u0006\u0001"+
		"\u0006\u0001\u0006\u0001\u0006\u0005\u0006\u00a6\b\u0006\n\u0006\f\u0006"+
		"\u00a9\t\u0006\u0001\u0006\u0001\u0006\u0003\u0006\u00ad\b\u0006\u0001"+
		"\u0006\u0001\u0006\u0001\u0007\u0001\u0007\u0001\u0007\u0001\u0007\u0001"+
		"\u0007\u0001\u0007\u0001\u0007\u0003\u0007\u00b8\b\u0007\u0001\b\u0001"+
		"\b\u0001\b\u0001\b\u0001\b\u0001\b\u0001\b\u0001\b\u0001\b\u0001\t\u0001"+
		"\t\u0001\t\u0001\t\u0001\t\u0003\t\u00c8\b\t\u0001\t\u0001\t\u0001\n\u0001"+
		"\n\u0001\n\u0001\n\u0001\n\u0005\n\u00d1\b\n\n\n\f\n\u00d4\t\n\u0001\n"+
		"\u0001\n\u0001\u000b\u0001\u000b\u0001\u000b\u0001\u000b\u0001\u000b\u0001"+
		"\u000b\u0001\u000b\u0001\u000b\u0001\u000b\u0001\u000b\u0001\u000b\u0001"+
		"\u000b\u0001\u000b\u0001\u000b\u0001\u000b\u0001\u000b\u0001\u000b\u0001"+
		"\u000b\u0001\u000b\u0001\u000b\u0001\u000b\u0001\u000b\u0001\u000b\u0001"+
		"\u000b\u0001\u000b\u0001\u000b\u0001\u000b\u0003\u000b\u00f3\b\u000b\u0001"+
		"\f\u0001\f\u0001\f\u0001\f\u0001\f\u0001\f\u0005\f\u00fb\b\f\n\f\f\f\u00fe"+
		"\t\f\u0001\f\u0001\f\u0001\f\u0001\f\u0003\f\u0104\b\f\u0001\f\u0001\f"+
		"\u0001\r\u0001\r\u0001\r\u0001\r\u0001\r\u0001\r\u0001\r\u0003\r\u010f"+
		"\b\r\u0001\r\u0001\r\u0001\u000e\u0001\u000e\u0001\u000e\u0001\u000e\u0001"+
		"\u000e\u0001\u000e\u0001\u000e\u0005\u000e\u011a\b\u000e\n\u000e\f\u000e"+
		"\u011d\t\u000e\u0001\u000e\u0001\u000e\u0003\u000e\u0121\b\u000e\u0001"+
		"\u000e\u0001\u000e\u0001\u000e\u0001\u000e\u0001\u000e\u0001\u000e\u0003"+
		"\u000e\u0129\b\u000e\u0001\u000e\u0001\u000e\u0001\u000f\u0001\u000f\u0001"+
		"\u000f\u0005\u000f\u0130\b\u000f\n\u000f\f\u000f\u0133\t\u000f\u0001\u0010"+
		"\u0001\u0010\u0001\u0011\u0001\u0011\u0001\u0011\u0001\u0011\u0001\u0012"+
		"\u0001\u0012\u0001\u0012\u0001\u0012\u0001\u0012\u0001\u0012\u0001\u0012"+
		"\u0001\u0012\u0001\u0012\u0001\u0012\u0001\u0012\u0001\u0012\u0003\u0012"+
		"\u0147\b\u0012\u0001\u0013\u0001\u0013\u0001\u0014\u0001\u0014\u0001\u0014"+
		"\u0001\u0014\u0001\u0014\u0001\u0014\u0003\u0014\u0151\b\u0014\u0001\u0014"+
		"\u0001\u0014\u0001\u0014\u0001\u0014\u0001\u0014\u0001\u0014\u0005\u0014"+
		"\u0159\b\u0014\n\u0014\f\u0014\u015c\t\u0014\u0001\u0015\u0001\u0015\u0001"+
		"\u0015\u0001\u0015\u0001\u0016\u0001\u0016\u0001\u0017\u0001\u0017\u0001"+
		"\u0018\u0001\u0018\u0001\u0019\u0001\u0019\u0001\u0019\u0001\u001a\u0001"+
		"\u001a\u0001\u001a\u0001\u001a\u0001\u001a\u0005\u001a\u0170\b\u001a\n"+
		"\u001a\f\u001a\u0173\t\u001a\u0001\u001a\u0001\u001a\u0001\u001b\u0001"+
		"\u001b\u0001\u001b\u0001\u001b\u0003\u001b\u017b\b\u001b\u0001\u001b\u0001"+
		"\u001b\u0001\u001b\u0003\u001b\u0180\b\u001b\u0001\u001b\u0001\u001b\u0001"+
		"\u001b\u0003\u001b\u0185\b\u001b\u0001\u001b\u0001\u001b\u0001\u001b\u0003"+
		"\u001b\u018a\b\u001b\u0001\u001b\u0001\u001b\u0001\u001b\u0003\u001b\u018f"+
		"\b\u001b\u0001\u001b\u0001\u001b\u0001\u001b\u0003\u001b\u0194\b\u001b"+
		"\u0001\u001b\u0001\u001b\u0001\u001b\u0003\u001b\u0199\b\u001b\u0001\u001b"+
		"\u0001\u001b\u0001\u001b\u0003\u001b\u019e\b\u001b\u0001\u001b\u0001\u001b"+
		"\u0001\u001b\u0001\u001b\u0001\u001b\u0001\u001b\u0003\u001b\u01a6\b\u001b"+
		"\u0001\u001b\u0001\u001b\u0001\u001b\u0003\u001b\u01ab\b\u001b\u0001\u001b"+
		"\u0001\u001b\u0001\u001b\u0001\u001b\u0001\u001b\u0005\u001b\u01b2\b\u001b"+
		"\n\u001b\f\u001b\u01b5\t\u001b\u0001\u001b\u0001\u001b\u0001\u001b\u0003"+
		"\u001b\u01ba\b\u001b\u0001\u001b\u0001\u001b\u0001\u001b\u0001\u001b\u0001"+
		"\u001b\u0005\u001b\u01c1\b\u001b\n\u001b\f\u001b\u01c4\t\u001b\u0001\u001b"+
		"\u0001\u001b\u0001\u001b\u0001\u001b\u0001\u001b\u0001\u001b\u0001\u001b"+
		"\u0005\u001b\u01cd\b\u001b\n\u001b\f\u001b\u01d0\t\u001b\u0001\u001b\u0001"+
		"\u001b\u0001\u001b\u0003\u001b\u01d5\b\u001b\u0003\u001b\u01d7\b\u001b"+
		"\u0001\u001c\u0001\u001c\u0001\u001c\u0000\u0001(\u001d\u0000\u0002\u0004"+
		"\u0006\b\n\f\u000e\u0010\u0012\u0014\u0016\u0018\u001a\u001c\u001e \""+
		"$&(*,.02468\u0000\u0006\u0002\u0000\u0001\u0001\u001e\u001e\u0002\u0000"+
		"\u0005\u0005RR\u0001\u000012\u0003\u0000LLPPRS\u0003\u0000BCIJMM\u0002"+
		"\u0000CGKK\u020b\u0000=\u0001\u0000\u0000\u0000\u0002L\u0001\u0000\u0000"+
		"\u0000\u0004N\u0001\u0000\u0000\u0000\u0006b\u0001\u0000\u0000\u0000\b"+
		"\u0086\u0001\u0000\u0000\u0000\n\u0088\u0001\u0000\u0000\u0000\f\u0098"+
		"\u0001\u0000\u0000\u0000\u000e\u00b7\u0001\u0000\u0000\u0000\u0010\u00b9"+
		"\u0001\u0000\u0000\u0000\u0012\u00c2\u0001\u0000\u0000\u0000\u0014\u00cb"+
		"\u0001\u0000\u0000\u0000\u0016\u00f2\u0001\u0000\u0000\u0000\u0018\u00f4"+
		"\u0001\u0000\u0000\u0000\u001a\u0107\u0001\u0000\u0000\u0000\u001c\u0112"+
		"\u0001\u0000\u0000\u0000\u001e\u012c\u0001\u0000\u0000\u0000 \u0134\u0001"+
		"\u0000\u0000\u0000\"\u0136\u0001\u0000\u0000\u0000$\u0146\u0001\u0000"+
		"\u0000\u0000&\u0148\u0001\u0000\u0000\u0000(\u0150\u0001\u0000\u0000\u0000"+
		"*\u015d\u0001\u0000\u0000\u0000,\u0161\u0001\u0000\u0000\u0000.\u0163"+
		"\u0001\u0000\u0000\u00000\u0165\u0001\u0000\u0000\u00002\u0167\u0001\u0000"+
		"\u0000\u00004\u016a\u0001\u0000\u0000\u00006\u01d6\u0001\u0000\u0000\u0000"+
		"8\u01d8\u0001\u0000\u0000\u0000:<\u0003\u0002\u0001\u0000;:\u0001\u0000"+
		"\u0000\u0000<?\u0001\u0000\u0000\u0000=;\u0001\u0000\u0000\u0000=>\u0001"+
		"\u0000\u0000\u0000>@\u0001\u0000\u0000\u0000?=\u0001\u0000\u0000\u0000"+
		"@A\u0005\u0000\u0000\u0001A\u0001\u0001\u0000\u0000\u0000BM\u0003\u0004"+
		"\u0002\u0000CM\u0003\u0006\u0003\u0000DM\u0003\n\u0005\u0000EM\u0003\f"+
		"\u0006\u0000FM\u0003\u0010\b\u0000GM\u0003\u0012\t\u0000HM\u0003\u0018"+
		"\f\u0000IM\u0003\u001a\r\u0000JM\u0003\u001c\u000e\u0000KM\u00038\u001c"+
		"\u0000LB\u0001\u0000\u0000\u0000LC\u0001\u0000\u0000\u0000LD\u0001\u0000"+
		"\u0000\u0000LE\u0001\u0000\u0000\u0000LF\u0001\u0000\u0000\u0000LG\u0001"+
		"\u0000\u0000\u0000LH\u0001\u0000\u0000\u0000LI\u0001\u0000\u0000\u0000"+
		"LJ\u0001\u0000\u0000\u0000LK\u0001\u0000\u0000\u0000M\u0003\u0001\u0000"+
		"\u0000\u0000NO\u0005\t\u0000\u0000OS\u0005(\u0000\u0000PQ\u0005\u0013"+
		"\u0000\u0000QR\u0005\u001a\u0000\u0000RT\u0005\u000f\u0000\u0000SP\u0001"+
		"\u0000\u0000\u0000ST\u0001\u0000\u0000\u0000TU\u0001\u0000\u0000\u0000"+
		"UV\u0005Q\u0000\u0000VW\u0005H\u0000\u0000W\\\u00032\u0019\u0000XY\u0005"+
		"A\u0000\u0000Y[\u00032\u0019\u0000ZX\u0001\u0000\u0000\u0000[^\u0001\u0000"+
		"\u0000\u0000\\Z\u0001\u0000\u0000\u0000\\]\u0001\u0000\u0000\u0000]_\u0001"+
		"\u0000\u0000\u0000^\\\u0001\u0000\u0000\u0000_`\u0005N\u0000\u0000`a\u0005"+
		"O\u0000\u0000a\u0005\u0001\u0000\u0000\u0000bc\u0005\t\u0000\u0000cd\u0005"+
		"\n\u0000\u0000de\u0005\'\u0000\u0000ef\u0005Q\u0000\u0000fg\u0005H\u0000"+
		"\u0000gl\u00032\u0019\u0000hi\u0005A\u0000\u0000ik\u00032\u0019\u0000"+
		"jh\u0001\u0000\u0000\u0000kn\u0001\u0000\u0000\u0000lj\u0001\u0000\u0000"+
		"\u0000lm\u0001\u0000\u0000\u0000mo\u0001\u0000\u0000\u0000nl\u0001\u0000"+
		"\u0000\u0000o|\u0005N\u0000\u0000pq\u0005/\u0000\u0000qr\u0005H\u0000"+
		"\u0000rw\u0003\b\u0004\u0000st\u0005A\u0000\u0000tv\u0003\b\u0004\u0000"+
		"us\u0001\u0000\u0000\u0000vy\u0001\u0000\u0000\u0000wu\u0001\u0000\u0000"+
		"\u0000wx\u0001\u0000\u0000\u0000xz\u0001\u0000\u0000\u0000yw\u0001\u0000"+
		"\u0000\u0000z{\u0005N\u0000\u0000{}\u0001\u0000\u0000\u0000|p\u0001\u0000"+
		"\u0000\u0000|}\u0001\u0000\u0000\u0000}~\u0001\u0000\u0000\u0000~\u007f"+
		"\u0005O\u0000\u0000\u007f\u0007\u0001\u0000\u0000\u0000\u0080\u0081\u0005"+
		"\u000b\u0000\u0000\u0081\u0082\u0005C\u0000\u0000\u0082\u0087\u0005S\u0000"+
		"\u0000\u0083\u0084\u0005*\u0000\u0000\u0084\u0085\u0005C\u0000\u0000\u0085"+
		"\u0087\u0005S\u0000\u0000\u0086\u0080\u0001\u0000\u0000\u0000\u0086\u0083"+
		"\u0001\u0000\u0000\u0000\u0087\t\u0001\u0000\u0000\u0000\u0088\u0089\u0005"+
		"\t\u0000\u0000\u0089\u008a\u0005\u0007\u0000\u0000\u008a\u008b\u0005)"+
		"\u0000\u0000\u008b\u008c\u0005Q\u0000\u0000\u008c\u008d\u0005H\u0000\u0000"+
		"\u008d\u0092\u0003\u000e\u0007\u0000\u008e\u008f\u0005A\u0000\u0000\u008f"+
		"\u0091\u0003\u000e\u0007\u0000\u0090\u008e\u0001\u0000\u0000\u0000\u0091"+
		"\u0094\u0001\u0000\u0000\u0000\u0092\u0090\u0001\u0000\u0000\u0000\u0092"+
		"\u0093\u0001\u0000\u0000\u0000\u0093\u0095\u0001\u0000\u0000\u0000\u0094"+
		"\u0092\u0001\u0000\u0000\u0000\u0095\u0096\u0005N\u0000\u0000\u0096\u0097"+
		"\u0005O\u0000\u0000\u0097\u000b\u0001\u0000\u0000\u0000\u0098\u0099\u0005"+
		"\t\u0000\u0000\u0099\u009a\u0005\u0014\u0000\u0000\u009a\u009b\u0005)"+
		"\u0000\u0000\u009b\u009c\u0005Q\u0000\u0000\u009c\u009d\u0005\u0011\u0000"+
		"\u0000\u009d\u009e\u0005S\u0000\u0000\u009e\u009f\u0005,\u0000\u0000\u009f"+
		"\u00ac\u0005S\u0000\u0000\u00a0\u00a1\u0005/\u0000\u0000\u00a1\u00a2\u0005"+
		"H\u0000\u0000\u00a2\u00a7\u0003\u000e\u0007\u0000\u00a3\u00a4\u0005A\u0000"+
		"\u0000\u00a4\u00a6\u0003\u000e\u0007\u0000\u00a5\u00a3\u0001\u0000\u0000"+
		"\u0000\u00a6\u00a9\u0001\u0000\u0000\u0000\u00a7\u00a5\u0001\u0000\u0000"+
		"\u0000\u00a7\u00a8\u0001\u0000\u0000\u0000\u00a8\u00aa\u0001\u0000\u0000"+
		"\u0000\u00a9\u00a7\u0001\u0000\u0000\u0000\u00aa\u00ab\u0005N\u0000\u0000"+
		"\u00ab\u00ad\u0001\u0000\u0000\u0000\u00ac\u00a0\u0001\u0000\u0000\u0000"+
		"\u00ac\u00ad\u0001\u0000\u0000\u0000\u00ad\u00ae\u0001\u0000\u0000\u0000"+
		"\u00ae\u00af\u0005O\u0000\u0000\u00af\r\u0001\u0000\u0000\u0000\u00b0"+
		"\u00b1\u0005\u001c\u0000\u0000\u00b1\u00b2\u0005C\u0000\u0000\u00b2\u00b8"+
		"\u0005R\u0000\u0000\u00b3\u00b4\u0005\u001b\u0000\u0000\u00b4\u00b5\u0005"+
		"C\u0000\u0000\u00b5\u00b8\u0005R\u0000\u0000\u00b6\u00b8\u00032\u0019"+
		"\u0000\u00b7\u00b0\u0001\u0000\u0000\u0000\u00b7\u00b3\u0001\u0000\u0000"+
		"\u0000\u00b7\u00b6\u0001\u0000\u0000\u0000\u00b8\u000f\u0001\u0000\u0000"+
		"\u0000\u00b9\u00ba\u0005\u0003\u0000\u0000\u00ba\u00bb\u0005(\u0000\u0000"+
		"\u00bb\u00bc\u0005Q\u0000\u0000\u00bc\u00bd\u0005\u0002\u0000\u0000\u00bd"+
		"\u00be\u0005\u0006\u0000\u0000\u00be\u00bf\u0005Q\u0000\u0000\u00bf\u00c0"+
		"\u00036\u001b\u0000\u00c0\u00c1\u0005O\u0000\u0000\u00c1\u0011\u0001\u0000"+
		"\u0000\u0000\u00c2\u00c3\u0005!\u0000\u0000\u00c3\u00c4\u0005Q\u0000\u0000"+
		"\u00c4\u00c5\u0005\u0017\u0000\u0000\u00c5\u00c7\u0005Q\u0000\u0000\u00c6"+
		"\u00c8\u0003\u0014\n\u0000\u00c7\u00c6\u0001\u0000\u0000\u0000\u00c7\u00c8"+
		"\u0001\u0000\u0000\u0000\u00c8\u00c9\u0001\u0000\u0000\u0000\u00c9\u00ca"+
		"\u0005O\u0000\u0000\u00ca\u0013\u0001\u0000\u0000\u0000\u00cb\u00cc\u0005"+
		"/\u0000\u0000\u00cc\u00cd\u0005H\u0000\u0000\u00cd\u00d2\u0003\u0016\u000b"+
		"\u0000\u00ce\u00cf\u0005A\u0000\u0000\u00cf\u00d1\u0003\u0016\u000b\u0000"+
		"\u00d0\u00ce\u0001\u0000\u0000\u0000\u00d1\u00d4\u0001\u0000\u0000\u0000"+
		"\u00d2\u00d0\u0001\u0000\u0000\u0000\u00d2\u00d3\u0001\u0000\u0000\u0000"+
		"\u00d3\u00d5\u0001\u0000\u0000\u0000\u00d4\u00d2\u0001\u0000\u0000\u0000"+
		"\u00d5\u00d6\u0005N\u0000\u0000\u00d6\u0015\u0001\u0000\u0000\u0000\u00d7"+
		"\u00d8\u0005\b\u0000\u0000\u00d8\u00d9\u0005C\u0000\u0000\u00d9\u00f3"+
		"\u0007\u0000\u0000\u0000\u00da\u00db\u0005\u0019\u0000\u0000\u00db\u00dc"+
		"\u0005C\u0000\u0000\u00dc\u00f3\u0005R\u0000\u0000\u00dd\u00de\u0005%"+
		"\u0000\u0000\u00de\u00df\u0005C\u0000\u0000\u00df\u00f3\u0007\u0001\u0000"+
		"\u0000\u00e0\u00e1\u0005$\u0000\u0000\u00e1\u00e2\u0005C\u0000\u0000\u00e2"+
		"\u00f3\u0005R\u0000\u0000\u00e3\u00e4\u0005&\u0000\u0000\u00e4\u00e5\u0005"+
		"C\u0000\u0000\u00e5\u00f3\u0005S\u0000\u0000\u00e6\u00e7\u0005\u001f\u0000"+
		"\u0000\u00e7\u00e8\u0005C\u0000\u0000\u00e8\u00f3\u0005S\u0000\u0000\u00e9"+
		"\u00ea\u0005\"\u0000\u0000\u00ea\u00eb\u0005C\u0000\u0000\u00eb\u00f3"+
		"\u0005S\u0000\u0000\u00ec\u00ed\u00050\u0000\u0000\u00ed\u00ee\u0005C"+
		"\u0000\u0000\u00ee\u00f3\u0007\u0002\u0000\u0000\u00ef\u00f0\u00053\u0000"+
		"\u0000\u00f0\u00f1\u0005C\u0000\u0000\u00f1\u00f3\u0007\u0002\u0000\u0000"+
		"\u00f2\u00d7\u0001\u0000\u0000\u0000\u00f2\u00da\u0001\u0000\u0000\u0000"+
		"\u00f2\u00dd\u0001\u0000\u0000\u0000\u00f2\u00e0\u0001\u0000\u0000\u0000"+
		"\u00f2\u00e3\u0001\u0000\u0000\u0000\u00f2\u00e6\u0001\u0000\u0000\u0000"+
		"\u00f2\u00e9\u0001\u0000\u0000\u0000\u00f2\u00ec\u0001\u0000\u0000\u0000"+
		"\u00f2\u00ef\u0001\u0000\u0000\u0000\u00f3\u0017\u0001\u0000\u0000\u0000"+
		"\u00f4\u00f5\u0005+\u0000\u0000\u00f5\u00f6\u0005Q\u0000\u0000\u00f6\u00f7"+
		"\u0005#\u0000\u0000\u00f7\u00fc\u0003\"\u0011\u0000\u00f8\u00f9\u0005"+
		"A\u0000\u0000\u00f9\u00fb\u0003\"\u0011\u0000\u00fa\u00f8\u0001\u0000"+
		"\u0000\u0000\u00fb\u00fe\u0001\u0000\u0000\u0000\u00fc\u00fa\u0001\u0000"+
		"\u0000\u0000\u00fc\u00fd\u0001\u0000\u0000\u0000\u00fd\u00ff\u0001\u0000"+
		"\u0000\u0000\u00fe\u00fc\u0001\u0000\u0000\u0000\u00ff\u0100\u0005.\u0000"+
		"\u0000\u0100\u0103\u0003(\u0014\u0000\u0101\u0102\u0005/\u0000\u0000\u0102"+
		"\u0104\u0005 \u0000\u0000\u0103\u0101\u0001\u0000\u0000\u0000\u0103\u0104"+
		"\u0001\u0000\u0000\u0000\u0104\u0105\u0001\u0000\u0000\u0000\u0105\u0106"+
		"\u0005O\u0000\u0000\u0106\u0019\u0001\u0000\u0000\u0000\u0107\u0108\u0005"+
		"\r\u0000\u0000\u0108\u0109\u0005\u0012\u0000\u0000\u0109\u010a\u0005Q"+
		"\u0000\u0000\u010a\u010b\u0005.\u0000\u0000\u010b\u010e\u0003(\u0014\u0000"+
		"\u010c\u010d\u0005/\u0000\u0000\u010d\u010f\u0005 \u0000\u0000\u010e\u010c"+
		"\u0001\u0000\u0000\u0000\u010e\u010f\u0001\u0000\u0000\u0000\u010f\u0110"+
		"\u0001\u0000\u0000\u0000\u0110\u0111\u0005O\u0000\u0000\u0111\u001b\u0001"+
		"\u0000\u0000\u0000\u0112\u0113\u0005\u0016\u0000\u0000\u0113\u0114\u0005"+
		"\u0017\u0000\u0000\u0114\u0120\u0003.\u0017\u0000\u0115\u0116\u0005H\u0000"+
		"\u0000\u0116\u011b\u00030\u0018\u0000\u0117\u0118\u0005A\u0000\u0000\u0118"+
		"\u011a\u00030\u0018\u0000\u0119\u0117\u0001\u0000\u0000\u0000\u011a\u011d"+
		"\u0001\u0000\u0000\u0000\u011b\u0119\u0001\u0000\u0000\u0000\u011b\u011c"+
		"\u0001\u0000\u0000\u0000\u011c\u011e\u0001\u0000\u0000\u0000\u011d\u011b"+
		"\u0001\u0000\u0000\u0000\u011e\u011f\u0005N\u0000\u0000\u011f\u0121\u0001"+
		"\u0000\u0000\u0000\u0120\u0115\u0001\u0000\u0000\u0000\u0120\u0121\u0001"+
		"\u0000\u0000\u0000\u0121\u0122\u0001\u0000\u0000\u0000\u0122\u0123\u0005"+
		"-\u0000\u0000\u0123\u0124\u0005H\u0000\u0000\u0124\u0125\u0003\u001e\u000f"+
		"\u0000\u0125\u0128\u0005N\u0000\u0000\u0126\u0127\u0005/\u0000\u0000\u0127"+
		"\u0129\u0005 \u0000\u0000\u0128\u0126\u0001\u0000\u0000\u0000\u0128\u0129"+
		"\u0001\u0000\u0000\u0000\u0129\u012a\u0001\u0000\u0000\u0000\u012a\u012b"+
		"\u0005O\u0000\u0000\u012b\u001d\u0001\u0000\u0000\u0000\u012c\u0131\u0003"+
		" \u0010\u0000\u012d\u012e\u0005A\u0000\u0000\u012e\u0130\u0003 \u0010"+
		"\u0000\u012f\u012d\u0001\u0000\u0000\u0000\u0130\u0133\u0001\u0000\u0000"+
		"\u0000\u0131\u012f\u0001\u0000\u0000\u0000\u0131\u0132\u0001\u0000\u0000"+
		"\u0000\u0132\u001f\u0001\u0000\u0000\u0000\u0133\u0131\u0001\u0000\u0000"+
		"\u0000\u0134\u0135\u0007\u0003\u0000\u0000\u0135!\u0001\u0000\u0000\u0000"+
		"\u0136\u0137\u0005Q\u0000\u0000\u0137\u0138\u0005C\u0000\u0000\u0138\u0139"+
		"\u0003$\u0012\u0000\u0139#\u0001\u0000\u0000\u0000\u013a\u0147\u0005L"+
		"\u0000\u0000\u013b\u0147\u0005S\u0000\u0000\u013c\u0147\u0005R\u0000\u0000"+
		"\u013d\u0147\u0005P\u0000\u0000\u013e\u013f\u0005Q\u0000\u0000\u013f\u0140"+
		"\u0003&\u0013\u0000\u0140\u0141\u0003$\u0012\u0000\u0141\u0147\u0001\u0000"+
		"\u0000\u0000\u0142\u0143\u0005H\u0000\u0000\u0143\u0144\u0003$\u0012\u0000"+
		"\u0144\u0145\u0005N\u0000\u0000\u0145\u0147\u0001\u0000\u0000\u0000\u0146"+
		"\u013a\u0001\u0000\u0000\u0000\u0146\u013b\u0001\u0000\u0000\u0000\u0146"+
		"\u013c\u0001\u0000\u0000\u0000\u0146\u013d\u0001\u0000\u0000\u0000\u0146"+
		"\u013e\u0001\u0000\u0000\u0000\u0146\u0142\u0001\u0000\u0000\u0000\u0147"+
		"%\u0001\u0000\u0000\u0000\u0148\u0149\u0007\u0004\u0000\u0000\u0149\'"+
		"\u0001\u0000\u0000\u0000\u014a\u014b\u0006\u0014\uffff\uffff\u0000\u014b"+
		"\u0151\u0003*\u0015\u0000\u014c\u014d\u0005H\u0000\u0000\u014d\u014e\u0003"+
		"(\u0014\u0000\u014e\u014f\u0005N\u0000\u0000\u014f\u0151\u0001\u0000\u0000"+
		"\u0000\u0150\u014a\u0001\u0000\u0000\u0000\u0150\u014c\u0001\u0000\u0000"+
		"\u0000\u0151\u015a\u0001\u0000\u0000\u0000\u0152\u0153\n\u0002\u0000\u0000"+
		"\u0153\u0154\u0005\u0004\u0000\u0000\u0154\u0159\u0003(\u0014\u0003\u0155"+
		"\u0156\n\u0001\u0000\u0000\u0156\u0157\u0005\u001d\u0000\u0000\u0157\u0159"+
		"\u0003(\u0014\u0002\u0158\u0152\u0001\u0000\u0000\u0000\u0158\u0155\u0001"+
		"\u0000\u0000\u0000\u0159\u015c\u0001\u0000\u0000\u0000\u015a\u0158\u0001"+
		"\u0000\u0000\u0000\u015a\u015b\u0001\u0000\u0000\u0000\u015b)\u0001\u0000"+
		"\u0000\u0000\u015c\u015a\u0001\u0000\u0000\u0000\u015d\u015e\u0005Q\u0000"+
		"\u0000\u015e\u015f\u0003,\u0016\u0000\u015f\u0160\u0007\u0003\u0000\u0000"+
		"\u0160+\u0001\u0000\u0000\u0000\u0161\u0162\u0007\u0005\u0000\u0000\u0162"+
		"-\u0001\u0000\u0000\u0000\u0163\u0164\u0005Q\u0000\u0000\u0164/\u0001"+
		"\u0000\u0000\u0000\u0165\u0166\u0005Q\u0000\u0000\u01661\u0001\u0000\u0000"+
		"\u0000\u0167\u0168\u0005Q\u0000\u0000\u0168\u0169\u00036\u001b\u0000\u0169"+
		"3\u0001\u0000\u0000\u0000\u016a\u016b\u0005Q\u0000\u0000\u016b\u016c\u0005"+
		"H\u0000\u0000\u016c\u0171\u00032\u0019\u0000\u016d\u016e\u0005A\u0000"+
		"\u0000\u016e\u0170\u00032\u0019\u0000\u016f\u016d\u0001\u0000\u0000\u0000"+
		"\u0170\u0173\u0001\u0000\u0000\u0000\u0171\u016f\u0001\u0000\u0000\u0000"+
		"\u0171\u0172\u0001\u0000\u0000\u0000\u0172\u0174\u0001\u0000\u0000\u0000"+
		"\u0173\u0171\u0001\u0000\u0000\u0000\u0174\u0175\u0005N\u0000\u0000\u0175"+
		"5\u0001\u0000\u0000\u0000\u0176\u01d7\u00059\u0000\u0000\u0177\u017a\u0005"+
		"6\u0000\u0000\u0178\u0179\u0005\u001a\u0000\u0000\u0179\u017b\u0005\u0015"+
		"\u0000\u0000\u017a\u0178\u0001\u0000\u0000\u0000\u017a\u017b\u0001\u0000"+
		"\u0000\u0000\u017b\u01d7\u0001\u0000\u0000\u0000\u017c\u017f\u00055\u0000"+
		"\u0000\u017d\u017e\u0005\u001a\u0000\u0000\u017e\u0180\u0005\u0015\u0000"+
		"\u0000\u017f\u017d\u0001\u0000\u0000\u0000\u017f\u0180\u0001\u0000\u0000"+
		"\u0000\u0180\u01d7\u0001\u0000\u0000\u0000\u0181\u0184\u0005\u0018\u0000"+
		"\u0000\u0182\u0183\u0005\u001a\u0000\u0000\u0183\u0185\u0005\u0015\u0000"+
		"\u0000\u0184\u0182\u0001\u0000\u0000\u0000\u0184\u0185\u0001\u0000\u0000"+
		"\u0000\u0185\u01d7\u0001\u0000\u0000\u0000\u0186\u0189\u0005\u0010\u0000"+
		"\u0000\u0187\u0188\u0005\u001a\u0000\u0000\u0188\u018a\u0005\u0015\u0000"+
		"\u0000\u0189\u0187\u0001\u0000\u0000\u0000\u0189\u018a\u0001\u0000\u0000"+
		"\u0000\u018a\u01d7\u0001\u0000\u0000\u0000\u018b\u018e\u0005\u000e\u0000"+
		"\u0000\u018c\u018d\u0005\u001a\u0000\u0000\u018d\u018f\u0005\u0015\u0000"+
		"\u0000\u018e\u018c\u0001\u0000\u0000\u0000\u018e\u018f\u0001\u0000\u0000"+
		"\u0000\u018f\u01d7\u0001\u0000\u0000\u0000\u0190\u0193\u00054\u0000\u0000"+
		"\u0191\u0192\u0005\u001a\u0000\u0000\u0192\u0194\u0005\u0015\u0000\u0000"+
		"\u0193\u0191\u0001\u0000\u0000\u0000\u0193\u0194\u0001\u0000\u0000\u0000"+
		"\u0194\u01d7\u0001\u0000\u0000\u0000\u0195\u0198\u0005\f\u0000\u0000\u0196"+
		"\u0197\u0005\u001a\u0000\u0000\u0197\u0199\u0005\u0015\u0000\u0000\u0198"+
		"\u0196\u0001\u0000\u0000\u0000\u0198\u0199\u0001\u0000\u0000\u0000\u0199"+
		"\u01d7\u0001\u0000\u0000\u0000\u019a\u019d\u0005:\u0000\u0000\u019b\u019c"+
		"\u0005\u001a\u0000\u0000\u019c\u019e\u0005\u0015\u0000\u0000\u019d\u019b"+
		"\u0001\u0000\u0000\u0000\u019d\u019e\u0001\u0000\u0000\u0000\u019e\u01d7"+
		"\u0001\u0000\u0000\u0000\u019f\u01d7\u0005;\u0000\u0000\u01a0\u01d7\u0005"+
		"<\u0000\u0000\u01a1\u01d7\u0005=\u0000\u0000\u01a2\u01a5\u0005>\u0000"+
		"\u0000\u01a3\u01a4\u0005\u001a\u0000\u0000\u01a4\u01a6\u0005\u0015\u0000"+
		"\u0000\u01a5\u01a3\u0001\u0000\u0000\u0000\u01a5\u01a6\u0001\u0000\u0000"+
		"\u0000\u01a6\u01d7\u0001\u0000\u0000\u0000\u01a7\u01aa\u0005?\u0000\u0000"+
		"\u01a8\u01a9\u0005\u001a\u0000\u0000\u01a9\u01ab\u0005\u0015\u0000\u0000"+
		"\u01aa\u01a8\u0001\u0000\u0000\u0000\u01aa\u01ab\u0001\u0000\u0000\u0000"+
		"\u01ab\u01d7\u0001\u0000\u0000\u0000\u01ac\u01ad\u00058\u0000\u0000\u01ad"+
		"\u01ae\u0005H\u0000\u0000\u01ae\u01b3\u00032\u0019\u0000\u01af\u01b0\u0005"+
		"A\u0000\u0000\u01b0\u01b2\u00032\u0019\u0000\u01b1\u01af\u0001\u0000\u0000"+
		"\u0000\u01b2\u01b5\u0001\u0000\u0000\u0000\u01b3\u01b1\u0001\u0000\u0000"+
		"\u0000\u01b3\u01b4\u0001\u0000\u0000\u0000\u01b4\u01b6\u0001\u0000\u0000"+
		"\u0000\u01b5\u01b3\u0001\u0000\u0000\u0000\u01b6\u01b9\u0005N\u0000\u0000"+
		"\u01b7\u01b8\u0005\u001a\u0000\u0000\u01b8\u01ba\u0005\u0015\u0000\u0000"+
		"\u01b9\u01b7\u0001\u0000\u0000\u0000\u01b9\u01ba\u0001\u0000\u0000\u0000"+
		"\u01ba\u01d7\u0001\u0000\u0000\u0000\u01bb\u01bc\u00057\u0000\u0000\u01bc"+
		"\u01bd\u0005H\u0000\u0000\u01bd\u01c2\u00032\u0019\u0000\u01be\u01bf\u0005"+
		"A\u0000\u0000\u01bf\u01c1\u00032\u0019\u0000\u01c0\u01be\u0001\u0000\u0000"+
		"\u0000\u01c1\u01c4\u0001\u0000\u0000\u0000\u01c2\u01c0\u0001\u0000\u0000"+
		"\u0000\u01c2\u01c3\u0001\u0000\u0000\u0000\u01c3\u01c5\u0001\u0000\u0000"+
		"\u0000\u01c4\u01c2\u0001\u0000\u0000\u0000\u01c5\u01c6\u0005N\u0000\u0000"+
		"\u01c6\u01d7\u0001\u0000\u0000\u0000\u01c7\u01c8\u0005@\u0000\u0000\u01c8"+
		"\u01c9\u0005H\u0000\u0000\u01c9\u01ce\u00034\u001a\u0000\u01ca\u01cb\u0005"+
		"A\u0000\u0000\u01cb\u01cd\u00034\u001a\u0000\u01cc\u01ca\u0001\u0000\u0000"+
		"\u0000\u01cd\u01d0\u0001\u0000\u0000\u0000\u01ce\u01cc\u0001\u0000\u0000"+
		"\u0000\u01ce\u01cf\u0001\u0000\u0000\u0000\u01cf\u01d1\u0001\u0000\u0000"+
		"\u0000\u01d0\u01ce\u0001\u0000\u0000\u0000\u01d1\u01d4\u0005N\u0000\u0000"+
		"\u01d2\u01d3\u0005\u001a\u0000\u0000\u01d3\u01d5\u0005\u0015\u0000\u0000"+
		"\u01d4\u01d2\u0001\u0000\u0000\u0000\u01d4\u01d5\u0001\u0000\u0000\u0000"+
		"\u01d5\u01d7\u0001\u0000\u0000\u0000\u01d6\u0176\u0001\u0000\u0000\u0000"+
		"\u01d6\u0177\u0001\u0000\u0000\u0000\u01d6\u017c\u0001\u0000\u0000\u0000"+
		"\u01d6\u0181\u0001\u0000\u0000\u0000\u01d6\u0186\u0001\u0000\u0000\u0000"+
		"\u01d6\u018b\u0001\u0000\u0000\u0000\u01d6\u0190\u0001\u0000\u0000\u0000"+
		"\u01d6\u0195\u0001\u0000\u0000\u0000\u01d6\u019a\u0001\u0000\u0000\u0000"+
		"\u01d6\u019f\u0001\u0000\u0000\u0000\u01d6\u01a0\u0001\u0000\u0000\u0000"+
		"\u01d6\u01a1\u0001\u0000\u0000\u0000\u01d6\u01a2\u0001\u0000\u0000\u0000"+
		"\u01d6\u01a7\u0001\u0000\u0000\u0000\u01d6\u01ac\u0001\u0000\u0000\u0000"+
		"\u01d6\u01bb\u0001\u0000\u0000\u0000\u01d6\u01c7\u0001\u0000\u0000\u0000"+
		"\u01d77\u0001\u0000\u0000\u0000\u01d8\u01d9\u0005T\u0000\u0000\u01d99"+
		"\u0001\u0000\u0000\u0000+=LS\\lw|\u0086\u0092\u00a7\u00ac\u00b7\u00c7"+
		"\u00d2\u00f2\u00fc\u0103\u010e\u011b\u0120\u0128\u0131\u0146\u0150\u0158"+
		"\u015a\u0171\u017a\u017f\u0184\u0189\u018e\u0193\u0198\u019d\u01a5\u01aa"+
		"\u01b3\u01b9\u01c2\u01ce\u01d4\u01d6";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}