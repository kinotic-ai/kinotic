grammar KinoticSQL;

// Keywords match in any case; a non-reserved keyword is also a name, see identifier
options { caseInsensitive = true; }

migrations
    : statement* EOF
    ;

statement
    : createTableStatement
    | createDataStreamStatement
    | createComponentTemplateStatement
    | createIndexTemplateStatement
    | alterTableStatement
    | reindexStatement
    | updateStatement
    | deleteStatement
    | insertStatement
    | selectStatement
    | comment
    ;

createTableStatement
    : CREATE TABLE (IF NOT EXISTS)? identifier LPAREN columnDefinition (COMMA columnDefinition)* RPAREN SEMICOLON
    ;

createDataStreamStatement
    : CREATE DATA STREAM identifier LPAREN columnDefinition (COMMA columnDefinition)* RPAREN (WITH LPAREN dataStreamOption (COMMA dataStreamOption)* RPAREN)? SEMICOLON
    ;

dataStreamOption
    : DATA_RETENTION ASSIGN STRING
    | TIME_REFERENCE ASSIGN STRING
    ;

createComponentTemplateStatement
    : CREATE COMPONENT TEMPLATE identifier LPAREN templatePart (COMMA templatePart)* RPAREN SEMICOLON
    ;

createIndexTemplateStatement
    : CREATE INDEX TEMPLATE identifier FOR STRING USING STRING (WITH LPAREN templatePart (COMMA templatePart)* RPAREN)? SEMICOLON
    ;

templatePart
    : NUMBER_OF_SHARDS ASSIGN INTEGER_LITERAL
    | NUMBER_OF_REPLICAS ASSIGN INTEGER_LITERAL
    | columnDefinition
    ;

alterTableStatement
    : ALTER TABLE identifier ADD COLUMN identifier type SEMICOLON
    ;

reindexStatement
    : REINDEX identifier INTO identifier reindexOptions? SEMICOLON
    ;

reindexOptions
    : WITH LPAREN reindexOption (COMMA reindexOption)* RPAREN
    ;

reindexOption
    : CONFLICTS ASSIGN (ABORT | PROCEED)
    | MAX_DOCS ASSIGN INTEGER_LITERAL
    | SLICES ASSIGN (AUTO | INTEGER_LITERAL)
    | SIZE ASSIGN INTEGER_LITERAL
    | SOURCE_FIELDS ASSIGN STRING
    | QUERY ASSIGN STRING
    | SCRIPT ASSIGN STRING
    | WAIT ASSIGN BOOLEAN_LITERAL
    | SKIP_IF_NO_SOURCE ASSIGN BOOLEAN_LITERAL
    ;

updateStatement
    : UPDATE identifier SET assignment (COMMA assignment)* WHERE whereClause (WITH REFRESH)? SEMICOLON
    ;

deleteStatement
    : DELETE FROM identifier WHERE whereClause (WITH REFRESH)? SEMICOLON
    ;

insertStatement
    : INSERT INTO tableName (LPAREN columnName (COMMA columnName)* RPAREN)? VALUES LPAREN valueList RPAREN (WITH insertOption (COMMA insertOption)*)? SEMICOLON
    ;

selectStatement
    : SELECT selectList FROM identifier (WHERE whereClause)? (GROUP BY selectExpression (COMMA selectExpression)*)?
      (ORDER BY orderBy (COMMA orderBy)*)? (LIMIT INTEGER_LITERAL)? SEMICOLON
    ;

selectList
    : MULTIPLY
    | selectItem (COMMA selectItem)*
    ;

selectItem
    : selectExpression (AS identifier)?
    ;

// A field or a function call; a statement whose SELECT list or GROUP BY calls a function is an aggregate
selectExpression
    : functionCall
    | fieldPath
    ;

functionCall
    : identifier LPAREN (MULTIPLY | functionArgument (COMMA functionArgument)*)? RPAREN
    ;

functionArgument
    : selectExpression
    | STRING
    | numberLiteral
    | namedParameter
    ;

orderBy
    : fieldPath (ASC | DESC)?
    ;

// A field, or a sub-field of an object field joined by dots
fieldPath
    : identifier (DOT identifier)*
    ;

