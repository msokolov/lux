<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="2.0">

  <xsl:template match="/">
    <xsl:apply-templates />
  </xsl:template>

    <!-- chunk each SCENE, and each LINE separately -->
  <xsl:template match="ACT">
    <xsl:apply-templates>
      <xsl:with-param name="act" select="position()" tunnel="yes" />
    </xsl:apply-templates>
  </xsl:template>
 
  <xsl:template match="SCENE">
    <xsl:param name="act" tunnel="yes" />
    <xsl:output-document href="{base-uri(.)}/{$act}/{$scene}">
      <xsl:copy-of select="." />
    </xsl:output-document>
    <xsl:apply-templates>
      <xsl:with-param name="scene" select="position()" tunnel="yes" />
    </xsl:apply-templates>
  </xsl:template>

  <xsl:template match="LINE">
    <xsl:param name="act" tunnel="yes" />
    <xsl:param name="scene" tunnel="yes" />
    <xsl:output-document href="{base-uri(.)}/act{$act}/sc{$scene}/l{position()}">
      <xsl:copy-of select="." />
    </xsl:output-document>
  </xsl:template>
  
</xsl:stylesheet>
