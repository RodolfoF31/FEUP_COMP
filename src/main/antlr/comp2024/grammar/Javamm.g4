grammar Javamm;

@header {
    package pt.up.fe.comp2024;
}

EQUALS : '=';
SEMI : ';' ;
LCURLY : '{' ;
RCURLY : '}' ;
LPAREN : '(' ;
RPAREN : ')' ;
MUL : '*' ;
ADD : '+' ;
DOT : '.';

VOID: 'void';
STATIC: 'static';
IMPORT: 'import';
EXTENDS: 'extends';
CLASS : 'class' ;
ARRAY : '[' WS* ']' WS*;
DOUBLE : 'double';
FLOAT: 'float';
BOOLEAN : 'boolean';
INT : 'int';
STRING: 'String';
PUBLIC: 'public';
RETURN : 'return';
IF : 'if';
ELSE: 'else';
WHILE: 'while';
THIS: 'this';
NEW: 'new';
VARARGS : '...';

INTEGER: [0] | [1-9][0-9]*;
ID : [a-zA-Z_$][a-zA-Z_$0-9]* ;

COMMENT : ('/*' .*? '*/' | '//' ~[\r\n]*) -> skip;

WS : [ \t\n\r]+ -> skip;

program
    : (importDecl)* stmt EOF
    | (importDecl)* classDecl EOF
    ;

importDecl
    : IMPORT value+=ID (DOT value += ID)* SEMI #ImportStmt
    ;

classDecl
    : CLASS name=ID (EXTENDS extendedClass=ID)? LCURLY (varDecl)* (methodDecl)* RCURLY
    ;

varDecl locals[boolean isPrivate=false]
    : ('private' {$isPrivate=true;})? type name=ID SEMI #VarDeclaration
    ;

type locals[boolean isVarArg=false]
    : name = DOUBLE ('...' {$isVarArg=true;})? #DoubleType
    | name = FLOAT ('...' {$isVarArg=true;})? #FloatType
    | name = BOOLEAN ('...' {$isVarArg=true;})? #BooleanType
    | name = INT ('...' {$isVarArg=true;})? #IntegerType
    | name = STRING ('...' {$isVarArg=true;})? #StringType
    | name = VOID #VoidType
    | name = ID #OtherType
    | type ARRAY #ArrayType
    ;

methodDecl locals[boolean isPublic=false, boolean isStatic=false]
      : (PUBLIC {$isPublic=true;})? (STATIC {$isStatic=true;})? type name=ID LPAREN (parameters)? RPAREN LCURLY varDecl* stmt* returnStmt RCURLY #NormalMethod
      | (PUBLIC {$isPublic=true;})? (STATIC {$isStatic=true;})? type name=ID LPAREN parameters? RPAREN LCURLY varDecl* stmt* RCURLY #MainMethod
      ;


parameters
    : param (',' param)* #MethodParameters
    ;

param
    : type name=ID #Parameter
    ;

funcParameter
    : expr (',' expr) *
    ;

stmt
    : expr EQUALS expr SEMI #AssignStmt
    | expr SEMI #ExprStmt
    | LCURLY stmt* RCURLY #EmptyScopeStmt
    | expr SEMI #ScopeStmt
    | IF LPAREN expr RPAREN stmt ELSE stmt #IfElseStmt
    | WHILE LPAREN expr RPAREN stmt #WhileStmt
    ;

returnStmt
    : RETURN expr SEMI
    ;

expr
    : value=INTEGER #IntegerLiteral
    | name=('true' | 'false') #BoolValue
    | name=ID #Identifier
    | expr '.' 'length' #Length
    | expr '[' expr ']' #ArrayAccess
    | name=THIS #This
    | name=ID '(' funcParameter? ')' #FunctionCall
    | expr '.' name=ID '(' funcParameter? ')' #ObjectFunctionCall
    | '(' expr ')' #Parentesis
    | expr op=('*' | '/') expr #BinaryExpr
    | expr op=('+' | '-') expr #BinaryExpr
    | expr op=('&&' | '||') expr #BinaryExpr
    | expr op=('<' | '<=' | '>' | '>=' | '==' | '!=') expr #BinaryExpr
    | '!' expr #NegOperator
    | '[' expr (',' expr)* ']' #SimpleArray
    | NEW name='int' '[' expr ']' #NewArray
    | NEW name=ID LPAREN RPAREN #NewClass
    ;