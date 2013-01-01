<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="2.0">

  <xsl:template match="/">
    <xsl:variable name="title">
      <xsl:value-of select="(inproceedings/title,'untitled')[1]" />
    </xsl:variable>
    <a href="view.xqy{base-uri()}"><xsl:value-of select="$title" /></a>
  </xsl:template>

</xsl:stylesheet>
