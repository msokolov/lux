declare namespace local="http://localhost/";

declare variable $local:integer as element() external;

declare function local:test () {
    $local:integer/element
};

local:test()