declare namespace lux="http://luxproject.net";
declare variable $lux:http as document-node() external;

let $query := $lux:http/http/parameters/param[@name="query"]/value/string()
let $tokens := 
  for $s in ("apple","banana","cherry") return 
    concat('"', $query, '-', $s, '"')

return
text { '[', string-join ($tokens, ","), ']' }
