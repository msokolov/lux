xquery version "1.0";

declare namespace demo="http://luxproject.net/demo";

import module namespace layout="http://luxproject.net/demo/layout" at "layout.xqy";
import module namespace search="http://luxproject.net/search" at "search-lib.xqy";

declare variable $lux:http as document-node() external;

(: Search Page
    
    TODO: document parameters, autocomplete behavior, pagination, view
          dispatcher, search results formatting :)


declare function demo:search-results ($query, $start as xs:integer, $page-size as xs:integer, $sort as xs:string?)
{
  for $doc in subsequence(lux:search ($query, (), $sort), $start, $page-size)
  let $doctype := name($doc/*)
  let $stylesheet-name := concat("file:src/main/webapp/", $doctype, "-result.xsl")
  let $result-markup := 
    if (doc-available ($stylesheet-name)) then
      lux:transform (doc($stylesheet-name), $doc)
    else
      let $uri := base-uri ($doc)
      return
        if (starts-with($uri, "/")) then
        <a href="view.xqy{$doc/base-uri()}">{$doc/base-uri()}</a>
      else <a href="view.xqy/{$doc/base-uri()}">{$doc/base-uri()}</a>
    return <li>{$result-markup}</li>
};

declare function demo:search ($params as element(param)*, $start as xs:integer, $page-size as xs:integer)
{
let $sort := $params[@name='sort']/value/string()
let $query := search:query ($params)
let $total := if ($query) then lux:count ($query) else 0
return
<search-results total="{$total}">{
  if ($query) then demo:search-results ($query, $start, $page-size, $sort) else ()
}</search-results>
};

declare function demo:search () {
let $page-size := 20
let $params := $lux:http/http/params/param 
let $start as xs:integer := if (number($params[@name='start'])) then xs:integer($params[@name='start']) else 1
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
    <div class="indent-body">
      <br/>
      <input type="button" value="erase all" onclick="javascript:if (confirm('Are you sure you want to irretrievably delete the entire contents of your database?')) location.href='delete-all.xqy'" />
    </div>
    { search:javascript-postamble () }
    {
      let $qname := $params[@name='qname'] where not ($qname) return
      <script>$("#qname").focus()</script>
    }
  </body>/*

return layout:outer($lux:http/http/@uri, $body)
};

demo:search()
