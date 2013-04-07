declare namespace xs="http://localhost/xs";
declare boundary-space preserve;
let $doc := document{<xs:test><a>a</a> <xs:b>xs:b</xs:b></xs:test>}
return ($doc//xs:*/string(), $doc//*:a/string())
