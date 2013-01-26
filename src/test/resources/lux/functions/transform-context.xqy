declare variable $external-var external;

let $testdoc := <test>1</test>
let $xslt := 
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="2.0">
  <xsl:param name="external-var" select="'undefined'"/>
  <xsl:template match="test">
    <xsl:value-of select="$external-var" />
  </xsl:template>
</xsl:stylesheet>
  
 return lux:transform ($xslt, $testdoc)
