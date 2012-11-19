declare namespace lux="http://luxproject.net";
declare variable $lux:http as document-node() external;

let $query := $lux:http/http/parameters/param[@name="query"]/value/string()
let $qname := $lux:http/http/parameters/param[@name="qname"]/value/string()
let $term-offset := string-length($qname)+2
let $tokens := 
  for $s in subsequence(lux:fieldTerms("lux_elt_text", concat($qname,":",$query)), 1, 15) return 
    concat('"', substring($s,$term-offset), '"')

return
text { '[', string-join ($tokens, ","), ']' }
