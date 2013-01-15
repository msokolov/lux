<?xml version="1.0" encoding="UTF-8"?>

<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" 
                xmlns:xs="http://www.w3.org/2001/XMLSchema"
                xmlns:http="http://expath.org/ns/http-client"
                xmlns:java="java:org.expath.saxon.HttpClient"
                version="2.0">

   <xsl:function name="http:send-request" as="item()+">
      <xsl:param name="request" as="element(http:request)?"/>
      <xsl:sequence select="java:send-request($request)"/>
   </xsl:function>

   <xsl:function name="http:send-request" as="item()+">
      <xsl:param name="request" as="element(http:request)?"/>
      <xsl:param name="uri"     as="xs:string?"/>
      <xsl:sequence select="java:send-request($uri, $request)"/>
   </xsl:function>

   <xsl:function name="http:send-request" as="item()+">
      <xsl:param name="request" as="element(http:request)?"/>
      <xsl:param name="uri"     as="xs:string?"/>
      <xsl:param name="bodies"  as="item()*"/>
      <xsl:sequence select="java:send-request($uri, $request, $bodies)"/>
   </xsl:function>

</xsl:stylesheet>
