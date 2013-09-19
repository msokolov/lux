declare namespace http="http://expath.org/ns/webapp";
declare variable $http:input external;
<http:response status="200" message="OK">
  <http:body content-type="application/xml+test">{
    $http:input/http:request
  }</http:body>
</http:response>
