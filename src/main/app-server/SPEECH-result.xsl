<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" 
                xmlns:xs="http://www.w3.org/2001/XMLSchema"
                xmlns:local="http://local" 
                version="2.0">

  <xsl:template match="/SPEECH">
    <xsl:variable name="title">
      <xsl:value-of select="LINE[1]" />
    </xsl:variable>
    <a href="view.xqy{base-uri(/)}"><xsl:value-of select="$title" /></a>
    <span class="play-info">
      <span class="speaker"><xsl:value-of select="local:capitalize(SPEAKER[1])" /></span>
      <span class="source"><xsl:value-of select="@play" /></span>
      <span class="locator"><xsl:value-of select="@act" />;<xsl:value-of select="@scene" /></span>
    </span>
  </xsl:template>

  <xsl:function name="local:capitalize">
    <xsl:param name="string" />
    <xsl:variable name="words" as="xs:string*">
      <xsl:for-each select="tokenize($string, ' ')">
        <xsl:value-of select="concat(upper-case(substring(.,1,1)),lower-case(substring(.,2)))" />
      </xsl:for-each>
    </xsl:variable>
    <xsl:value-of select="string-join($words, ' ')" />
  </xsl:function>

</xsl:stylesheet>
