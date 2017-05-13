/*
    A slightly altered version of 'glow's grammar for
    parsing clojure code. It is less strict in its constraints
    regarding parenthesis and keyword consistency.
 */

grammar clojure;

file: form *;

form: comment | whitespace | literal
    | list
    | vector
    | map
    | set
    | reader_macro
    | close
    ;

forms: form* ;

close: ')' | ']' | '}' ;

list: '(' forms ;

vector: '[' forms ;

map: '{' forms ;

set: '#{' forms ;

reader_macro
    : lambda
    | meta_data
    | var_quote
    | host_expr
    | tag
    | discard
    | dispatch
    | deref
    | quote
    | backtick
    | unquote
    | unquote_splicing
    | gensym
    ;

// TJP added '&' (gather a variable number of arguments)
quote
    : '\'' form | '\''
    ;

backtick
    : '`' form | '`'
    ;

unquote
    : '~' form | '~'
    ;

unquote_splicing
    : '~@' form | '~@'
    ;

tag
    : '^' form | '^'
    ;

deref
    : '@' form | '@'
    ;

gensym
    : SYMBOL '#'
    ;

lambda
    : '#' list | '#'
    ;

meta_data
    : '#^' (map form | form) | '#^'
    ;

var_quote
    : '#\'' symbol | '#\''
    ;

host_expr
    : '#+' form form | '#+'
    ;

discard
    : '#_' form | '#_'
    ;

dispatch
    : '#' symbol form | '#'
    ;

literal
    : string
    | regex
    | number
    | character
    | nil
    | boolean
    | keyword
    | symbol
    | param_name
    ;

string: STRING;

regex
    : '#' STRING
    ;

number: NUMBER;

NUMBER
    : FLOAT
    | HEX
    | BIN
    | BIGN
    | LONG
    ;

character
    : named_char
    | u_hex_quad
    | any_char
    ;
named_char: CHAR_NAMED ;
any_char: CHAR_ANY ;
u_hex_quad: CHAR_U ;

nil: NIL;
boolean: BOOLEAN;


keyword: KEYWORD;
KEYWORD: (':' | '::') | (':' | '::') KWNAME;

symbol: ns_symbol | simple_sym;
simple_sym: SYMBOL;
ns_symbol: NS_SYMBOL;

param_name: PARAM_NAME;

// Lexers
//--------------------------------------------------------------------

STRING : '"' (~["\\] | ESCAPE | CHAR_NAMED | CHAR_ANY | CHAR_U)* '"' ;

fragment
ESCAPE:
  '\\' ('b'|'t'|'n'|'f'|'r'|'"'|'\\');

// FIXME: Doesn't deal with arbitrary read radixes, BigNums
FLOAT
    : '-'? [0-9]+ FLOAT_TAIL
    | '-'? 'Infinity'
    | '-'? 'NaN'
    ;

fragment
FLOAT_TAIL
    : FLOAT_DECIMAL FLOAT_EXP
    | FLOAT_DECIMAL
    | FLOAT_EXP
    ;

fragment
FLOAT_DECIMAL
    : '.' [0-9]+ | ',' [0-9]+
    ;

fragment
FLOAT_EXP
    : [eE] '-'? [0-9]+
    ;
fragment
HEXD: [0-9a-fA-F] ;
HEX: '0' [xX] HEXD+ ;
BIN: '0' [bB] [10]+ ;
LONG: '-'? [0-9]+[lL]?;
BIGN: '-'? [0-9]+[nN];

CHAR_U
    : '\\' 'u'[0-9D-Fd-f] HEXD HEXD HEXD ;
CHAR_NAMED
    : '\\' ( 'newline'
           | 'return'
           | 'space'
           | 'tab'
           | 'formfeed'
           | 'backspace' ) ;
CHAR_ANY
    : '\\' . ;

NIL : 'nil';

BOOLEAN : 'true' | 'false' ;

SYMBOL
    : '.'
    | '/'
    | NAME
    ;

NS_SYMBOL
    : NAME '/' SYMBOL
    ;

PARAM_NAME: '%' ((('1'..'9')('0'..'9')*)|'&')? ;

// Fragments
//--------------------------------------------------------------------

fragment
NAME: SYMBOL_HEAD SYMBOL_REST* (':' SYMBOL_REST+)* ;

fragment
KWNAME: ((SYMBOL_REST | '%' | '#')+ '/')* (SYMBOL_REST | '%' | '#')+;

fragment
SYMBOL_HEAD
    : ~('0' .. '9'
        | '^' | '`' | '\'' | '"' | '#' | '~' | '@' | ':' | '/' | '%' | '(' | ')' | '[' | ']' | '{' | '}' // FIXME: could be one group
        | [ \n\r\t] // FIXME: could be WS
        )
    ;

fragment
SYMBOL_REST
    : SYMBOL_HEAD
    | '0'..'9'
    | '.'
    | ','
    ;

// Whitespace, Comments
//--------------------------------------------------------------------

whitespace: WS;
WS: [ \n\r\t]+;

comment: COMMENT_CHAR;
COMMENT_CHAR: ';' ~[\n\r]+ '\n';