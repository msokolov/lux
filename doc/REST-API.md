# Lux REST API documentation #

Lux extends Solr's HTTP REST API to provide XML indexing for documents
inserted via the Solr update API, and XQuery processing via the Solr search
API.

For complete documentation on the underlying Solr API, see the [Solr
wiki](http://wiki.apache.org/solr/).  There are also books and many other
resources available online.

Lux provides these extensions to Solr: 

1. [LuxUpdateProcessor](#update)
2. [XQueryComponent](#xquery)
3. [AppServerComponent](#appserver)

<a name="update" />
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

<a name="xquery" />
## XQueryComponent ##

The XQueryComponent (by default at path: `/solr/xquery`) evaluates XQuery
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
(it post-processes the complete result set in memory), so it is better to
paginate using subsequence() expressions within the XQuery expression
itself.

Requests may be submitted as either GET or POST; typically GET should be
used only for simple tests and ad hoc interactive queries, since XQuery
modules will tend to be long and will need to be URL-escaped to pass as GET
query parameters.

### Result Format ###

Results are marshalled using standard Solr API conventions, which are
controlled by the RequestWriter that is configured.  By default this is an
XML format.  The XQueryComponent returns the result of the query evalution
as a sequence of elements in the list named "xpath-results" .  The name of
each "string" returned is the type of the result. So for example:

     http://localhost:8080/solr/xquery?q=(1,2,3)

returns:

    <response>
      <lst name="responseHeader"><int name="status">0</int><int name="QTime">1</int></lst>
      <lst name="xpath-results">
           <str name="xs:string">1</str>
           <str name="xs:string">2</str>
           <str name="xs:string">3</str>
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

<a name="appserver" />
## AppServerComponent ##

The AppServer component provides XQuery evaluations services akin to the
XQuery component, except that it reads its XQuery from a local file, rather
than from the request, and serializes its results and supplies them as the
body of the HTTP response.

This component enables the creation of XQuery applications like the Lux
demo.

In particular, AppServer accepts a `lux.xquery` parameter, which is used as
a url to fetch the query body to evaluate.  At the moment, this can only be
a path or local file URL, but evetually will enable retrieving modules from
the Lucene document store.

If errors occur, the component returns an HTTP 400 status (or 404 if the
query cannot be read) with an error message and Java Exception stack trace
listed as the body.  We attempt to identify the location of the error in
the XQuery source code, however note that due to the Lux query rewriting,
line numbering is relative to the rewritten code, rather than the original
code, and these are often quite different.  As a partial workaround, the
error report contains some substantial context from the rewritten query.


