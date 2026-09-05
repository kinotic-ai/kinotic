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
		CREATE=9, DATA=10, DATA_RETENTION=11, DATE=12, DELETE=13, DOCUMENT_ID=14, 
		DOUBLE=15, EXISTS=16, FLOAT=17, FOR=18, FROM=19, IF=20, INDEX=21, INDEXED=22, 
		INSERT=23, INTO=24, LONG=25, MAX_DOCS=26, NOT=27, NUMBER_OF_REPLICAS=28, 
		NUMBER_OF_SHARDS=29, OR=30, PROCEED=31, QUERY=32, REFRESH=33, REINDEX=34, 
		ROUTING=35, SCRIPT=36, SET=37, SIZE=38, SLICES=39, SOURCE_FIELDS=40, STREAM=41, 
		TABLE=42, TEMPLATE=43, TIME_REFERENCE=44, UPDATE=45, USING=46, VALUES=47, 
		WHERE=48, WITH=49, WAIT=50, TRUE=51, FALSE=52, SKIP_IF_NO_SOURCE=53, BOOLEAN=54, 
		INTEGER=55, KEYWORD=56, NESTED=57, OBJECT=58, TEXT=59, JSON=60, BINARY=61, 
		GEO_POINT=62, GEO_SHAPE=63, UUID=64, DECIMAL=65, UNION=66, ASSIGN=67, 
		COLON=68, COMMA=69, DIVIDE=70, EQUALS=71, GREATER_THAN=72, GREATER_THAN_EQUALS=73, 
		LBRACE=74, LBRACKET=75, LESS_THAN=76, LESS_THAN_EQUALS=77, LPAREN=78, 
		MINUS=79, MULTIPLY=80, NOT_EQUALS=81, PLUS=82, RBRACE=83, RBRACKET=84, 
		RPAREN=85, SEMICOLON=86, BOOLEAN_LITERAL=87, DECIMAL_LITERAL=88, NULL_LITERAL=89, 
		ID=90, INTEGER_LITERAL=91, STRING=92, COMMENT=93, WS=94;
	public static final int
		RULE_migrations = 0, RULE_statement = 1, RULE_createTableStatement = 2, 
		RULE_createDataStreamStatement = 3, RULE_dataStreamOption = 4, RULE_createComponentTemplateStatement = 5, 
		RULE_createIndexTemplateStatement = 6, RULE_templatePart = 7, RULE_alterTableStatement = 8, 
		RULE_reindexStatement = 9, RULE_reindexOptions = 10, RULE_reindexOption = 11, 
		RULE_updateStatement = 12, RULE_deleteStatement = 13, RULE_insertStatement = 14, 
		RULE_insertOption = 15, RULE_valueList = 16, RULE_value = 17, RULE_namedParameter = 18, 
		RULE_objectLiteral = 19, RULE_objectField = 20, RULE_arrayLiteral = 21, 
		RULE_numberLiteral = 22, RULE_assignment = 23, RULE_expression = 24, RULE_operator = 25, 
		RULE_whereClause = 26, RULE_condition = 27, RULE_comparisonOperator = 28, 
		RULE_tableName = 29, RULE_columnName = 30, RULE_columnDefinition = 31, 
		RULE_unionVariant = 32, RULE_type = 33, RULE_comment = 34;
	private static String[] makeRuleNames() {
		return new String[] {
			"migrations", "statement", "createTableStatement", "createDataStreamStatement", 
			"dataStreamOption", "createComponentTemplateStatement", "createIndexTemplateStatement", 
			"templatePart", "alterTableStatement", "reindexStatement", "reindexOptions", 
			"reindexOption", "updateStatement", "deleteStatement", "insertStatement", 
			"insertOption", "valueList", "value", "namedParameter", "objectLiteral", 
			"objectField", "arrayLiteral", "numberLiteral", "assignment", "expression", 
			"operator", "whereClause", "condition", "comparisonOperator", "tableName", 
			"columnName", "columnDefinition", "unionVariant", "type", "comment"
		};
	}
	public static final String[] ruleNames = makeRuleNames();

	private static String[] makeLiteralNames() {
		return new String[] {
			null, "'ABORT'", "'ADD'", "'ALTER'", "'AND'", "'AUTO'", "'COLUMN'", "'COMPONENT'", 
			"'CONFLICTS'", "'CREATE'", "'DATA'", "'DATA_RETENTION'", "'DATE'", "'DELETE'", 
			"'DOCUMENT_ID'", "'DOUBLE'", "'EXISTS'", "'FLOAT'", "'FOR'", "'FROM'", 
			"'IF'", "'INDEX'", "'INDEXED'", "'INSERT'", "'INTO'", "'LONG'", "'MAX_DOCS'", 
			"'NOT'", "'NUMBER_OF_REPLICAS'", "'NUMBER_OF_SHARDS'", "'OR'", "'PROCEED'", 
			"'QUERY'", "'REFRESH'", "'REINDEX'", "'ROUTING'", "'SCRIPT'", "'SET'", 
			"'SIZE'", "'SLICES'", "'SOURCE_FIELDS'", "'STREAM'", "'TABLE'", "'TEMPLATE'", 
			"'TIME_REFERENCE'", "'UPDATE'", "'USING'", "'VALUES'", "'WHERE'", "'WITH'", 
			"'WAIT'", "'TRUE'", "'FALSE'", "'SKIP_IF_NO_SOURCE'", "'BOOLEAN'", "'INTEGER'", 
			"'KEYWORD'", "'NESTED'", "'OBJECT'", "'TEXT'", "'JSON'", "'BINARY'", 
			"'GEO_POINT'", "'GEO_SHAPE'", "'UUID'", "'DECIMAL'", "'UNION'", "'='", 
			"':'", "','", "'/'", "'=='", "'>'", "'>='", "'{'", "'['", "'<'", "'<='", 
			"'('", "'-'", "'*'", "'!='", "'+'", "'}'", "']'", "')'", "';'", null, 
			null, "'null'"
		};
	}
	private static final String[] _LITERAL_NAMES = makeLiteralNames();
	private static String[] makeSymbolicNames() {
		return new String[] {
			null, "ABORT", "ADD", "ALTER", "AND", "AUTO", "COLUMN", "COMPONENT", 
			"CONFLICTS", "CREATE", "DATA", "DATA_RETENTION", "DATE", "DELETE", "DOCUMENT_ID", 
			"DOUBLE", "EXISTS", "FLOAT", "FOR", "FROM", "IF", "INDEX", "INDEXED", 
			"INSERT", "INTO", "LONG", "MAX_DOCS", "NOT", "NUMBER_OF_REPLICAS", "NUMBER_OF_SHARDS", 
			"OR", "PROCEED", "QUERY", "REFRESH", "REINDEX", "ROUTING", "SCRIPT", 
			"SET", "SIZE", "SLICES", "SOURCE_FIELDS", "STREAM", "TABLE", "TEMPLATE", 
			"TIME_REFERENCE", "UPDATE", "USING", "VALUES", "WHERE", "WITH", "WAIT", 
			"TRUE", "FALSE", "SKIP_IF_NO_SOURCE", "BOOLEAN", "INTEGER", "KEYWORD", 
			"NESTED", "OBJECT", "TEXT", "JSON", "BINARY", "GEO_POINT", "GEO_SHAPE", 
			"UUID", "DECIMAL", "UNION", "ASSIGN", "COLON", "COMMA", "DIVIDE", "EQUALS", 
			"GREATER_THAN", "GREATER_THAN_EQUALS", "LBRACE", "LBRACKET", "LESS_THAN", 
			"LESS_THAN_EQUALS", "LPAREN", "MINUS", "MULTIPLY", "NOT_EQUALS", "PLUS", 
			"RBRACE", "RBRACKET", "RPAREN", "SEMICOLON", "BOOLEAN_LITERAL", "DECIMAL_LITERAL", 
			"NULL_LITERAL", "ID", "INTEGER_LITERAL", "STRING", "COMMENT", "WS"
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
			setState(73);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while ((((_la) & ~0x3f) == 0 && ((1L << _la) & 35201560355336L) != 0) || _la==COMMENT) {
				{
				{
				setState(70);
				statement();
				}
				}
				setState(75);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(76);
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
			setState(88);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,1,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(78);
				createTableStatement();
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(79);
				createDataStreamStatement();
				}
				break;
			case 3:
				enterOuterAlt(_localctx, 3);
				{
				setState(80);
				createComponentTemplateStatement();
				}
				break;
			case 4:
				enterOuterAlt(_localctx, 4);
				{
				setState(81);
				createIndexTemplateStatement();
				}
				break;
			case 5:
				enterOuterAlt(_localctx, 5);
				{
				setState(82);
				alterTableStatement();
				}
				break;
			case 6:
				enterOuterAlt(_localctx, 6);
				{
				setState(83);
				reindexStatement();
				}
				break;
			case 7:
				enterOuterAlt(_localctx, 7);
				{
				setState(84);
				updateStatement();
				}
				break;
			case 8:
				enterOuterAlt(_localctx, 8);
				{
				setState(85);
				deleteStatement();
				}
				break;
			case 9:
				enterOuterAlt(_localctx, 9);
				{
				setState(86);
				insertStatement();
				}
				break;
			case 10:
				enterOuterAlt(_localctx, 10);
				{
				setState(87);
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
			setState(90);
			match(CREATE);
			setState(91);
			match(TABLE);
			setState(95);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==IF) {
				{
				setState(92);
				match(IF);
				setState(93);
				match(NOT);
				setState(94);
				match(EXISTS);
				}
			}

			setState(97);
			match(ID);
			setState(98);
			match(LPAREN);
			setState(99);
			columnDefinition();
			setState(104);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==COMMA) {
				{
				{
				setState(100);
				match(COMMA);
				setState(101);
				columnDefinition();
				}
				}
				setState(106);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(107);
			match(RPAREN);
			setState(108);
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
			setState(110);
			match(CREATE);
			setState(111);
			match(DATA);
			setState(112);
			match(STREAM);
			setState(113);
			match(ID);
			setState(114);
			match(LPAREN);
			setState(115);
			columnDefinition();
			setState(120);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==COMMA) {
				{
				{
				setState(116);
				match(COMMA);
				setState(117);
				columnDefinition();
				}
				}
				setState(122);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(123);
			match(RPAREN);
			setState(136);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==WITH) {
				{
				setState(124);
				match(WITH);
				setState(125);
				match(LPAREN);
				setState(126);
				dataStreamOption();
				setState(131);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while (_la==COMMA) {
					{
					{
					setState(127);
					match(COMMA);
					setState(128);
					dataStreamOption();
					}
					}
					setState(133);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}
				setState(134);
				match(RPAREN);
				}
			}

			setState(138);
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
			setState(146);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case DATA_RETENTION:
				enterOuterAlt(_localctx, 1);
				{
				setState(140);
				match(DATA_RETENTION);
				setState(141);
				match(ASSIGN);
				setState(142);
				match(STRING);
				}
				break;
			case TIME_REFERENCE:
				enterOuterAlt(_localctx, 2);
				{
				setState(143);
				match(TIME_REFERENCE);
				setState(144);
				match(ASSIGN);
				setState(145);
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
			setState(148);
			match(CREATE);
			setState(149);
			match(COMPONENT);
			setState(150);
			match(TEMPLATE);
			setState(151);
			match(ID);
			setState(152);
			match(LPAREN);
			setState(153);
			templatePart();
			setState(158);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==COMMA) {
				{
				{
				setState(154);
				match(COMMA);
				setState(155);
				templatePart();
				}
				}
				setState(160);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(161);
			match(RPAREN);
			setState(162);
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
			setState(164);
			match(CREATE);
			setState(165);
			match(INDEX);
			setState(166);
			match(TEMPLATE);
			setState(167);
			match(ID);
			setState(168);
			match(FOR);
			setState(169);
			match(STRING);
			setState(170);
			match(USING);
			setState(171);
			match(STRING);
			setState(184);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==WITH) {
				{
				setState(172);
				match(WITH);
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
				}
			}

			setState(186);
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
			setState(195);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case NUMBER_OF_SHARDS:
				enterOuterAlt(_localctx, 1);
				{
				setState(188);
				match(NUMBER_OF_SHARDS);
				setState(189);
				match(ASSIGN);
				setState(190);
				match(INTEGER_LITERAL);
				}
				break;
			case NUMBER_OF_REPLICAS:
				enterOuterAlt(_localctx, 2);
				{
				setState(191);
				match(NUMBER_OF_REPLICAS);
				setState(192);
				match(ASSIGN);
				setState(193);
				match(INTEGER_LITERAL);
				}
				break;
			case ID:
				enterOuterAlt(_localctx, 3);
				{
				setState(194);
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
			setState(197);
			match(ALTER);
			setState(198);
			match(TABLE);
			setState(199);
			match(ID);
			setState(200);
			match(ADD);
			setState(201);
			match(COLUMN);
			setState(202);
			match(ID);
			setState(203);
			type();
			setState(204);
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
			setState(206);
			match(REINDEX);
			setState(207);
			match(ID);
			setState(208);
			match(INTO);
			setState(209);
			match(ID);
			setState(211);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==WITH) {
				{
				setState(210);
				reindexOptions();
				}
			}

			setState(213);
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
			setState(215);
			match(WITH);
			setState(216);
			match(LPAREN);
			setState(217);
			reindexOption();
			setState(222);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==COMMA) {
				{
				{
				setState(218);
				match(COMMA);
				setState(219);
				reindexOption();
				}
				}
				setState(224);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(225);
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
			setState(254);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case CONFLICTS:
				enterOuterAlt(_localctx, 1);
				{
				setState(227);
				match(CONFLICTS);
				setState(228);
				match(ASSIGN);
				setState(229);
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
				setState(230);
				match(MAX_DOCS);
				setState(231);
				match(ASSIGN);
				setState(232);
				match(INTEGER_LITERAL);
				}
				break;
			case SLICES:
				enterOuterAlt(_localctx, 3);
				{
				setState(233);
				match(SLICES);
				setState(234);
				match(ASSIGN);
				setState(235);
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
				setState(236);
				match(SIZE);
				setState(237);
				match(ASSIGN);
				setState(238);
				match(INTEGER_LITERAL);
				}
				break;
			case SOURCE_FIELDS:
				enterOuterAlt(_localctx, 5);
				{
				setState(239);
				match(SOURCE_FIELDS);
				setState(240);
				match(ASSIGN);
				setState(241);
				match(STRING);
				}
				break;
			case QUERY:
				enterOuterAlt(_localctx, 6);
				{
				setState(242);
				match(QUERY);
				setState(243);
				match(ASSIGN);
				setState(244);
				match(STRING);
				}
				break;
			case SCRIPT:
				enterOuterAlt(_localctx, 7);
				{
				setState(245);
				match(SCRIPT);
				setState(246);
				match(ASSIGN);
				setState(247);
				match(STRING);
				}
				break;
			case WAIT:
				enterOuterAlt(_localctx, 8);
				{
				setState(248);
				match(WAIT);
				setState(249);
				match(ASSIGN);
				setState(250);
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
				setState(251);
				match(SKIP_IF_NO_SOURCE);
				setState(252);
				match(ASSIGN);
				setState(253);
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
			setState(256);
			match(UPDATE);
			setState(257);
			match(ID);
			setState(258);
			match(SET);
			setState(259);
			assignment();
			setState(264);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==COMMA) {
				{
				{
				setState(260);
				match(COMMA);
				setState(261);
				assignment();
				}
				}
				setState(266);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(267);
			match(WHERE);
			setState(268);
			whereClause(0);
			setState(271);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==WITH) {
				{
				setState(269);
				match(WITH);
				setState(270);
				match(REFRESH);
				}
			}

			setState(273);
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
			setState(275);
			match(DELETE);
			setState(276);
			match(FROM);
			setState(277);
			match(ID);
			setState(278);
			match(WHERE);
			setState(279);
			whereClause(0);
			setState(282);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==WITH) {
				{
				setState(280);
				match(WITH);
				setState(281);
				match(REFRESH);
				}
			}

			setState(284);
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
			setState(286);
			match(INSERT);
			setState(287);
			match(INTO);
			setState(288);
			tableName();
			setState(300);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==LPAREN) {
				{
				setState(289);
				match(LPAREN);
				setState(290);
				columnName();
				setState(295);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while (_la==COMMA) {
					{
					{
					setState(291);
					match(COMMA);
					setState(292);
					columnName();
					}
					}
					setState(297);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}
				setState(298);
				match(RPAREN);
				}
			}

			setState(302);
			match(VALUES);
			setState(303);
			match(LPAREN);
			setState(304);
			valueList();
			setState(305);
			match(RPAREN);
			setState(315);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==WITH) {
				{
				setState(306);
				match(WITH);
				setState(307);
				insertOption();
				setState(312);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while (_la==COMMA) {
					{
					{
					setState(308);
					match(COMMA);
					setState(309);
					insertOption();
					}
					}
					setState(314);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}
				}
			}

			setState(317);
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
		enterRule(_localctx, 30, RULE_insertOption);
		try {
			setState(324);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case REFRESH:
				enterOuterAlt(_localctx, 1);
				{
				setState(319);
				match(REFRESH);
				}
				break;
			case ROUTING:
				enterOuterAlt(_localctx, 2);
				{
				setState(320);
				match(ROUTING);
				setState(321);
				match(STRING);
				}
				break;
			case DOCUMENT_ID:
				enterOuterAlt(_localctx, 3);
				{
				setState(322);
				match(DOCUMENT_ID);
				setState(323);
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
		enterRule(_localctx, 32, RULE_valueList);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(326);
			value();
			setState(331);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==COMMA) {
				{
				{
				setState(327);
				match(COMMA);
				setState(328);
				value();
				}
				}
				setState(333);
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
		enterRule(_localctx, 34, RULE_value);
		try {
			setState(341);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case STRING:
				enterOuterAlt(_localctx, 1);
				{
				setState(334);
				match(STRING);
				}
				break;
			case MINUS:
			case DECIMAL_LITERAL:
			case INTEGER_LITERAL:
				enterOuterAlt(_localctx, 2);
				{
				setState(335);
				numberLiteral();
				}
				break;
			case BOOLEAN_LITERAL:
				enterOuterAlt(_localctx, 3);
				{
				setState(336);
				match(BOOLEAN_LITERAL);
				}
				break;
			case NULL_LITERAL:
				enterOuterAlt(_localctx, 4);
				{
				setState(337);
				match(NULL_LITERAL);
				}
				break;
			case COLON:
				enterOuterAlt(_localctx, 5);
				{
				setState(338);
				namedParameter();
				}
				break;
			case LBRACE:
				enterOuterAlt(_localctx, 6);
				{
				setState(339);
				objectLiteral();
				}
				break;
			case LBRACKET:
				enterOuterAlt(_localctx, 7);
				{
				setState(340);
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
		enterRule(_localctx, 36, RULE_namedParameter);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(343);
			match(COLON);
			setState(344);
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
		enterRule(_localctx, 38, RULE_objectLiteral);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(346);
			match(LBRACE);
			setState(355);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==ID || _la==STRING) {
				{
				setState(347);
				objectField();
				setState(352);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while (_la==COMMA) {
					{
					{
					setState(348);
					match(COMMA);
					setState(349);
					objectField();
					}
					}
					setState(354);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}
				}
			}

			setState(357);
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
		enterRule(_localctx, 40, RULE_objectField);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(359);
			_la = _input.LA(1);
			if ( !(_la==ID || _la==STRING) ) {
			_errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			setState(360);
			match(COLON);
			setState(361);
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
		enterRule(_localctx, 42, RULE_arrayLiteral);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(363);
			match(LBRACKET);
			setState(372);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (((((_la - 68)) & ~0x3f) == 0 && ((1L << (_la - 68)) & 28838081L) != 0)) {
				{
				setState(364);
				value();
				setState(369);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while (_la==COMMA) {
					{
					{
					setState(365);
					match(COMMA);
					setState(366);
					value();
					}
					}
					setState(371);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}
				}
			}

			setState(374);
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
		enterRule(_localctx, 44, RULE_numberLiteral);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(377);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==MINUS) {
				{
				setState(376);
				match(MINUS);
				}
			}

			setState(379);
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
		enterRule(_localctx, 46, RULE_assignment);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(381);
			match(ID);
			setState(382);
			match(ASSIGN);
			setState(383);
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
		enterRule(_localctx, 48, RULE_expression);
		try {
			setState(394);
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
				setState(385);
				value();
				}
				break;
			case ID:
				enterOuterAlt(_localctx, 2);
				{
				setState(386);
				match(ID);
				setState(387);
				operator();
				setState(388);
				expression();
				}
				break;
			case LPAREN:
				enterOuterAlt(_localctx, 3);
				{
				setState(390);
				match(LPAREN);
				setState(391);
				expression();
				setState(392);
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
		enterRule(_localctx, 50, RULE_operator);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(396);
			_la = _input.LA(1);
			if ( !(((((_la - 70)) & ~0x3f) == 0 && ((1L << (_la - 70)) & 5635L) != 0)) ) {
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
		int _startState = 52;
		enterRecursionRule(_localctx, 52, RULE_whereClause, _p);
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(404);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case ID:
				{
				setState(399);
				condition();
				}
				break;
			case LPAREN:
				{
				setState(400);
				match(LPAREN);
				setState(401);
				whereClause(0);
				setState(402);
				match(RPAREN);
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
			_ctx.stop = _input.LT(-1);
			setState(414);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,33,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					if ( _parseListeners!=null ) triggerExitRuleEvent();
					_prevctx = _localctx;
					{
					setState(412);
					_errHandler.sync(this);
					switch ( getInterpreter().adaptivePredict(_input,32,_ctx) ) {
					case 1:
						{
						_localctx = new WhereClauseContext(_parentctx, _parentState);
						pushNewRecursionContext(_localctx, _startState, RULE_whereClause);
						setState(406);
						if (!(precpred(_ctx, 2))) throw new FailedPredicateException(this, "precpred(_ctx, 2)");
						setState(407);
						match(AND);
						setState(408);
						whereClause(3);
						}
						break;
					case 2:
						{
						_localctx = new WhereClauseContext(_parentctx, _parentState);
						pushNewRecursionContext(_localctx, _startState, RULE_whereClause);
						setState(409);
						if (!(precpred(_ctx, 1))) throw new FailedPredicateException(this, "precpred(_ctx, 1)");
						setState(410);
						match(OR);
						setState(411);
						whereClause(2);
						}
						break;
					}
					} 
				}
				setState(416);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,33,_ctx);
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
		enterRule(_localctx, 54, RULE_condition);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(417);
			match(ID);
			setState(418);
			comparisonOperator();
			setState(423);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case COLON:
				{
				setState(419);
				namedParameter();
				}
				break;
			case STRING:
				{
				setState(420);
				match(STRING);
				}
				break;
			case MINUS:
			case DECIMAL_LITERAL:
			case INTEGER_LITERAL:
				{
				setState(421);
				numberLiteral();
				}
				break;
			case BOOLEAN_LITERAL:
				{
				setState(422);
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
		enterRule(_localctx, 56, RULE_comparisonOperator);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(425);
			_la = _input.LA(1);
			if ( !(((((_la - 71)) & ~0x3f) == 0 && ((1L << (_la - 71)) & 1127L) != 0)) ) {
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
		enterRule(_localctx, 58, RULE_tableName);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(427);
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
		enterRule(_localctx, 60, RULE_columnName);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(429);
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
		enterRule(_localctx, 62, RULE_columnDefinition);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(431);
			match(ID);
			setState(432);
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
		enterRule(_localctx, 64, RULE_unionVariant);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(434);
			match(ID);
			setState(435);
			match(LPAREN);
			setState(436);
			columnDefinition();
			setState(441);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==COMMA) {
				{
				{
				setState(437);
				match(COMMA);
				setState(438);
				columnDefinition();
				}
				}
				setState(443);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(444);
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
		enterRule(_localctx, 66, RULE_type);
		int _la;
		try {
			setState(542);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case TEXT:
				enterOuterAlt(_localctx, 1);
				{
				setState(446);
				match(TEXT);
				}
				break;
			case KEYWORD:
				enterOuterAlt(_localctx, 2);
				{
				setState(447);
				match(KEYWORD);
				setState(450);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==NOT) {
					{
					setState(448);
					match(NOT);
					setState(449);
					match(INDEXED);
					}
				}

				}
				break;
			case INTEGER:
				enterOuterAlt(_localctx, 3);
				{
				setState(452);
				match(INTEGER);
				setState(455);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==NOT) {
					{
					setState(453);
					match(NOT);
					setState(454);
					match(INDEXED);
					}
				}

				}
				break;
			case LONG:
				enterOuterAlt(_localctx, 4);
				{
				setState(457);
				match(LONG);
				setState(460);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==NOT) {
					{
					setState(458);
					match(NOT);
					setState(459);
					match(INDEXED);
					}
				}

				}
				break;
			case FLOAT:
				enterOuterAlt(_localctx, 5);
				{
				setState(462);
				match(FLOAT);
				setState(465);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==NOT) {
					{
					setState(463);
					match(NOT);
					setState(464);
					match(INDEXED);
					}
				}

				}
				break;
			case DOUBLE:
				enterOuterAlt(_localctx, 6);
				{
				setState(467);
				match(DOUBLE);
				setState(470);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==NOT) {
					{
					setState(468);
					match(NOT);
					setState(469);
					match(INDEXED);
					}
				}

				}
				break;
			case BOOLEAN:
				enterOuterAlt(_localctx, 7);
				{
				setState(472);
				match(BOOLEAN);
				setState(475);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==NOT) {
					{
					setState(473);
					match(NOT);
					setState(474);
					match(INDEXED);
					}
				}

				}
				break;
			case DATE:
				enterOuterAlt(_localctx, 8);
				{
				setState(477);
				match(DATE);
				setState(480);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==NOT) {
					{
					setState(478);
					match(NOT);
					setState(479);
					match(INDEXED);
					}
				}

				}
				break;
			case JSON:
				enterOuterAlt(_localctx, 9);
				{
				setState(482);
				match(JSON);
				setState(485);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==NOT) {
					{
					setState(483);
					match(NOT);
					setState(484);
					match(INDEXED);
					}
				}

				}
				break;
			case BINARY:
				enterOuterAlt(_localctx, 10);
				{
				setState(487);
				match(BINARY);
				}
				break;
			case GEO_POINT:
				enterOuterAlt(_localctx, 11);
				{
				setState(488);
				match(GEO_POINT);
				}
				break;
			case GEO_SHAPE:
				enterOuterAlt(_localctx, 12);
				{
				setState(489);
				match(GEO_SHAPE);
				}
				break;
			case UUID:
				enterOuterAlt(_localctx, 13);
				{
				setState(490);
				match(UUID);
				setState(493);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==NOT) {
					{
					setState(491);
					match(NOT);
					setState(492);
					match(INDEXED);
					}
				}

				}
				break;
			case DECIMAL:
				enterOuterAlt(_localctx, 14);
				{
				setState(495);
				match(DECIMAL);
				setState(498);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==NOT) {
					{
					setState(496);
					match(NOT);
					setState(497);
					match(INDEXED);
					}
				}

				}
				break;
			case OBJECT:
				enterOuterAlt(_localctx, 15);
				{
				setState(500);
				match(OBJECT);
				setState(501);
				match(LPAREN);
				setState(502);
				columnDefinition();
				setState(507);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while (_la==COMMA) {
					{
					{
					setState(503);
					match(COMMA);
					setState(504);
					columnDefinition();
					}
					}
					setState(509);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}
				setState(510);
				match(RPAREN);
				setState(513);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==NOT) {
					{
					setState(511);
					match(NOT);
					setState(512);
					match(INDEXED);
					}
				}

				}
				break;
			case NESTED:
				enterOuterAlt(_localctx, 16);
				{
				setState(515);
				match(NESTED);
				setState(516);
				match(LPAREN);
				setState(517);
				columnDefinition();
				setState(522);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while (_la==COMMA) {
					{
					{
					setState(518);
					match(COMMA);
					setState(519);
					columnDefinition();
					}
					}
					setState(524);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}
				setState(525);
				match(RPAREN);
				}
				break;
			case UNION:
				enterOuterAlt(_localctx, 17);
				{
				setState(527);
				match(UNION);
				setState(528);
				match(LPAREN);
				setState(529);
				unionVariant();
				setState(534);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while (_la==COMMA) {
					{
					{
					setState(530);
					match(COMMA);
					setState(531);
					unionVariant();
					}
					}
					setState(536);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}
				setState(537);
				match(RPAREN);
				setState(540);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==NOT) {
					{
					setState(538);
					match(NOT);
					setState(539);
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
		enterRule(_localctx, 68, RULE_comment);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(544);
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
		case 26:
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
		"\u0004\u0001^\u0223\u0002\u0000\u0007\u0000\u0002\u0001\u0007\u0001\u0002"+
		"\u0002\u0007\u0002\u0002\u0003\u0007\u0003\u0002\u0004\u0007\u0004\u0002"+
		"\u0005\u0007\u0005\u0002\u0006\u0007\u0006\u0002\u0007\u0007\u0007\u0002"+
		"\b\u0007\b\u0002\t\u0007\t\u0002\n\u0007\n\u0002\u000b\u0007\u000b\u0002"+
		"\f\u0007\f\u0002\r\u0007\r\u0002\u000e\u0007\u000e\u0002\u000f\u0007\u000f"+
		"\u0002\u0010\u0007\u0010\u0002\u0011\u0007\u0011\u0002\u0012\u0007\u0012"+
		"\u0002\u0013\u0007\u0013\u0002\u0014\u0007\u0014\u0002\u0015\u0007\u0015"+
		"\u0002\u0016\u0007\u0016\u0002\u0017\u0007\u0017\u0002\u0018\u0007\u0018"+
		"\u0002\u0019\u0007\u0019\u0002\u001a\u0007\u001a\u0002\u001b\u0007\u001b"+
		"\u0002\u001c\u0007\u001c\u0002\u001d\u0007\u001d\u0002\u001e\u0007\u001e"+
		"\u0002\u001f\u0007\u001f\u0002 \u0007 \u0002!\u0007!\u0002\"\u0007\"\u0001"+
		"\u0000\u0005\u0000H\b\u0000\n\u0000\f\u0000K\t\u0000\u0001\u0000\u0001"+
		"\u0000\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001"+
		"\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0003\u0001Y\b"+
		"\u0001\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0003"+
		"\u0002`\b\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001"+
		"\u0002\u0005\u0002g\b\u0002\n\u0002\f\u0002j\t\u0002\u0001\u0002\u0001"+
		"\u0002\u0001\u0002\u0001\u0003\u0001\u0003\u0001\u0003\u0001\u0003\u0001"+
		"\u0003\u0001\u0003\u0001\u0003\u0001\u0003\u0005\u0003w\b\u0003\n\u0003"+
		"\f\u0003z\t\u0003\u0001\u0003\u0001\u0003\u0001\u0003\u0001\u0003\u0001"+
		"\u0003\u0001\u0003\u0005\u0003\u0082\b\u0003\n\u0003\f\u0003\u0085\t\u0003"+
		"\u0001\u0003\u0001\u0003\u0003\u0003\u0089\b\u0003\u0001\u0003\u0001\u0003"+
		"\u0001\u0004\u0001\u0004\u0001\u0004\u0001\u0004\u0001\u0004\u0001\u0004"+
		"\u0003\u0004\u0093\b\u0004\u0001\u0005\u0001\u0005\u0001\u0005\u0001\u0005"+
		"\u0001\u0005\u0001\u0005\u0001\u0005\u0001\u0005\u0005\u0005\u009d\b\u0005"+
		"\n\u0005\f\u0005\u00a0\t\u0005\u0001\u0005\u0001\u0005\u0001\u0005\u0001"+
		"\u0006\u0001\u0006\u0001\u0006\u0001\u0006\u0001\u0006\u0001\u0006\u0001"+
		"\u0006\u0001\u0006\u0001\u0006\u0001\u0006\u0001\u0006\u0001\u0006\u0001"+
		"\u0006\u0005\u0006\u00b2\b\u0006\n\u0006\f\u0006\u00b5\t\u0006\u0001\u0006"+
		"\u0001\u0006\u0003\u0006\u00b9\b\u0006\u0001\u0006\u0001\u0006\u0001\u0007"+
		"\u0001\u0007\u0001\u0007\u0001\u0007\u0001\u0007\u0001\u0007\u0001\u0007"+
		"\u0003\u0007\u00c4\b\u0007\u0001\b\u0001\b\u0001\b\u0001\b\u0001\b\u0001"+
		"\b\u0001\b\u0001\b\u0001\b\u0001\t\u0001\t\u0001\t\u0001\t\u0001\t\u0003"+
		"\t\u00d4\b\t\u0001\t\u0001\t\u0001\n\u0001\n\u0001\n\u0001\n\u0001\n\u0005"+
		"\n\u00dd\b\n\n\n\f\n\u00e0\t\n\u0001\n\u0001\n\u0001\u000b\u0001\u000b"+
		"\u0001\u000b\u0001\u000b\u0001\u000b\u0001\u000b\u0001\u000b\u0001\u000b"+
		"\u0001\u000b\u0001\u000b\u0001\u000b\u0001\u000b\u0001\u000b\u0001\u000b"+
		"\u0001\u000b\u0001\u000b\u0001\u000b\u0001\u000b\u0001\u000b\u0001\u000b"+
		"\u0001\u000b\u0001\u000b\u0001\u000b\u0001\u000b\u0001\u000b\u0001\u000b"+
		"\u0001\u000b\u0003\u000b\u00ff\b\u000b\u0001\f\u0001\f\u0001\f\u0001\f"+
		"\u0001\f\u0001\f\u0005\f\u0107\b\f\n\f\f\f\u010a\t\f\u0001\f\u0001\f\u0001"+
		"\f\u0001\f\u0003\f\u0110\b\f\u0001\f\u0001\f\u0001\r\u0001\r\u0001\r\u0001"+
		"\r\u0001\r\u0001\r\u0001\r\u0003\r\u011b\b\r\u0001\r\u0001\r\u0001\u000e"+
		"\u0001\u000e\u0001\u000e\u0001\u000e\u0001\u000e\u0001\u000e\u0001\u000e"+
		"\u0005\u000e\u0126\b\u000e\n\u000e\f\u000e\u0129\t\u000e\u0001\u000e\u0001"+
		"\u000e\u0003\u000e\u012d\b\u000e\u0001\u000e\u0001\u000e\u0001\u000e\u0001"+
		"\u000e\u0001\u000e\u0001\u000e\u0001\u000e\u0001\u000e\u0005\u000e\u0137"+
		"\b\u000e\n\u000e\f\u000e\u013a\t\u000e\u0003\u000e\u013c\b\u000e\u0001"+
		"\u000e\u0001\u000e\u0001\u000f\u0001\u000f\u0001\u000f\u0001\u000f\u0001"+
		"\u000f\u0003\u000f\u0145\b\u000f\u0001\u0010\u0001\u0010\u0001\u0010\u0005"+
		"\u0010\u014a\b\u0010\n\u0010\f\u0010\u014d\t\u0010\u0001\u0011\u0001\u0011"+
		"\u0001\u0011\u0001\u0011\u0001\u0011\u0001\u0011\u0001\u0011\u0003\u0011"+
		"\u0156\b\u0011\u0001\u0012\u0001\u0012\u0001\u0012\u0001\u0013\u0001\u0013"+
		"\u0001\u0013\u0001\u0013\u0005\u0013\u015f\b\u0013\n\u0013\f\u0013\u0162"+
		"\t\u0013\u0003\u0013\u0164\b\u0013\u0001\u0013\u0001\u0013\u0001\u0014"+
		"\u0001\u0014\u0001\u0014\u0001\u0014\u0001\u0015\u0001\u0015\u0001\u0015"+
		"\u0001\u0015\u0005\u0015\u0170\b\u0015\n\u0015\f\u0015\u0173\t\u0015\u0003"+
		"\u0015\u0175\b\u0015\u0001\u0015\u0001\u0015\u0001\u0016\u0003\u0016\u017a"+
		"\b\u0016\u0001\u0016\u0001\u0016\u0001\u0017\u0001\u0017\u0001\u0017\u0001"+
		"\u0017\u0001\u0018\u0001\u0018\u0001\u0018\u0001\u0018\u0001\u0018\u0001"+
		"\u0018\u0001\u0018\u0001\u0018\u0001\u0018\u0003\u0018\u018b\b\u0018\u0001"+
		"\u0019\u0001\u0019\u0001\u001a\u0001\u001a\u0001\u001a\u0001\u001a\u0001"+
		"\u001a\u0001\u001a\u0003\u001a\u0195\b\u001a\u0001\u001a\u0001\u001a\u0001"+
		"\u001a\u0001\u001a\u0001\u001a\u0001\u001a\u0005\u001a\u019d\b\u001a\n"+
		"\u001a\f\u001a\u01a0\t\u001a\u0001\u001b\u0001\u001b\u0001\u001b\u0001"+
		"\u001b\u0001\u001b\u0001\u001b\u0003\u001b\u01a8\b\u001b\u0001\u001c\u0001"+
		"\u001c\u0001\u001d\u0001\u001d\u0001\u001e\u0001\u001e\u0001\u001f\u0001"+
		"\u001f\u0001\u001f\u0001 \u0001 \u0001 \u0001 \u0001 \u0005 \u01b8\b "+
		"\n \f \u01bb\t \u0001 \u0001 \u0001!\u0001!\u0001!\u0001!\u0003!\u01c3"+
		"\b!\u0001!\u0001!\u0001!\u0003!\u01c8\b!\u0001!\u0001!\u0001!\u0003!\u01cd"+
		"\b!\u0001!\u0001!\u0001!\u0003!\u01d2\b!\u0001!\u0001!\u0001!\u0003!\u01d7"+
		"\b!\u0001!\u0001!\u0001!\u0003!\u01dc\b!\u0001!\u0001!\u0001!\u0003!\u01e1"+
		"\b!\u0001!\u0001!\u0001!\u0003!\u01e6\b!\u0001!\u0001!\u0001!\u0001!\u0001"+
		"!\u0001!\u0003!\u01ee\b!\u0001!\u0001!\u0001!\u0003!\u01f3\b!\u0001!\u0001"+
		"!\u0001!\u0001!\u0001!\u0005!\u01fa\b!\n!\f!\u01fd\t!\u0001!\u0001!\u0001"+
		"!\u0003!\u0202\b!\u0001!\u0001!\u0001!\u0001!\u0001!\u0005!\u0209\b!\n"+
		"!\f!\u020c\t!\u0001!\u0001!\u0001!\u0001!\u0001!\u0001!\u0001!\u0005!"+
		"\u0215\b!\n!\f!\u0218\t!\u0001!\u0001!\u0001!\u0003!\u021d\b!\u0003!\u021f"+
		"\b!\u0001\"\u0001\"\u0001\"\u0000\u00014#\u0000\u0002\u0004\u0006\b\n"+
		"\f\u000e\u0010\u0012\u0014\u0016\u0018\u001a\u001c\u001e \"$&(*,.0246"+
		"8:<>@BD\u0000\u0007\u0002\u0000\u0001\u0001\u001f\u001f\u0002\u0000\u0005"+
		"\u0005[[\u0001\u000034\u0002\u0000ZZ\\\\\u0002\u0000XX[[\u0003\u0000F"+
		"GOPRR\u0003\u0000GILMQQ\u025b\u0000I\u0001\u0000\u0000\u0000\u0002X\u0001"+
		"\u0000\u0000\u0000\u0004Z\u0001\u0000\u0000\u0000\u0006n\u0001\u0000\u0000"+
		"\u0000\b\u0092\u0001\u0000\u0000\u0000\n\u0094\u0001\u0000\u0000\u0000"+
		"\f\u00a4\u0001\u0000\u0000\u0000\u000e\u00c3\u0001\u0000\u0000\u0000\u0010"+
		"\u00c5\u0001\u0000\u0000\u0000\u0012\u00ce\u0001\u0000\u0000\u0000\u0014"+
		"\u00d7\u0001\u0000\u0000\u0000\u0016\u00fe\u0001\u0000\u0000\u0000\u0018"+
		"\u0100\u0001\u0000\u0000\u0000\u001a\u0113\u0001\u0000\u0000\u0000\u001c"+
		"\u011e\u0001\u0000\u0000\u0000\u001e\u0144\u0001\u0000\u0000\u0000 \u0146"+
		"\u0001\u0000\u0000\u0000\"\u0155\u0001\u0000\u0000\u0000$\u0157\u0001"+
		"\u0000\u0000\u0000&\u015a\u0001\u0000\u0000\u0000(\u0167\u0001\u0000\u0000"+
		"\u0000*\u016b\u0001\u0000\u0000\u0000,\u0179\u0001\u0000\u0000\u0000."+
		"\u017d\u0001\u0000\u0000\u00000\u018a\u0001\u0000\u0000\u00002\u018c\u0001"+
		"\u0000\u0000\u00004\u0194\u0001\u0000\u0000\u00006\u01a1\u0001\u0000\u0000"+
		"\u00008\u01a9\u0001\u0000\u0000\u0000:\u01ab\u0001\u0000\u0000\u0000<"+
		"\u01ad\u0001\u0000\u0000\u0000>\u01af\u0001\u0000\u0000\u0000@\u01b2\u0001"+
		"\u0000\u0000\u0000B\u021e\u0001\u0000\u0000\u0000D\u0220\u0001\u0000\u0000"+
		"\u0000FH\u0003\u0002\u0001\u0000GF\u0001\u0000\u0000\u0000HK\u0001\u0000"+
		"\u0000\u0000IG\u0001\u0000\u0000\u0000IJ\u0001\u0000\u0000\u0000JL\u0001"+
		"\u0000\u0000\u0000KI\u0001\u0000\u0000\u0000LM\u0005\u0000\u0000\u0001"+
		"M\u0001\u0001\u0000\u0000\u0000NY\u0003\u0004\u0002\u0000OY\u0003\u0006"+
		"\u0003\u0000PY\u0003\n\u0005\u0000QY\u0003\f\u0006\u0000RY\u0003\u0010"+
		"\b\u0000SY\u0003\u0012\t\u0000TY\u0003\u0018\f\u0000UY\u0003\u001a\r\u0000"+
		"VY\u0003\u001c\u000e\u0000WY\u0003D\"\u0000XN\u0001\u0000\u0000\u0000"+
		"XO\u0001\u0000\u0000\u0000XP\u0001\u0000\u0000\u0000XQ\u0001\u0000\u0000"+
		"\u0000XR\u0001\u0000\u0000\u0000XS\u0001\u0000\u0000\u0000XT\u0001\u0000"+
		"\u0000\u0000XU\u0001\u0000\u0000\u0000XV\u0001\u0000\u0000\u0000XW\u0001"+
		"\u0000\u0000\u0000Y\u0003\u0001\u0000\u0000\u0000Z[\u0005\t\u0000\u0000"+
		"[_\u0005*\u0000\u0000\\]\u0005\u0014\u0000\u0000]^\u0005\u001b\u0000\u0000"+
		"^`\u0005\u0010\u0000\u0000_\\\u0001\u0000\u0000\u0000_`\u0001\u0000\u0000"+
		"\u0000`a\u0001\u0000\u0000\u0000ab\u0005Z\u0000\u0000bc\u0005N\u0000\u0000"+
		"ch\u0003>\u001f\u0000de\u0005E\u0000\u0000eg\u0003>\u001f\u0000fd\u0001"+
		"\u0000\u0000\u0000gj\u0001\u0000\u0000\u0000hf\u0001\u0000\u0000\u0000"+
		"hi\u0001\u0000\u0000\u0000ik\u0001\u0000\u0000\u0000jh\u0001\u0000\u0000"+
		"\u0000kl\u0005U\u0000\u0000lm\u0005V\u0000\u0000m\u0005\u0001\u0000\u0000"+
		"\u0000no\u0005\t\u0000\u0000op\u0005\n\u0000\u0000pq\u0005)\u0000\u0000"+
		"qr\u0005Z\u0000\u0000rs\u0005N\u0000\u0000sx\u0003>\u001f\u0000tu\u0005"+
		"E\u0000\u0000uw\u0003>\u001f\u0000vt\u0001\u0000\u0000\u0000wz\u0001\u0000"+
		"\u0000\u0000xv\u0001\u0000\u0000\u0000xy\u0001\u0000\u0000\u0000y{\u0001"+
		"\u0000\u0000\u0000zx\u0001\u0000\u0000\u0000{\u0088\u0005U\u0000\u0000"+
		"|}\u00051\u0000\u0000}~\u0005N\u0000\u0000~\u0083\u0003\b\u0004\u0000"+
		"\u007f\u0080\u0005E\u0000\u0000\u0080\u0082\u0003\b\u0004\u0000\u0081"+
		"\u007f\u0001\u0000\u0000\u0000\u0082\u0085\u0001\u0000\u0000\u0000\u0083"+
		"\u0081\u0001\u0000\u0000\u0000\u0083\u0084\u0001\u0000\u0000\u0000\u0084"+
		"\u0086\u0001\u0000\u0000\u0000\u0085\u0083\u0001\u0000\u0000\u0000\u0086"+
		"\u0087\u0005U\u0000\u0000\u0087\u0089\u0001\u0000\u0000\u0000\u0088|\u0001"+
		"\u0000\u0000\u0000\u0088\u0089\u0001\u0000\u0000\u0000\u0089\u008a\u0001"+
		"\u0000\u0000\u0000\u008a\u008b\u0005V\u0000\u0000\u008b\u0007\u0001\u0000"+
		"\u0000\u0000\u008c\u008d\u0005\u000b\u0000\u0000\u008d\u008e\u0005C\u0000"+
		"\u0000\u008e\u0093\u0005\\\u0000\u0000\u008f\u0090\u0005,\u0000\u0000"+
		"\u0090\u0091\u0005C\u0000\u0000\u0091\u0093\u0005\\\u0000\u0000\u0092"+
		"\u008c\u0001\u0000\u0000\u0000\u0092\u008f\u0001\u0000\u0000\u0000\u0093"+
		"\t\u0001\u0000\u0000\u0000\u0094\u0095\u0005\t\u0000\u0000\u0095\u0096"+
		"\u0005\u0007\u0000\u0000\u0096\u0097\u0005+\u0000\u0000\u0097\u0098\u0005"+
		"Z\u0000\u0000\u0098\u0099\u0005N\u0000\u0000\u0099\u009e\u0003\u000e\u0007"+
		"\u0000\u009a\u009b\u0005E\u0000\u0000\u009b\u009d\u0003\u000e\u0007\u0000"+
		"\u009c\u009a\u0001\u0000\u0000\u0000\u009d\u00a0\u0001\u0000\u0000\u0000"+
		"\u009e\u009c\u0001\u0000\u0000\u0000\u009e\u009f\u0001\u0000\u0000\u0000"+
		"\u009f\u00a1\u0001\u0000\u0000\u0000\u00a0\u009e\u0001\u0000\u0000\u0000"+
		"\u00a1\u00a2\u0005U\u0000\u0000\u00a2\u00a3\u0005V\u0000\u0000\u00a3\u000b"+
		"\u0001\u0000\u0000\u0000\u00a4\u00a5\u0005\t\u0000\u0000\u00a5\u00a6\u0005"+
		"\u0015\u0000\u0000\u00a6\u00a7\u0005+\u0000\u0000\u00a7\u00a8\u0005Z\u0000"+
		"\u0000\u00a8\u00a9\u0005\u0012\u0000\u0000\u00a9\u00aa\u0005\\\u0000\u0000"+
		"\u00aa\u00ab\u0005.\u0000\u0000\u00ab\u00b8\u0005\\\u0000\u0000\u00ac"+
		"\u00ad\u00051\u0000\u0000\u00ad\u00ae\u0005N\u0000\u0000\u00ae\u00b3\u0003"+
		"\u000e\u0007\u0000\u00af\u00b0\u0005E\u0000\u0000\u00b0\u00b2\u0003\u000e"+
		"\u0007\u0000\u00b1\u00af\u0001\u0000\u0000\u0000\u00b2\u00b5\u0001\u0000"+
		"\u0000\u0000\u00b3\u00b1\u0001\u0000\u0000\u0000\u00b3\u00b4\u0001\u0000"+
		"\u0000\u0000\u00b4\u00b6\u0001\u0000\u0000\u0000\u00b5\u00b3\u0001\u0000"+
		"\u0000\u0000\u00b6\u00b7\u0005U\u0000\u0000\u00b7\u00b9\u0001\u0000\u0000"+
		"\u0000\u00b8\u00ac\u0001\u0000\u0000\u0000\u00b8\u00b9\u0001\u0000\u0000"+
		"\u0000\u00b9\u00ba\u0001\u0000\u0000\u0000\u00ba\u00bb\u0005V\u0000\u0000"+
		"\u00bb\r\u0001\u0000\u0000\u0000\u00bc\u00bd\u0005\u001d\u0000\u0000\u00bd"+
		"\u00be\u0005C\u0000\u0000\u00be\u00c4\u0005[\u0000\u0000\u00bf\u00c0\u0005"+
		"\u001c\u0000\u0000\u00c0\u00c1\u0005C\u0000\u0000\u00c1\u00c4\u0005[\u0000"+
		"\u0000\u00c2\u00c4\u0003>\u001f\u0000\u00c3\u00bc\u0001\u0000\u0000\u0000"+
		"\u00c3\u00bf\u0001\u0000\u0000\u0000\u00c3\u00c2\u0001\u0000\u0000\u0000"+
		"\u00c4\u000f\u0001\u0000\u0000\u0000\u00c5\u00c6\u0005\u0003\u0000\u0000"+
		"\u00c6\u00c7\u0005*\u0000\u0000\u00c7\u00c8\u0005Z\u0000\u0000\u00c8\u00c9"+
		"\u0005\u0002\u0000\u0000\u00c9\u00ca\u0005\u0006\u0000\u0000\u00ca\u00cb"+
		"\u0005Z\u0000\u0000\u00cb\u00cc\u0003B!\u0000\u00cc\u00cd\u0005V\u0000"+
		"\u0000\u00cd\u0011\u0001\u0000\u0000\u0000\u00ce\u00cf\u0005\"\u0000\u0000"+
		"\u00cf\u00d0\u0005Z\u0000\u0000\u00d0\u00d1\u0005\u0018\u0000\u0000\u00d1"+
		"\u00d3\u0005Z\u0000\u0000\u00d2\u00d4\u0003\u0014\n\u0000\u00d3\u00d2"+
		"\u0001\u0000\u0000\u0000\u00d3\u00d4\u0001\u0000\u0000\u0000\u00d4\u00d5"+
		"\u0001\u0000\u0000\u0000\u00d5\u00d6\u0005V\u0000\u0000\u00d6\u0013\u0001"+
		"\u0000\u0000\u0000\u00d7\u00d8\u00051\u0000\u0000\u00d8\u00d9\u0005N\u0000"+
		"\u0000\u00d9\u00de\u0003\u0016\u000b\u0000\u00da\u00db\u0005E\u0000\u0000"+
		"\u00db\u00dd\u0003\u0016\u000b\u0000\u00dc\u00da\u0001\u0000\u0000\u0000"+
		"\u00dd\u00e0\u0001\u0000\u0000\u0000\u00de\u00dc\u0001\u0000\u0000\u0000"+
		"\u00de\u00df\u0001\u0000\u0000\u0000\u00df\u00e1\u0001\u0000\u0000\u0000"+
		"\u00e0\u00de\u0001\u0000\u0000\u0000\u00e1\u00e2\u0005U\u0000\u0000\u00e2"+
		"\u0015\u0001\u0000\u0000\u0000\u00e3\u00e4\u0005\b\u0000\u0000\u00e4\u00e5"+
		"\u0005C\u0000\u0000\u00e5\u00ff\u0007\u0000\u0000\u0000\u00e6\u00e7\u0005"+
		"\u001a\u0000\u0000\u00e7\u00e8\u0005C\u0000\u0000\u00e8\u00ff\u0005[\u0000"+
		"\u0000\u00e9\u00ea\u0005\'\u0000\u0000\u00ea\u00eb\u0005C\u0000\u0000"+
		"\u00eb\u00ff\u0007\u0001\u0000\u0000\u00ec\u00ed\u0005&\u0000\u0000\u00ed"+
		"\u00ee\u0005C\u0000\u0000\u00ee\u00ff\u0005[\u0000\u0000\u00ef\u00f0\u0005"+
		"(\u0000\u0000\u00f0\u00f1\u0005C\u0000\u0000\u00f1\u00ff\u0005\\\u0000"+
		"\u0000\u00f2\u00f3\u0005 \u0000\u0000\u00f3\u00f4\u0005C\u0000\u0000\u00f4"+
		"\u00ff\u0005\\\u0000\u0000\u00f5\u00f6\u0005$\u0000\u0000\u00f6\u00f7"+
		"\u0005C\u0000\u0000\u00f7\u00ff\u0005\\\u0000\u0000\u00f8\u00f9\u0005"+
		"2\u0000\u0000\u00f9\u00fa\u0005C\u0000\u0000\u00fa\u00ff\u0007\u0002\u0000"+
		"\u0000\u00fb\u00fc\u00055\u0000\u0000\u00fc\u00fd\u0005C\u0000\u0000\u00fd"+
		"\u00ff\u0007\u0002\u0000\u0000\u00fe\u00e3\u0001\u0000\u0000\u0000\u00fe"+
		"\u00e6\u0001\u0000\u0000\u0000\u00fe\u00e9\u0001\u0000\u0000\u0000\u00fe"+
		"\u00ec\u0001\u0000\u0000\u0000\u00fe\u00ef\u0001\u0000\u0000\u0000\u00fe"+
		"\u00f2\u0001\u0000\u0000\u0000\u00fe\u00f5\u0001\u0000\u0000\u0000\u00fe"+
		"\u00f8\u0001\u0000\u0000\u0000\u00fe\u00fb\u0001\u0000\u0000\u0000\u00ff"+
		"\u0017\u0001\u0000\u0000\u0000\u0100\u0101\u0005-\u0000\u0000\u0101\u0102"+
		"\u0005Z\u0000\u0000\u0102\u0103\u0005%\u0000\u0000\u0103\u0108\u0003."+
		"\u0017\u0000\u0104\u0105\u0005E\u0000\u0000\u0105\u0107\u0003.\u0017\u0000"+
		"\u0106\u0104\u0001\u0000\u0000\u0000\u0107\u010a\u0001\u0000\u0000\u0000"+
		"\u0108\u0106\u0001\u0000\u0000\u0000\u0108\u0109\u0001\u0000\u0000\u0000"+
		"\u0109\u010b\u0001\u0000\u0000\u0000\u010a\u0108\u0001\u0000\u0000\u0000"+
		"\u010b\u010c\u00050\u0000\u0000\u010c\u010f\u00034\u001a\u0000\u010d\u010e"+
		"\u00051\u0000\u0000\u010e\u0110\u0005!\u0000\u0000\u010f\u010d\u0001\u0000"+
		"\u0000\u0000\u010f\u0110\u0001\u0000\u0000\u0000\u0110\u0111\u0001\u0000"+
		"\u0000\u0000\u0111\u0112\u0005V\u0000\u0000\u0112\u0019\u0001\u0000\u0000"+
		"\u0000\u0113\u0114\u0005\r\u0000\u0000\u0114\u0115\u0005\u0013\u0000\u0000"+
		"\u0115\u0116\u0005Z\u0000\u0000\u0116\u0117\u00050\u0000\u0000\u0117\u011a"+
		"\u00034\u001a\u0000\u0118\u0119\u00051\u0000\u0000\u0119\u011b\u0005!"+
		"\u0000\u0000\u011a\u0118\u0001\u0000\u0000\u0000\u011a\u011b\u0001\u0000"+
		"\u0000\u0000\u011b\u011c\u0001\u0000\u0000\u0000\u011c\u011d\u0005V\u0000"+
		"\u0000\u011d\u001b\u0001\u0000\u0000\u0000\u011e\u011f\u0005\u0017\u0000"+
		"\u0000\u011f\u0120\u0005\u0018\u0000\u0000\u0120\u012c\u0003:\u001d\u0000"+
		"\u0121\u0122\u0005N\u0000\u0000\u0122\u0127\u0003<\u001e\u0000\u0123\u0124"+
		"\u0005E\u0000\u0000\u0124\u0126\u0003<\u001e\u0000\u0125\u0123\u0001\u0000"+
		"\u0000\u0000\u0126\u0129\u0001\u0000\u0000\u0000\u0127\u0125\u0001\u0000"+
		"\u0000\u0000\u0127\u0128\u0001\u0000\u0000\u0000\u0128\u012a\u0001\u0000"+
		"\u0000\u0000\u0129\u0127\u0001\u0000\u0000\u0000\u012a\u012b\u0005U\u0000"+
		"\u0000\u012b\u012d\u0001\u0000\u0000\u0000\u012c\u0121\u0001\u0000\u0000"+
		"\u0000\u012c\u012d\u0001\u0000\u0000\u0000\u012d\u012e\u0001\u0000\u0000"+
		"\u0000\u012e\u012f\u0005/\u0000\u0000\u012f\u0130\u0005N\u0000\u0000\u0130"+
		"\u0131\u0003 \u0010\u0000\u0131\u013b\u0005U\u0000\u0000\u0132\u0133\u0005"+
		"1\u0000\u0000\u0133\u0138\u0003\u001e\u000f\u0000\u0134\u0135\u0005E\u0000"+
		"\u0000\u0135\u0137\u0003\u001e\u000f\u0000\u0136\u0134\u0001\u0000\u0000"+
		"\u0000\u0137\u013a\u0001\u0000\u0000\u0000\u0138\u0136\u0001\u0000\u0000"+
		"\u0000\u0138\u0139\u0001\u0000\u0000\u0000\u0139\u013c\u0001\u0000\u0000"+
		"\u0000\u013a\u0138\u0001\u0000\u0000\u0000\u013b\u0132\u0001\u0000\u0000"+
		"\u0000\u013b\u013c\u0001\u0000\u0000\u0000\u013c\u013d\u0001\u0000\u0000"+
		"\u0000\u013d\u013e\u0005V\u0000\u0000\u013e\u001d\u0001\u0000\u0000\u0000"+
		"\u013f\u0145\u0005!\u0000\u0000\u0140\u0141\u0005#\u0000\u0000\u0141\u0145"+
		"\u0005\\\u0000\u0000\u0142\u0143\u0005\u000e\u0000\u0000\u0143\u0145\u0005"+
		"\\\u0000\u0000\u0144\u013f\u0001\u0000\u0000\u0000\u0144\u0140\u0001\u0000"+
		"\u0000\u0000\u0144\u0142\u0001\u0000\u0000\u0000\u0145\u001f\u0001\u0000"+
		"\u0000\u0000\u0146\u014b\u0003\"\u0011\u0000\u0147\u0148\u0005E\u0000"+
		"\u0000\u0148\u014a\u0003\"\u0011\u0000\u0149\u0147\u0001\u0000\u0000\u0000"+
		"\u014a\u014d\u0001\u0000\u0000\u0000\u014b\u0149\u0001\u0000\u0000\u0000"+
		"\u014b\u014c\u0001\u0000\u0000\u0000\u014c!\u0001\u0000\u0000\u0000\u014d"+
		"\u014b\u0001\u0000\u0000\u0000\u014e\u0156\u0005\\\u0000\u0000\u014f\u0156"+
		"\u0003,\u0016\u0000\u0150\u0156\u0005W\u0000\u0000\u0151\u0156\u0005Y"+
		"\u0000\u0000\u0152\u0156\u0003$\u0012\u0000\u0153\u0156\u0003&\u0013\u0000"+
		"\u0154\u0156\u0003*\u0015\u0000\u0155\u014e\u0001\u0000\u0000\u0000\u0155"+
		"\u014f\u0001\u0000\u0000\u0000\u0155\u0150\u0001\u0000\u0000\u0000\u0155"+
		"\u0151\u0001\u0000\u0000\u0000\u0155\u0152\u0001\u0000\u0000\u0000\u0155"+
		"\u0153\u0001\u0000\u0000\u0000\u0155\u0154\u0001\u0000\u0000\u0000\u0156"+
		"#\u0001\u0000\u0000\u0000\u0157\u0158\u0005D\u0000\u0000\u0158\u0159\u0005"+
		"Z\u0000\u0000\u0159%\u0001\u0000\u0000\u0000\u015a\u0163\u0005J\u0000"+
		"\u0000\u015b\u0160\u0003(\u0014\u0000\u015c\u015d\u0005E\u0000\u0000\u015d"+
		"\u015f\u0003(\u0014\u0000\u015e\u015c\u0001\u0000\u0000\u0000\u015f\u0162"+
		"\u0001\u0000\u0000\u0000\u0160\u015e\u0001\u0000\u0000\u0000\u0160\u0161"+
		"\u0001\u0000\u0000\u0000\u0161\u0164\u0001\u0000\u0000\u0000\u0162\u0160"+
		"\u0001\u0000\u0000\u0000\u0163\u015b\u0001\u0000\u0000\u0000\u0163\u0164"+
		"\u0001\u0000\u0000\u0000\u0164\u0165\u0001\u0000\u0000\u0000\u0165\u0166"+
		"\u0005S\u0000\u0000\u0166\'\u0001\u0000\u0000\u0000\u0167\u0168\u0007"+
		"\u0003\u0000\u0000\u0168\u0169\u0005D\u0000\u0000\u0169\u016a\u0003\""+
		"\u0011\u0000\u016a)\u0001\u0000\u0000\u0000\u016b\u0174\u0005K\u0000\u0000"+
		"\u016c\u0171\u0003\"\u0011\u0000\u016d\u016e\u0005E\u0000\u0000\u016e"+
		"\u0170\u0003\"\u0011\u0000\u016f\u016d\u0001\u0000\u0000\u0000\u0170\u0173"+
		"\u0001\u0000\u0000\u0000\u0171\u016f\u0001\u0000\u0000\u0000\u0171\u0172"+
		"\u0001\u0000\u0000\u0000\u0172\u0175\u0001\u0000\u0000\u0000\u0173\u0171"+
		"\u0001\u0000\u0000\u0000\u0174\u016c\u0001\u0000\u0000\u0000\u0174\u0175"+
		"\u0001\u0000\u0000\u0000\u0175\u0176\u0001\u0000\u0000\u0000\u0176\u0177"+
		"\u0005T\u0000\u0000\u0177+\u0001\u0000\u0000\u0000\u0178\u017a\u0005O"+
		"\u0000\u0000\u0179\u0178\u0001\u0000\u0000\u0000\u0179\u017a\u0001\u0000"+
		"\u0000\u0000\u017a\u017b\u0001\u0000\u0000\u0000\u017b\u017c\u0007\u0004"+
		"\u0000\u0000\u017c-\u0001\u0000\u0000\u0000\u017d\u017e\u0005Z\u0000\u0000"+
		"\u017e\u017f\u0005C\u0000\u0000\u017f\u0180\u00030\u0018\u0000\u0180/"+
		"\u0001\u0000\u0000\u0000\u0181\u018b\u0003\"\u0011\u0000\u0182\u0183\u0005"+
		"Z\u0000\u0000\u0183\u0184\u00032\u0019\u0000\u0184\u0185\u00030\u0018"+
		"\u0000\u0185\u018b\u0001\u0000\u0000\u0000\u0186\u0187\u0005N\u0000\u0000"+
		"\u0187\u0188\u00030\u0018\u0000\u0188\u0189\u0005U\u0000\u0000\u0189\u018b"+
		"\u0001\u0000\u0000\u0000\u018a\u0181\u0001\u0000\u0000\u0000\u018a\u0182"+
		"\u0001\u0000\u0000\u0000\u018a\u0186\u0001\u0000\u0000\u0000\u018b1\u0001"+
		"\u0000\u0000\u0000\u018c\u018d\u0007\u0005\u0000\u0000\u018d3\u0001\u0000"+
		"\u0000\u0000\u018e\u018f\u0006\u001a\uffff\uffff\u0000\u018f\u0195\u0003"+
		"6\u001b\u0000\u0190\u0191\u0005N\u0000\u0000\u0191\u0192\u00034\u001a"+
		"\u0000\u0192\u0193\u0005U\u0000\u0000\u0193\u0195\u0001\u0000\u0000\u0000"+
		"\u0194\u018e\u0001\u0000\u0000\u0000\u0194\u0190\u0001\u0000\u0000\u0000"+
		"\u0195\u019e\u0001\u0000\u0000\u0000\u0196\u0197\n\u0002\u0000\u0000\u0197"+
		"\u0198\u0005\u0004\u0000\u0000\u0198\u019d\u00034\u001a\u0003\u0199\u019a"+
		"\n\u0001\u0000\u0000\u019a\u019b\u0005\u001e\u0000\u0000\u019b\u019d\u0003"+
		"4\u001a\u0002\u019c\u0196\u0001\u0000\u0000\u0000\u019c\u0199\u0001\u0000"+
		"\u0000\u0000\u019d\u01a0\u0001\u0000\u0000\u0000\u019e\u019c\u0001\u0000"+
		"\u0000\u0000\u019e\u019f\u0001\u0000\u0000\u0000\u019f5\u0001\u0000\u0000"+
		"\u0000\u01a0\u019e\u0001\u0000\u0000\u0000\u01a1\u01a2\u0005Z\u0000\u0000"+
		"\u01a2\u01a7\u00038\u001c\u0000\u01a3\u01a8\u0003$\u0012\u0000\u01a4\u01a8"+
		"\u0005\\\u0000\u0000\u01a5\u01a8\u0003,\u0016\u0000\u01a6\u01a8\u0005"+
		"W\u0000\u0000\u01a7\u01a3\u0001\u0000\u0000\u0000\u01a7\u01a4\u0001\u0000"+
		"\u0000\u0000\u01a7\u01a5\u0001\u0000\u0000\u0000\u01a7\u01a6\u0001\u0000"+
		"\u0000\u0000\u01a87\u0001\u0000\u0000\u0000\u01a9\u01aa\u0007\u0006\u0000"+
		"\u0000\u01aa9\u0001\u0000\u0000\u0000\u01ab\u01ac\u0005Z\u0000\u0000\u01ac"+
		";\u0001\u0000\u0000\u0000\u01ad\u01ae\u0005Z\u0000\u0000\u01ae=\u0001"+
		"\u0000\u0000\u0000\u01af\u01b0\u0005Z\u0000\u0000\u01b0\u01b1\u0003B!"+
		"\u0000\u01b1?\u0001\u0000\u0000\u0000\u01b2\u01b3\u0005Z\u0000\u0000\u01b3"+
		"\u01b4\u0005N\u0000\u0000\u01b4\u01b9\u0003>\u001f\u0000\u01b5\u01b6\u0005"+
		"E\u0000\u0000\u01b6\u01b8\u0003>\u001f\u0000\u01b7\u01b5\u0001\u0000\u0000"+
		"\u0000\u01b8\u01bb\u0001\u0000\u0000\u0000\u01b9\u01b7\u0001\u0000\u0000"+
		"\u0000\u01b9\u01ba\u0001\u0000\u0000\u0000\u01ba\u01bc\u0001\u0000\u0000"+
		"\u0000\u01bb\u01b9\u0001\u0000\u0000\u0000\u01bc\u01bd\u0005U\u0000\u0000"+
		"\u01bdA\u0001\u0000\u0000\u0000\u01be\u021f\u0005;\u0000\u0000\u01bf\u01c2"+
		"\u00058\u0000\u0000\u01c0\u01c1\u0005\u001b\u0000\u0000\u01c1\u01c3\u0005"+
		"\u0016\u0000\u0000\u01c2\u01c0\u0001\u0000\u0000\u0000\u01c2\u01c3\u0001"+
		"\u0000\u0000\u0000\u01c3\u021f\u0001\u0000\u0000\u0000\u01c4\u01c7\u0005"+
		"7\u0000\u0000\u01c5\u01c6\u0005\u001b\u0000\u0000\u01c6\u01c8\u0005\u0016"+
		"\u0000\u0000\u01c7\u01c5\u0001\u0000\u0000\u0000\u01c7\u01c8\u0001\u0000"+
		"\u0000\u0000\u01c8\u021f\u0001\u0000\u0000\u0000\u01c9\u01cc\u0005\u0019"+
		"\u0000\u0000\u01ca\u01cb\u0005\u001b\u0000\u0000\u01cb\u01cd\u0005\u0016"+
		"\u0000\u0000\u01cc\u01ca\u0001\u0000\u0000\u0000\u01cc\u01cd\u0001\u0000"+
		"\u0000\u0000\u01cd\u021f\u0001\u0000\u0000\u0000\u01ce\u01d1\u0005\u0011"+
		"\u0000\u0000\u01cf\u01d0\u0005\u001b\u0000\u0000\u01d0\u01d2\u0005\u0016"+
		"\u0000\u0000\u01d1\u01cf\u0001\u0000\u0000\u0000\u01d1\u01d2\u0001\u0000"+
		"\u0000\u0000\u01d2\u021f\u0001\u0000\u0000\u0000\u01d3\u01d6\u0005\u000f"+
		"\u0000\u0000\u01d4\u01d5\u0005\u001b\u0000\u0000\u01d5\u01d7\u0005\u0016"+
		"\u0000\u0000\u01d6\u01d4\u0001\u0000\u0000\u0000\u01d6\u01d7\u0001\u0000"+
		"\u0000\u0000\u01d7\u021f\u0001\u0000\u0000\u0000\u01d8\u01db\u00056\u0000"+
		"\u0000\u01d9\u01da\u0005\u001b\u0000\u0000\u01da\u01dc\u0005\u0016\u0000"+
		"\u0000\u01db\u01d9\u0001\u0000\u0000\u0000\u01db\u01dc\u0001\u0000\u0000"+
		"\u0000\u01dc\u021f\u0001\u0000\u0000\u0000\u01dd\u01e0\u0005\f\u0000\u0000"+
		"\u01de\u01df\u0005\u001b\u0000\u0000\u01df\u01e1\u0005\u0016\u0000\u0000"+
		"\u01e0\u01de\u0001\u0000\u0000\u0000\u01e0\u01e1\u0001\u0000\u0000\u0000"+
		"\u01e1\u021f\u0001\u0000\u0000\u0000\u01e2\u01e5\u0005<\u0000\u0000\u01e3"+
		"\u01e4\u0005\u001b\u0000\u0000\u01e4\u01e6\u0005\u0016\u0000\u0000\u01e5"+
		"\u01e3\u0001\u0000\u0000\u0000\u01e5\u01e6\u0001\u0000\u0000\u0000\u01e6"+
		"\u021f\u0001\u0000\u0000\u0000\u01e7\u021f\u0005=\u0000\u0000\u01e8\u021f"+
		"\u0005>\u0000\u0000\u01e9\u021f\u0005?\u0000\u0000\u01ea\u01ed\u0005@"+
		"\u0000\u0000\u01eb\u01ec\u0005\u001b\u0000\u0000\u01ec\u01ee\u0005\u0016"+
		"\u0000\u0000\u01ed\u01eb\u0001\u0000\u0000\u0000\u01ed\u01ee\u0001\u0000"+
		"\u0000\u0000\u01ee\u021f\u0001\u0000\u0000\u0000\u01ef\u01f2\u0005A\u0000"+
		"\u0000\u01f0\u01f1\u0005\u001b\u0000\u0000\u01f1\u01f3\u0005\u0016\u0000"+
		"\u0000\u01f2\u01f0\u0001\u0000\u0000\u0000\u01f2\u01f3\u0001\u0000\u0000"+
		"\u0000\u01f3\u021f\u0001\u0000\u0000\u0000\u01f4\u01f5\u0005:\u0000\u0000"+
		"\u01f5\u01f6\u0005N\u0000\u0000\u01f6\u01fb\u0003>\u001f\u0000\u01f7\u01f8"+
		"\u0005E\u0000\u0000\u01f8\u01fa\u0003>\u001f\u0000\u01f9\u01f7\u0001\u0000"+
		"\u0000\u0000\u01fa\u01fd\u0001\u0000\u0000\u0000\u01fb\u01f9\u0001\u0000"+
		"\u0000\u0000\u01fb\u01fc\u0001\u0000\u0000\u0000\u01fc\u01fe\u0001\u0000"+
		"\u0000\u0000\u01fd\u01fb\u0001\u0000\u0000\u0000\u01fe\u0201\u0005U\u0000"+
		"\u0000\u01ff\u0200\u0005\u001b\u0000\u0000\u0200\u0202\u0005\u0016\u0000"+
		"\u0000\u0201\u01ff\u0001\u0000\u0000\u0000\u0201\u0202\u0001\u0000\u0000"+
		"\u0000\u0202\u021f\u0001\u0000\u0000\u0000\u0203\u0204\u00059\u0000\u0000"+
		"\u0204\u0205\u0005N\u0000\u0000\u0205\u020a\u0003>\u001f\u0000\u0206\u0207"+
		"\u0005E\u0000\u0000\u0207\u0209\u0003>\u001f\u0000\u0208\u0206\u0001\u0000"+
		"\u0000\u0000\u0209\u020c\u0001\u0000\u0000\u0000\u020a\u0208\u0001\u0000"+
		"\u0000\u0000\u020a\u020b\u0001\u0000\u0000\u0000\u020b\u020d\u0001\u0000"+
		"\u0000\u0000\u020c\u020a\u0001\u0000\u0000\u0000\u020d\u020e\u0005U\u0000"+
		"\u0000\u020e\u021f\u0001\u0000\u0000\u0000\u020f\u0210\u0005B\u0000\u0000"+
		"\u0210\u0211\u0005N\u0000\u0000\u0211\u0216\u0003@ \u0000\u0212\u0213"+
		"\u0005E\u0000\u0000\u0213\u0215\u0003@ \u0000\u0214\u0212\u0001\u0000"+
		"\u0000\u0000\u0215\u0218\u0001\u0000\u0000\u0000\u0216\u0214\u0001\u0000"+
		"\u0000\u0000\u0216\u0217\u0001\u0000\u0000\u0000\u0217\u0219\u0001\u0000"+
		"\u0000\u0000\u0218\u0216\u0001\u0000\u0000\u0000\u0219\u021c\u0005U\u0000"+
		"\u0000\u021a\u021b\u0005\u001b\u0000\u0000\u021b\u021d\u0005\u0016\u0000"+
		"\u0000\u021c\u021a\u0001\u0000\u0000\u0000\u021c\u021d\u0001\u0000\u0000"+
		"\u0000\u021d\u021f\u0001\u0000\u0000\u0000\u021e\u01be\u0001\u0000\u0000"+
		"\u0000\u021e\u01bf\u0001\u0000\u0000\u0000\u021e\u01c4\u0001\u0000\u0000"+
		"\u0000\u021e\u01c9\u0001\u0000\u0000\u0000\u021e\u01ce\u0001\u0000\u0000"+
		"\u0000\u021e\u01d3\u0001\u0000\u0000\u0000\u021e\u01d8\u0001\u0000\u0000"+
		"\u0000\u021e\u01dd\u0001\u0000\u0000\u0000\u021e\u01e2\u0001\u0000\u0000"+
		"\u0000\u021e\u01e7\u0001\u0000\u0000\u0000\u021e\u01e8\u0001\u0000\u0000"+
		"\u0000\u021e\u01e9\u0001\u0000\u0000\u0000\u021e\u01ea\u0001\u0000\u0000"+
		"\u0000\u021e\u01ef\u0001\u0000\u0000\u0000\u021e\u01f4\u0001\u0000\u0000"+
		"\u0000\u021e\u0203\u0001\u0000\u0000\u0000\u021e\u020f\u0001\u0000\u0000"+
		"\u0000\u021fC\u0001\u0000\u0000\u0000\u0220\u0221\u0005]\u0000\u0000\u0221"+
		"E\u0001\u0000\u0000\u00004IX_hx\u0083\u0088\u0092\u009e\u00b3\u00b8\u00c3"+
		"\u00d3\u00de\u00fe\u0108\u010f\u011a\u0127\u012c\u0138\u013b\u0144\u014b"+
		"\u0155\u0160\u0163\u0171\u0174\u0179\u018a\u0194\u019c\u019e\u01a7\u01b9"+
		"\u01c2\u01c7\u01cc\u01d1\u01d6\u01db\u01e0\u01e5\u01ed\u01f2\u01fb\u0201"+
		"\u020a\u0216\u021c\u021e";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}