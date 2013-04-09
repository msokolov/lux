module namespace fact="http://luxdb.org/factorial";

declare function fact:factorial ($n as xs:integer)
as xs:integer
{
    if ($n lt 0) then 0
    else if ($n lt 2) then 1
    else $n * fact:factorial ($n - 1)
};
