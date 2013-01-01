xquery version "1.0";

module namespace search="http://luxproject.net/search";

declare function search:format-param ($p as element(param)) as xs:string
{
  fn:concat ($p/@name, "=", fn:string-join ($p/value, ","))
};

declare function search:query ($params as element(param)*)
  as xs:string
{
  let $qname := $params[@name='qname']/value/string()
  let $term := $params[@name='term']/value/string()
  let $type := $params[@name='type']/value/string()
  let $eqname := replace ($qname, "([:{}])", "\\$1")
  let $type-term := if ($type) then concat("doctype_s:", $type) else ()
  let $text-term :=
    if ($qname and $term) then
      concat ("node<", $eqname, ":", $term)
    else if ($term) then
      $term
    else if ($qname) then
      concat ("node<", $eqname, ":[* TO *]")
    else
      "*:*"

      return string-join (($text-term, $type-term), " AND ")
};

declare function search:select-control ($name as xs:string, $label as xs:string, $options as element(option)*, $selected-value as xs:string?)
  as element (span)
{
    <span class="control">
      <span class="label">{$label}</span>
      <select name="{$name}">{
        for $option in $options return
          if ($selected-value eq $option/@value) then
          <option selected="selected">{$option/@*, $option/node()}</option>
        else
          $option
      }</select>
    </span>
};

declare function search:sort-control ($params as element(param)*)
{
  let $sort := $params[@name="sort"]
  let $options := (
      <option value="">in document order</option>,
      <option value="title">by title</option>,
      <option value="title descending">by title, reversed</option> )
  return search:select-control ("sort", "order by", $options, $sort)
};

declare function search:type-control ($params as element(param)*)
{
  let $type := $params[@name="type"]
  let $options := 
  for $t in lux:field-terms ("doctype_s")
  return <option value="{$t}">{$t}</option>
  return search:select-control ("type", "type", $options, $type)
};

declare function search:search-controls ($params as element(param)*) 
{
<div class="container">
  <div>
    <input type="text" name="term" id="term" value="{$params[@name='term']}" size="10" />
    in <input type="text" name="qname" id="qname" value="{$params[@name='qname']}" size="10" />
  <input type="submit" value="search" />
  { search:type-control ($params) }
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
