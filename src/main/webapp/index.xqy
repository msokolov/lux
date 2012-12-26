xquery version "1.0";

declare namespace lux="http://luxproject.net";
declare namespace demo="http://luxproject.net/demo";

import module namespace layout="http://luxproject.net/layout" at "src/main/webapp/layout.xqy";
import module namespace search="http://luxproject.net/search" at "src/main/webapp/search-lib.xqy";

declare variable $lux:http as document-node() external;

(: Search Page
    
    TODO: document parameters, autocomplete behavior, pagination, view
          dispatcher, search results formatting :)


declare function demo:search-results ($query, $start as xs:int, $page-size as xs:int, $sort as xs:string?)
{
  for $doc in subsequence(lux:search ($query, (), $sort), $start, $page-size)
  let $doctype := name($doc/*)
  let $stylesheet-name := concat("file:src/main/webapp/", $doctype, "-result.xsl")
  let $result-markup := 
    if (doc-available ($stylesheet-name)) then
      lux:transform (doc($stylesheet-name), $doc)
    else
    <a href="view.xqy{$doc/base-uri()}">{$doc/base-uri()}</a>
    return <li>{$result-markup}</li>
};

declare function demo:search ($params as element(param)*, $start as xs:integer, $page-size as xs:integer)
{
let $qname := $params[@name='qname']/value/string()
let $term := $params[@name='term']/value/string()
let $sort := $params[@name='sort']/value/string()
let $query := search:query ($qname, $term)
let $total := if ($query) then lux:count ($query) else 0
return
<search-results total="{$total}">{
  if ($query) then demo:search-results ($query, $start, $page-size, $sort) else ()
}</search-results>
};

declare function demo:search () {
let $page-size := 20
let $params := $lux:http/http/params/param 
let $start := if (number($params[@name='start'])) then number($params[@name='start']) else 1
let $results := demo:search ($params, $start, $page-size)
let $next := $start + count($results/*)
let $body := 
  <body>
    <form action="index.xqy" id="search" name="search">
      { search:search-controls ($params) }
      { search:search-description ($start, $results/@total, $next, $params) }
    </form>
    <ul id="search-results">{$results/*}</ul>
    { search:search-pagination ($start, $results/@total, $next, $page-size) }
    <p>titles: { subsequence (lux:field-terms ("title", "a"), 1, 10) }</p>
    { search:javascript-postamble () }
    {
      let $qname := $params[@name='qname'] where not ($qname) return
      <script>$("#qname").focus()</script>
    }
  </body>/*

return layout:outer($lux:http/http/@uri, $body)
};

demo:search()
