// Generated from AbacPolicy.g4 by ANTLR 4.13.2
package org.kinotic.auth.parser;
import org.antlr.v4.runtime.atn.*;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.misc.*;
import org.antlr.v4.runtime.tree.*;
import java.util.List;
import java.util.Iterator;
import java.util.ArrayList;

@SuppressWarnings({"all", "warnings", "unchecked", "unused", "cast", "CheckReturnValue", "this-escape"})
public class AbacPolicyParser extends Parser {
	static { RuntimeMetaData.checkVersion("4.13.2", RuntimeMetaData.VERSION); }

	protected static final DFA[] _decisionToDFA;
	protected static final PredictionContextCache _sharedContextCache =
		new PredictionContextCache();
	public static final int
		AND=1, OR=2, NOT=3, IN=4, CONTAINS=5, EXISTS=6, LIKE=7, EQ=8, NEQ=9, GT=10, 
		GTE=11, LT=12, LTE=13, DOT=14, COMMA=15, LPAREN=16, RPAREN=17, LBRACKET=18, 
		RBRACKET=19, BOOLEAN=20, DECIMAL=21, INTEGER=22, STRING=23, IDENTIFIER=24, 
		WS=25;
	public static final int
		RULE_policy = 0, RULE_expression = 1, RULE_comparison = 2, RULE_comparisonOp = 3, 
		RULE_path = 4, RULE_literal = 5, RULE_array = 6;
	private static String[] makeRuleNames() {
		return new String[] {
			"policy", "expression", "comparison", "comparisonOp", "path", "literal", 
			"array"
		};
	}
	public static final String[] ruleNames = makeRuleNames();

	private static String[] makeLiteralNames() {
		return new String[] {
			null, null, null, null, null, null, null, null, "'=='", "'!='", "'>'", 
			"'>='", "'<'", "'<='", "'.'", "','", "'('", "')'", "'['", "']'"
		};
	}
	private static final String[] _LITERAL_NAMES = makeLiteralNames();
	private static String[] makeSymbolicNames() {
		return new String[] {
			null, "AND", "OR", "NOT", "IN", "CONTAINS", "EXISTS", "LIKE", "EQ", "NEQ", 
			"GT", "GTE", "LT", "LTE", "DOT", "COMMA", "LPAREN", "RPAREN", "LBRACKET", 
			"RBRACKET", "BOOLEAN", "DECIMAL", "INTEGER", "STRING", "IDENTIFIER", 
			"WS"
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
	public String getGrammarFileName() { return "AbacPolicy.g4"; }

	@Override
	public String[] getRuleNames() { return ruleNames; }

	@Override
	public String getSerializedATN() { return _serializedATN; }

	@Override
	public ATN getATN() { return _ATN; }

	public AbacPolicyParser(TokenStream input) {
		super(input);
		_interp = new ParserATNSimulator(this,_ATN,_decisionToDFA,_sharedContextCache);
	}

	@SuppressWarnings("CheckReturnValue")
	public static class PolicyContext extends ParserRuleContext {
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public TerminalNode EOF() { return getToken(AbacPolicyParser.EOF, 0); }
		public PolicyContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_policy; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof AbacPolicyListener ) ((AbacPolicyListener)listener).enterPolicy(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof AbacPolicyListener ) ((AbacPolicyListener)listener).exitPolicy(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof AbacPolicyVisitor ) return ((AbacPolicyVisitor<? extends T>)visitor).visitPolicy(this);
			else return visitor.visitChildren(this);
		}
	}

