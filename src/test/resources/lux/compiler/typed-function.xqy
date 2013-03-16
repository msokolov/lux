declare namespace local="http://localhost/";

declare function local:int-sequence ($n as xs:integer)
  as xs:integer
{
  (: in fact return a sequence - this should throw a dynamic type error :)
  if ($n le 1) then (1) else (local:int-sequence($n - 1), $n)
};

local:int-sequence (3)
