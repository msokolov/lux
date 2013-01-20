declare namespace local="http://localhost/";

declare function local:strings ($args as item()*)
{
    for $arg in $args
      where $arg instance of xs:string
      return $arg
};

declare function local:castable-strings ($args as item()*)
{
    for $arg in $args
      where $arg castable as xs:string
      return $arg
};

concat (
  string-join (local:strings(("test", 1, ",", <node/>, "test")), ""),
  string-join (local:castable-strings(("test", 1, ",", <node/>, "test")), ""))
