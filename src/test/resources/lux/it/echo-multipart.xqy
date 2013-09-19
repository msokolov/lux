declare namespace http="http://expath.org/ns/webapp";
declare variable $http:input external;
<http:response status="200" message="OK">
  <http:body content-type="text/xml" charset="iso-8859-1">
    <http:result-sequence>{
      $http:input/http:request,
      for $part in ($http:input/http:request/http:body | $http:input/http:request/http:multipart/http:body) return
      <http:part>{
        $part/@*,
        $http:input[$part/@position + 1]
      }</http:part>
    }</http:result-sequence>
  </http:body>
</http:response>
