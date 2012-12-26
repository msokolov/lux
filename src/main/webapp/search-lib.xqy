xquery version "1.0";

module namespace search="http://luxproject.net/search";

declare function search:format-param ($p as element(param)) as xs:string
{
  fn:concat ($p/@name, "=", fn:string-join ($p/value, ","))
};

declare function search:query ($qname, $term)
  as xs:string
{
  let $eqname := replace ($qname, "([:{}])", "\\$1")
  return
  if ($qname and $term) then
    concat ("node<", $eqname, ":", $term)
  else if ($term) then
    $term
  else if ($qname) then
    concat ("node<", $eqname, ":[* TO *]")
  else
    "*:*"
};

declare function search:sort-control ($params as element(param)*)
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

declare function search:search-controls ($params as element(param)*) 
{
<div class="container">
  <div>
  Context <input type="text" name="qname" id="qname" value="{$params[@name='qname']}"/>
  Text <input type="text" name="term" id="term" value="{$params[@name='term']}"/>
  <input type="submit" value="search" />
  { search:sort-control ($params) }
  </div>
  <div id="selection"></div>
  <input type="hidden" name="start" id="start" value="1" />
</div>
  (: We reset the start position to 1 whenever search is clicked. :)
  (: Pagination controls override by setting start before submitting. :)
};

declare function search:search-description ($start as xs:integer, $total as xs:integer, $next as xs:integer, $params as element(param)*)
{
<div id="search-description">{
  let $filters := string-join (for $param in $params return search:format-param ($param), "; ")
  let $summary := if ($total eq 0) then "No results matched " else
    concat ($start, " to ", $next - 1, " of ", $total, 
    " documents with ", $filters)
    return $summary
}</div>
};

declare function search:search-pagination ($start as xs:integer, $total as xs:integer, $next as xs:integer, $page-size as xs:integer) 
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

declare function search:javascript-postamble () 
{
<div>
  <script src="js/jquery-1.8.2.min.js"></script>
  <script src="js/jquery.autocomplete.js"></script>
  <script src="js/getQNames.js"></script>
  {
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
