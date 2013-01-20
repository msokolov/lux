declare namespace local="http://localhost/";
declare function local:minus ($x as xs:integer) as xs:integer { - $x };
local:minus(local:minus(1))
