module namespace http = "http://expath.org/ns/http-client";

declare namespace java = "java:org.expath.saxon.HttpClient";

declare function http:send-request(
   $request as element(http:request)?
) as item()+
{
   java:send-request($request)
};

declare function http:send-request(
   $request as element(http:request)?,
   $uri as xs:string?
) as item()+
{
   java:send-request($request, $uri)
};

declare function http:send-request(
   $request as element(http:request)?,
   $uri as xs:string?,
   $bodies as item()*
) as item()+
{
   java:send-request($request, $uri, $bodies)
};