insertOption
    : REFRESH
    | ROUTING STRING
    | DOCUMENT_ID STRING
    ;

valueList
    : value (COMMA value)*
    ;

value
    : STRING
    | numberLiteral
    | BOOLEAN_LITERAL
    | NULL_LITERAL
    | namedParameter
    | objectLiteral
    | arrayLiteral
    ;

// Two tokens rather than one ':name' lexer rule, which would swallow the colon of an object field
// whose value starts a word: '{ street:null }' lexes as ID COLON NULL_LITERAL either way
namedParameter
    : COLON identifier
    ;

// JSON style literals that populate OBJECT/NESTED/UNION columns: an object literal is one
// sub-document, an array literal a list of them. Both nest to any depth.
objectLiteral
    : LBRACE (objectField (COMMA objectField)*)? RBRACE
    ;

objectField
    : (identifier | STRING) COLON value
    ;

arrayLiteral
    : LBRACKET (value (COMMA value)*)? RBRACKET
    ;

numberLiteral
    : MINUS? (INTEGER_LITERAL | DECIMAL_LITERAL)
    ;

assignment
    : identifier ASSIGN expression
    ;

expression
    : value
    | identifier operator expression  // e.g., age + 1
    | LPAREN expression RPAREN
    ;

operator
    : PLUS
    | MINUS
    | MULTIPLY
    | DIVIDE
    ;

whereClause
    : condition
    | LPAREN whereClause RPAREN
    | whereClause AND whereClause
    | whereClause OR whereClause
    ;

condition
    : fieldPath comparisonOperator (namedParameter | STRING | numberLiteral | BOOLEAN_LITERAL)
    ;

// '=' compares here and assigns in SET and WITH options; a comparison only appears in WHERE
comparisonOperator
    : ASSIGN
    | NOT_EQUALS
    | LESS_THAN
    | GREATER_THAN
    | LESS_THAN_EQUALS
    | GREATER_THAN_EQUALS
    ;

tableName
    : identifier
    ;
    
columnName
    : identifier
    ;

columnDefinition
    : identifier type
    ;

unionVariant
    : identifier LPAREN columnDefinition (COMMA columnDefinition)* RPAREN
    ;

type
    : TEXT
    | KEYWORD (NOT INDEXED)?
    | INTEGER (NOT INDEXED)?
    | LONG (NOT INDEXED)?
    | FLOAT (NOT INDEXED)?
    | DOUBLE (NOT INDEXED)?
    | BOOLEAN (NOT INDEXED)?
    | DATE (NOT INDEXED)?
    | JSON (NOT INDEXED)?
    | BINARY
    | GEO_POINT
    | GEO_SHAPE
    | UUID (NOT INDEXED)?
    | DECIMAL (NOT INDEXED)?
    | OBJECT LPAREN columnDefinition (COMMA columnDefinition)* RPAREN (NOT INDEXED)?
    | NESTED LPAREN columnDefinition (COMMA columnDefinition)* RPAREN
    | UNION  LPAREN unionVariant   (COMMA unionVariant)*   RPAREN (NOT INDEXED)?
    ;

comment
    : COMMENT
    ;

// A name: a field, table, alias, function or parameter. A keyword that does not structure a statement is a name
// wherever a name can appear, so a field can be called date or size; a reserved word is a name when double-quoted.
identifier
    : ID
    | QUOTED_ID
    | nonReserved
    ;

nonReserved
    : ABORT | ADD | ASC | AUTO | COLUMN | COMPONENT | CONFLICTS | DATA | DATA_RETENTION | DATE | DESC | DOCUMENT_ID
    | DOUBLE | EXISTS | FLOAT | FOR | IF | INDEX | INDEXED | LONG | MAX_DOCS | NUMBER_OF_REPLICAS | NUMBER_OF_SHARDS
    | PROCEED | QUERY | REFRESH | ROUTING | SCRIPT | SIZE | SLICES | SOURCE_FIELDS | STREAM | TABLE | TEMPLATE
    | TIME_REFERENCE | USING | WAIT | SKIP_IF_NO_SOURCE
    | BOOLEAN | INTEGER | KEYWORD | NESTED | OBJECT | TEXT | JSON | BINARY | GEO_POINT | GEO_SHAPE | UUID | DECIMAL | UNION
    ;

