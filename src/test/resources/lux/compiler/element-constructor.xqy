declare copy-namespaces preserve, inherit;
declare namespace local="http://localhost/";

declare function local:element ($name, $id, $text)
{
  element { $name } { 
    attribute xml:id { $id },
    $text
  }
};

local:element ("xml:Father", "Bosak", "John Bosak")
