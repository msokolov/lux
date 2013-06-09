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
also available at [https://github.com/msokolov/lux](GitHub).  

When the library is installed in the context of a Solr installation it
provides an XQuery REST service.  This will be most useful for those
wishing to invoke XQuery from another programming environment.  The REST
API will be familiar to Solr users since it is a straightforward extension
of Solr's existing API.

The Lux-enhanced Solr also provides a web application server for
applications written in XQuery and XSLT, accessing XML indexed and stored
in Solr/Lucene.

## Standalone Lux app server (w/Solr embedded) ##

1. [http://luxdb.org/Download.html](Download) the complete server bundle.

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
           /SPEECH[@act="1"][@scene="1"][@speech="1"]
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

The Lux demo application is bundled inside the Java war file (if you want
to see the source code, you can extract it from there using an unzip tool,
or go look in the source repository on github), but you can deploy your own
applications as files.  To set up an external application, you need to edit
soltconfig.xml, which is in the `solr/[corename]/conf` folder: each
application folder must be configured for each core separately.

1. In solrconfig.xml, find the request handler configuration element whose start tag is:
   &lt;requestHandler name="/lux" class="solr.SearchHandler" id="lux">
2. Make a copy of this element, and edit it as follows:
   1. Change the value of the name attribute from /lux to the path where you want your application to be hosted.  If your core is called "library1," and you name your application "reader," then your application will be served at the url http://server.name:8080/library1/reader.
   2. Change the id to something unique, usually the same as the name, but without a leading slash.
   3. Edit the contents of the <code>&lt;str name="lux.baseUri"></code> element, replacing the default value of context:/lux with the URI where your application's source files will reside.  The only supported URI schemes are context, resource, and file.  The context and resource schemes refer to the contents of the war file; you will almost certainly want to use a file-based URI here.  For example, if your application will be stored at /var/www/reader, then you would enter file:///var/www/reader as the SearchHandler's lux.baseUri.
3. Copy the file lux-application.xml from lux-appserver/contexts-available to lux-appserver/contexts.  Note: this is a standard Jetty configuration file, so you can use any appropriate Jetty IOC-style configuration here.  But the only required steps are:
    1. Set contextPath to the path of your application (in the example above: /library1/reader).
    2. Set resourceBase to the same path you used for lux.baseUri above
4. Restart lux.  Your new application should now be available.  Any files with any ".xq*" extension (ie: .xqy, .xq, .xqm, .xquery, .xqpaloozaFest1999, etc.) will be loaded by Lux and evaluated, with output serialized and returned as HTML.  All other files will be served without any processing.

*** Create XPath fields

*** load some documents

**** using XQuery

**** using curl

*** proxy the service using apache or nginx

is configured to occupy the root context (/) on port 8080,
with the webapp directory as the web app root folder. The Solr service is at /solr.
Application files are set up to be read from the webapps/demo folder.
The xrepo folder is configured as an EXPath repository from which the app server will 
load EXPath modules.  These paths and ports are configurable by editing the lux.properties file.

## Lux query service (integrate with existing Solr) ##

1. Download the library bundle as a [zip
   file](http://luxdb.net/download/lux-0.5.zip) "Download Lux zip") or [tar
   archive](http://luxdb.net/download/lux-0.5.tar.gz "Download Lux tar").

2. Unpack the download. Move or copy the contents of the lib folder (jar
   files) into the solr/lib folder so as to add the libraries to your Solr
   classpath.

3. Insert the contents of the conf/luxconfig.xml file into Solr's
   configuration file: solrconfig.xml; just before the closing &lt;config> tag
   is a good place.

4. In solrconfig.xml, insert the Lux update chain in the configuration
   block for each update processor:

        <lst name="defaults">
          <str name="update.chain">lux-update-chain</str>
        </lst>

   For example, the configuration for the XmlUpdateRequestHandler should
   look like:

        <requestHandler name="/update" class="solr.XmlUpdateRequestHandler">
          <lst name="defaults">
            <str name="update.chain">lux-update-chain</str>
          </lst>                  
        </requestHandler>

   Also insert the Lux update chain into the configuration for
   solr.BinaryUpdateRequestHandler, solr.CSVRequestHandler and
   solr.JsonRequestHandler, or whichever update handler you will be
   using.  You can refer to the provided solrconfig.xml file for an
   example of what this should look like.

4. Lux requires that a unique string-valued key be defined. If no such field
   exists, add the following to the schema:

        <field name="lux_uri" type="string" indexed="true" stored="true" multiValued="false"/>
        <uniqueKey>lux_uri</uniqueKey>
           
   If a unique id field is already defined, you can configure Lux to use it by editing
   the configuration element <code>&lt;updateRequestProcessorChain name="lux-update-chain"></code>
   in solrconfig.xml.

   Lux will automatically register all the other fields it needs.  Their names all begin with "lux_", so it
   should not usually be necessary to rename them, but it is possible to do so using configuration in solrconfog.xml.
   Also, if you are already storing the complete text of your XML documents in Solr/Lucene, you may wish to instruct 
   Lux to use your existing field, rather than registering its own (by default: lux_xml).  This will avoid storing each
   document twice.

5. Restart Solr.  Watch the Solr logs to make sure there are no errors.
   You may see ClassNotFoundException.  If you do, that probably means the
   jars are not in the right folder: you may need to read up about Solr
   configuration: see above for a link.

   Now you have an RESTful XQuery service with automatic node and full text indexes and a search capability.  
   You can insert XML documents using the standard Solr mechanisms, and they will 
   automatically be indexed for fast retrieval using XQuery. You can send XQuery requests to it via HTTP, and receive
   responses using one of Solr's standard response writers, which wrap your results in one way or another, or
   you can use the LuxResponseWriter, which serializes the results directly as the response.

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

If you install the Lux library into a pre-existing environment, and you
also want to make use of EXPath modules, you need to add the EXPath (for
Saxon) package manager jars to your classpath, create an EXPath repository,
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

The Lux library is also available from Maven central using the groupID
"org.luxdb" and the artifactId "lux".