	public final PolicyContext policy() throws RecognitionException {
		PolicyContext _localctx = new PolicyContext(_ctx, getState());
		enterRule(_localctx, 0, RULE_policy);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(14);
			expression(0);
			setState(15);
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
	public static class ExpressionContext extends ParserRuleContext {
		public ExpressionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_expression; }
	 
		public ExpressionContext() { }
		public void copyFrom(ExpressionContext ctx) {
			super.copyFrom(ctx);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class NotExprContext extends ExpressionContext {
		public TerminalNode NOT() { return getToken(AbacPolicyParser.NOT, 0); }
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public NotExprContext(ExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof AbacPolicyListener ) ((AbacPolicyListener)listener).enterNotExpr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof AbacPolicyListener ) ((AbacPolicyListener)listener).exitNotExpr(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof AbacPolicyVisitor ) return ((AbacPolicyVisitor<? extends T>)visitor).visitNotExpr(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class OrExprContext extends ExpressionContext {
		public List<ExpressionContext> expression() {
			return getRuleContexts(ExpressionContext.class);
		}
		public ExpressionContext expression(int i) {
			return getRuleContext(ExpressionContext.class,i);
		}
		public TerminalNode OR() { return getToken(AbacPolicyParser.OR, 0); }
		public OrExprContext(ExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof AbacPolicyListener ) ((AbacPolicyListener)listener).enterOrExpr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof AbacPolicyListener ) ((AbacPolicyListener)listener).exitOrExpr(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof AbacPolicyVisitor ) return ((AbacPolicyVisitor<? extends T>)visitor).visitOrExpr(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class ComparisonExprContext extends ExpressionContext {
		public ComparisonContext comparison() {
			return getRuleContext(ComparisonContext.class,0);
		}
		public ComparisonExprContext(ExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof AbacPolicyListener ) ((AbacPolicyListener)listener).enterComparisonExpr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof AbacPolicyListener ) ((AbacPolicyListener)listener).exitComparisonExpr(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof AbacPolicyVisitor ) return ((AbacPolicyVisitor<? extends T>)visitor).visitComparisonExpr(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class ParenExprContext extends ExpressionContext {
		public TerminalNode LPAREN() { return getToken(AbacPolicyParser.LPAREN, 0); }
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public TerminalNode RPAREN() { return getToken(AbacPolicyParser.RPAREN, 0); }
		public ParenExprContext(ExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof AbacPolicyListener ) ((AbacPolicyListener)listener).enterParenExpr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof AbacPolicyListener ) ((AbacPolicyListener)listener).exitParenExpr(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof AbacPolicyVisitor ) return ((AbacPolicyVisitor<? extends T>)visitor).visitParenExpr(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class AndExprContext extends ExpressionContext {
		public List<ExpressionContext> expression() {
			return getRuleContexts(ExpressionContext.class);
		}
		public ExpressionContext expression(int i) {
			return getRuleContext(ExpressionContext.class,i);
		}
		public TerminalNode AND() { return getToken(AbacPolicyParser.AND, 0); }
		public AndExprContext(ExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof AbacPolicyListener ) ((AbacPolicyListener)listener).enterAndExpr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof AbacPolicyListener ) ((AbacPolicyListener)listener).exitAndExpr(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof AbacPolicyVisitor ) return ((AbacPolicyVisitor<? extends T>)visitor).visitAndExpr(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ExpressionContext expression() throws RecognitionException {
		return expression(0);
	}

	private ExpressionContext expression(int _p) throws RecognitionException {
		ParserRuleContext _parentctx = _ctx;
		int _parentState = getState();
		ExpressionContext _localctx = new ExpressionContext(_ctx, _parentState);
		ExpressionContext _prevctx = _localctx;
		int _startState = 2;
		enterRecursionRule(_localctx, 2, RULE_expression, _p);
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(25);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case LPAREN:
				{
				_localctx = new ParenExprContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;

				setState(18);
				match(LPAREN);
				setState(19);
				expression(0);
				setState(20);
				match(RPAREN);
				}
				break;
			case NOT:
				{
				_localctx = new NotExprContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(22);
				match(NOT);
				setState(23);
				expression(4);
				}
				break;
			case IDENTIFIER:
				{
				_localctx = new ComparisonExprContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(24);
				comparison();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
			_ctx.stop = _input.LT(-1);
			setState(35);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,2,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					if ( _parseListeners!=null ) triggerExitRuleEvent();
					_prevctx = _localctx;
					{
					setState(33);
					_errHandler.sync(this);
					switch ( getInterpreter().adaptivePredict(_input,1,_ctx) ) {
					case 1:
						{
						_localctx = new AndExprContext(new ExpressionContext(_parentctx, _parentState));
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(27);
						if (!(precpred(_ctx, 3))) throw new FailedPredicateException(this, "precpred(_ctx, 3)");
						setState(28);
						match(AND);
						setState(29);
						expression(4);
						}
						break;
					case 2:
						{
						_localctx = new OrExprContext(new ExpressionContext(_parentctx, _parentState));
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(30);
						if (!(precpred(_ctx, 2))) throw new FailedPredicateException(this, "precpred(_ctx, 2)");
						setState(31);
						match(OR);
						setState(32);
						expression(3);
						}
						break;
					}
					} 
				}
				setState(37);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,2,_ctx);
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
	public static class ComparisonContext extends ParserRuleContext {
		public ComparisonContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_comparison; }
	 
		public ComparisonContext() { }
		public void copyFrom(ComparisonContext ctx) {
			super.copyFrom(ctx);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class InComparisonContext extends ComparisonContext {
		public PathContext left;
		public TerminalNode IN() { return getToken(AbacPolicyParser.IN, 0); }
		public ArrayContext array() {
			return getRuleContext(ArrayContext.class,0);
		}
		public PathContext path() {
			return getRuleContext(PathContext.class,0);
		}
		public InComparisonContext(ComparisonContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof AbacPolicyListener ) ((AbacPolicyListener)listener).enterInComparison(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof AbacPolicyListener ) ((AbacPolicyListener)listener).exitInComparison(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof AbacPolicyVisitor ) return ((AbacPolicyVisitor<? extends T>)visitor).visitInComparison(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class LiteralComparisonContext extends ComparisonContext {
		public PathContext left;
		public ComparisonOpContext op;
		public LiteralContext right;
		public PathContext path() {
			return getRuleContext(PathContext.class,0);
		}
		public ComparisonOpContext comparisonOp() {
			return getRuleContext(ComparisonOpContext.class,0);
		}
		public LiteralContext literal() {
			return getRuleContext(LiteralContext.class,0);
		}
		public LiteralComparisonContext(ComparisonContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof AbacPolicyListener ) ((AbacPolicyListener)listener).enterLiteralComparison(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof AbacPolicyListener ) ((AbacPolicyListener)listener).exitLiteralComparison(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof AbacPolicyVisitor ) return ((AbacPolicyVisitor<? extends T>)visitor).visitLiteralComparison(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class ExistsComparisonContext extends ComparisonContext {
		public PathContext left;
		public TerminalNode EXISTS() { return getToken(AbacPolicyParser.EXISTS, 0); }
		public PathContext path() {
			return getRuleContext(PathContext.class,0);
		}
		public ExistsComparisonContext(ComparisonContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof AbacPolicyListener ) ((AbacPolicyListener)listener).enterExistsComparison(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof AbacPolicyListener ) ((AbacPolicyListener)listener).exitExistsComparison(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof AbacPolicyVisitor ) return ((AbacPolicyVisitor<? extends T>)visitor).visitExistsComparison(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class LikeComparisonContext extends ComparisonContext {
		public PathContext left;
		public Token right;
		public TerminalNode LIKE() { return getToken(AbacPolicyParser.LIKE, 0); }
		public PathContext path() {
			return getRuleContext(PathContext.class,0);
		}
		public TerminalNode STRING() { return getToken(AbacPolicyParser.STRING, 0); }
		public LikeComparisonContext(ComparisonContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof AbacPolicyListener ) ((AbacPolicyListener)listener).enterLikeComparison(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof AbacPolicyListener ) ((AbacPolicyListener)listener).exitLikeComparison(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof AbacPolicyVisitor ) return ((AbacPolicyVisitor<? extends T>)visitor).visitLikeComparison(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class ContainsComparisonContext extends ComparisonContext {
		public PathContext left;
		public LiteralContext right;
		public TerminalNode CONTAINS() { return getToken(AbacPolicyParser.CONTAINS, 0); }
		public PathContext path() {
			return getRuleContext(PathContext.class,0);
		}
		public LiteralContext literal() {
			return getRuleContext(LiteralContext.class,0);
		}
		public ContainsComparisonContext(ComparisonContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof AbacPolicyListener ) ((AbacPolicyListener)listener).enterContainsComparison(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof AbacPolicyListener ) ((AbacPolicyListener)listener).exitContainsComparison(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof AbacPolicyVisitor ) return ((AbacPolicyVisitor<? extends T>)visitor).visitContainsComparison(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class PathComparisonContext extends ComparisonContext {
		public PathContext left;
		public ComparisonOpContext op;
		public PathContext right;
		public List<PathContext> path() {
			return getRuleContexts(PathContext.class);
		}
		public PathContext path(int i) {
			return getRuleContext(PathContext.class,i);
		}
		public ComparisonOpContext comparisonOp() {
			return getRuleContext(ComparisonOpContext.class,0);
		}
		public PathComparisonContext(ComparisonContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof AbacPolicyListener ) ((AbacPolicyListener)listener).enterPathComparison(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof AbacPolicyListener ) ((AbacPolicyListener)listener).exitPathComparison(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof AbacPolicyVisitor ) return ((AbacPolicyVisitor<? extends T>)visitor).visitPathComparison(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ComparisonContext comparison() throws RecognitionException {
		ComparisonContext _localctx = new ComparisonContext(_ctx, getState());
		enterRule(_localctx, 4, RULE_comparison);
		try {
			setState(61);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,3,_ctx) ) {
			case 1:
				_localctx = new PathComparisonContext(_localctx);
				enterOuterAlt(_localctx, 1);
				{
				setState(38);
				((PathComparisonContext)_localctx).left = path();
				setState(39);
				((PathComparisonContext)_localctx).op = comparisonOp();
				setState(40);
				((PathComparisonContext)_localctx).right = path();
				}
				break;
			case 2:
				_localctx = new LiteralComparisonContext(_localctx);
				enterOuterAlt(_localctx, 2);
				{
				setState(42);
				((LiteralComparisonContext)_localctx).left = path();
				setState(43);
				((LiteralComparisonContext)_localctx).op = comparisonOp();
				setState(44);
				((LiteralComparisonContext)_localctx).right = literal();
				}
				break;
			case 3:
				_localctx = new InComparisonContext(_localctx);
				enterOuterAlt(_localctx, 3);
				{
				setState(46);
				((InComparisonContext)_localctx).left = path();
				setState(47);
				match(IN);
				setState(48);
				array();
				}
				break;
			case 4:
				_localctx = new ContainsComparisonContext(_localctx);
				enterOuterAlt(_localctx, 4);
				{
				setState(50);
				((ContainsComparisonContext)_localctx).left = path();
				setState(51);
				match(CONTAINS);
				setState(52);
				((ContainsComparisonContext)_localctx).right = literal();
				}
				break;
			case 5:
				_localctx = new ExistsComparisonContext(_localctx);
				enterOuterAlt(_localctx, 5);
				{
				setState(54);
				((ExistsComparisonContext)_localctx).left = path();
				setState(55);
				match(EXISTS);
				}
				break;
			case 6:
				_localctx = new LikeComparisonContext(_localctx);
				enterOuterAlt(_localctx, 6);
				{
				setState(57);
				((LikeComparisonContext)_localctx).left = path();
				setState(58);
				match(LIKE);
				setState(59);
				((LikeComparisonContext)_localctx).right = match(STRING);
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
	public static class ComparisonOpContext extends ParserRuleContext {
		public TerminalNode EQ() { return getToken(AbacPolicyParser.EQ, 0); }
		public TerminalNode NEQ() { return getToken(AbacPolicyParser.NEQ, 0); }
		public TerminalNode GT() { return getToken(AbacPolicyParser.GT, 0); }
		public TerminalNode LT() { return getToken(AbacPolicyParser.LT, 0); }
		public TerminalNode GTE() { return getToken(AbacPolicyParser.GTE, 0); }
		public TerminalNode LTE() { return getToken(AbacPolicyParser.LTE, 0); }
		public ComparisonOpContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_comparisonOp; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof AbacPolicyListener ) ((AbacPolicyListener)listener).enterComparisonOp(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof AbacPolicyListener ) ((AbacPolicyListener)listener).exitComparisonOp(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof AbacPolicyVisitor ) return ((AbacPolicyVisitor<? extends T>)visitor).visitComparisonOp(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ComparisonOpContext comparisonOp() throws RecognitionException {
		ComparisonOpContext _localctx = new ComparisonOpContext(_ctx, getState());
		enterRule(_localctx, 6, RULE_comparisonOp);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(63);
			_la = _input.LA(1);
			if ( !((((_la) & ~0x3f) == 0 && ((1L << _la) & 16128L) != 0)) ) {
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
	public static class PathContext extends ParserRuleContext {
		public List<TerminalNode> IDENTIFIER() { return getTokens(AbacPolicyParser.IDENTIFIER); }
		public TerminalNode IDENTIFIER(int i) {
			return getToken(AbacPolicyParser.IDENTIFIER, i);
		}
		public List<TerminalNode> DOT() { return getTokens(AbacPolicyParser.DOT); }
		public TerminalNode DOT(int i) {
			return getToken(AbacPolicyParser.DOT, i);
		}
		public PathContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_path; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof AbacPolicyListener ) ((AbacPolicyListener)listener).enterPath(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof AbacPolicyListener ) ((AbacPolicyListener)listener).exitPath(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof AbacPolicyVisitor ) return ((AbacPolicyVisitor<? extends T>)visitor).visitPath(this);
			else return visitor.visitChildren(this);
		}
	}

	public final PathContext path() throws RecognitionException {
		PathContext _localctx = new PathContext(_ctx, getState());
		enterRule(_localctx, 8, RULE_path);
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(65);
			match(IDENTIFIER);
			setState(70);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,4,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					{
					{
					setState(66);
					match(DOT);
					setState(67);
					match(IDENTIFIER);
					}
					} 
				}
				setState(72);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,4,_ctx);
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
	public static class LiteralContext extends ParserRuleContext {
		public TerminalNode STRING() { return getToken(AbacPolicyParser.STRING, 0); }
		public TerminalNode INTEGER() { return getToken(AbacPolicyParser.INTEGER, 0); }
		public TerminalNode DECIMAL() { return getToken(AbacPolicyParser.DECIMAL, 0); }
		public TerminalNode BOOLEAN() { return getToken(AbacPolicyParser.BOOLEAN, 0); }
		public LiteralContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_literal; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof AbacPolicyListener ) ((AbacPolicyListener)listener).enterLiteral(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof AbacPolicyListener ) ((AbacPolicyListener)listener).exitLiteral(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof AbacPolicyVisitor ) return ((AbacPolicyVisitor<? extends T>)visitor).visitLiteral(this);
			else return visitor.visitChildren(this);
		}
	}

	public final LiteralContext literal() throws RecognitionException {
		LiteralContext _localctx = new LiteralContext(_ctx, getState());
		enterRule(_localctx, 10, RULE_literal);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(73);
			_la = _input.LA(1);
			if ( !((((_la) & ~0x3f) == 0 && ((1L << _la) & 15728640L) != 0)) ) {
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
	public static class ArrayContext extends ParserRuleContext {
		public TerminalNode LBRACKET() { return getToken(AbacPolicyParser.LBRACKET, 0); }
		public List<LiteralContext> literal() {
			return getRuleContexts(LiteralContext.class);
		}
		public LiteralContext literal(int i) {
			return getRuleContext(LiteralContext.class,i);
		}
		public TerminalNode RBRACKET() { return getToken(AbacPolicyParser.RBRACKET, 0); }
		public List<TerminalNode> COMMA() { return getTokens(AbacPolicyParser.COMMA); }
		public TerminalNode COMMA(int i) {
			return getToken(AbacPolicyParser.COMMA, i);
		}
		public ArrayContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_array; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof AbacPolicyListener ) ((AbacPolicyListener)listener).enterArray(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof AbacPolicyListener ) ((AbacPolicyListener)listener).exitArray(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof AbacPolicyVisitor ) return ((AbacPolicyVisitor<? extends T>)visitor).visitArray(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ArrayContext array() throws RecognitionException {
		ArrayContext _localctx = new ArrayContext(_ctx, getState());
		enterRule(_localctx, 12, RULE_array);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(75);
			match(LBRACKET);
			setState(76);
			literal();
			setState(81);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==COMMA) {
				{
				{
				setState(77);
				match(COMMA);
				setState(78);
				literal();
				}
				}
				setState(83);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(84);
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

	public boolean sempred(RuleContext _localctx, int ruleIndex, int predIndex) {
		switch (ruleIndex) {
		case 1:
			return expression_sempred((ExpressionContext)_localctx, predIndex);
		}
		return true;
	}
	private boolean expression_sempred(ExpressionContext _localctx, int predIndex) {
		switch (predIndex) {
		case 0:
			return precpred(_ctx, 3);
		case 1:
			return precpred(_ctx, 2);
		}
		return true;
	}

	public static final String _serializedATN =
		"\u0004\u0001\u0019W\u0002\u0000\u0007\u0000\u0002\u0001\u0007\u0001\u0002"+
		"\u0002\u0007\u0002\u0002\u0003\u0007\u0003\u0002\u0004\u0007\u0004\u0002"+
		"\u0005\u0007\u0005\u0002\u0006\u0007\u0006\u0001\u0000\u0001\u0000\u0001"+
		"\u0000\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001"+
		"\u0001\u0001\u0001\u0001\u0001\u0003\u0001\u001a\b\u0001\u0001\u0001\u0001"+
		"\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0005\u0001\"\b"+
		"\u0001\n\u0001\f\u0001%\t\u0001\u0001\u0002\u0001\u0002\u0001\u0002\u0001"+
		"\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001"+
		"\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001"+
		"\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001"+
		"\u0002\u0001\u0002\u0003\u0002>\b\u0002\u0001\u0003\u0001\u0003\u0001"+
		"\u0004\u0001\u0004\u0001\u0004\u0005\u0004E\b\u0004\n\u0004\f\u0004H\t"+
		"\u0004\u0001\u0005\u0001\u0005\u0001\u0006\u0001\u0006\u0001\u0006\u0001"+
		"\u0006\u0005\u0006P\b\u0006\n\u0006\f\u0006S\t\u0006\u0001\u0006\u0001"+
		"\u0006\u0001\u0006\u0000\u0001\u0002\u0007\u0000\u0002\u0004\u0006\b\n"+
		"\f\u0000\u0002\u0001\u0000\b\r\u0001\u0000\u0014\u0017Z\u0000\u000e\u0001"+
		"\u0000\u0000\u0000\u0002\u0019\u0001\u0000\u0000\u0000\u0004=\u0001\u0000"+
		"\u0000\u0000\u0006?\u0001\u0000\u0000\u0000\bA\u0001\u0000\u0000\u0000"+
		"\nI\u0001\u0000\u0000\u0000\fK\u0001\u0000\u0000\u0000\u000e\u000f\u0003"+
		"\u0002\u0001\u0000\u000f\u0010\u0005\u0000\u0000\u0001\u0010\u0001\u0001"+
		"\u0000\u0000\u0000\u0011\u0012\u0006\u0001\uffff\uffff\u0000\u0012\u0013"+
		"\u0005\u0010\u0000\u0000\u0013\u0014\u0003\u0002\u0001\u0000\u0014\u0015"+
		"\u0005\u0011\u0000\u0000\u0015\u001a\u0001\u0000\u0000\u0000\u0016\u0017"+
		"\u0005\u0003\u0000\u0000\u0017\u001a\u0003\u0002\u0001\u0004\u0018\u001a"+
		"\u0003\u0004\u0002\u0000\u0019\u0011\u0001\u0000\u0000\u0000\u0019\u0016"+
		"\u0001\u0000\u0000\u0000\u0019\u0018\u0001\u0000\u0000\u0000\u001a#\u0001"+
		"\u0000\u0000\u0000\u001b\u001c\n\u0003\u0000\u0000\u001c\u001d\u0005\u0001"+
		"\u0000\u0000\u001d\"\u0003\u0002\u0001\u0004\u001e\u001f\n\u0002\u0000"+
		"\u0000\u001f \u0005\u0002\u0000\u0000 \"\u0003\u0002\u0001\u0003!\u001b"+
		"\u0001\u0000\u0000\u0000!\u001e\u0001\u0000\u0000\u0000\"%\u0001\u0000"+
		"\u0000\u0000#!\u0001\u0000\u0000\u0000#$\u0001\u0000\u0000\u0000$\u0003"+
		"\u0001\u0000\u0000\u0000%#\u0001\u0000\u0000\u0000&\'\u0003\b\u0004\u0000"+
		"\'(\u0003\u0006\u0003\u0000()\u0003\b\u0004\u0000)>\u0001\u0000\u0000"+
		"\u0000*+\u0003\b\u0004\u0000+,\u0003\u0006\u0003\u0000,-\u0003\n\u0005"+
		"\u0000->\u0001\u0000\u0000\u0000./\u0003\b\u0004\u0000/0\u0005\u0004\u0000"+
		"\u000001\u0003\f\u0006\u00001>\u0001\u0000\u0000\u000023\u0003\b\u0004"+
		"\u000034\u0005\u0005\u0000\u000045\u0003\n\u0005\u00005>\u0001\u0000\u0000"+
		"\u000067\u0003\b\u0004\u000078\u0005\u0006\u0000\u00008>\u0001\u0000\u0000"+
		"\u00009:\u0003\b\u0004\u0000:;\u0005\u0007\u0000\u0000;<\u0005\u0017\u0000"+
		"\u0000<>\u0001\u0000\u0000\u0000=&\u0001\u0000\u0000\u0000=*\u0001\u0000"+
		"\u0000\u0000=.\u0001\u0000\u0000\u0000=2\u0001\u0000\u0000\u0000=6\u0001"+
		"\u0000\u0000\u0000=9\u0001\u0000\u0000\u0000>\u0005\u0001\u0000\u0000"+
		"\u0000?@\u0007\u0000\u0000\u0000@\u0007\u0001\u0000\u0000\u0000AF\u0005"+
		"\u0018\u0000\u0000BC\u0005\u000e\u0000\u0000CE\u0005\u0018\u0000\u0000"+
		"DB\u0001\u0000\u0000\u0000EH\u0001\u0000\u0000\u0000FD\u0001\u0000\u0000"+
		"\u0000FG\u0001\u0000\u0000\u0000G\t\u0001\u0000\u0000\u0000HF\u0001\u0000"+
		"\u0000\u0000IJ\u0007\u0001\u0000\u0000J\u000b\u0001\u0000\u0000\u0000"+
		"KL\u0005\u0012\u0000\u0000LQ\u0003\n\u0005\u0000MN\u0005\u000f\u0000\u0000"+
		"NP\u0003\n\u0005\u0000OM\u0001\u0000\u0000\u0000PS\u0001\u0000\u0000\u0000"+
		"QO\u0001\u0000\u0000\u0000QR\u0001\u0000\u0000\u0000RT\u0001\u0000\u0000"+
		"\u0000SQ\u0001\u0000\u0000\u0000TU\u0005\u0013\u0000\u0000U\r\u0001\u0000"+
		"\u0000\u0000\u0006\u0019!#=FQ";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}