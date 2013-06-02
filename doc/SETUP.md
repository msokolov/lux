# Lux Setup #

This document describes the Lux distribution, and gives instructions for
setting up a new application server, as well as how to integrate Lux into
an existing Solr installation.

Lux is distributed as a compiled library which is ready to be dropped into
an existing Solr installation, and as a complete application server bundle,
including Solr and all of its dependencies.  The complete source code is
also available at [https://github.com/msokolov/lux](GitHub).  

When the library is installed in the context of a Solr installation it
provides an XQuery REST service.  This will be most useful for those
wishing to invoke XQuery from another programming environment.  The REST
API will be familiar to Solr users since it is a straightforwward extension
of Solr's existing API.

The Lux-enhanced Solr also provides a web application server for
applications written in XQuery and XSLT, accessing XML indexed and stored
in Solr/Lucene.

## Standalone Lux app server (w/Solr embedded) ##

1. [http://luxdb.org/Download.html](Download) the complete server bundle.

   This download includes the Lux application server, Solr, Saxon and all
   of their required dependencies.  Please see THIRDPARTYLICENSES.md for a
   complete list.

2. Start the server using the Windows batch file lux.bat, or the UNIX
   shell script lux.

TODO --- edit below --

3. The app server comes with a single core (that is a Lucene index) called
   collection1, a small demo search application and xquery evaluation
   window (querybox).  You can ensure that Lux is running properly by
   opening
   [http://localhost:8080/collection1/lux/index.xqy](http://localhost:8080/collection1/lux/query.xqy)
   in your browser; you should see the query box, and can experiment with
   evaluating XQuery interactively.  There are no documents in the index as shipped.

*** Load documents from ibiblio

*** run some sample queries

*** try the search interface

*** set up your own index

*** set up your own app folder

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
   configuration file: solrconfig.xml; just before the closing <config> tag
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
   the configuration element <updateRequestProcessorChain name="lux-update-chain">
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