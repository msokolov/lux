xquery version "1.0";

declare namespace lux="http://luxproject.net";
declare namespace demo="http://luxproject.net/demo";

declare variable $lux:http as document-node() external;

declare function demo:format-param ($p as element(param)) as xs:string
{
  fn:concat ($p/@name, "=", fn:string-join ($p/value, ","))
};

declare function demo:query ($qname, $term)
  as xs:string?
{
  let $query := concat ("node<", $qname, ":", $term)
  where $qname and $term
  return $query
};

declare function demo:search ($query, $start, $page-size)
{
  for $doc in subsequence(lux:search ($query), $start, $page-size)
  return <li><a href="doc.xqy/{$doc/base-uri()}">{substring($doc,1,120)}</a></li>
};

let $page-size := 20
let $params := $lux:http/http/parameters/param 
let $qname := $params[@name='qname']/value/string()
let $term := $params[@name='term']/value/string()
let $start := if (number($params[@name='start'])) then number($params[@name='start']) else 1
let $query := demo:query ($qname, $term)
let $total := if ($query) then lux:count ($query) else 0
let $results := if ($query) then demo:search ($query, $start, $page-size) else ()
let $next := $start + count($results)
return

(:
  OK - the basic form evaluation / http now works. How can we showcase 
  integration w/Lucene?
  4. search for matching documents, showing title and snippet(s)
  pagination (no sorting?)

  Error reporting is poor - we get the last of any syntax errors that 
  are reported by Saxon - the first (or all) would be better
:)
<html>
  <head>
    <title>Lux Demo</title>
    <link href="styles.css" rel="stylesheet" />
  </head>
  <body>
    <h1><img class="logo" src="img/sunflwor52.png" alt="Lux" height="40" /> Lux Demo</h1>
    <form action="index.xqy" id="search" name="search">
      <div class="container">
        <div>
          Context <input type="text" name="qname" id="qname" value="{$params[@name='qname']}"/>
          Text <input type="text" name="term" id="term" value="{$params[@name='term']}"/>
          <input type="submit" value="search" />
        </div>
        <div id="selection"></div>
      </div>
      <input type="hidden" name="start" id="start" value="1" />
      <div id="search-description">{
        let $filters := string-join (for $param in $params return demo:format-param ($param), "; ")
        let $summary := if ($total eq 0) then "No results matched " else
          concat ($start, " to ", $next - 1, " of ", $total, 
                  " documents with ", $filters)
        return $summary
      }</div>

    </form>
    <ul id="search-results">{$results}</ul>
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
  </body>
</html>