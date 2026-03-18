parser grammar ELSDParser;

options { tokenVocab = ELSDLexer; }

program
    : statementList EOF
    ;

statementList
    : statement+
    ;

statement
    : declaration
    | assignment
    | flowStructure
    | computation
    | io
    ;

declaration
    : type idList (ASSIGN expression)? SEMI
    ;

type
    : GENE
    | GENES
    | PARENT
    | GENERATION
    | BOOLEAN
    | STRING_TYPE
    | NUMBER_TYPE
    ;

idList
    : ID (COMMA ID)*
    ;

assignment
    : SET field? ID ASSIGN expression SEMI                      # assignExpr
    | SET field? ID ASSIGN computation                          # assignComputation
    | SET field? idList ASSIGN exprList SEMI                    # assignMulti
    | SET DOM COLON ID ARROW ID SEMI                            # assignDominance
    ;

field
    : LABEL
    | DOM
    | PHENOTYPE
    | GENOTYPE
    | CODOMINANCE
    | LOCATION
    | LINKED
    | SEXLINKED
    | AUTOSOMAL
    | RATIO
    ;

flowStructure
    : IF condition THEN statementList
          (ELIF condition THEN statementList)*
          (ELSE statementList)?
      END SEMI                                                  # ifStatement
    | condition QUESTION statement COLON statement SEMI          # ternaryStatement
    | WHILE condition DO statementList END SEMI                  # whileStatement
    | FOR ID IN exprList DO statementList END SEMI               # forStatement
    ;

condition
    : NOT condition                                              # condNot
    | condition AND condition                                    # condAnd
    | condition OR condition                                     # condOr
    | expression operator expression                             # condCompare
    | LPAREN condition RPAREN                                    # condParen
    ;

operator
    : LT
    | GT
    | LE
    | GE
    | EQ
    | NEQ
    | AND
    | OR
    ;

expression
    : MINUS expression                                           # exprUnaryMinus
    | NOT expression                                             # exprNot
    | expression (STAR | SLASH) expression                       # exprMulDiv
    | expression (PLUS | MINUS) expression                       # exprAddSub
    | NUMBER                                                     # exprNumber
    | STRING_LITERAL                                             # exprString
    | TRUE                                                       # exprTrue
    | FALSE                                                      # exprFalse
    | event                                                      # exprEvent
    | ID                                                         # exprId
    | LPAREN expression RPAREN                                   # exprParen
    ;

exprList
    : expression (COMMA expression)*
    ;

paramList
    : (ID | expression) (COMMA (ID | expression))*
    ;

computation
    : findExpr
    | crossExpr
    | predExpr
    | estExpr
    | inferExpr
    | probExpr
    | linkExpr
    | sexExpr
    | bloodExpr
    ;

findExpr
    : FIND field ID genTag? SEMI
    ;

crossExpr
    : CROSS ID CROSS_OP ID ARROW ID (RATIO exprList)? SEMI
    ;

predExpr
    : PRED idList genTag? SEMI
    ;

estExpr
    : ESTIMATE ID NUMBER (CONFIDENCE NUMBER)? SEMI
    ;

inferExpr
    : INFER PARENTS FROM ID (COMMA idList)? SEMI                # inferParents
    | INFER field FROM ID (COMMA idList)? SEMI                  # inferField
    ;

probExpr
    : PROBABILITY event SEMI                                     # probSimple
    | PROBABILITY event GIVEN event SEMI                         # probConditionalSingle
    | PROBABILITY event GIVEN eventList SEMI                     # probConditionalMulti
    | PROBABILITY eventList SEMI                                 # probMulti
    ;

linkExpr
    : LINKAGE ID COMMA ID RECOMBINATION NUMBER SEMI              # linkPair
    | LINKAGE idList RECOMBINATION NUMBER (DISTANCE NUMBER)? SEMI # linkMulti
    ;

sexExpr
    : SEXLINKED ID SEMI                                          # sexSimple
    | SEXLINKED ID field expression SEMI                         # sexWithField
    ;

bloodExpr
    : BLOODGROUP ID SYSTEM bloodsys SEMI                         # bloodSingle
    | BLOODGROUP idList SYSTEM bloodsys
          (PHENOTYPE exprList)? SEMI                             # bloodMulti
    ;

event
    : PHENOTYPE LPAREN ID RPAREN                                 # eventPhenotype
    | GENOTYPE LPAREN ID RPAREN                                  # eventGenotype
    | CARRIES LPAREN ID COMMA alleleList RPAREN                  # eventCarries
    ;

eventList
    : event (COMMA event)*
    ;

allele
    : ID
    ;

alleleList
    : allele (COMMA allele)*
    ;

bloodsys
    : ABO
    | RH
    ;

labelOpt
    : LABEL STRING_LITERAL
    | /* epsilon */
    ;

genTag
    : GENERATION NUMBER
    ;

io
    : PRINT ID SEMI                                              # printId
    | PRINT field (ID | ALL expression)? SEMI                    # printField
    | PRINT exprList SEMI                                        # printExprList
    | PRINT eventList SEMI                                       # printEventList
    ;
