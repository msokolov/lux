declare function local:split ($s as xs:string, $depth as xs:integer)
as xs:string+
{
    if ($depth le 0) then $s else
    
    let $n := string-length ($s)
    let $l := substring ($s, 0, $n div 2)
    let $r := substring ($s, $n div 2)
    return (
        local:split (concat($l, $s), $depth - 1), 
        $s, 
        local:split(concat($s, $r), $depth - 1)
        )
};

local:split ("abracadabra", 16)