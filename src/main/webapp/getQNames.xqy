declare namespace lux="http://luxproject.net";
declare variable $lux:http as document-node() external;

let $query := $lux:http/http/parameters/param[@name="query"]/value/string()
let $tokens := 
  for $s in subsequence(lux:fieldTerms("lux_elt_name", $query), 1, 15) return 
    concat('"', $s, '"')

return
text { '[', string-join ($tokens, ","), ']' }
