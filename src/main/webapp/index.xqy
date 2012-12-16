xquery version "1.0";

declare namespace lux="http://luxproject.net";
declare namespace demo="http://luxproject.net/demo";

import module namespace layout="http://www.luxproject.net/layout" at "src/main/webapp/layout.xqy";

declare variable $lux:http as document-node() external;

(:
    Search Page
    
    TODO: document parameters, autocomplete behavior, pagination, view dispatcher,
          search results formatting
:)

declare function demo:format-param ($p as element(param)) as xs:string
{
  fn:concat ($p/@name, "=", fn:string-join ($p/value, ","))
};

declare function demo:query ($qname, $term)
  as xs:string
{
  if ($qname and $term) then
    concat ("node<", $qname, ":", $term)
  else if ($term) then
    $term
  else if ($qname) then
    concat ("node<", $qname, ":[* TO *]")
  else
    "*:*"
};

declare function demo:sort-control ($params as element(param)*)
{
  let $sort := $params[@name="sort"]
  let $options := (
      <option value="">in document order</option>,
      <option value="title">by title</option>,
      <option value="title descending">by title, reversed</option> )
  return
  <select name="sort">{
    for $option in $options return
      if ($sort eq $option/@value) then
        <option selected="selected">{$option/@*, $option/node()}</option>
      else
       $option
  }</select>
};

declare function demo:search-controls ($params as element(param)*) 
{
<div class="container">
  <div>
  Context <input type="text" name="qname" id="qname" value="{$params[@name='qname']}"/>
  Text <input type="text" name="term" id="term" value="{$params[@name='term']}"/>
  <input type="submit" value="search" />
  { demo:sort-control ($params) }
  </div>
  <div id="selection"></div>
  <input type="hidden" name="start" id="start" value="1" />
</div>
  (: We reset the start position to 1 whenever search is clicked. :)
  (: Pagination controls override by setting start before submitting. :)
};

declare function demo:search-description ($start as xs:int, $total as xs:int, $next as xs:int, $params as element(param)*)
{
<div id="search-description">{
  let $filters := string-join (for $param in $params return demo:format-param ($param), "; ")
  let $summary := if ($total eq 0) then "No results matched " else
    concat ($start, " to ", $next - 1, " of ", $total, 
    " documents with ", $filters)
    return $summary
}</div>
};

declare function demo:search-pagination ($start as xs:int, $total as xs:int, $next as xs:int, $page-size as xs:int) 
{
<div id="pagination">{
  let $prev := if ($start le 1) then () else 
    if ($start gt $page-size) then
      $start - $page-size 
    else 1
      return (
        if ($prev) then <a href="javascript:postform('start',{$prev})">previous</a> else (),
          if ($next lt $total) then <a style="float:right" href="javascript:postform('start',{$next})">next</a> else ()
      )
}</div>
};

declare function demo:javascript-postamble ($qname as xs:string) 
{
<div>
  <script src="js/jquery-1.8.2.min.js"></script>
  <script src="js/jquery.autocomplete.js"></script>
  <script src="js/getQNames.js"></script>
  {
    if ($qname eq '') then <script>$("#qname").focus()</script> else (),
    <script>
    function postform (param, value) {{
      document.getElementById(param).value=value;
      document.forms.search.submit();
      return false;
    }}
    </script>
  }
</div>
};

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

declare function demo:search ($params as element(param)*, $start as xs:int, $page-size as xs:int)
{
let $qname := $params[@name='qname']/value/string()
let $term := $params[@name='term']/value/string()
let $sort := $params[@name='sort']/value/string()
let $query := demo:query ($qname, $term)
let $total := if ($query) then lux:count ($query) else 0
return
<search-results total="{$total}">{
  if ($query) then demo:search-results ($query, $start, $page-size, $sort) else ()
}</search-results>
};

declare function demo:search () {
let $page-size := xs:int(20)
let $params := $lux:http/http/params/param 
let $start := if (number($params[@name='start'])) then number($params[@name='start']) else 1
let $results := demo:search ($params, $start, $page-size)
let $next := $start + count($results/*)
let $body := 
  <body>
    <form action="index.xqy" id="search" name="search">
      { demo:search-controls ($params) }
      { demo:search-description ($start, $results/@total, $next, $params) }
    </form>
    <ul id="search-results">{$results/*}</ul>
    { demo:search-pagination ($start, $results/@total, $next, $page-size) }
    <p>titles: { subsequence (lux:field-terms ("title", "a"), 1, 10) }</p>
    { demo:javascript-postamble ($params[@name='qname']) }
  </body>/*

return layout:outer($lux:http/http/@uri, $body)
};

demo:search()
