<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" 
                xmlns:wp="http://www.mediawiki.org/xml/export-0.8/" 
                version="2.0">

  <xsl:template match="/">
    <xsl:variable name="title">
      <xsl:value-of select="(article/title,'untitled')[1]" />
    </xsl:variable>
    <a href="view.xqy{base-uri()}"><xsl:value-of select="$title" /></a>
  </xsl:template>

</xsl:stylesheet>
