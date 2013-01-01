declare namespace lux="http://luxproject.net";
declare variable $lux:http as document-node() external;

let $query := $lux:http/http/params/param[@name="query"]/value/string()
let $qname := $lux:http/http/params/param[@name="qname"]/value/string()
let $term-offset := string-length($qname)+2
let $tokens := 
  if ($qname) then
    for $s in subsequence(lux:field-terms("lux_elt_text", concat($qname,":",$query)), 1, 15) return 
      concat('"', substring($s,$term-offset), '"')
  else
    for $s in subsequence(lux:field-terms("lux_text", $query), 1, 15) return 
      concat('"', $s, '"')

return
text { '[', string-join ($tokens, ","), ']' }
