declare namespace http="http://expath.org/ns/webapp";
declare variable $http:input external;
<http:response status="302">
    <http:header name="Location" value="/collection1/testapp/lux/it/echo-request.xqy" />
</http:response>
