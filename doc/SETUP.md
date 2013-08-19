---
layout: page
title: Setup
group: navbar
pos:   3
---
# Lux Setup #

This document explains how to get Lux up and running. It describes the Lux
distribution, and gives instructions for setting up a new application
server, as well as how to integrate Lux into an existing Solr installation.

Lux is distributed as a compiled library which is ready to be dropped into
an existing Solr installation, and as a complete application server bundle,
including Solr and all of its dependencies.  The complete source code is
also available at [GitHub](https://github.com/msokolov/lux).  

When the library is installed in the context of a Solr installation it
provides an XQuery REST service.  This will be most useful for those
wishing to invoke XQuery from another programming environment.  The REST
API will be familiar to Solr users since it is a straightforward extension
of Solr's existing API.

The Lux-enhanced Solr also provides a web application server for
applications written in XQuery and XSLT, accessing XML indexed and stored
in Solr/Lucene.

## Standalone Lux app server (w/Solr embedded) ##

1. [Download](http://luxdb.org/Download.html) the complete server bundle.

   This download includes the Lux application server, Solr, Saxon and all
   of their required dependencies.  Please see THIRDPARTYLICENSES.md for a
   complete list.

2. Start the server using the Windows batch file lux.bat, or the UNIX shell
   script lux.  You can just enter "./lux" or "./lux start" to start Lux,
   and "./lux stop" to stop it.

3. The app server comes with a single core (that is a Lucene index) called
   collection1, and includes a small demo search application and xquery
   evaluation window (querybox).  You can ensure that Lux is running
   properly by opening [http://localhost:8080/](http://localhost:8080) in
   your browser; you should see the Solr 4 administrative interface.

Once you have the server running and it responds successfully via browser,
you can dive right in and start loading documents and writing queries of
your own.  If you just want to play around for a bit though, you might want
to try loading some sample data and take a look at Lux's demonstration app.

### Try the demo app

To open the demo app, select the "collection1" core link at the bottom of
the left-hand side menu, and then select the "Lux" link in the upper-right
qudrant.

#### Load documents from ibiblio

The collection1 core is delivered empty: it has no documents, initially.
In order to load some sample data, select "load shakespeare" from the top
menu; this will retrieve a complete list of shakespeare plays hosted at
ibiblio.org.  Then if you click "load selected plays," the demo app will
load the XML from ibiblio (using the EXPath http extension), chunk the
plays into scenes, and insert them into the collection1 index.  Note: you
must have an active internet connection for this to work.

#### Try the demo search interface

The "search" menu item links to a simple document-oriented search and
display application written in XQuery, XSLT, and Javascript.  The demo app
shows off Lux's index-aware capabilities.  Note that when you start typing
in the search boxes, terms from the index are shown in a typical
autocomplete menu.  One interesting feature of Lux that is not universally
available is the ability to treat tag names as first class terms in the
index (note the autocomplete for element names).  Having a QName path index
can be invaluable when working with a new tag set, for example.  Note that
the shakespeare tag names are all upper case, and that the tag name index
is case-sensitive, so in order to see matching elements, it is necessary to
enter an upper-case letter or something that sorts before 'A' like a space.

Results may be ordered by document order, by relevance, or by any indexed
field, such as title.  Document order *of documents* is simply insertion
order, and only happens to correspond to the order within the plays because
the chunker inserted lines in order.

Searches are word-oriented, and, as in typical full text search
applications, words are subject to normalizing transformations (analysis,
in Lucene parlance) to achieve better search precision and recall. Search
results show search-term highlighting.

#### Run some sample queries

The "query" menu item links to an interactive "query box" that allows for
executing arbitrary XQuery, with results displayed in the area below; XML
results are rendered with an expand/collapse viewer for easy navigation.
The original motivation for Lux was to provide this powerful ad hoc query
capability in a Solr environment.  As an example, enter:
           lux:search ("<STAGEDIR:bear") 
to get a list of all documents containing the word "bear" in a stage direction.
Or try:
           <code>/SPEECH\[@act="1"\]\[@scene="1"\]\[@speech="1"\]</code>
to see the all the first lines of Shakespeare's plays.

### Add a new core

Each Solr core functions as a completely independent index; uri uniqueness
is preserved within a single core, all searches are scoped by core, commits
are per-core, and so on.  You can have multiple cores in each Solr (or Lux)
install.  To add a new core:

1. Create a new folder in the lux-appserver/solr directory called, e.g., "new-core"
         cd lux-appserver
         mkdir new-core
2. Copy the conf folder (containing schema.xml and solrconfig.xml) from an
existing core directory (like collection1) into the new core directory:
         cp -r solr/collection1/conf/ new-core/conf/
3. Edit solr.xml (in lux-appserver/solr) and list the new core there:
      &lt;core name="new-core" instanceDir="new-core" />
4. restart lux
   ./lux restart

The new core should appear in the solr admin on the lower left-hand side.

Note: the Solr admin (in the "core admin" area) gives the impression that
you can add a core using the web GUI, but this is not actually true.  This
UI seems to edit solr.xml, but will not create the new core folder.  Its
function has more to do with distributed configurations in which cores may
migrate from host to host.

### Set up an application

It's pretty easy to deploy a web application using Lux. Just create a
folder containing your XQuery, XSLT and supporting files, and then configure Lux to point at it.  You'll need to edit two configuration files. `solrconfig.xml`, which is in your Solr core's `conf` folder, tells Lux how to map URLs to your XQuery files.  In addition, you will need to create a context in Jetty configuration in order to include static assets (js, css, images, etc).

1. In `solrconfig.xml`, find the request handler configuration element whose start tag is:
    &lt;requestHandler name="/lux" class="solr.SearchHandler" id="lux">
   It should be at the very end of the file.
2. Make a copy of this element, and edit it as follows:
   1. Change the value of the name attribute from /lux to the path where you want your application to be hosted.  If your core is called "library1," and you name your application "reader," then your application will be served at the url `http://server.name:8080/library1/reader`.
   2. Change the id to something unique, usually the same as the name, but without a leading slash.
   3. Edit the contents of the <code>&lt;str name="lux.baseUri"></code> element, replacing the default value of `context:/lux` with the (file-based) URI where your application's source files will reside.  For example, if your application will be stored at `/var/www/reader`, then you would enter `file:///var/www/reader` as the SearchHandler's `lux.baseUri`.
3. Copy the file `lux-application.xml` from `lux-appserver/contexts-available` to `lux-appserver/contexts`.  Note: this is a standard Jetty configuration file, so you can use any appropriate Jetty IOC-style configuration here.  But the only required steps are:
    1. Set `contextPath` to the path of your application (in the example above: `/library1/reader`).
    2. Set `resourceBase` to the same path you used for `lux.baseUri` above
4. Restart lux.  Your new application should now be available.  Any files with an ".xq\*" extension (ie: `.xqy`, `.xq`, `.xqm`, `.xquery`, `.xqpaloozaFest1999`, etc.) will be loaded by Lux and evaluated, with output serialized and returned as HTML.  All other files will be served without any processing.

Note: the Lux demo application is bundled inside the Java war file (if you
want to see the source code, you can extract it from there using an unzip
tool, or go look in the source repository on github).

### Create XPath fields

Lux will automatically index all element and attribute names, paths, and
content using the built-in fields lux_path, lux_elt_text, and lux_att_text.

You can also define fields indexing specific XPath expressions. You can
reference such fields in `order by` expressions (when single-valued), in
explicit search queries using the Lucene syntax, and as the target of
lux:field-values and lux:field-terms.  Soon, comparisons involving indexed
paths will be optimized using the Lucene index, but this has not yet been
implemented.

To define an XPath field, edit the lux-update-chain definition stanza in
solrconfig.xml, as in this example:

    <lst name="fields">
      <!--  define additional fields using XPath-->
      <str name="title">/descendant::TITLE</str>
      <str name="doctype_s">local-name(/*)</str>
    </lst>

The Solr field name, as defined in the `str/@name` attribute, must correspond to a field name or field name pattern defined in `schema.xml`.  To define a string-valued field, use a field name ending "_s". Integer- and long-valued fields are denoted by "_i" and "_l" suffixes, respectively.  Multiple XPaths separated by commas \(,\) are allowed.

Once new fields have been defined, restart the Lux service.  All documents
inserted after that point will be indexed by the new field.  Any existing
documents will need to be reloaded in order to have the new field
definition(s) applied to them.

### Load some documents

Your application will most likely require some data.  First, consider how best to structure your data.  Because Lux indexes documents, you will get the most benefit from the indexing optimizations when the things you search for are mostly documents, rather than elements within documents.

You can insert documents using the REST API that Lux inherits from Solr, or
using the Lux XQuery API.

#### Inserting documents using the REST API

Solr's REST service accepts documents in a number of different formats; the XML format, documented on the [UpdateXmlMessages](http://wiki.apache.org/solr/UpdateXmlMessages) page is probably the most convenient. That page also shows examples of using `curl` to post updates and other commands (such as commit) to solr.  You can use any HTTP-capable client, but curl is the most widely used command-line client, and is suitable for use in scripts.  There are also Solr clients available in many languages, including Java, .Net, Ruby, PHP, and Python.

A document in Lucene/Solr is essentially a list of field names and values. 
Lux requires two fields to be present in order to trigger its XML-aware
indexing: lux_uri and lux_xml.  Documents in Lux are uniquely identified by lux_uri; their contents are stored in lux_xml, which must be a well-formed XML document.  When submitting documents via the REST API, field values are embedded in an XML document: the lux_xml field must therefore be escaped, or wrapped as CDATA, to protect its tagging from being interpreted as part of the enclosing structure.

Here's an example, using curl, of inserting a simple document:

       curl http://localhost:8080/solr/collection1/update? -H "Content-Type: text/xml" --data-binary @update.xml

The contents of update.xml might look like this:

    <update>
      <add>
        <doc>
          <field name="lux_uri">/doc/hello.xhtml</field>
          <field name="lux_xml">
            <![CDATA[
              <html>
                <head><title>Hello, World</title></head>
                <body>Hello, World!</body>
              </html>
            ]]>
          </field>
      </add>
    </update>

#### using XQuery

The lux:insert() function inserts a document into the active Solr core (or
into an embedded Lucene index if you are using Lux as an extension to a
Java program).  Following the Solr/Lucene persistence model, you must issue
a lux:commit() in order for inserted documents to be committed to the index
and made visible to future queries (note: this area is in flux.  We have an
open issue to apply automatic commits, and also to delay committing until a
query is complete in order to respect XQuery/XSLT semantics).

But where will these documents come from?  You can read files using the
doc() function with a "file:" URI.  You can use the provided EXPath HTTP
extension module to read documents via HTTP.  Once you have the documents
in memory, you can process them and then insert them to Lux.

## Linux service setup

Linux init scripts, both for sysV setups and for the newer upstart
mechanism used by Ubuntu, are provided in the etc/init folder.  You should
be able to copy these into the relevant directory on your system
(/etc/init.d for sys V systems, and /etc/init for upstart), and for sys V
init, enable the service using chkconfig.

## Lux library only ##

If you are a Lucene user, but do not use Solr, you can still use Lux as a
means of indexing XML content and querying it using XQuery, since Lux
provides a Lucene-only API and its dependencies on Solr only arise in the
service of Solr request handlers.  Similarly, if you are Saxon user and
want to use Saxon to execute queries against a persistent indexed data
store, you can use Lux to do that without needing Solr.

In these cases, you would embed Lux in your application by placing the Lux
jar file (and its dependencies) on your classpath; then you could make use
of its Java API.

## EXPath Package Manager

Lux is distributed with a copy of the EXPath package manager, which enables
loading of standardized extensions to the XPath function library.  The app
server is configured to initialize the package manager, and will load any
modules in the EXPath repository.  It comes with two modules installed: an
HTTP client and a Zip file access module.

If you use the Lux library in some other environment, and you also want to
make use of EXPath modules, you would need to add the EXPath (for Saxon)
package manager jars to your classpath, create an EXPath repository,
install the extension jars to that repository, and install the extensions
you want into the repository.  The EXPath jars are available from the
EXPath site, and are also distributed as part of the Lux app server bundle.

To activate the EXPath extensions, you must provide the location of the
repo as the value of the system property "org.expath.pkg.saxon.repo".

## Lux source code ##

The source distribution comes with Maven project files and Eclipse
.project files, so it is easiest to build it using those tools.  As usual,
"mvn package" will compile all the source code, run all the unit tests, and
build the various artifacts.  "mvn assembly:single" builds distribution bundles.

## Maven distribution ##

Java developers wishing to embed Lux may depend on the Lux library using
Maven dependency declarations.  It is available on Maven central using the
groupID "org.luxdb" and the artifactId "lux".
