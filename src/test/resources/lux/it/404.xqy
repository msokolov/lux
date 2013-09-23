declare namespace http="http://expath.org/ns/webapp";
declare variable $http:input external;
<http:response status="404" message="Not Found">
  <http:body content-type="text/html" charset="unknown">
    <html><head><title>Not Found</title></head><body>Sorry, not here, not now.</body></html>
  </http:body>
</http:response>