// SQL Keywords
ABORT: 'ABORT';
ADD: 'ADD';
ALTER: 'ALTER';
AND: 'AND';
AS: 'AS';
ASC: 'ASC';
AUTO: 'AUTO';
BY: 'BY';
COLUMN: 'COLUMN';
COMPONENT: 'COMPONENT';
CONFLICTS: 'CONFLICTS';
CREATE: 'CREATE';
DATA: 'DATA';
DATA_RETENTION: 'DATA_RETENTION';
DATE: 'DATE';
DELETE: 'DELETE';
DESC: 'DESC';
DOCUMENT_ID: 'DOCUMENT_ID';
DOUBLE: 'DOUBLE';
EXISTS: 'EXISTS';
FLOAT: 'FLOAT';
FOR: 'FOR';
FROM: 'FROM';
GROUP: 'GROUP';
IF: 'IF';
INDEX: 'INDEX';
INDEXED: 'INDEXED';
INSERT: 'INSERT';
INTO: 'INTO';
LIMIT: 'LIMIT';
LONG: 'LONG';
MAX_DOCS: 'MAX_DOCS';
NOT: 'NOT';
NUMBER_OF_REPLICAS: 'NUMBER_OF_REPLICAS';
NUMBER_OF_SHARDS: 'NUMBER_OF_SHARDS';
OR: 'OR';
ORDER: 'ORDER';
PROCEED: 'PROCEED';
QUERY: 'QUERY';
REFRESH: 'REFRESH';
REINDEX: 'REINDEX';
ROUTING: 'ROUTING';
SCRIPT: 'SCRIPT';
SELECT: 'SELECT';
SET: 'SET';
SIZE: 'SIZE';
SLICES: 'SLICES';
SOURCE_FIELDS: 'SOURCE_FIELDS';
STREAM: 'STREAM';
TABLE: 'TABLE';
TEMPLATE: 'TEMPLATE';
TIME_REFERENCE: 'TIME_REFERENCE';
UPDATE: 'UPDATE';
USING: 'USING';
VALUES: 'VALUES';
WHERE: 'WHERE';
WITH: 'WITH';
WAIT: 'WAIT';
SKIP_IF_NO_SOURCE: 'SKIP_IF_NO_SOURCE';

// Type Keywords
BOOLEAN: 'BOOLEAN';
INTEGER: 'INTEGER';
KEYWORD: 'KEYWORD';
NESTED: 'NESTED';
OBJECT: 'OBJECT';
TEXT: 'TEXT';
JSON: 'JSON';
BINARY: 'BINARY';
GEO_POINT: 'GEO_POINT';
GEO_SHAPE: 'GEO_SHAPE';
UUID: 'UUID';
DECIMAL: 'DECIMAL';
UNION: 'UNION';

// Punctuation and Operators
ASSIGN: '=';
COLON: ':';
COMMA: ',';
DIVIDE: '/';
DOT: '.';
GREATER_THAN: '>';
GREATER_THAN_EQUALS: '>=';
LBRACE: '{';
LBRACKET: '[';
LESS_THAN: '<';
LESS_THAN_EQUALS: '<=';
LPAREN: '(';
MINUS: '-';
MULTIPLY: '*';
NOT_EQUALS: '!=';
PLUS: '+';
RBRACE: '}';
RBRACKET: ']';
RPAREN: ')';
SEMICOLON: ';';

// Literals and Identifiers
BOOLEAN_LITERAL: 'true' | 'false';
DECIMAL_LITERAL: [0-9]+ '.' [0-9]+;
// Word literals are declared before ID because a match of equal length goes to whichever rule comes first
NULL_LITERAL: 'null';
ID: [a-z_][a-z_0-9]*;
QUOTED_ID: '"' [a-z_][a-z_0-9]* '"';
INTEGER_LITERAL: [0-9]+;
STRING: '\'' ~[']* '\'';

// Skip Tokens
COMMENT: '--' ~[\r\n]* -> skip;
WS: [ \t\r\n]+ -> skip;

