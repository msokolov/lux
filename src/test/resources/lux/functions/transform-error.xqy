let $testdoc := <test>1</test>
let $xslt := <xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="2.0" />

(: order of arguments is reversed :)  
return lux:transform ($testdoc, $xslt)
