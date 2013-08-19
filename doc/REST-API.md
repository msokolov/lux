---
layout: page
title: Lux REST API
group: api
pos: 1
---
# REST API #

Lux extends Solr's HTTP REST API to provide XML indexing for documents
inserted via the Solr update API (endpoint: `/update`), XQuery processing
via the Solr search API (endpoint: `/xquery`), and an XQuery application
server (endpoint: `/lux`).  

Note: The actual URLs for these services will be of the form:
`http://{hostname}:{port}/{context}/{core}/{service}`, where hostname is
the name of the server where Lux is installed, port and context are defined
in web.xml for the entire Solr service, and Solr cores (each distinct index
is called a 'core') are defined in solr.xml (see
[CoreAdmin](http://wiki.apache.org/solr/CoreAdmin) for information about
administering cores using Solr's REST API).  Lux comes configured so that
you can access services for its single core at
`http://localhost:8080/solr/collection1/{service}`.

1. [LuxUpdateProcessor](#luxupdateprocessor); enhances the /update endpoint by adding XML indexing
2. [XQueryComponent](#xquerycomponent); the /xquery endpoint evaluates XQuery 1.0 queries supplied via HTTP GET or POST
3. [AppServerComponent](#appservercomponent); the /lux endpoint evaluates XQuery stored in modules on the file system, enabling applications to be written in XQuery

## /update - LuxUpdateProcessor ##

The basic structure of API calls for performing document updates (and
deletes) is the same as the Solr API: you POST a document to the update
request handler `/update`. The update handler detects the format of the
update message from its HTTP Content-Type header.  Supported formats are:
[xml](http://wiki.apache.org/solr/UpdateXmlMessages),
[json](http://wiki.apache.org/solr/UpdateJSON),
[csv](http://wiki.apache.org/solr/UpdateCSV), or
[javabin](http://wiki.apache.org/solr/javabin).

In order to trigger XML processing, you must include values for two fields:
`lux_uri` and `lux_xml`.  The value of `lux_uri` is the unique key that
refers to the document: it is used to retrieve the document with the
`fn:doc()` function. The value of `lux_xml` is the text of the XML
document. (Note: these two special fields may be renamed in solrconfig.xml.
Do this if you have an existing schema with a unique key field and/or an
existing document storage field you want to re-use).

The Lux update processor gets inserted into Solr's update chain in order to
perform XML-aware indexing.  It generates values for Lux's built-in fields,
as well as any user-defined XPath fields that have been configured.

If you use the preconfigured application server bundle, you don't need to
do anything special with this processor.  It is already configured to
register Lux's XML-aware fields with each Solr core automatically.  If you
insert documents with values for the `lux_uri` and `lux_xml` fields, the
Lux update processing chain will add its XML aware indexes. However, you
should read this section if you are deploying Lux in an existing Solr
setup, or if you need other more detailed information about the update
configuration, such as how to configure XPath indexes, or how to
enable/disable tinybin xml storage.

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
        <!--  declare namespace prefix bindings for use in XPath field definition -->
        <str name="wp">http://www.mediawiki.org/xml/export-0.8/</str>
      </lst>
      <lst name="fieldAliases">
        <!-- rename lux fields in (unlikely?) event there is a conflict -->
        <str name="xmlFieldName">lux_xml</str>
        <str name="uriFieldName">lux_uri</str>
      </lst>
    <!-- Enable tinybin storage; saves overhead of parsing when retrieving documents -->
      <str name="xml-format">tiny</str>
      <!-- Strip all namespaces from indexed content -->
      <!-- <str name="strip-namespaces">no</str> -->
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
expressions.  

Requests may be submitted as either GET or POST; typically GET should be
used only for simple tests and ad hoc interactive queries, since XQuery
modules will tend to be long and will need to be URL-escaped to pass as GET
query parameters.

### Parameters

The /xquery component is a Solr QueryComponent and adheres to Solr's
conventions regarding the arguments it supports:

* `q` The text of the query to evaluate is taken from the value of the `q`
parameter.
* `start` The component will skip the given number of results.
* `rows` Only the first `rows` results (after skipping past `start`) will
be returned.

Be aware that the pagination provided by `start` and `rows` is not fully
optimized (it post-processes the result set in memory), in contrast with
pagination using subsequence() expressions within the XQuery expression
itself which are subject to a deep-paging optimization that can avoid
processing unused documents.

* `lux.content-type` specifies the MIME type of the response; if text/xml
   then XML serialization is used for any nodes in the response; otherwise
   HTML serialization is used.

XQueryComponent ignores most other standard Solr query parameters, such as
those to control sorting, faceting, highlighting, etc.

### Result Format ###

Results are marshalled using standard Solr API conventions, which are
controlled by the RequestWriter that is configured.  By default this is an
XML format, although [JSON](http://wiki.apache.org/solr/SolJSON) and
[binary](http://wiki.apache.org/solr/javabin) response formats are
available as well, among others.  This enables any standard Solr client to
be used with Lux: clients are available for many programming languages; see
[Integrating Solr](http://wiki.apache.org/solr/IntegratingSolr) for a
complete list.

The XQueryComponent returns the result of the query evaluation as a sequence
of elements in the list named "xpath-results" .  The name of each "string"
returned is the type of the result. So for example:

     http://localhost:8080/solr/collection1/xquery?q=(1,2,3)

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

The AppServer component (endpoint: `/lux`) evaluates XQuery read from a
local file and returns the result (as XML or HTML) as the body of the HTTP
response.  This makes it possible to write web applications in XQuery (and
XSLT) with data pulled from the Lux data store.

### Parameters

* `lux.xquery` - a url reference to the query body.  The url scheme may be
either 'file' or 'resource'.  The `resource:` scheme references resources
on the java classpath, i.e. provided in a java library or in the Solr web
application and is primarily for internal use.  URLs with no scheme are
interpreted as file urls relative to the current `lux.baseUri`.  By
default, the base uri is the "real path" of the servlet context: generally
this will be the directory where the java container (eg jetty) explodes the
java war file, which is what people tend to expect.  But if you are
creating your own application, you will want to to override lux.baseUri in
solrconfig.xml; see the [detailed instructions
here](SETUP.html#set_up_an_application).

* `lux.contentType` - see above

### Error Reporting ###

If errors occur, the component returns an HTTP 400 status (or 404 if the
query cannot be read) with an error message and Java Exception stack trace
listed as the body. Lux attempts to identify the location of the error in
the XQuery source code, however note that due to query rewriting, line
numbering is relative to the rewritten code, rather than the original code,
and these are often quite different.  The error report contains some
substantial context from the rewritten query that helps to determine the
source of errors.
