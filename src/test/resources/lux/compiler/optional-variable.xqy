declare function local:factorial ($n as xs:integer)
{
    if ($n lt 0) then 0
    else if ($n lt 2) then 1
    else $n * local:factorial ($n - 1)
};
let $x as xs:string? := ("a", "b", "c")[local:factorial(2)]
let $y as xs:string? := ("x", $x)[local:factorial(3)]
return concat($x, $y)
