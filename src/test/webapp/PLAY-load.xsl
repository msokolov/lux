<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="2.0">

  <xsl:param name="uri-base" />

  <xsl:template match="/">
    <xsl:apply-templates />
  </xsl:template>

  <xsl:template match="@*|node()">
    <xsl:copy copy-namespaces="no">
      <xsl:apply-templates select="@*|node()" />
    </xsl:copy>
  </xsl:template>

  <!-- chunk each SCENE, and each LINE separately -->
  <xsl:template match="ACT">
    <ACT>
      <xsl:attribute name="act">
        <xsl:number />
      </xsl:attribute>
      <xsl:apply-templates />
    </ACT>
  </xsl:template>
 
  <xsl:template match="SCENE">
    <xsl:variable name="act">
      <xsl:number count="ACT" />
    </xsl:variable>
    <xsl:variable name="scene">
      <xsl:number />
    </xsl:variable>
    <xsl:variable name="uri" select="concat('/',$uri-base,'/act',$act,'/scene',$scene)" />
    <xsl:message select="$uri" />
    <xsl:result-document href="{$uri}">
      <SCENE play="{/PLAY/TITLE}" act="{$act}" scene="{$scene}">
        <xsl:apply-templates />
      </SCENE>
    </xsl:result-document>
    <SCENE uri="{$uri}" />
  </xsl:template>

  <xsl:template match="SPEECH">
    <xsl:variable name="act">
      <xsl:number count="ACT" />
    </xsl:variable>
    <xsl:variable name="scene">
      <xsl:number count="SCENE" />
    </xsl:variable>
    <xsl:variable name="speech">
      <xsl:choose>
        <xsl:when test="$scene">
          <xsl:number />
        </xsl:when>
        <xsl:otherwise>
          <xsl:number count="LINE[not(ancestor::SPEECH)]" from="ancestor::ACT" level="any">
        </xsl:otherwise>
      </xsl:choose>
    </xsl:variable>
    <xsl:variable name="uri" select="concat('/',$uri-base,'/act',$act,'/scene',$scene,'/speech',$speech)" />
    <xsl:variable name="speech">
      <SPEECH play="{/PLAY/TITLE}" act="{$act}" scene="{$scene}" speech="{$speech}">
        <xsl:apply-templates />
      </SPEECH>
    </xsl:variable>
    <xsl:copy-of select="$speech" />
    <xsl:result-document href="{$uri}">
      <xsl:copy-of select="$speech" />
    </xsl:result-document>
  </xsl:template>
  
</xsl:stylesheet>
