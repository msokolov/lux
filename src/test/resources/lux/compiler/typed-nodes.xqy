declare namespace local="http://localhost/";

declare function local:maybe-foo () 
as element(foo)?
{
    if (current-date() gt xs:date("1900-01-01")) then
        <foo /> else ()
};

declare function local:count-foo ($foos as element(foo)+)
as xs:integer
{
    count($foos [. instance of element(foo)])
};

let $x as element(foo)? := local:maybe-foo()
let $y as element(foo)? := local:maybe-foo()
let $z as element(foo)* := ($x, $y)
return local:count-foo($z)
