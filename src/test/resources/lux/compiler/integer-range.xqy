import module namespace fact="http://luxdb.org/factorial" at "factorial.xqy";

(: Saxon produces a special expression for integer range tests :)

(fact:factorial(1) to fact:factorial(5)) = 13
