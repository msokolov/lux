module namespace zip = "http://expath.org/ns/zip";

declare namespace java = "java:org.expath.saxon.Zip";

declare function zip:entries(
   $href as xs:string
) as element(zip:file)
{
   java:entries($href)
};

declare function zip:xml-entry(
   $href as xs:string,
   $path as xs:string
) as document-node()
{
   java:xml-entry($href, $path)
};

declare function zip:html-entry(
   $href as xs:string,
   $path as xs:string
) as document-node()
{
   java:html-entry($href, $path)
};

declare function zip:text-entry(
   $href as xs:string,
   $path as xs:string
) as xs:string
{
   java:text-entry($href, $path)
};

declare function zip:binary-entry(
   $href as xs:string,
   $path as xs:string
) as xs:base64Binary
{
   java:binary-entry($href, $path)
};

declare function zip:zip-file(
   $zip as element(zip:file)
) (: as empty() :)
{
   java:zip-file($zip)
};

declare function zip:update-entries(
   $zip    as element(zip:file),
   $output as xs:string
) (: as empty() :)
{
   java:update-entries($zip, $output)
};
