declare namespace local="http://localhost/";

declare function local:fibonacci ($n as xs:int)
  as xs:int
{
  (: inefficient fibonacci recursion :)
  if ($n le 1) 
  then 
    xs:int(1) 
  else 
    let $n1 := local:fibonacci(xs:int($n - 1))
    let $n2 := local:fibonacci(xs:int($n - 2))
    return xs:int($n1 + $n2)
};

let $x as xs:int := xs:int(4)
return local:fibonacci ($x)
