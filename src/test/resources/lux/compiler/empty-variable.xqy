declare function local:my-log ($s as xs:string, $e as empty-sequence(), $level as xs:string) 
    as empty-sequence()
{
    lux:log ($s, $level)
};

local:my-log ("OK!", (), "info")
