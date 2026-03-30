grammar AbacPolicy;

// Entry point
policy
    : expression EOF
    ;

// Boolean expressions with standard precedence: NOT > AND > OR
expression
    : LPAREN expression RPAREN                          # parenExpr
    | NOT expression                                    # notExpr
    | expression AND expression                         # andExpr
    | expression OR expression                          # orExpr
    | comparison                                        # comparisonExpr
    ;

// Comparisons between paths, literals, and sets
comparison
    : left=path op=comparisonOp right=path              # pathComparison
    | left=path op=comparisonOp right=literal           # literalComparison
    | left=path IN array                                # inComparison
    | left=path CONTAINS right=literal                  # containsComparison
    | left=path EXISTS                                  # existsComparison
    | left=path LIKE right=STRING                       # likeComparison
    ;

comparisonOp
    : EQ
    | NEQ
    | GT
    | LT
    | GTE
    | LTE
    ;

// Dotted attribute paths: principal.role, order.amount, context.time
path
    : IDENTIFIER (DOT IDENTIFIER)*
    ;

// Literal values
literal
    : STRING
    | INTEGER
    | DECIMAL
    | BOOLEAN
    ;

// Array of literals for 'in' expressions
array
    : LBRACKET literal (COMMA literal)* RBRACKET
    ;

// Keywords (case-insensitive via fragment letters)
AND         : A N D ;
OR          : O R ;
NOT         : N O T ;
IN          : I N ;
CONTAINS    : C O N T A I N S ;
EXISTS      : E X I S T S ;
LIKE        : L I K E ;

// Operators
EQ          : '==' ;
NEQ         : '!=' ;
GT          : '>' ;
GTE         : '>=' ;
LT          : '<' ;
LTE         : '<=' ;

// Punctuation
DOT         : '.' ;
COMMA       : ',' ;
LPAREN      : '(' ;
RPAREN      : ')' ;
LBRACKET    : '[' ;
RBRACKET    : ']' ;

// Literals
BOOLEAN     : T R U E | F A L S E ;
DECIMAL     : [0-9]+ '.' [0-9]+ ;
INTEGER     : [0-9]+ ;
STRING      : '\'' ( ~['] | '\\\'' )* '\'' ;

// Identifiers
IDENTIFIER  : [a-zA-Z_][a-zA-Z_0-9]* ;

// Skip whitespace
WS          : [ \t\r\n]+ -> skip ;

// Case-insensitive character fragments
fragment A : [aA] ;
fragment B : [bB] ;
fragment C : [cC] ;
fragment D : [dD] ;
fragment E : [eE] ;
fragment F : [fF] ;
fragment G : [gG] ;
fragment H : [hH] ;
fragment I : [iI] ;
fragment K : [kK] ;
fragment L : [lL] ;
fragment M : [mM] ;
fragment N : [nN] ;
fragment O : [oO] ;
fragment P : [pP] ;
fragment R : [rR] ;
fragment S : [sS] ;
fragment T : [tT] ;
fragment U : [uU] ;
fragment X : [xX] ;
