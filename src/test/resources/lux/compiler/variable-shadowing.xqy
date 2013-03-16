declare namespace local="http://localhost/";

(: Return a list with the random integer 
in the range 1-$range, inclusive and the next seed to pass :)
declare function local:rand ($range as xs:integer, $seed as xs:integer) 
  as xs:integer+
{
  let $r as xs:integer := (69069*$seed + 1) mod 4294967296
  return if ($range eq 0) then (1, $r) else ($r mod $range + 1, $r)
};

(: this should return the lasr $r - we need a complicated function here to make sure that
   Saxon doesn't optimize away the variables altogether :)
let $seed := 123456789
let $r := local:rand(100000, $seed) (: a largish number:)
let $r := local:rand(0, $r[2]) (: always returns 1 :)
let $r := local:rand($r[1], $r[2]) (: returns 0 or 1 :)
return $r[1]
