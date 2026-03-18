lexer grammar ELSDLexer;

GENE        : 'gene' ;
GENES       : 'genes' ;
PARENT      : 'parent' ;
GENERATION  : 'generation' ;
BOOLEAN     : 'boolean' ;
STRING_TYPE : 'string' ;
NUMBER_TYPE : 'number' ;

IF          : 'if' ;
THEN        : 'then' ;
ELSE        : 'else' ;
ELIF        : 'elif' ;
WHILE       : 'while' ;
DO          : 'do' ;
FOR         : 'for' ;
IN          : 'in' ;
END         : 'end' ;

// Keywords: Logic
AND         : 'and' ;
OR          : 'or' ;
NOT         : 'not' ;
TRUE        : 'true' ;
FALSE       : 'false' ;

// Keywords: Assignment / Fields
SET         : 'set' ;
DOM         : 'dom' ;
LABEL       : 'label' ;
PHENOTYPE   : 'phenotype' ;
GENOTYPE    : 'genotype' ;
CODOMINANCE : 'codominance' ;
LOCATION    : 'location' ;
LINKED      : 'linked' ;
SEXLINKED   : 'sexlinked' ;
AUTOSOMAL   : 'autosomal' ;
RATIO       : 'ratio' ;

CROSS       : 'cross' ;
FIND        : 'find' ;
PRED        : 'pred' ;
CROSS       : 'cross' ;
PRED        : 'pred' ;
ESTIMATE    : 'estimate' ;
PRINT       : 'print' ;
ALL         : 'all' ;
INFER       : 'infer' ;
PARENTS     : 'parents' ;
FROM        : 'from' ;
PROBABILITY : 'probability' ;
GIVEN       : 'given' ;
CONFIDENCE  : 'confidence' ;
LINKAGE     : 'linkage' ;
RECOMBINATION : 'recombination' ;
DISTANCE    : 'distance' ;
BLOODGROUP  : 'bloodgroup' ;
SYSTEM      : 'system' ;
CARRIES     : 'carries' ;

ABO         : 'ABO' ;
RH          : 'Rh' ;

PLUS        : '+' ;
MINUS       : '-' ;
STAR        : '*' ;
SLASH       : '/' ;
ASSIGN      : '=' ;
ARROW       : '->' ;
CROSS_OP    : 'x' ;

LT          : '<' ;
LE          : '<=' ;
GT          : '>' ;
GE          : '>=' ;
EQ          : '==' ;
NEQ         : '!=' ;

QUESTION    : '?' ;
COLON       : ':' ;

SEMI        : ';' ;
COMMA       : ',' ;
DOT         : '.' ;
LPAREN      : '(' ;
RPAREN      : ')' ;
LBRACK      : '[' ;
RBRACK      : ']' ;

NUMBER
    : '-'? DIGIT+ ('.' DIGIT+)?
    ;

STRING_LITERAL
    : '"' (~["\r\n])* '"'
    ;

ID
    : LETTER (LETTER | DIGIT | '_')*
    ;

LINE_COMMENT
    : '//' ~[\r\n]* -> skip
    ;

BLOCK_COMMENT
    : '/*' .*? '*/' -> skip
    ;

WS
    : [ \t\r\n]+ -> skip
    ;

fragment LETTER : [a-zA-Z] ;
fragment DIGIT  : [0-9] ;
