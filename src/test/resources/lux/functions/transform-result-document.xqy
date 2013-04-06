let $testdoc := <test>1</test>
let $xslt := 
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="2.0">
  <xsl:template match="test">
    <xsl:result-document href="/doc/1">
        <xsl:copy-of select="." />
    </xsl:result-document>
  </xsl:template>
</xsl:stylesheet>
  
return lux:transform ($xslt, $testdoc)
