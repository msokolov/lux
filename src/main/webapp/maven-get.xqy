xquery version "1.0";

declare namespace lux="http://luxproject.net";
declare namespace maven="http://luxproject.net/maven";

import module namespace layout="http://luxproject.net/layout" at "src/main/webapp/layout.xqy";
import module namespace search="http://luxproject.net/search" at "src/main/webapp/search-lib.xqy";
import module namespace http="http://expath.org/ns/http-client";

declare variable $lux:http as document-node() external;

declare function maven:parse-solr-response ($response as element(response))
{
  let $header := $response/lst[@name='responseHeader']
  let $result := $response/result[@name='response']
  let $total := $result/@numFound
  return <search-results total="{$total}">{
    $response/response,
    for $doc in $result/doc return <a href="#">{
      $doc/str[@name='id']/string(),
      $doc/str[@name='latestVersion']/string()
    }</a>
  }</search-results>
};

declare function maven:search-results ($query, $start as xs:int, $page-size as xs:int, $sort as xs:string?)
{
  let $url := concat ("http://search.maven.org/solrsearch/select?wt=xml&amp;q=",
    $query, "&amp;rows=", $page-size, "&amp;start=", $start - 1)
  let $request := <http:request method="get" href="{$url}" />
  let $response := http:send-request ($request)
  return maven:parse-solr-response ($response[2]/response)
};

declare function maven:search-controls ($params as element(param)*) 
{
<div class="container">
  <div>
    <input type="text" name="q" id="q" value="{$params[@name='q']}"/>
    <input type="submit" value="search" />
    { search:sort-control ($params) }
  </div>
  <div id="selection"></div>
  <input type="hidden" name="start" id="start" value="1" />
</div>
  (: We reset the start position to 1 whenever search is clicked. :)
  (: Pagination controls override by setting start before submitting. :)
};

declare function maven:search ($params as element(param)*, $start as xs:integer, $page-size as xs:integer)
{
  let $query := $params[@name='q']/value/string()
  let $sort := $params[@name='sort']/value/string()
  (: let $query := search:query ($field, $term) :)
  return
    if ($query) then maven:search-results ($query, $start, $page-size, $sort) else <search-results total="0" />
};

declare function maven:search-maven () 
{
  let $page-size := 20
  let $params := $lux:http/http/params/param 
  let $start := if (number($params[@name='start'])) then xs:int(number($params[@name='start'])) else 1
  let $results := maven:search ($params, $start, $page-size)
  let $next := xs:int($start + count($results/*))
  let $body := 
  <body>
    <form action="maven-get.xqy" id="search" name="search">
      { maven:search-controls ($params) }
      { search:search-description ($start, $results/@total, $next, $params) }
    </form>
    <ul id="search-results">{
      for $r in $results/*
        return <li>{$r}</li>
    }</ul>
    { search:search-pagination ($start, $results/@total, $next, $page-size) }
    { search:javascript-postamble () }
    </body>/*

    return layout:outer($lux:http/http/@uri, $body)
};

maven:search-maven()
