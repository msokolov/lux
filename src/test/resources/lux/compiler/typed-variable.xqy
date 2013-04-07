declare namespace local="http://localhost/";

declare function local:add-ints ($numbers as xs:int+)
    as xs:int
{
    xs:int(fn:sum($numbers))
};

declare function local:add-ints-unsafe ($numbers as xs:int+)
    as xs:int
{
    fn:sum($numbers)
};

declare function local:fibonacci ($n as xs:int)
  as xs:int
{
  (: inefficient fibonacci recursion :)
  if ($n le 1) 
  then 
    xs:int(1) 
  else 
    let $n1 as xs:int := local:fibonacci(xs:int($n - 1))
    let $n2 as xs:int := local:fibonacci(xs:int($n - 2))
    return local:add-ints(($n1, $n2))
};

let $x as xs:int := xs:int(4)
let $y as xs:int := local:add-ints-unsafe ((xs:int(1), xs:int(1)))
return local:fibonacci (xs:int($x + $y))
