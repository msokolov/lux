declare namespace local="http://localhost/";
(: Broken? this returns () :)
declare function local:minus ($x) { - $x };
local:minus(local:minus(1))
