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
		ABORT=1, ADD=2, ALTER=3, AND=4, ASC=5, AUTO=6, BY=7, COLUMN=8, COMPONENT=9, 
		CONFLICTS=10, CREATE=11, DATA=12, DATA_RETENTION=13, DATE=14, DELETE=15, 
		DESC=16, DOCUMENT_ID=17, DOUBLE=18, EXISTS=19, FLOAT=20, FOR=21, FROM=22, 
		IF=23, INDEX=24, INDEXED=25, INSERT=26, INTO=27, LIMIT=28, LONG=29, MAX_DOCS=30, 
		NOT=31, NUMBER_OF_REPLICAS=32, NUMBER_OF_SHARDS=33, OR=34, ORDER=35, PROCEED=36, 
		QUERY=37, REFRESH=38, REINDEX=39, ROUTING=40, SCRIPT=41, SELECT=42, SET=43, 
		SIZE=44, SLICES=45, SOURCE_FIELDS=46, STREAM=47, TABLE=48, TEMPLATE=49, 
		TIME_REFERENCE=50, UPDATE=51, USING=52, VALUES=53, WHERE=54, WITH=55, 
		WAIT=56, TRUE=57, FALSE=58, SKIP_IF_NO_SOURCE=59, BOOLEAN=60, INTEGER=61, 
		KEYWORD=62, NESTED=63, OBJECT=64, TEXT=65, JSON=66, BINARY=67, GEO_POINT=68, 
		GEO_SHAPE=69, UUID=70, DECIMAL=71, UNION=72, ASSIGN=73, COLON=74, COMMA=75, 
		DIVIDE=76, EQUALS=77, GREATER_THAN=78, GREATER_THAN_EQUALS=79, LBRACE=80, 
		LBRACKET=81, LESS_THAN=82, LESS_THAN_EQUALS=83, LPAREN=84, MINUS=85, MULTIPLY=86, 
		NOT_EQUALS=87, PLUS=88, RBRACE=89, RBRACKET=90, RPAREN=91, SEMICOLON=92, 
		BOOLEAN_LITERAL=93, DECIMAL_LITERAL=94, NULL_LITERAL=95, ID=96, INTEGER_LITERAL=97, 
		STRING=98, COMMENT=99, WS=100;
	public static final int
		RULE_migrations = 0, RULE_statement = 1, RULE_createTableStatement = 2, 
		RULE_createDataStreamStatement = 3, RULE_dataStreamOption = 4, RULE_createComponentTemplateStatement = 5, 
		RULE_createIndexTemplateStatement = 6, RULE_templatePart = 7, RULE_alterTableStatement = 8, 
		RULE_reindexStatement = 9, RULE_reindexOptions = 10, RULE_reindexOption = 11, 
		RULE_updateStatement = 12, RULE_deleteStatement = 13, RULE_insertStatement = 14, 
		RULE_selectStatement = 15, RULE_selectList = 16, RULE_orderBy = 17, RULE_insertOption = 18, 
		RULE_valueList = 19, RULE_value = 20, RULE_namedParameter = 21, RULE_objectLiteral = 22, 
		RULE_objectField = 23, RULE_arrayLiteral = 24, RULE_numberLiteral = 25, 
		RULE_assignment = 26, RULE_expression = 27, RULE_operator = 28, RULE_whereClause = 29, 
		RULE_condition = 30, RULE_comparisonOperator = 31, RULE_tableName = 32, 
		RULE_columnName = 33, RULE_columnDefinition = 34, RULE_unionVariant = 35, 
		RULE_type = 36, RULE_comment = 37;
	private static String[] makeRuleNames() {
		return new String[] {
			"migrations", "statement", "createTableStatement", "createDataStreamStatement", 
			"dataStreamOption", "createComponentTemplateStatement", "createIndexTemplateStatement", 
			"templatePart", "alterTableStatement", "reindexStatement", "reindexOptions", 
			"reindexOption", "updateStatement", "deleteStatement", "insertStatement", 
			"selectStatement", "selectList", "orderBy", "insertOption", "valueList", 
			"value", "namedParameter", "objectLiteral", "objectField", "arrayLiteral", 
			"numberLiteral", "assignment", "expression", "operator", "whereClause", 
			"condition", "comparisonOperator", "tableName", "columnName", "columnDefinition", 
			"unionVariant", "type", "comment"
		};
	}
	public static final String[] ruleNames = makeRuleNames();

	private static String[] makeLiteralNames() {
		return new String[] {
			null, "'ABORT'", "'ADD'", "'ALTER'", "'AND'", "'ASC'", "'AUTO'", "'BY'", 
			"'COLUMN'", "'COMPONENT'", "'CONFLICTS'", "'CREATE'", "'DATA'", "'DATA_RETENTION'", 
			"'DATE'", "'DELETE'", "'DESC'", "'DOCUMENT_ID'", "'DOUBLE'", "'EXISTS'", 
			"'FLOAT'", "'FOR'", "'FROM'", "'IF'", "'INDEX'", "'INDEXED'", "'INSERT'", 
			"'INTO'", "'LIMIT'", "'LONG'", "'MAX_DOCS'", "'NOT'", "'NUMBER_OF_REPLICAS'", 
			"'NUMBER_OF_SHARDS'", "'OR'", "'ORDER'", "'PROCEED'", "'QUERY'", "'REFRESH'", 
			"'REINDEX'", "'ROUTING'", "'SCRIPT'", "'SELECT'", "'SET'", "'SIZE'", 
			"'SLICES'", "'SOURCE_FIELDS'", "'STREAM'", "'TABLE'", "'TEMPLATE'", "'TIME_REFERENCE'", 
			"'UPDATE'", "'USING'", "'VALUES'", "'WHERE'", "'WITH'", "'WAIT'", "'TRUE'", 
			"'FALSE'", "'SKIP_IF_NO_SOURCE'", "'BOOLEAN'", "'INTEGER'", "'KEYWORD'", 
			"'NESTED'", "'OBJECT'", "'TEXT'", "'JSON'", "'BINARY'", "'GEO_POINT'", 
			"'GEO_SHAPE'", "'UUID'", "'DECIMAL'", "'UNION'", "'='", "':'", "','", 
			"'/'", "'=='", "'>'", "'>='", "'{'", "'['", "'<'", "'<='", "'('", "'-'", 
			"'*'", "'!='", "'+'", "'}'", "']'", "')'", "';'", null, null, "'null'"
		};
	}
	private static final String[] _LITERAL_NAMES = makeLiteralNames();
	private static String[] makeSymbolicNames() {
		return new String[] {
			null, "ABORT", "ADD", "ALTER", "AND", "ASC", "AUTO", "BY", "COLUMN", 
			"COMPONENT", "CONFLICTS", "CREATE", "DATA", "DATA_RETENTION", "DATE", 
			"DELETE", "DESC", "DOCUMENT_ID", "DOUBLE", "EXISTS", "FLOAT", "FOR", 
			"FROM", "IF", "INDEX", "INDEXED", "INSERT", "INTO", "LIMIT", "LONG", 
			"MAX_DOCS", "NOT", "NUMBER_OF_REPLICAS", "NUMBER_OF_SHARDS", "OR", "ORDER", 
			"PROCEED", "QUERY", "REFRESH", "REINDEX", "ROUTING", "SCRIPT", "SELECT", 
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
			setState(79);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while ((((_la) & ~0x3f) == 0 && ((1L << _la) & 2256747683153928L) != 0) || _la==COMMENT) {
				{
				{
				setState(76);
				statement();
				}
				}
				setState(81);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(82);
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
			setState(95);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,1,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(84);
				createTableStatement();
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(85);
				createDataStreamStatement();
				}
				break;
			case 3:
				enterOuterAlt(_localctx, 3);
				{
				setState(86);
				createComponentTemplateStatement();
				}
				break;
			case 4:
				enterOuterAlt(_localctx, 4);
				{
				setState(87);
				createIndexTemplateStatement();
				}
				break;
			case 5:
				enterOuterAlt(_localctx, 5);
				{
				setState(88);
				alterTableStatement();
				}
				break;
			case 6:
				enterOuterAlt(_localctx, 6);
				{
				setState(89);
				reindexStatement();
				}
				break;
			case 7:
				enterOuterAlt(_localctx, 7);
				{
				setState(90);
				updateStatement();
				}
				break;
			case 8:
				enterOuterAlt(_localctx, 8);
				{
				setState(91);
				deleteStatement();
				}
				break;
			case 9:
				enterOuterAlt(_localctx, 9);
				{
				setState(92);
				insertStatement();
				}
				break;
			case 10:
				enterOuterAlt(_localctx, 10);
				{
				setState(93);
				selectStatement();
				}
				break;
			case 11:
				enterOuterAlt(_localctx, 11);
				{
				setState(94);
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
			setState(97);
			match(CREATE);
			setState(98);
			match(TABLE);
			setState(102);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==IF) {
				{
				setState(99);
				match(IF);
				setState(100);
				match(NOT);
				setState(101);
				match(EXISTS);
				}
			}

			setState(104);
			match(ID);
			setState(105);
			match(LPAREN);
			setState(106);
			columnDefinition();
			setState(111);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==COMMA) {
				{
				{
				setState(107);
				match(COMMA);
				setState(108);
				columnDefinition();
				}
				}
				setState(113);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(114);
			match(RPAREN);
			setState(115);
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
			setState(117);
			match(CREATE);
			setState(118);
			match(DATA);
			setState(119);
			match(STREAM);
			setState(120);
			match(ID);
			setState(121);
			match(LPAREN);
			setState(122);
			columnDefinition();
			setState(127);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==COMMA) {
				{
				{
				setState(123);
				match(COMMA);
				setState(124);
				columnDefinition();
				}
				}
				setState(129);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(130);
			match(RPAREN);
			setState(143);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==WITH) {
				{
				setState(131);
				match(WITH);
				setState(132);
				match(LPAREN);
				setState(133);
				dataStreamOption();
				setState(138);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while (_la==COMMA) {
					{
					{
					setState(134);
					match(COMMA);
					setState(135);
					dataStreamOption();
					}
					}
					setState(140);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}
				setState(141);
				match(RPAREN);
				}
			}

			setState(145);
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
			setState(153);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case DATA_RETENTION:
				enterOuterAlt(_localctx, 1);
				{
				setState(147);
				match(DATA_RETENTION);
				setState(148);
				match(ASSIGN);
				setState(149);
				match(STRING);
				}
				break;
			case TIME_REFERENCE:
				enterOuterAlt(_localctx, 2);
				{
				setState(150);
				match(TIME_REFERENCE);
				setState(151);
				match(ASSIGN);
				setState(152);
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
			setState(155);
			match(CREATE);
			setState(156);
			match(COMPONENT);
			setState(157);
			match(TEMPLATE);
			setState(158);
			match(ID);
			setState(159);
			match(LPAREN);
			setState(160);
			templatePart();
			setState(165);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==COMMA) {
				{
				{
				setState(161);
				match(COMMA);
				setState(162);
				templatePart();
				}
				}
				setState(167);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(168);
			match(RPAREN);
			setState(169);
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
			setState(171);
			match(CREATE);
			setState(172);
			match(INDEX);
			setState(173);
			match(TEMPLATE);
			setState(174);
			match(ID);
			setState(175);
			match(FOR);
			setState(176);
			match(STRING);
			setState(177);
			match(USING);
			setState(178);
			match(STRING);
			setState(191);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==WITH) {
				{
				setState(179);
				match(WITH);
				setState(180);
				match(LPAREN);
				setState(181);
				templatePart();
				setState(186);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while (_la==COMMA) {
					{
					{
					setState(182);
					match(COMMA);
					setState(183);
					templatePart();
					}
					}
					setState(188);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}
				setState(189);
				match(RPAREN);
				}
			}

			setState(193);
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
			setState(202);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case NUMBER_OF_SHARDS:
				enterOuterAlt(_localctx, 1);
				{
				setState(195);
				match(NUMBER_OF_SHARDS);
				setState(196);
				match(ASSIGN);
				setState(197);
				match(INTEGER_LITERAL);
				}
				break;
			case NUMBER_OF_REPLICAS:
				enterOuterAlt(_localctx, 2);
				{
				setState(198);
				match(NUMBER_OF_REPLICAS);
				setState(199);
				match(ASSIGN);
				setState(200);
				match(INTEGER_LITERAL);
				}
				break;
			case ID:
				enterOuterAlt(_localctx, 3);
				{
				setState(201);
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
			setState(204);
			match(ALTER);
			setState(205);
			match(TABLE);
			setState(206);
			match(ID);
			setState(207);
			match(ADD);
			setState(208);
			match(COLUMN);
			setState(209);
			match(ID);
			setState(210);
			type();
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
			setState(213);
			match(REINDEX);
			setState(214);
			match(ID);
			setState(215);
			match(INTO);
			setState(216);
			match(ID);
			setState(218);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==WITH) {
				{
				setState(217);
				reindexOptions();
				}
			}

			setState(220);
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
			setState(222);
			match(WITH);
			setState(223);
			match(LPAREN);
			setState(224);
			reindexOption();
			setState(229);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==COMMA) {
				{
				{
				setState(225);
				match(COMMA);
				setState(226);
				reindexOption();
				}
				}
				setState(231);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(232);
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
			setState(261);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case CONFLICTS:
				enterOuterAlt(_localctx, 1);
				{
				setState(234);
				match(CONFLICTS);
				setState(235);
				match(ASSIGN);
				setState(236);
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
				setState(237);
				match(MAX_DOCS);
				setState(238);
				match(ASSIGN);
				setState(239);
				match(INTEGER_LITERAL);
				}
				break;
			case SLICES:
				enterOuterAlt(_localctx, 3);
				{
				setState(240);
				match(SLICES);
				setState(241);
				match(ASSIGN);
				setState(242);
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
				setState(243);
				match(SIZE);
				setState(244);
				match(ASSIGN);
				setState(245);
				match(INTEGER_LITERAL);
				}
				break;
			case SOURCE_FIELDS:
				enterOuterAlt(_localctx, 5);
				{
				setState(246);
				match(SOURCE_FIELDS);
				setState(247);
				match(ASSIGN);
				setState(248);
				match(STRING);
				}
				break;
			case QUERY:
				enterOuterAlt(_localctx, 6);
				{
				setState(249);
				match(QUERY);
				setState(250);
				match(ASSIGN);
				setState(251);
				match(STRING);
				}
				break;
			case SCRIPT:
				enterOuterAlt(_localctx, 7);
				{
				setState(252);
				match(SCRIPT);
				setState(253);
				match(ASSIGN);
				setState(254);
				match(STRING);
				}
				break;
			case WAIT:
				enterOuterAlt(_localctx, 8);
				{
				setState(255);
				match(WAIT);
				setState(256);
				match(ASSIGN);
				setState(257);
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
				setState(258);
				match(SKIP_IF_NO_SOURCE);
				setState(259);
				match(ASSIGN);
				setState(260);
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
			setState(263);
			match(UPDATE);
			setState(264);
			match(ID);
			setState(265);
			match(SET);
			setState(266);
			assignment();
			setState(271);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==COMMA) {
				{
				{
				setState(267);
				match(COMMA);
				setState(268);
				assignment();
				}
				}
				setState(273);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(274);
			match(WHERE);
			setState(275);
			whereClause(0);
			setState(278);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==WITH) {
				{
				setState(276);
				match(WITH);
				setState(277);
				match(REFRESH);
				}
			}

			setState(280);
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
			setState(282);
			match(DELETE);
			setState(283);
			match(FROM);
			setState(284);
			match(ID);
			setState(285);
			match(WHERE);
			setState(286);
			whereClause(0);
			setState(289);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==WITH) {
				{
				setState(287);
				match(WITH);
				setState(288);
				match(REFRESH);
				}
			}

			setState(291);
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
			setState(293);
			match(INSERT);
			setState(294);
			match(INTO);
			setState(295);
			tableName();
			setState(307);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==LPAREN) {
				{
				setState(296);
				match(LPAREN);
				setState(297);
				columnName();
				setState(302);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while (_la==COMMA) {
					{
					{
					setState(298);
					match(COMMA);
					setState(299);
					columnName();
					}
					}
					setState(304);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}
				setState(305);
				match(RPAREN);
				}
			}

			setState(309);
			match(VALUES);
			setState(310);
			match(LPAREN);
			setState(311);
			valueList();
			setState(312);
			match(RPAREN);
			setState(322);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==WITH) {
				{
				setState(313);
				match(WITH);
				setState(314);
				insertOption();
				setState(319);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while (_la==COMMA) {
					{
					{
					setState(315);
					match(COMMA);
					setState(316);
					insertOption();
					}
					}
					setState(321);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}
				}
			}

			setState(324);
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
		public TerminalNode ID() { return getToken(KinoticSQLParser.ID, 0); }
		public TerminalNode SEMICOLON() { return getToken(KinoticSQLParser.SEMICOLON, 0); }
		public TerminalNode WHERE() { return getToken(KinoticSQLParser.WHERE, 0); }
		public WhereClauseContext whereClause() {
			return getRuleContext(WhereClauseContext.class,0);
		}
		public TerminalNode ORDER() { return getToken(KinoticSQLParser.ORDER, 0); }
		public TerminalNode BY() { return getToken(KinoticSQLParser.BY, 0); }
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
			setState(326);
			match(SELECT);
			setState(327);
			selectList();
			setState(328);
			match(FROM);
			setState(329);
			match(ID);
			setState(332);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==WHERE) {
				{
				setState(330);
				match(WHERE);
				setState(331);
				whereClause(0);
				}
			}

			setState(344);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==ORDER) {
				{
				setState(334);
				match(ORDER);
				setState(335);
				match(BY);
				setState(336);
				orderBy();
				setState(341);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while (_la==COMMA) {
					{
					{
					setState(337);
					match(COMMA);
					setState(338);
					orderBy();
					}
					}
					setState(343);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}
				}
			}

			setState(348);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==LIMIT) {
				{
				setState(346);
				match(LIMIT);
				setState(347);
				match(INTEGER_LITERAL);
				}
			}

			setState(350);
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
		public List<ColumnNameContext> columnName() {
			return getRuleContexts(ColumnNameContext.class);
		}
		public ColumnNameContext columnName(int i) {
			return getRuleContext(ColumnNameContext.class,i);
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
			setState(361);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case MULTIPLY:
				enterOuterAlt(_localctx, 1);
				{
				setState(352);
				match(MULTIPLY);
				}
				break;
			case ID:
				enterOuterAlt(_localctx, 2);
				{
				setState(353);
				columnName();
				setState(358);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while (_la==COMMA) {
					{
					{
					setState(354);
					match(COMMA);
					setState(355);
					columnName();
					}
					}
					setState(360);
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
	public static class OrderByContext extends ParserRuleContext {
		public TerminalNode ID() { return getToken(KinoticSQLParser.ID, 0); }
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
		enterRule(_localctx, 34, RULE_orderBy);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(363);
			match(ID);
			setState(365);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==ASC || _la==DESC) {
				{
				setState(364);
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
		enterRule(_localctx, 36, RULE_insertOption);
		try {
			setState(372);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case REFRESH:
				enterOuterAlt(_localctx, 1);
				{
				setState(367);
				match(REFRESH);
				}
				break;
			case ROUTING:
				enterOuterAlt(_localctx, 2);
				{
				setState(368);
				match(ROUTING);
				setState(369);
				match(STRING);
				}
				break;
			case DOCUMENT_ID:
				enterOuterAlt(_localctx, 3);
				{
				setState(370);
				match(DOCUMENT_ID);
				setState(371);
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
		enterRule(_localctx, 38, RULE_valueList);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(374);
			value();
			setState(379);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==COMMA) {
				{
				{
				setState(375);
				match(COMMA);
				setState(376);
				value();
				}
				}
				setState(381);
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
		enterRule(_localctx, 40, RULE_value);
		try {
			setState(389);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case STRING:
				enterOuterAlt(_localctx, 1);
				{
				setState(382);
				match(STRING);
				}
				break;
			case MINUS:
			case DECIMAL_LITERAL:
			case INTEGER_LITERAL:
				enterOuterAlt(_localctx, 2);
				{
				setState(383);
				numberLiteral();
				}
				break;
			case BOOLEAN_LITERAL:
				enterOuterAlt(_localctx, 3);
				{
				setState(384);
				match(BOOLEAN_LITERAL);
				}
				break;
			case NULL_LITERAL:
				enterOuterAlt(_localctx, 4);
				{
				setState(385);
				match(NULL_LITERAL);
				}
				break;
			case COLON:
				enterOuterAlt(_localctx, 5);
				{
				setState(386);
				namedParameter();
				}
				break;
			case LBRACE:
				enterOuterAlt(_localctx, 6);
				{
				setState(387);
				objectLiteral();
				}
				break;
			case LBRACKET:
				enterOuterAlt(_localctx, 7);
				{
				setState(388);
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
		enterRule(_localctx, 42, RULE_namedParameter);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(391);
			match(COLON);
			setState(392);
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
		enterRule(_localctx, 44, RULE_objectLiteral);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(394);
			match(LBRACE);
			setState(403);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==ID || _la==STRING) {
				{
				setState(395);
				objectField();
				setState(400);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while (_la==COMMA) {
					{
					{
					setState(396);
					match(COMMA);
					setState(397);
					objectField();
					}
					}
					setState(402);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}
				}
			}

			setState(405);
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
		enterRule(_localctx, 46, RULE_objectField);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(407);
			_la = _input.LA(1);
			if ( !(_la==ID || _la==STRING) ) {
			_errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			setState(408);
			match(COLON);
			setState(409);
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
		enterRule(_localctx, 48, RULE_arrayLiteral);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(411);
			match(LBRACKET);
			setState(420);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (((((_la - 74)) & ~0x3f) == 0 && ((1L << (_la - 74)) & 28838081L) != 0)) {
				{
				setState(412);
				value();
				setState(417);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while (_la==COMMA) {
					{
					{
					setState(413);
					match(COMMA);
					setState(414);
					value();
					}
					}
					setState(419);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}
				}
			}

			setState(422);
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
		enterRule(_localctx, 50, RULE_numberLiteral);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(425);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==MINUS) {
				{
				setState(424);
				match(MINUS);
				}
			}

			setState(427);
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
		enterRule(_localctx, 52, RULE_assignment);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(429);
			match(ID);
			setState(430);
			match(ASSIGN);
			setState(431);
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
		enterRule(_localctx, 54, RULE_expression);
		try {
			setState(442);
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
				setState(433);
				value();
				}
				break;
			case ID:
				enterOuterAlt(_localctx, 2);
				{
				setState(434);
				match(ID);
				setState(435);
				operator();
				setState(436);
				expression();
				}
				break;
			case LPAREN:
				enterOuterAlt(_localctx, 3);
				{
				setState(438);
				match(LPAREN);
				setState(439);
				expression();
				setState(440);
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
		enterRule(_localctx, 56, RULE_operator);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(444);
			_la = _input.LA(1);
			if ( !(((((_la - 76)) & ~0x3f) == 0 && ((1L << (_la - 76)) & 5635L) != 0)) ) {
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
		int _startState = 58;
		enterRecursionRule(_localctx, 58, RULE_whereClause, _p);
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(452);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case ID:
				{
				setState(447);
				condition();
				}
				break;
			case LPAREN:
				{
				setState(448);
				match(LPAREN);
				setState(449);
				whereClause(0);
				setState(450);
				match(RPAREN);
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
			_ctx.stop = _input.LT(-1);
			setState(462);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,40,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					if ( _parseListeners!=null ) triggerExitRuleEvent();
					_prevctx = _localctx;
					{
					setState(460);
					_errHandler.sync(this);
					switch ( getInterpreter().adaptivePredict(_input,39,_ctx) ) {
					case 1:
						{
						_localctx = new WhereClauseContext(_parentctx, _parentState);
						pushNewRecursionContext(_localctx, _startState, RULE_whereClause);
						setState(454);
						if (!(precpred(_ctx, 2))) throw new FailedPredicateException(this, "precpred(_ctx, 2)");
						setState(455);
						match(AND);
						setState(456);
						whereClause(3);
						}
						break;
					case 2:
						{
						_localctx = new WhereClauseContext(_parentctx, _parentState);
						pushNewRecursionContext(_localctx, _startState, RULE_whereClause);
						setState(457);
						if (!(precpred(_ctx, 1))) throw new FailedPredicateException(this, "precpred(_ctx, 1)");
						setState(458);
						match(OR);
						setState(459);
						whereClause(2);
						}
						break;
					}
					} 
				}
				setState(464);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,40,_ctx);
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
		enterRule(_localctx, 60, RULE_condition);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(465);
			match(ID);
			setState(466);
			comparisonOperator();
			setState(471);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case COLON:
				{
				setState(467);
				namedParameter();
				}
				break;
			case STRING:
				{
				setState(468);
				match(STRING);
				}
				break;
			case MINUS:
			case DECIMAL_LITERAL:
			case INTEGER_LITERAL:
				{
				setState(469);
				numberLiteral();
				}
				break;
			case BOOLEAN_LITERAL:
				{
				setState(470);
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
		enterRule(_localctx, 62, RULE_comparisonOperator);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(473);
			_la = _input.LA(1);
			if ( !(((((_la - 77)) & ~0x3f) == 0 && ((1L << (_la - 77)) & 1127L) != 0)) ) {
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
		enterRule(_localctx, 64, RULE_tableName);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(475);
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
		enterRule(_localctx, 66, RULE_columnName);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(477);
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
		enterRule(_localctx, 68, RULE_columnDefinition);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(479);
			match(ID);
			setState(480);
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
		enterRule(_localctx, 70, RULE_unionVariant);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(482);
			match(ID);
			setState(483);
			match(LPAREN);
			setState(484);
			columnDefinition();
			setState(489);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==COMMA) {
				{
				{
				setState(485);
				match(COMMA);
				setState(486);
				columnDefinition();
				}
				}
				setState(491);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(492);
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
		enterRule(_localctx, 72, RULE_type);
		int _la;
		try {
			setState(590);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case TEXT:
				enterOuterAlt(_localctx, 1);
				{
				setState(494);
				match(TEXT);
				}
				break;
			case KEYWORD:
				enterOuterAlt(_localctx, 2);
				{
				setState(495);
				match(KEYWORD);
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
			case INTEGER:
				enterOuterAlt(_localctx, 3);
				{
				setState(500);
				match(INTEGER);
				setState(503);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==NOT) {
					{
					setState(501);
					match(NOT);
					setState(502);
					match(INDEXED);
					}
				}

				}
				break;
			case LONG:
				enterOuterAlt(_localctx, 4);
				{
				setState(505);
				match(LONG);
				setState(508);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==NOT) {
					{
					setState(506);
					match(NOT);
					setState(507);
					match(INDEXED);
					}
				}

				}
				break;
			case FLOAT:
				enterOuterAlt(_localctx, 5);
				{
				setState(510);
				match(FLOAT);
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
			case DOUBLE:
				enterOuterAlt(_localctx, 6);
				{
				setState(515);
				match(DOUBLE);
				setState(518);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==NOT) {
					{
					setState(516);
					match(NOT);
					setState(517);
					match(INDEXED);
					}
				}

				}
				break;
			case BOOLEAN:
				enterOuterAlt(_localctx, 7);
				{
				setState(520);
				match(BOOLEAN);
				setState(523);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==NOT) {
					{
					setState(521);
					match(NOT);
					setState(522);
					match(INDEXED);
					}
				}

				}
				break;
			case DATE:
				enterOuterAlt(_localctx, 8);
				{
				setState(525);
				match(DATE);
				setState(528);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==NOT) {
					{
					setState(526);
					match(NOT);
					setState(527);
					match(INDEXED);
					}
				}

				}
				break;
			case JSON:
				enterOuterAlt(_localctx, 9);
				{
				setState(530);
				match(JSON);
				setState(533);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==NOT) {
					{
					setState(531);
					match(NOT);
					setState(532);
					match(INDEXED);
					}
				}

				}
				break;
			case BINARY:
				enterOuterAlt(_localctx, 10);
				{
				setState(535);
				match(BINARY);
				}
				break;
			case GEO_POINT:
				enterOuterAlt(_localctx, 11);
				{
				setState(536);
				match(GEO_POINT);
				}
				break;
			case GEO_SHAPE:
				enterOuterAlt(_localctx, 12);
				{
				setState(537);
				match(GEO_SHAPE);
				}
				break;
			case UUID:
				enterOuterAlt(_localctx, 13);
				{
				setState(538);
				match(UUID);
				setState(541);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==NOT) {
					{
					setState(539);
					match(NOT);
					setState(540);
					match(INDEXED);
					}
				}

				}
				break;
			case DECIMAL:
				enterOuterAlt(_localctx, 14);
				{
				setState(543);
				match(DECIMAL);
				setState(546);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==NOT) {
					{
					setState(544);
					match(NOT);
					setState(545);
					match(INDEXED);
					}
				}

				}
				break;
			case OBJECT:
				enterOuterAlt(_localctx, 15);
				{
				setState(548);
				match(OBJECT);
				setState(549);
				match(LPAREN);
				setState(550);
				columnDefinition();
				setState(555);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while (_la==COMMA) {
					{
					{
					setState(551);
					match(COMMA);
					setState(552);
					columnDefinition();
					}
					}
					setState(557);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}
				setState(558);
				match(RPAREN);
				setState(561);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==NOT) {
					{
					setState(559);
					match(NOT);
					setState(560);
					match(INDEXED);
					}
				}

				}
				break;
			case NESTED:
				enterOuterAlt(_localctx, 16);
				{
				setState(563);
				match(NESTED);
				setState(564);
				match(LPAREN);
				setState(565);
				columnDefinition();
				setState(570);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while (_la==COMMA) {
					{
					{
					setState(566);
					match(COMMA);
					setState(567);
					columnDefinition();
					}
					}
					setState(572);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}
				setState(573);
				match(RPAREN);
				}
				break;
			case UNION:
				enterOuterAlt(_localctx, 17);
				{
				setState(575);
				match(UNION);
				setState(576);
				match(LPAREN);
				setState(577);
				unionVariant();
				setState(582);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while (_la==COMMA) {
					{
					{
					setState(578);
					match(COMMA);
					setState(579);
					unionVariant();
					}
					}
					setState(584);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}
				setState(585);
				match(RPAREN);
				setState(588);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==NOT) {
					{
					setState(586);
					match(NOT);
					setState(587);
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
		enterRule(_localctx, 74, RULE_comment);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(592);
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
		case 29:
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
		"\u0004\u0001d\u0253\u0002\u0000\u0007\u0000\u0002\u0001\u0007\u0001\u0002"+
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
		"#\u0007#\u0002$\u0007$\u0002%\u0007%\u0001\u0000\u0005\u0000N\b\u0000"+
		"\n\u0000\f\u0000Q\t\u0000\u0001\u0000\u0001\u0000\u0001\u0001\u0001\u0001"+
		"\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001"+
		"\u0001\u0001\u0001\u0001\u0001\u0001\u0003\u0001`\b\u0001\u0001\u0002"+
		"\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0003\u0002g\b\u0002"+
		"\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0005\u0002"+
		"n\b\u0002\n\u0002\f\u0002q\t\u0002\u0001\u0002\u0001\u0002\u0001\u0002"+
		"\u0001\u0003\u0001\u0003\u0001\u0003\u0001\u0003\u0001\u0003\u0001\u0003"+
		"\u0001\u0003\u0001\u0003\u0005\u0003~\b\u0003\n\u0003\f\u0003\u0081\t"+
		"\u0003\u0001\u0003\u0001\u0003\u0001\u0003\u0001\u0003\u0001\u0003\u0001"+
		"\u0003\u0005\u0003\u0089\b\u0003\n\u0003\f\u0003\u008c\t\u0003\u0001\u0003"+
		"\u0001\u0003\u0003\u0003\u0090\b\u0003\u0001\u0003\u0001\u0003\u0001\u0004"+
		"\u0001\u0004\u0001\u0004\u0001\u0004\u0001\u0004\u0001\u0004\u0003\u0004"+
		"\u009a\b\u0004\u0001\u0005\u0001\u0005\u0001\u0005\u0001\u0005\u0001\u0005"+
		"\u0001\u0005\u0001\u0005\u0001\u0005\u0005\u0005\u00a4\b\u0005\n\u0005"+
		"\f\u0005\u00a7\t\u0005\u0001\u0005\u0001\u0005\u0001\u0005\u0001\u0006"+
		"\u0001\u0006\u0001\u0006\u0001\u0006\u0001\u0006\u0001\u0006\u0001\u0006"+
		"\u0001\u0006\u0001\u0006\u0001\u0006\u0001\u0006\u0001\u0006\u0001\u0006"+
		"\u0005\u0006\u00b9\b\u0006\n\u0006\f\u0006\u00bc\t\u0006\u0001\u0006\u0001"+
		"\u0006\u0003\u0006\u00c0\b\u0006\u0001\u0006\u0001\u0006\u0001\u0007\u0001"+
		"\u0007\u0001\u0007\u0001\u0007\u0001\u0007\u0001\u0007\u0001\u0007\u0003"+
		"\u0007\u00cb\b\u0007\u0001\b\u0001\b\u0001\b\u0001\b\u0001\b\u0001\b\u0001"+
		"\b\u0001\b\u0001\b\u0001\t\u0001\t\u0001\t\u0001\t\u0001\t\u0003\t\u00db"+
		"\b\t\u0001\t\u0001\t\u0001\n\u0001\n\u0001\n\u0001\n\u0001\n\u0005\n\u00e4"+
		"\b\n\n\n\f\n\u00e7\t\n\u0001\n\u0001\n\u0001\u000b\u0001\u000b\u0001\u000b"+
		"\u0001\u000b\u0001\u000b\u0001\u000b\u0001\u000b\u0001\u000b\u0001\u000b"+
		"\u0001\u000b\u0001\u000b\u0001\u000b\u0001\u000b\u0001\u000b\u0001\u000b"+
		"\u0001\u000b\u0001\u000b\u0001\u000b\u0001\u000b\u0001\u000b\u0001\u000b"+
		"\u0001\u000b\u0001\u000b\u0001\u000b\u0001\u000b\u0001\u000b\u0001\u000b"+
		"\u0003\u000b\u0106\b\u000b\u0001\f\u0001\f\u0001\f\u0001\f\u0001\f\u0001"+
		"\f\u0005\f\u010e\b\f\n\f\f\f\u0111\t\f\u0001\f\u0001\f\u0001\f\u0001\f"+
		"\u0003\f\u0117\b\f\u0001\f\u0001\f\u0001\r\u0001\r\u0001\r\u0001\r\u0001"+
		"\r\u0001\r\u0001\r\u0003\r\u0122\b\r\u0001\r\u0001\r\u0001\u000e\u0001"+
		"\u000e\u0001\u000e\u0001\u000e\u0001\u000e\u0001\u000e\u0001\u000e\u0005"+
		"\u000e\u012d\b\u000e\n\u000e\f\u000e\u0130\t\u000e\u0001\u000e\u0001\u000e"+
		"\u0003\u000e\u0134\b\u000e\u0001\u000e\u0001\u000e\u0001\u000e\u0001\u000e"+
		"\u0001\u000e\u0001\u000e\u0001\u000e\u0001\u000e\u0005\u000e\u013e\b\u000e"+
		"\n\u000e\f\u000e\u0141\t\u000e\u0003\u000e\u0143\b\u000e\u0001\u000e\u0001"+
		"\u000e\u0001\u000f\u0001\u000f\u0001\u000f\u0001\u000f\u0001\u000f\u0001"+
		"\u000f\u0003\u000f\u014d\b\u000f\u0001\u000f\u0001\u000f\u0001\u000f\u0001"+
		"\u000f\u0001\u000f\u0005\u000f\u0154\b\u000f\n\u000f\f\u000f\u0157\t\u000f"+
		"\u0003\u000f\u0159\b\u000f\u0001\u000f\u0001\u000f\u0003\u000f\u015d\b"+
		"\u000f\u0001\u000f\u0001\u000f\u0001\u0010\u0001\u0010\u0001\u0010\u0001"+
		"\u0010\u0005\u0010\u0165\b\u0010\n\u0010\f\u0010\u0168\t\u0010\u0003\u0010"+
		"\u016a\b\u0010\u0001\u0011\u0001\u0011\u0003\u0011\u016e\b\u0011\u0001"+
		"\u0012\u0001\u0012\u0001\u0012\u0001\u0012\u0001\u0012\u0003\u0012\u0175"+
		"\b\u0012\u0001\u0013\u0001\u0013\u0001\u0013\u0005\u0013\u017a\b\u0013"+
		"\n\u0013\f\u0013\u017d\t\u0013\u0001\u0014\u0001\u0014\u0001\u0014\u0001"+
		"\u0014\u0001\u0014\u0001\u0014\u0001\u0014\u0003\u0014\u0186\b\u0014\u0001"+
		"\u0015\u0001\u0015\u0001\u0015\u0001\u0016\u0001\u0016\u0001\u0016\u0001"+
		"\u0016\u0005\u0016\u018f\b\u0016\n\u0016\f\u0016\u0192\t\u0016\u0003\u0016"+
		"\u0194\b\u0016\u0001\u0016\u0001\u0016\u0001\u0017\u0001\u0017\u0001\u0017"+
		"\u0001\u0017\u0001\u0018\u0001\u0018\u0001\u0018\u0001\u0018\u0005\u0018"+
		"\u01a0\b\u0018\n\u0018\f\u0018\u01a3\t\u0018\u0003\u0018\u01a5\b\u0018"+
		"\u0001\u0018\u0001\u0018\u0001\u0019\u0003\u0019\u01aa\b\u0019\u0001\u0019"+
		"\u0001\u0019\u0001\u001a\u0001\u001a\u0001\u001a\u0001\u001a\u0001\u001b"+
		"\u0001\u001b\u0001\u001b\u0001\u001b\u0001\u001b\u0001\u001b\u0001\u001b"+
		"\u0001\u001b\u0001\u001b\u0003\u001b\u01bb\b\u001b\u0001\u001c\u0001\u001c"+
		"\u0001\u001d\u0001\u001d\u0001\u001d\u0001\u001d\u0001\u001d\u0001\u001d"+
		"\u0003\u001d\u01c5\b\u001d\u0001\u001d\u0001\u001d\u0001\u001d\u0001\u001d"+
		"\u0001\u001d\u0001\u001d\u0005\u001d\u01cd\b\u001d\n\u001d\f\u001d\u01d0"+
		"\t\u001d\u0001\u001e\u0001\u001e\u0001\u001e\u0001\u001e\u0001\u001e\u0001"+
		"\u001e\u0003\u001e\u01d8\b\u001e\u0001\u001f\u0001\u001f\u0001 \u0001"+
		" \u0001!\u0001!\u0001\"\u0001\"\u0001\"\u0001#\u0001#\u0001#\u0001#\u0001"+
		"#\u0005#\u01e8\b#\n#\f#\u01eb\t#\u0001#\u0001#\u0001$\u0001$\u0001$\u0001"+
		"$\u0003$\u01f3\b$\u0001$\u0001$\u0001$\u0003$\u01f8\b$\u0001$\u0001$\u0001"+
		"$\u0003$\u01fd\b$\u0001$\u0001$\u0001$\u0003$\u0202\b$\u0001$\u0001$\u0001"+
		"$\u0003$\u0207\b$\u0001$\u0001$\u0001$\u0003$\u020c\b$\u0001$\u0001$\u0001"+
		"$\u0003$\u0211\b$\u0001$\u0001$\u0001$\u0003$\u0216\b$\u0001$\u0001$\u0001"+
		"$\u0001$\u0001$\u0001$\u0003$\u021e\b$\u0001$\u0001$\u0001$\u0003$\u0223"+
		"\b$\u0001$\u0001$\u0001$\u0001$\u0001$\u0005$\u022a\b$\n$\f$\u022d\t$"+
		"\u0001$\u0001$\u0001$\u0003$\u0232\b$\u0001$\u0001$\u0001$\u0001$\u0001"+
		"$\u0005$\u0239\b$\n$\f$\u023c\t$\u0001$\u0001$\u0001$\u0001$\u0001$\u0001"+
		"$\u0001$\u0005$\u0245\b$\n$\f$\u0248\t$\u0001$\u0001$\u0001$\u0003$\u024d"+
		"\b$\u0003$\u024f\b$\u0001%\u0001%\u0001%\u0000\u0001:&\u0000\u0002\u0004"+
		"\u0006\b\n\f\u000e\u0010\u0012\u0014\u0016\u0018\u001a\u001c\u001e \""+
		"$&(*,.02468:<>@BDFHJ\u0000\b\u0002\u0000\u0001\u0001$$\u0002\u0000\u0006"+
		"\u0006aa\u0001\u00009:\u0002\u0000\u0005\u0005\u0010\u0010\u0002\u0000"+
		"``bb\u0002\u0000^^aa\u0003\u0000LMUVXX\u0003\u0000MORSWW\u0290\u0000O"+
		"\u0001\u0000\u0000\u0000\u0002_\u0001\u0000\u0000\u0000\u0004a\u0001\u0000"+
		"\u0000\u0000\u0006u\u0001\u0000\u0000\u0000\b\u0099\u0001\u0000\u0000"+
		"\u0000\n\u009b\u0001\u0000\u0000\u0000\f\u00ab\u0001\u0000\u0000\u0000"+
		"\u000e\u00ca\u0001\u0000\u0000\u0000\u0010\u00cc\u0001\u0000\u0000\u0000"+
		"\u0012\u00d5\u0001\u0000\u0000\u0000\u0014\u00de\u0001\u0000\u0000\u0000"+
		"\u0016\u0105\u0001\u0000\u0000\u0000\u0018\u0107\u0001\u0000\u0000\u0000"+
		"\u001a\u011a\u0001\u0000\u0000\u0000\u001c\u0125\u0001\u0000\u0000\u0000"+
		"\u001e\u0146\u0001\u0000\u0000\u0000 \u0169\u0001\u0000\u0000\u0000\""+
		"\u016b\u0001\u0000\u0000\u0000$\u0174\u0001\u0000\u0000\u0000&\u0176\u0001"+
		"\u0000\u0000\u0000(\u0185\u0001\u0000\u0000\u0000*\u0187\u0001\u0000\u0000"+
		"\u0000,\u018a\u0001\u0000\u0000\u0000.\u0197\u0001\u0000\u0000\u00000"+
		"\u019b\u0001\u0000\u0000\u00002\u01a9\u0001\u0000\u0000\u00004\u01ad\u0001"+
		"\u0000\u0000\u00006\u01ba\u0001\u0000\u0000\u00008\u01bc\u0001\u0000\u0000"+
		"\u0000:\u01c4\u0001\u0000\u0000\u0000<\u01d1\u0001\u0000\u0000\u0000>"+
		"\u01d9\u0001\u0000\u0000\u0000@\u01db\u0001\u0000\u0000\u0000B\u01dd\u0001"+
		"\u0000\u0000\u0000D\u01df\u0001\u0000\u0000\u0000F\u01e2\u0001\u0000\u0000"+
		"\u0000H\u024e\u0001\u0000\u0000\u0000J\u0250\u0001\u0000\u0000\u0000L"+
		"N\u0003\u0002\u0001\u0000ML\u0001\u0000\u0000\u0000NQ\u0001\u0000\u0000"+
		"\u0000OM\u0001\u0000\u0000\u0000OP\u0001\u0000\u0000\u0000PR\u0001\u0000"+
		"\u0000\u0000QO\u0001\u0000\u0000\u0000RS\u0005\u0000\u0000\u0001S\u0001"+
		"\u0001\u0000\u0000\u0000T`\u0003\u0004\u0002\u0000U`\u0003\u0006\u0003"+
		"\u0000V`\u0003\n\u0005\u0000W`\u0003\f\u0006\u0000X`\u0003\u0010\b\u0000"+
		"Y`\u0003\u0012\t\u0000Z`\u0003\u0018\f\u0000[`\u0003\u001a\r\u0000\\`"+
		"\u0003\u001c\u000e\u0000]`\u0003\u001e\u000f\u0000^`\u0003J%\u0000_T\u0001"+
		"\u0000\u0000\u0000_U\u0001\u0000\u0000\u0000_V\u0001\u0000\u0000\u0000"+
		"_W\u0001\u0000\u0000\u0000_X\u0001\u0000\u0000\u0000_Y\u0001\u0000\u0000"+
		"\u0000_Z\u0001\u0000\u0000\u0000_[\u0001\u0000\u0000\u0000_\\\u0001\u0000"+
		"\u0000\u0000_]\u0001\u0000\u0000\u0000_^\u0001\u0000\u0000\u0000`\u0003"+
		"\u0001\u0000\u0000\u0000ab\u0005\u000b\u0000\u0000bf\u00050\u0000\u0000"+
		"cd\u0005\u0017\u0000\u0000de\u0005\u001f\u0000\u0000eg\u0005\u0013\u0000"+
		"\u0000fc\u0001\u0000\u0000\u0000fg\u0001\u0000\u0000\u0000gh\u0001\u0000"+
		"\u0000\u0000hi\u0005`\u0000\u0000ij\u0005T\u0000\u0000jo\u0003D\"\u0000"+
		"kl\u0005K\u0000\u0000ln\u0003D\"\u0000mk\u0001\u0000\u0000\u0000nq\u0001"+
		"\u0000\u0000\u0000om\u0001\u0000\u0000\u0000op\u0001\u0000\u0000\u0000"+
		"pr\u0001\u0000\u0000\u0000qo\u0001\u0000\u0000\u0000rs\u0005[\u0000\u0000"+
		"st\u0005\\\u0000\u0000t\u0005\u0001\u0000\u0000\u0000uv\u0005\u000b\u0000"+
		"\u0000vw\u0005\f\u0000\u0000wx\u0005/\u0000\u0000xy\u0005`\u0000\u0000"+
		"yz\u0005T\u0000\u0000z\u007f\u0003D\"\u0000{|\u0005K\u0000\u0000|~\u0003"+
		"D\"\u0000}{\u0001\u0000\u0000\u0000~\u0081\u0001\u0000\u0000\u0000\u007f"+
		"}\u0001\u0000\u0000\u0000\u007f\u0080\u0001\u0000\u0000\u0000\u0080\u0082"+
		"\u0001\u0000\u0000\u0000\u0081\u007f\u0001\u0000\u0000\u0000\u0082\u008f"+
		"\u0005[\u0000\u0000\u0083\u0084\u00057\u0000\u0000\u0084\u0085\u0005T"+
		"\u0000\u0000\u0085\u008a\u0003\b\u0004\u0000\u0086\u0087\u0005K\u0000"+
		"\u0000\u0087\u0089\u0003\b\u0004\u0000\u0088\u0086\u0001\u0000\u0000\u0000"+
		"\u0089\u008c\u0001\u0000\u0000\u0000\u008a\u0088\u0001\u0000\u0000\u0000"+
		"\u008a\u008b\u0001\u0000\u0000\u0000\u008b\u008d\u0001\u0000\u0000\u0000"+
		"\u008c\u008a\u0001\u0000\u0000\u0000\u008d\u008e\u0005[\u0000\u0000\u008e"+
		"\u0090\u0001\u0000\u0000\u0000\u008f\u0083\u0001\u0000\u0000\u0000\u008f"+
		"\u0090\u0001\u0000\u0000\u0000\u0090\u0091\u0001\u0000\u0000\u0000\u0091"+
		"\u0092\u0005\\\u0000\u0000\u0092\u0007\u0001\u0000\u0000\u0000\u0093\u0094"+
		"\u0005\r\u0000\u0000\u0094\u0095\u0005I\u0000\u0000\u0095\u009a\u0005"+
		"b\u0000\u0000\u0096\u0097\u00052\u0000\u0000\u0097\u0098\u0005I\u0000"+
		"\u0000\u0098\u009a\u0005b\u0000\u0000\u0099\u0093\u0001\u0000\u0000\u0000"+
		"\u0099\u0096\u0001\u0000\u0000\u0000\u009a\t\u0001\u0000\u0000\u0000\u009b"+
		"\u009c\u0005\u000b\u0000\u0000\u009c\u009d\u0005\t\u0000\u0000\u009d\u009e"+
		"\u00051\u0000\u0000\u009e\u009f\u0005`\u0000\u0000\u009f\u00a0\u0005T"+
		"\u0000\u0000\u00a0\u00a5\u0003\u000e\u0007\u0000\u00a1\u00a2\u0005K\u0000"+
		"\u0000\u00a2\u00a4\u0003\u000e\u0007\u0000\u00a3\u00a1\u0001\u0000\u0000"+
		"\u0000\u00a4\u00a7\u0001\u0000\u0000\u0000\u00a5\u00a3\u0001\u0000\u0000"+
		"\u0000\u00a5\u00a6\u0001\u0000\u0000\u0000\u00a6\u00a8\u0001\u0000\u0000"+
		"\u0000\u00a7\u00a5\u0001\u0000\u0000\u0000\u00a8\u00a9\u0005[\u0000\u0000"+
		"\u00a9\u00aa\u0005\\\u0000\u0000\u00aa\u000b\u0001\u0000\u0000\u0000\u00ab"+
		"\u00ac\u0005\u000b\u0000\u0000\u00ac\u00ad\u0005\u0018\u0000\u0000\u00ad"+
		"\u00ae\u00051\u0000\u0000\u00ae\u00af\u0005`\u0000\u0000\u00af\u00b0\u0005"+
		"\u0015\u0000\u0000\u00b0\u00b1\u0005b\u0000\u0000\u00b1\u00b2\u00054\u0000"+
		"\u0000\u00b2\u00bf\u0005b\u0000\u0000\u00b3\u00b4\u00057\u0000\u0000\u00b4"+
		"\u00b5\u0005T\u0000\u0000\u00b5\u00ba\u0003\u000e\u0007\u0000\u00b6\u00b7"+
		"\u0005K\u0000\u0000\u00b7\u00b9\u0003\u000e\u0007\u0000\u00b8\u00b6\u0001"+
		"\u0000\u0000\u0000\u00b9\u00bc\u0001\u0000\u0000\u0000\u00ba\u00b8\u0001"+
		"\u0000\u0000\u0000\u00ba\u00bb\u0001\u0000\u0000\u0000\u00bb\u00bd\u0001"+
		"\u0000\u0000\u0000\u00bc\u00ba\u0001\u0000\u0000\u0000\u00bd\u00be\u0005"+
		"[\u0000\u0000\u00be\u00c0\u0001\u0000\u0000\u0000\u00bf\u00b3\u0001\u0000"+
		"\u0000\u0000\u00bf\u00c0\u0001\u0000\u0000\u0000\u00c0\u00c1\u0001\u0000"+
		"\u0000\u0000\u00c1\u00c2\u0005\\\u0000\u0000\u00c2\r\u0001\u0000\u0000"+
		"\u0000\u00c3\u00c4\u0005!\u0000\u0000\u00c4\u00c5\u0005I\u0000\u0000\u00c5"+
		"\u00cb\u0005a\u0000\u0000\u00c6\u00c7\u0005 \u0000\u0000\u00c7\u00c8\u0005"+
		"I\u0000\u0000\u00c8\u00cb\u0005a\u0000\u0000\u00c9\u00cb\u0003D\"\u0000"+
		"\u00ca\u00c3\u0001\u0000\u0000\u0000\u00ca\u00c6\u0001\u0000\u0000\u0000"+
		"\u00ca\u00c9\u0001\u0000\u0000\u0000\u00cb\u000f\u0001\u0000\u0000\u0000"+
		"\u00cc\u00cd\u0005\u0003\u0000\u0000\u00cd\u00ce\u00050\u0000\u0000\u00ce"+
		"\u00cf\u0005`\u0000\u0000\u00cf\u00d0\u0005\u0002\u0000\u0000\u00d0\u00d1"+
		"\u0005\b\u0000\u0000\u00d1\u00d2\u0005`\u0000\u0000\u00d2\u00d3\u0003"+
		"H$\u0000\u00d3\u00d4\u0005\\\u0000\u0000\u00d4\u0011\u0001\u0000\u0000"+
		"\u0000\u00d5\u00d6\u0005\'\u0000\u0000\u00d6\u00d7\u0005`\u0000\u0000"+
		"\u00d7\u00d8\u0005\u001b\u0000\u0000\u00d8\u00da\u0005`\u0000\u0000\u00d9"+
		"\u00db\u0003\u0014\n\u0000\u00da\u00d9\u0001\u0000\u0000\u0000\u00da\u00db"+
		"\u0001\u0000\u0000\u0000\u00db\u00dc\u0001\u0000\u0000\u0000\u00dc\u00dd"+
		"\u0005\\\u0000\u0000\u00dd\u0013\u0001\u0000\u0000\u0000\u00de\u00df\u0005"+
		"7\u0000\u0000\u00df\u00e0\u0005T\u0000\u0000\u00e0\u00e5\u0003\u0016\u000b"+
		"\u0000\u00e1\u00e2\u0005K\u0000\u0000\u00e2\u00e4\u0003\u0016\u000b\u0000"+
		"\u00e3\u00e1\u0001\u0000\u0000\u0000\u00e4\u00e7\u0001\u0000\u0000\u0000"+
		"\u00e5\u00e3\u0001\u0000\u0000\u0000\u00e5\u00e6\u0001\u0000\u0000\u0000"+
		"\u00e6\u00e8\u0001\u0000\u0000\u0000\u00e7\u00e5\u0001\u0000\u0000\u0000"+
		"\u00e8\u00e9\u0005[\u0000\u0000\u00e9\u0015\u0001\u0000\u0000\u0000\u00ea"+
		"\u00eb\u0005\n\u0000\u0000\u00eb\u00ec\u0005I\u0000\u0000\u00ec\u0106"+
		"\u0007\u0000\u0000\u0000\u00ed\u00ee\u0005\u001e\u0000\u0000\u00ee\u00ef"+
		"\u0005I\u0000\u0000\u00ef\u0106\u0005a\u0000\u0000\u00f0\u00f1\u0005-"+
		"\u0000\u0000\u00f1\u00f2\u0005I\u0000\u0000\u00f2\u0106\u0007\u0001\u0000"+
		"\u0000\u00f3\u00f4\u0005,\u0000\u0000\u00f4\u00f5\u0005I\u0000\u0000\u00f5"+
		"\u0106\u0005a\u0000\u0000\u00f6\u00f7\u0005.\u0000\u0000\u00f7\u00f8\u0005"+
		"I\u0000\u0000\u00f8\u0106\u0005b\u0000\u0000\u00f9\u00fa\u0005%\u0000"+
		"\u0000\u00fa\u00fb\u0005I\u0000\u0000\u00fb\u0106\u0005b\u0000\u0000\u00fc"+
		"\u00fd\u0005)\u0000\u0000\u00fd\u00fe\u0005I\u0000\u0000\u00fe\u0106\u0005"+
		"b\u0000\u0000\u00ff\u0100\u00058\u0000\u0000\u0100\u0101\u0005I\u0000"+
		"\u0000\u0101\u0106\u0007\u0002\u0000\u0000\u0102\u0103\u0005;\u0000\u0000"+
		"\u0103\u0104\u0005I\u0000\u0000\u0104\u0106\u0007\u0002\u0000\u0000\u0105"+
		"\u00ea\u0001\u0000\u0000\u0000\u0105\u00ed\u0001\u0000\u0000\u0000\u0105"+
		"\u00f0\u0001\u0000\u0000\u0000\u0105\u00f3\u0001\u0000\u0000\u0000\u0105"+
		"\u00f6\u0001\u0000\u0000\u0000\u0105\u00f9\u0001\u0000\u0000\u0000\u0105"+
		"\u00fc\u0001\u0000\u0000\u0000\u0105\u00ff\u0001\u0000\u0000\u0000\u0105"+
		"\u0102\u0001\u0000\u0000\u0000\u0106\u0017\u0001\u0000\u0000\u0000\u0107"+
		"\u0108\u00053\u0000\u0000\u0108\u0109\u0005`\u0000\u0000\u0109\u010a\u0005"+
		"+\u0000\u0000\u010a\u010f\u00034\u001a\u0000\u010b\u010c\u0005K\u0000"+
		"\u0000\u010c\u010e\u00034\u001a\u0000\u010d\u010b\u0001\u0000\u0000\u0000"+
		"\u010e\u0111\u0001\u0000\u0000\u0000\u010f\u010d\u0001\u0000\u0000\u0000"+
		"\u010f\u0110\u0001\u0000\u0000\u0000\u0110\u0112\u0001\u0000\u0000\u0000"+
		"\u0111\u010f\u0001\u0000\u0000\u0000\u0112\u0113\u00056\u0000\u0000\u0113"+
		"\u0116\u0003:\u001d\u0000\u0114\u0115\u00057\u0000\u0000\u0115\u0117\u0005"+
		"&\u0000\u0000\u0116\u0114\u0001\u0000\u0000\u0000\u0116\u0117\u0001\u0000"+
		"\u0000\u0000\u0117\u0118\u0001\u0000\u0000\u0000\u0118\u0119\u0005\\\u0000"+
		"\u0000\u0119\u0019\u0001\u0000\u0000\u0000\u011a\u011b\u0005\u000f\u0000"+
		"\u0000\u011b\u011c\u0005\u0016\u0000\u0000\u011c\u011d\u0005`\u0000\u0000"+
		"\u011d\u011e\u00056\u0000\u0000\u011e\u0121\u0003:\u001d\u0000\u011f\u0120"+
		"\u00057\u0000\u0000\u0120\u0122\u0005&\u0000\u0000\u0121\u011f\u0001\u0000"+
		"\u0000\u0000\u0121\u0122\u0001\u0000\u0000\u0000\u0122\u0123\u0001\u0000"+
		"\u0000\u0000\u0123\u0124\u0005\\\u0000\u0000\u0124\u001b\u0001\u0000\u0000"+
		"\u0000\u0125\u0126\u0005\u001a\u0000\u0000\u0126\u0127\u0005\u001b\u0000"+
		"\u0000\u0127\u0133\u0003@ \u0000\u0128\u0129\u0005T\u0000\u0000\u0129"+
		"\u012e\u0003B!\u0000\u012a\u012b\u0005K\u0000\u0000\u012b\u012d\u0003"+
		"B!\u0000\u012c\u012a\u0001\u0000\u0000\u0000\u012d\u0130\u0001\u0000\u0000"+
		"\u0000\u012e\u012c\u0001\u0000\u0000\u0000\u012e\u012f\u0001\u0000\u0000"+
		"\u0000\u012f\u0131\u0001\u0000\u0000\u0000\u0130\u012e\u0001\u0000\u0000"+
		"\u0000\u0131\u0132\u0005[\u0000\u0000\u0132\u0134\u0001\u0000\u0000\u0000"+
		"\u0133\u0128\u0001\u0000\u0000\u0000\u0133\u0134\u0001\u0000\u0000\u0000"+
		"\u0134\u0135\u0001\u0000\u0000\u0000\u0135\u0136\u00055\u0000\u0000\u0136"+
		"\u0137\u0005T\u0000\u0000\u0137\u0138\u0003&\u0013\u0000\u0138\u0142\u0005"+
		"[\u0000\u0000\u0139\u013a\u00057\u0000\u0000\u013a\u013f\u0003$\u0012"+
		"\u0000\u013b\u013c\u0005K\u0000\u0000\u013c\u013e\u0003$\u0012\u0000\u013d"+
		"\u013b\u0001\u0000\u0000\u0000\u013e\u0141\u0001\u0000\u0000\u0000\u013f"+
		"\u013d\u0001\u0000\u0000\u0000\u013f\u0140\u0001\u0000\u0000\u0000\u0140"+
		"\u0143\u0001\u0000\u0000\u0000\u0141\u013f\u0001\u0000\u0000\u0000\u0142"+
		"\u0139\u0001\u0000\u0000\u0000\u0142\u0143\u0001\u0000\u0000\u0000\u0143"+
		"\u0144\u0001\u0000\u0000\u0000\u0144\u0145\u0005\\\u0000\u0000\u0145\u001d"+
		"\u0001\u0000\u0000\u0000\u0146\u0147\u0005*\u0000\u0000\u0147\u0148\u0003"+
		" \u0010\u0000\u0148\u0149\u0005\u0016\u0000\u0000\u0149\u014c\u0005`\u0000"+
		"\u0000\u014a\u014b\u00056\u0000\u0000\u014b\u014d\u0003:\u001d\u0000\u014c"+
		"\u014a\u0001\u0000\u0000\u0000\u014c\u014d\u0001\u0000\u0000\u0000\u014d"+
		"\u0158\u0001\u0000\u0000\u0000\u014e\u014f\u0005#\u0000\u0000\u014f\u0150"+
		"\u0005\u0007\u0000\u0000\u0150\u0155\u0003\"\u0011\u0000\u0151\u0152\u0005"+
		"K\u0000\u0000\u0152\u0154\u0003\"\u0011\u0000\u0153\u0151\u0001\u0000"+
		"\u0000\u0000\u0154\u0157\u0001\u0000\u0000\u0000\u0155\u0153\u0001\u0000"+
		"\u0000\u0000\u0155\u0156\u0001\u0000\u0000\u0000\u0156\u0159\u0001\u0000"+
		"\u0000\u0000\u0157\u0155\u0001\u0000\u0000\u0000\u0158\u014e\u0001\u0000"+
		"\u0000\u0000\u0158\u0159\u0001\u0000\u0000\u0000\u0159\u015c\u0001\u0000"+
		"\u0000\u0000\u015a\u015b\u0005\u001c\u0000\u0000\u015b\u015d\u0005a\u0000"+
		"\u0000\u015c\u015a\u0001\u0000\u0000\u0000\u015c\u015d\u0001\u0000\u0000"+
		"\u0000\u015d\u015e\u0001\u0000\u0000\u0000\u015e\u015f\u0005\\\u0000\u0000"+
		"\u015f\u001f\u0001\u0000\u0000\u0000\u0160\u016a\u0005V\u0000\u0000\u0161"+
		"\u0166\u0003B!\u0000\u0162\u0163\u0005K\u0000\u0000\u0163\u0165\u0003"+
		"B!\u0000\u0164\u0162\u0001\u0000\u0000\u0000\u0165\u0168\u0001\u0000\u0000"+
		"\u0000\u0166\u0164\u0001\u0000\u0000\u0000\u0166\u0167\u0001\u0000\u0000"+
		"\u0000\u0167\u016a\u0001\u0000\u0000\u0000\u0168\u0166\u0001\u0000\u0000"+
		"\u0000\u0169\u0160\u0001\u0000\u0000\u0000\u0169\u0161\u0001\u0000\u0000"+
		"\u0000\u016a!\u0001\u0000\u0000\u0000\u016b\u016d\u0005`\u0000\u0000\u016c"+
		"\u016e\u0007\u0003\u0000\u0000\u016d\u016c\u0001\u0000\u0000\u0000\u016d"+
		"\u016e\u0001\u0000\u0000\u0000\u016e#\u0001\u0000\u0000\u0000\u016f\u0175"+
		"\u0005&\u0000\u0000\u0170\u0171\u0005(\u0000\u0000\u0171\u0175\u0005b"+
		"\u0000\u0000\u0172\u0173\u0005\u0011\u0000\u0000\u0173\u0175\u0005b\u0000"+
		"\u0000\u0174\u016f\u0001\u0000\u0000\u0000\u0174\u0170\u0001\u0000\u0000"+
		"\u0000\u0174\u0172\u0001\u0000\u0000\u0000\u0175%\u0001\u0000\u0000\u0000"+
		"\u0176\u017b\u0003(\u0014\u0000\u0177\u0178\u0005K\u0000\u0000\u0178\u017a"+
		"\u0003(\u0014\u0000\u0179\u0177\u0001\u0000\u0000\u0000\u017a\u017d\u0001"+
		"\u0000\u0000\u0000\u017b\u0179\u0001\u0000\u0000\u0000\u017b\u017c\u0001"+
		"\u0000\u0000\u0000\u017c\'\u0001\u0000\u0000\u0000\u017d\u017b\u0001\u0000"+
		"\u0000\u0000\u017e\u0186\u0005b\u0000\u0000\u017f\u0186\u00032\u0019\u0000"+
		"\u0180\u0186\u0005]\u0000\u0000\u0181\u0186\u0005_\u0000\u0000\u0182\u0186"+
		"\u0003*\u0015\u0000\u0183\u0186\u0003,\u0016\u0000\u0184\u0186\u00030"+
		"\u0018\u0000\u0185\u017e\u0001\u0000\u0000\u0000\u0185\u017f\u0001\u0000"+
		"\u0000\u0000\u0185\u0180\u0001\u0000\u0000\u0000\u0185\u0181\u0001\u0000"+
		"\u0000\u0000\u0185\u0182\u0001\u0000\u0000\u0000\u0185\u0183\u0001\u0000"+
		"\u0000\u0000\u0185\u0184\u0001\u0000\u0000\u0000\u0186)\u0001\u0000\u0000"+
		"\u0000\u0187\u0188\u0005J\u0000\u0000\u0188\u0189\u0005`\u0000\u0000\u0189"+
		"+\u0001\u0000\u0000\u0000\u018a\u0193\u0005P\u0000\u0000\u018b\u0190\u0003"+
		".\u0017\u0000\u018c\u018d\u0005K\u0000\u0000\u018d\u018f\u0003.\u0017"+
		"\u0000\u018e\u018c\u0001\u0000\u0000\u0000\u018f\u0192\u0001\u0000\u0000"+
		"\u0000\u0190\u018e\u0001\u0000\u0000\u0000\u0190\u0191\u0001\u0000\u0000"+
		"\u0000\u0191\u0194\u0001\u0000\u0000\u0000\u0192\u0190\u0001\u0000\u0000"+
		"\u0000\u0193\u018b\u0001\u0000\u0000\u0000\u0193\u0194\u0001\u0000\u0000"+
		"\u0000\u0194\u0195\u0001\u0000\u0000\u0000\u0195\u0196\u0005Y\u0000\u0000"+
		"\u0196-\u0001\u0000\u0000\u0000\u0197\u0198\u0007\u0004\u0000\u0000\u0198"+
		"\u0199\u0005J\u0000\u0000\u0199\u019a\u0003(\u0014\u0000\u019a/\u0001"+
		"\u0000\u0000\u0000\u019b\u01a4\u0005Q\u0000\u0000\u019c\u01a1\u0003(\u0014"+
		"\u0000\u019d\u019e\u0005K\u0000\u0000\u019e\u01a0\u0003(\u0014\u0000\u019f"+
		"\u019d\u0001\u0000\u0000\u0000\u01a0\u01a3\u0001\u0000\u0000\u0000\u01a1"+
		"\u019f\u0001\u0000\u0000\u0000\u01a1\u01a2\u0001\u0000\u0000\u0000\u01a2"+
		"\u01a5\u0001\u0000\u0000\u0000\u01a3\u01a1\u0001\u0000\u0000\u0000\u01a4"+
		"\u019c\u0001\u0000\u0000\u0000\u01a4\u01a5\u0001\u0000\u0000\u0000\u01a5"+
		"\u01a6\u0001\u0000\u0000\u0000\u01a6\u01a7\u0005Z\u0000\u0000\u01a71\u0001"+
		"\u0000\u0000\u0000\u01a8\u01aa\u0005U\u0000\u0000\u01a9\u01a8\u0001\u0000"+
		"\u0000\u0000\u01a9\u01aa\u0001\u0000\u0000\u0000\u01aa\u01ab\u0001\u0000"+
		"\u0000\u0000\u01ab\u01ac\u0007\u0005\u0000\u0000\u01ac3\u0001\u0000\u0000"+
		"\u0000\u01ad\u01ae\u0005`\u0000\u0000\u01ae\u01af\u0005I\u0000\u0000\u01af"+
		"\u01b0\u00036\u001b\u0000\u01b05\u0001\u0000\u0000\u0000\u01b1\u01bb\u0003"+
		"(\u0014\u0000\u01b2\u01b3\u0005`\u0000\u0000\u01b3\u01b4\u00038\u001c"+
		"\u0000\u01b4\u01b5\u00036\u001b\u0000\u01b5\u01bb\u0001\u0000\u0000\u0000"+
		"\u01b6\u01b7\u0005T\u0000\u0000\u01b7\u01b8\u00036\u001b\u0000\u01b8\u01b9"+
		"\u0005[\u0000\u0000\u01b9\u01bb\u0001\u0000\u0000\u0000\u01ba\u01b1\u0001"+
		"\u0000\u0000\u0000\u01ba\u01b2\u0001\u0000\u0000\u0000\u01ba\u01b6\u0001"+
		"\u0000\u0000\u0000\u01bb7\u0001\u0000\u0000\u0000\u01bc\u01bd\u0007\u0006"+
		"\u0000\u0000\u01bd9\u0001\u0000\u0000\u0000\u01be\u01bf\u0006\u001d\uffff"+
		"\uffff\u0000\u01bf\u01c5\u0003<\u001e\u0000\u01c0\u01c1\u0005T\u0000\u0000"+
		"\u01c1\u01c2\u0003:\u001d\u0000\u01c2\u01c3\u0005[\u0000\u0000\u01c3\u01c5"+
		"\u0001\u0000\u0000\u0000\u01c4\u01be\u0001\u0000\u0000\u0000\u01c4\u01c0"+
		"\u0001\u0000\u0000\u0000\u01c5\u01ce\u0001\u0000\u0000\u0000\u01c6\u01c7"+
		"\n\u0002\u0000\u0000\u01c7\u01c8\u0005\u0004\u0000\u0000\u01c8\u01cd\u0003"+
		":\u001d\u0003\u01c9\u01ca\n\u0001\u0000\u0000\u01ca\u01cb\u0005\"\u0000"+
		"\u0000\u01cb\u01cd\u0003:\u001d\u0002\u01cc\u01c6\u0001\u0000\u0000\u0000"+
		"\u01cc\u01c9\u0001\u0000\u0000\u0000\u01cd\u01d0\u0001\u0000\u0000\u0000"+
		"\u01ce\u01cc\u0001\u0000\u0000\u0000\u01ce\u01cf\u0001\u0000\u0000\u0000"+
		"\u01cf;\u0001\u0000\u0000\u0000\u01d0\u01ce\u0001\u0000\u0000\u0000\u01d1"+
		"\u01d2\u0005`\u0000\u0000\u01d2\u01d7\u0003>\u001f\u0000\u01d3\u01d8\u0003"+
		"*\u0015\u0000\u01d4\u01d8\u0005b\u0000\u0000\u01d5\u01d8\u00032\u0019"+
		"\u0000\u01d6\u01d8\u0005]\u0000\u0000\u01d7\u01d3\u0001\u0000\u0000\u0000"+
		"\u01d7\u01d4\u0001\u0000\u0000\u0000\u01d7\u01d5\u0001\u0000\u0000\u0000"+
		"\u01d7\u01d6\u0001\u0000\u0000\u0000\u01d8=\u0001\u0000\u0000\u0000\u01d9"+
		"\u01da\u0007\u0007\u0000\u0000\u01da?\u0001\u0000\u0000\u0000\u01db\u01dc"+
		"\u0005`\u0000\u0000\u01dcA\u0001\u0000\u0000\u0000\u01dd\u01de\u0005`"+
		"\u0000\u0000\u01deC\u0001\u0000\u0000\u0000\u01df\u01e0\u0005`\u0000\u0000"+
		"\u01e0\u01e1\u0003H$\u0000\u01e1E\u0001\u0000\u0000\u0000\u01e2\u01e3"+
		"\u0005`\u0000\u0000\u01e3\u01e4\u0005T\u0000\u0000\u01e4\u01e9\u0003D"+
		"\"\u0000\u01e5\u01e6\u0005K\u0000\u0000\u01e6\u01e8\u0003D\"\u0000\u01e7"+
		"\u01e5\u0001\u0000\u0000\u0000\u01e8\u01eb\u0001\u0000\u0000\u0000\u01e9"+
		"\u01e7\u0001\u0000\u0000\u0000\u01e9\u01ea\u0001\u0000\u0000\u0000\u01ea"+
		"\u01ec\u0001\u0000\u0000\u0000\u01eb\u01e9\u0001\u0000\u0000\u0000\u01ec"+
		"\u01ed\u0005[\u0000\u0000\u01edG\u0001\u0000\u0000\u0000\u01ee\u024f\u0005"+
		"A\u0000\u0000\u01ef\u01f2\u0005>\u0000\u0000\u01f0\u01f1\u0005\u001f\u0000"+
		"\u0000\u01f1\u01f3\u0005\u0019\u0000\u0000\u01f2\u01f0\u0001\u0000\u0000"+
		"\u0000\u01f2\u01f3\u0001\u0000\u0000\u0000\u01f3\u024f\u0001\u0000\u0000"+
		"\u0000\u01f4\u01f7\u0005=\u0000\u0000\u01f5\u01f6\u0005\u001f\u0000\u0000"+
		"\u01f6\u01f8\u0005\u0019\u0000\u0000\u01f7\u01f5\u0001\u0000\u0000\u0000"+
		"\u01f7\u01f8\u0001\u0000\u0000\u0000\u01f8\u024f\u0001\u0000\u0000\u0000"+
		"\u01f9\u01fc\u0005\u001d\u0000\u0000\u01fa\u01fb\u0005\u001f\u0000\u0000"+
		"\u01fb\u01fd\u0005\u0019\u0000\u0000\u01fc\u01fa\u0001\u0000\u0000\u0000"+
		"\u01fc\u01fd\u0001\u0000\u0000\u0000\u01fd\u024f\u0001\u0000\u0000\u0000"+
		"\u01fe\u0201\u0005\u0014\u0000\u0000\u01ff\u0200\u0005\u001f\u0000\u0000"+
		"\u0200\u0202\u0005\u0019\u0000\u0000\u0201\u01ff\u0001\u0000\u0000\u0000"+
		"\u0201\u0202\u0001\u0000\u0000\u0000\u0202\u024f\u0001\u0000\u0000\u0000"+
		"\u0203\u0206\u0005\u0012\u0000\u0000\u0204\u0205\u0005\u001f\u0000\u0000"+
		"\u0205\u0207\u0005\u0019\u0000\u0000\u0206\u0204\u0001\u0000\u0000\u0000"+
		"\u0206\u0207\u0001\u0000\u0000\u0000\u0207\u024f\u0001\u0000\u0000\u0000"+
		"\u0208\u020b\u0005<\u0000\u0000\u0209\u020a\u0005\u001f\u0000\u0000\u020a"+
		"\u020c\u0005\u0019\u0000\u0000\u020b\u0209\u0001\u0000\u0000\u0000\u020b"+
		"\u020c\u0001\u0000\u0000\u0000\u020c\u024f\u0001\u0000\u0000\u0000\u020d"+
		"\u0210\u0005\u000e\u0000\u0000\u020e\u020f\u0005\u001f\u0000\u0000\u020f"+
		"\u0211\u0005\u0019\u0000\u0000\u0210\u020e\u0001\u0000\u0000\u0000\u0210"+
		"\u0211\u0001\u0000\u0000\u0000\u0211\u024f\u0001\u0000\u0000\u0000\u0212"+
		"\u0215\u0005B\u0000\u0000\u0213\u0214\u0005\u001f\u0000\u0000\u0214\u0216"+
		"\u0005\u0019\u0000\u0000\u0215\u0213\u0001\u0000\u0000\u0000\u0215\u0216"+
		"\u0001\u0000\u0000\u0000\u0216\u024f\u0001\u0000\u0000\u0000\u0217\u024f"+
		"\u0005C\u0000\u0000\u0218\u024f\u0005D\u0000\u0000\u0219\u024f\u0005E"+
		"\u0000\u0000\u021a\u021d\u0005F\u0000\u0000\u021b\u021c\u0005\u001f\u0000"+
		"\u0000\u021c\u021e\u0005\u0019\u0000\u0000\u021d\u021b\u0001\u0000\u0000"+
		"\u0000\u021d\u021e\u0001\u0000\u0000\u0000\u021e\u024f\u0001\u0000\u0000"+
		"\u0000\u021f\u0222\u0005G\u0000\u0000\u0220\u0221\u0005\u001f\u0000\u0000"+
		"\u0221\u0223\u0005\u0019\u0000\u0000\u0222\u0220\u0001\u0000\u0000\u0000"+
		"\u0222\u0223\u0001\u0000\u0000\u0000\u0223\u024f\u0001\u0000\u0000\u0000"+
		"\u0224\u0225\u0005@\u0000\u0000\u0225\u0226\u0005T\u0000\u0000\u0226\u022b"+
		"\u0003D\"\u0000\u0227\u0228\u0005K\u0000\u0000\u0228\u022a\u0003D\"\u0000"+
		"\u0229\u0227\u0001\u0000\u0000\u0000\u022a\u022d\u0001\u0000\u0000\u0000"+
		"\u022b\u0229\u0001\u0000\u0000\u0000\u022b\u022c\u0001\u0000\u0000\u0000"+
		"\u022c\u022e\u0001\u0000\u0000\u0000\u022d\u022b\u0001\u0000\u0000\u0000"+
		"\u022e\u0231\u0005[\u0000\u0000\u022f\u0230\u0005\u001f\u0000\u0000\u0230"+
		"\u0232\u0005\u0019\u0000\u0000\u0231\u022f\u0001\u0000\u0000\u0000\u0231"+
		"\u0232\u0001\u0000\u0000\u0000\u0232\u024f\u0001\u0000\u0000\u0000\u0233"+
		"\u0234\u0005?\u0000\u0000\u0234\u0235\u0005T\u0000\u0000\u0235\u023a\u0003"+
		"D\"\u0000\u0236\u0237\u0005K\u0000\u0000\u0237\u0239\u0003D\"\u0000\u0238"+
		"\u0236\u0001\u0000\u0000\u0000\u0239\u023c\u0001\u0000\u0000\u0000\u023a"+
		"\u0238\u0001\u0000\u0000\u0000\u023a\u023b\u0001\u0000\u0000\u0000\u023b"+
		"\u023d\u0001\u0000\u0000\u0000\u023c\u023a\u0001\u0000\u0000\u0000\u023d"+
		"\u023e\u0005[\u0000\u0000\u023e\u024f\u0001\u0000\u0000\u0000\u023f\u0240"+
		"\u0005H\u0000\u0000\u0240\u0241\u0005T\u0000\u0000\u0241\u0246\u0003F"+
		"#\u0000\u0242\u0243\u0005K\u0000\u0000\u0243\u0245\u0003F#\u0000\u0244"+
		"\u0242\u0001\u0000\u0000\u0000\u0245\u0248\u0001\u0000\u0000\u0000\u0246"+
		"\u0244\u0001\u0000\u0000\u0000\u0246\u0247\u0001\u0000\u0000\u0000\u0247"+
		"\u0249\u0001\u0000\u0000\u0000\u0248\u0246\u0001\u0000\u0000\u0000\u0249"+
		"\u024c\u0005[\u0000\u0000\u024a\u024b\u0005\u001f\u0000\u0000\u024b\u024d"+
		"\u0005\u0019\u0000\u0000\u024c\u024a\u0001\u0000\u0000\u0000\u024c\u024d"+
		"\u0001\u0000\u0000\u0000\u024d\u024f\u0001\u0000\u0000\u0000\u024e\u01ee"+
		"\u0001\u0000\u0000\u0000\u024e\u01ef\u0001\u0000\u0000\u0000\u024e\u01f4"+
		"\u0001\u0000\u0000\u0000\u024e\u01f9\u0001\u0000\u0000\u0000\u024e\u01fe"+
		"\u0001\u0000\u0000\u0000\u024e\u0203\u0001\u0000\u0000\u0000\u024e\u0208"+
		"\u0001\u0000\u0000\u0000\u024e\u020d\u0001\u0000\u0000\u0000\u024e\u0212"+
		"\u0001\u0000\u0000\u0000\u024e\u0217\u0001\u0000\u0000\u0000\u024e\u0218"+
		"\u0001\u0000\u0000\u0000\u024e\u0219\u0001\u0000\u0000\u0000\u024e\u021a"+
		"\u0001\u0000\u0000\u0000\u024e\u021f\u0001\u0000\u0000\u0000\u024e\u0224"+
		"\u0001\u0000\u0000\u0000\u024e\u0233\u0001\u0000\u0000\u0000\u024e\u023f"+
		"\u0001\u0000\u0000\u0000\u024fI\u0001\u0000\u0000\u0000\u0250\u0251\u0005"+
		"c\u0000\u0000\u0251K\u0001\u0000\u0000\u0000;O_fo\u007f\u008a\u008f\u0099"+
		"\u00a5\u00ba\u00bf\u00ca\u00da\u00e5\u0105\u010f\u0116\u0121\u012e\u0133"+
		"\u013f\u0142\u014c\u0155\u0158\u015c\u0166\u0169\u016d\u0174\u017b\u0185"+
		"\u0190\u0193\u01a1\u01a4\u01a9\u01ba\u01c4\u01cc\u01ce\u01d7\u01e9\u01f2"+
		"\u01f7\u01fc\u0201\u0206\u020b\u0210\u0215\u021d\u0222\u022b\u0231\u023a"+
		"\u0246\u024c\u024e";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}