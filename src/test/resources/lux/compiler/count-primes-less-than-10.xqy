declare namespace local="http://localhost/";

declare function local:is-prime ($n as xs:integer)
{
    (: no sqrt function in xquery 1.0 :)
    exists(
        for $i in 2 to $n idiv 2
        where xs:integer($n idiv $i) * $i eq $n
        return $i
    )
};
 
declare function local:count-primes ($n as xs:integer) 
{
    count(
        for $i at $ii in 2 to $n where local:is-prime($i) and local:is-prime($ii + 1) return $i
    )
};

local:count-primes(9)
