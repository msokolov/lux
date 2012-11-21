xquery version "1.0";

declare namespace lux="http://luxproject.net";
declare namespace demo="http://luxproject.net/demo";

declare variable $lux:http as document-node() external;

let $path := $lux:http/http/path-extra
let $doc := doc($path)
let $doctype := name($doc/*)
let $stylesheet-name := concat("file:src/main/webapp/view-", $doctype, ".xsl")
return
  if (doc-available ($stylesheet-name)) then
    lux:transform (doc($stylesheet-name), $doc)
  else
    $doc
