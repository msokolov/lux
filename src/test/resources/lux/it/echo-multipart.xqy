declare namespace http="http://expath.org/ns/webapp";
declare variable $http:input external;
<http:response status="200" message="OK">
  <http:body content-type="text/xml" charset="iso-8859-1">
    <http:result-sequence>{
      $http:input/http:request,
      for $item in $http:input[2 to last()] return
      <http:part>{
        $item
      }</http:part>
    }</http:result-sequence>
  </http:body>
</http:response>
