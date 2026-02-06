grammar StructuresSQL;

migrations
    : statement* EOF
    ;

statement
    : createTableStatement
    | createComponentTemplateStatement
    | createIndexTemplateStatement
    | alterTableStatement
    | reindexStatement
    | updateStatement
    | deleteStatement
    | insertStatement
    | comment
    ;

createTableStatement
    : CREATE TABLE (IF NOT EXISTS)? ID LPAREN columnDefinition (COMMA columnDefinition)* RPAREN SEMICOLON
    ;

createComponentTemplateStatement
    : CREATE COMPONENT TEMPLATE ID LPAREN templatePart (COMMA templatePart)* RPAREN SEMICOLON
    ;

createIndexTemplateStatement
    : CREATE INDEX TEMPLATE ID FOR STRING USING STRING (WITH LPAREN templatePart (COMMA templatePart)* RPAREN)? SEMICOLON
    ;

templatePart
    : NUMBER_OF_SHARDS EQUALS INTEGER_LITERAL
    | NUMBER_OF_REPLICAS EQUALS INTEGER_LITERAL
    | columnDefinition
    ;

alterTableStatement
    : ALTER TABLE ID ADD COLUMN ID type SEMICOLON
    ;

reindexStatement
    : REINDEX ID INTO ID reindexOptions? SEMICOLON
    ;

reindexOptions
    : WITH LPAREN reindexOption (COMMA reindexOption)* RPAREN
    ;

reindexOption
    : CONFLICTS EQUALS (ABORT | PROCEED)
    | MAX_DOCS EQUALS INTEGER_LITERAL
    | SLICES EQUALS (AUTO | INTEGER_LITERAL)
    | SIZE EQUALS INTEGER_LITERAL
    | SOURCE_FIELDS EQUALS STRING
    | QUERY EQUALS STRING
    | SCRIPT EQUALS STRING
    | WAIT EQUALS (TRUE | FALSE)
    | SKIP_IF_NO_SOURCE EQUALS (TRUE | FALSE)
    ;

updateStatement
    : UPDATE ID SET assignment (COMMA assignment)* WHERE whereClause (WITH REFRESH)? SEMICOLON
    ;

deleteStatement
    : DELETE FROM ID WHERE whereClause (WITH REFRESH)? SEMICOLON
    ;

insertStatement
    : INSERT INTO tableName (LPAREN columnName (COMMA columnName)* RPAREN)? VALUES LPAREN valueList RPAREN (WITH REFRESH)? SEMICOLON
    ;

valueList
    : value (COMMA value)*
    ;

value
    : STRING
    | INTEGER_LITERAL
    | BOOLEAN_LITERAL
    | PARAMETER
    ;

assignment
    : ID EQUALS expression
    ;

expression
    : PARAMETER
    | STRING
    | INTEGER_LITERAL
    | BOOLEAN_LITERAL
    | ID operator expression  // e.g., age + 1, status == 'active'
    | LPAREN expression RPAREN
    ;

operator
    : PLUS
    | MINUS
    | MULTIPLY
    | DIVIDE
    | EQUALS  // For expressions like status == 'active'
    ;

whereClause
    : condition
    | LPAREN whereClause RPAREN
    | whereClause AND whereClause
    | whereClause OR whereClause
    ;

condition
    : ID comparisonOperator (PARAMETER | STRING | INTEGER_LITERAL | BOOLEAN_LITERAL)
    ;

comparisonOperator
    : EQUALS
    | NOT_EQUALS
    | LESS_THAN
    | GREATER_THAN
    | LESS_THAN_EQUALS
    | GREATER_THAN_EQUALS
    ;

tableName
    : ID
    ;
    
columnName
    : ID
    ;

columnDefinition
    : ID type
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
    ;

comment
    : COMMENT
    ;

// SQL Keywords
ABORT: 'ABORT';
ADD: 'ADD';
ALTER: 'ALTER';
AND: 'AND';
AUTO: 'AUTO';
COLUMN: 'COLUMN';
COMPONENT: 'COMPONENT';
CONFLICTS: 'CONFLICTS';
CREATE: 'CREATE';
DATE: 'DATE';
DELETE: 'DELETE';
DOUBLE: 'DOUBLE';
EXISTS: 'EXISTS';
FLOAT: 'FLOAT';
FOR: 'FOR';
FROM: 'FROM';
IF: 'IF';
INDEX: 'INDEX';
INDEXED: 'INDEXED';
INSERT: 'INSERT';
INTO: 'INTO';
LONG: 'LONG';
MAX_DOCS: 'MAX_DOCS';
NOT: 'NOT';
NUMBER_OF_REPLICAS: 'NUMBER_OF_REPLICAS';
NUMBER_OF_SHARDS: 'NUMBER_OF_SHARDS';
OR: 'OR';
PROCEED: 'PROCEED';
QUERY: 'QUERY';
REFRESH: 'REFRESH';
REINDEX: 'REINDEX';
SCRIPT: 'SCRIPT';
SET: 'SET';
SIZE: 'SIZE';
SLICES: 'SLICES';
SOURCE_FIELDS: 'SOURCE_FIELDS';
TABLE: 'TABLE';
TEMPLATE: 'TEMPLATE';
UPDATE: 'UPDATE';
USING: 'USING';
VALUES: 'VALUES';
WHERE: 'WHERE';
WITH: 'WITH';
WAIT: 'WAIT';
TRUE: 'TRUE';
FALSE: 'FALSE';
SKIP_IF_NO_SOURCE: 'SKIP_IF_NO_SOURCE';

// Type Keywords
BOOLEAN: 'BOOLEAN';
INTEGER: 'INTEGER';
KEYWORD: 'KEYWORD';
TEXT: 'TEXT';
JSON: 'JSON';
BINARY: 'BINARY';
GEO_POINT: 'GEO_POINT';
GEO_SHAPE: 'GEO_SHAPE';
UUID: 'UUID';
DECIMAL: 'DECIMAL';

// Punctuation and Operators
COMMA: ',';
DIVIDE: '/';
EQUALS: '==';
GREATER_THAN: '>';
GREATER_THAN_EQUALS: '>=';
LESS_THAN: '<';
LESS_THAN_EQUALS: '<=';
LPAREN: '(';
MINUS: '-';
MULTIPLY: '*';
NOT_EQUALS: '!=';
PARAMETER: '?';
PLUS: '+';
RPAREN: ')';
SEMICOLON: ';';

// Literals and Identifiers
BOOLEAN_LITERAL: 'true' | 'false';
ID: [a-zA-Z_][a-zA-Z_0-9]*;
INTEGER_LITERAL: [0-9]+;
STRING: '\'' ~[']* '\'';

// Skip Tokens
COMMENT: '--' ~[\r\n]* -> skip;
WS: [ \t\r\n]+ -> skip;

