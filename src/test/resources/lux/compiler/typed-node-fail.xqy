declare namespace local="http://localhost/";

declare function local:element () 
as element()
{
    if (current-date() lt xs:date("1900-01-01")) then
        <foo /> else
     <bar /> 
};

declare function local:maybe-foo () 
as element(foo)?
{
    local:element()
};

local:maybe-foo()
