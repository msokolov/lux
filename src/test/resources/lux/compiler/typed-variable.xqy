declare namespace local="http://localhost/";

declare function local:fibonacci ($n as xs:int)
  as xs:int
{
  (: inefficient fibonacci recursion :)
  if ($n le 1) then xs:int(1) else local:fibonacci($n - 1) + local:fibonacci($n - 2)
};

let $x as xs:int := xs:int(4)
return local:fibonacci ($x)
