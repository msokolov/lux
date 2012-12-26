declare namespace file="http://expath.org/ns/file";
declare namespace demo="http://luxproject.net/demo";

import module namespace layout="http://luxproject.net/layout" at "src/main/webapp/layout.xqy";

declare variable $lux:http as document-node() external;

declare function demo:load ()
as node()*
{
  let $files := $lux:http/http/params/param[@name='selection']/value/string()
  let $loaded := demo:load-files ($files)
  return <p>Loaded {count($loaded)} documents from { $files, lux:commit() }</p>
};

declare function demo:load-file ($file as xs:string)
as xs:string*
{
    let $doc := doc (concat("file:", $file))
    let $doctype := name($doc/*)
    let $load-xsl := concat ($doctype, "-load.xsl")
    return if (doc-available ($load-xsl)) then
      lux:transform (doc($load-xsl), $doc)
    else
      demo:default-load ($doc)
};

declare function demo:default-load ($doc as document-node())
{
    let $basename := replace($doc/base-uri(), "^.*/(.*)\.xml", "$1")
    return
    for $e at $i in $doc/*/* 
    let $uri := concat ($basename, "-", $i, ".xml")
    return (lux:insert ($uri, $e), $uri)
};

declare function demo:load-files ($files as xs:string*)
as xs:string*
{
  for $file in $files 
  return if (file:is-dir($file)) then
    demo:load-files (for $f in file:list ($file) return concat($file, "/", $f))
  else 
    demo:load-file($file)
};

let $erase-all := ($lux:http/http/params/param[@name='erase-all']='yes')
let $result := if ($erase-all) then
  <p>Erase all not yet supported</p>
else
  demo:load()
return layout:outer ($lux:http/http/@uri, $result)
