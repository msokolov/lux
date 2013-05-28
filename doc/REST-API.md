---
layout: page
title: REST API
group: api
pos: 1
---
# REST API #

Lux extends Solr's HTTP REST API to provide XML indexing for documents
inserted via the Solr update API, XQuery processing via the Solr search
API, and an XQuery application server.

For complete documentation on the underlying Solr API, see the [Solr
wiki](http://wiki.apache.org/solr/).  

Lux provides these extensions to Solr: 

1. [LuxUpdateProcessor](#luxupdateprocessor)
2. [XQueryComponent](#xquerycomponent)
3. [AppServerComponent](#appservercomponent)

## LuxUpdateProcessor ##

This processor is designed to be inserted into a standard Solr document
update chain, as in the example solrconfig.xml that comes with Lux. Its job
is to perform XML-aware indexing.  It generates values for Lux's built-in
fields, as well as any XPath fields configured in solrconfig.xml.

The basic structure of API calls for performing document updates (and
inserts and by the way deletes, too) is the same as the Solr API: you POST
a document to one of the update request handlers that includes the Lux
processor in its chain.  Each handler expects you to provide a set of
fields in a format specific to that handler.  The default handler expects
an XML syntax.

In order to trigger XML processing, you must include values for two fields:
`lux_uri` and `lux_xml`.  The value of `lux_uri` is the unique key that
refers to the document: it is used to retrieve the document with the
`fn:doc()` function. The value of `lux_xml` is the text of the XML
document. (Note: these two special fields may be renamed in solrconfig.xml.
Do this if you have an existing schema with a unique key field and/or an
existing document storage field you want to re-use).

Note: The example configuration injects the Lux processor into the /update
(default XML format) and /update/javabin handlers, but not in the
/update/csv or /update/json handlers, although you could certainly add it
to those as well if you use them.

### Configuration ###

The update processor is configured in solrconfig.xml.  A sample configuration element looks like this:

    <updateRequestProcessorChain name="lux-update-chain">
      <lst name="fields">
        <!--  define additional fields using XPath-->
        <str name="title">
          /descendant::title[1],
          /descendant::wp:title[1],
          /SPEECH/LINE[1]
        </str>
        <str name="doctype_s">local-name(/*)</str>
      </lst>
      <lst name="namespaces">
        <str name="wp">http://www.mediawiki.org/xml/export-0.8/</str>
      </lst>
      <lst name="fieldAliases">
        <str name="xmlFieldName">lux_xml</str>
        <str name="uriFieldName">lux_uri</str>
      </lst>
      <str name="xml-format">tiny</str>
      <str name="strip-namespaces">no</str>
      <processor class="lux.solr.LuxUpdateProcessorFactory" />
      <processor class="solr.LogUpdateProcessorFactory" />
      <processor class="solr.RunUpdateProcessorFactory" />
    </updateRequestProcessorChain>

#### XPath Fields ####

Each element `lst[@name='fields']/str` defines an XPath field (analogous to
a SQL 'index' or an XSLT 'key'). The value of the 'name' attribute is the
name of the field, and the element text defines one or more XPath
expressions to be indexed.  Multiple expressions are separated by commas
(,) and optional white space.  Namespace prefixes occurring in XPath field
definitions must be bound in the "namespaces" configuration, as follows.

Each element `lst[@name='namespaces']/str` defines an XML namespace
binding; the value of the `str/@name` attribute is taken as a prefix to be
bound to the namespace provided as the string value of the element.

#### Field Aliasing ####

When Lux is used with an existing Solr document index, it may be necessary
to conform with existing field naming. The elements in
`lst[@name='fieldAliases']` provide a mechanism to do so.  The value of
`str[@name='uriFieldName']` is used as the name of the unique identifier
(a.k.a primary key) field, and the value of `str[@name='xmlFieldName']` is
used as the name of the field used to store the complete XML document.

#### Document Format ####

The value of `str[@name='xml-format']` controls the XML document storage
format. Supplying a value of 'xml', or no value, causes documents to be
stored as serialized XML.  A value of 'tiny' enables the use of an
optimized binary format closely related to Saxon's TinyTree XML
representation. Using tiny format is recommended since documents in tiny
format are loaded into memory much more quickly than XML documents.

#### Namespace Stripping ####

When `str[@name='strip-namespaces']`='yes', all namespace information is
stripped from documents loaded into the index.

## XQueryComponent ##

The XQueryComponent (by default at path: `/xquery`) evaluates XQuery
expressions.  It is a Solr QueryComponent and adheres to Solr's conventions
regarding the arguments it supports:

* `q` The text of the query to evaluate is taken from the value of the `q`
parameter.
* `start` The component will skip the given number of results.
* `rows` Only the first `rows` results (after skipping past `start`) will
be returned.

XQueryComponent ignores most other standard Solr query parameters, such as
those to control sorting, faceting, highlighting, etc.  Also - be aware
that the pagination provided by `start` and `rows` is not fully optimized
(it post-processes the result set in memory), in contrast with pagination
using subsequence() expressions within the XQuery expression itself which
are subject to a deep-paging optimization that can avoid processing unused
documents.

Requests may be submitted as either GET or POST; typically GET should be
used only for simple tests and ad hoc interactive queries, since XQuery
modules will tend to be long and will need to be URL-escaped to pass as GET
query parameters.

The content-type and serialization regime of the output is controlled by the `lux.content-type` parameter:

* `lux.content-type` specifies the MIME type of the response; if text/xml
   then XML serialization is used for any nodes in the response; otherwise
   HTML serialization is used.

### Result Format ###

Results are marshalled using standard Solr API conventions, which are
controlled by the RequestWriter that is configured.  By default this is an
XML format.  This enables any standard Solr client to be used with Lux:
clients are available for many programming languages; see [Integrating
Solr](http://wiki.apache.org/solr/IntegratingSolr) for a complete list.

The XQueryComponent returns the result of the query evalution as a sequence
of elements in the list named "xpath-results" .  The name of each "string"
returned is the type of the result. So for example:

     http://localhost:8080/solr/xquery?q=(1,2,3)

returns:

    <response>
      <lst name="responseHeader"><int name="status">0</int><int name="QTime">1</int></lst>
      <lst name="xpath-results">
           <str name="xs:integer">1</str>
           <str name="xs:integer">2</str>
           <str name="xs:integer">3</str>
      </lst>
      <result name="response" numFound="0" start="0"/>
    </response>

while 

     http://localhost:8080/solr/xquery?q=<foo id="1" />

returns:

     ...
       <lst name="xpath-results">
            <str name="element">&lt;foo id="1"/&gt;</str>
       </lst>
     ...

### Error Reporting ###

When a query has an error of any kind, this is returned as the value of the
string "xpath-error"; for example, executing an empty query leads to this
error:

        <str name="xpath-error">query was blank</str>

## AppServerComponent ##

The AppServer component provides XQuery evaluation services akin to the
XQuery component, except that it reads its XQuery from a local file, rather
than from the request, and serializes its results and supplies them as the
body of the HTTP response.

-- TODO: rewrite this section; it's not all there yet --

This component enables the creation of XQuery applications like the Lux
demo.

In particular, AppServer accepts a `lux.xquery` parameter, which is used as
a url to fetch the query body to evaluate.  The url scheme may be either
'file' or 'resource'.  URLs with no scheme are interpreted as file urls
relative to the lux working may be a local path or file URI, but eventually
will enable retrieving modules from the Lucene document store.

If errors occur, the component returns an HTTP 400 status (or 404 if the
query cannot be read) with an error message and Java Exception stack trace
listed as the body. Lux attempts to identify the location of the error in
the XQuery source code, however note that due to query rewriting, line
numbering is relative to the rewritten code, rather than the original code,
and these are often quite different.  The error report contains some
substantial context from the rewritten query that helps to determine the
source of errors.


