<?xml version="1.0" encoding="UTF-8"?>

<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" 
                xmlns:xs="http://www.w3.org/2001/XMLSchema"
                xmlns:zip="http://expath.org/ns/zip"
                xmlns:java="java:org.expath.saxon.Zip"
                version="2.0">

   <xsl:function name="zip:entries" as="element(zip:file)">
      <xsl:param name="href" as="xs:string"/>
      <xsl:sequence select="java:entries($href)"/>
   </xsl:function>

   <xsl:function name="zip:xml-entry" as="document-node()">
      <xsl:param name="href" as="xs:string"/>
      <xsl:param name="path" as="xs:string"/>
      <xsl:sequence select="java:xml-entry($href, $path)"/>
   </xsl:function>

   <xsl:function name="zip:html-entry" as="document-node()">
      <xsl:param name="href" as="xs:string"/>
      <xsl:param name="path" as="xs:string"/>
      <xsl:sequence select="java:html-entry($href, $path)"/>
   </xsl:function>

   <xsl:function name="zip:text-entry" as="xs:string">
      <xsl:param name="href" as="xs:string"/>
      <xsl:param name="path" as="xs:string"/>
      <xsl:sequence select="java:text-entry($href, $path)"/>
   </xsl:function>

   <xsl:function name="zip:binary-entry" as="xs:base64Binary">
      <xsl:param name="href" as="xs:string"/>
      <xsl:param name="path" as="xs:string"/>
      <xsl:sequence select="java:binary-entry($href, $path)"/>
   </xsl:function>

   <xsl:function name="zip:zip-file"> <!-- as="empty()" -->
      <xsl:param name="zip" as="element(zip:file)"/>
      <xsl:sequence select="java:zip-file($zip)"/>
   </xsl:function>

   <xsl:function name="zip:update-entries"> <!-- as="empty()" -->
      <xsl:param name="zip"    as="element(zip:file)"/>
      <xsl:param name="output" as="xs:string"/>
      <xsl:sequence select="java:update-entries($zip, $output)"/>
   </xsl:function>

</xsl:stylesheet>
