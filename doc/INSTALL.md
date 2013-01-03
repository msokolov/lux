# Lux 0.5 Installation #

Lux is distributed in three basic forms: as source code, as a library, and
as a web application server bundle.  The app server bundle includes everything
(except the Java runtime) that you need to run Lux out of the box, as well as a demo
application for searching (and reading) the complete plays of Shakespeare. These are all
available for direct download from luxdb.net (see links below).  The source
code is also distributed via {source control system: GitHub?} at {scm-url}.

## Lux bundled with application server ##

The quickest way to get up and running with Lux is to install the complete
application bundle.  You will need to have [Java](http://java.com/en/download/index.jsp "Download") 
6 or greater installed in order to run Lux:

1. Download the bundle as a [zip file](http://luxdb.net/download/lux-server-0.5.zip) "Download app server zip") 
or [tar archive](http://luxdb.net/download/lux-server-0.5.tar.gz "Download app server tar").

2. Unpack the bundle (no installer required!).  This will create a folder
called "lux-0.5".

3. Run Lux.  You can do this from the command line using either the Windows
*lux.bat* batch file or UNIX *lux* bash script.

4. Verify that Lux is running by visiting http://localhost:8080/lux/demo in
your browser.  Lux comes with the Shakespeare demo installed: follow the
on-screen instructions there to load the text of all the Shakespeare plays,
try out the search, and explore the xquery source for the demo.

## Lux library ##

These instructions explain how to install Lux for use with Solr.   To install 
Solr, follow these [Solr installation instructions](http://wiki.apache.org/solr/SolrInstall "Solr Installation").

If you are a Lucene user, but do not use Solr, or if you use Saxon, but not Lucene, you can also make use of Lux; you just 
need to add the libraries to your classpath and code against the Lux API - you will need to read the 
javadocs to see how to index and query your content using Lux via its Java API.

1. Download the library bundle as a [zip file](http://luxdb.net/download/lux-0.5.zip) "Download Lux zip") 
or [tar archive](http://luxdb.net/download/lux-0.5.tar.gz "Download Lux tar").

2. Unpack the download. Move or copy the contents of the lib folder (jar files) into the solr/lib folder so as 
to add the libraries to your Solr classpath.

3. Copy the contents of the conf/lux.conf file into Solr's configuration, usually called solrconfig.xml

4. In solrconfig.xml, insert the Lux update chain in the configuration block for each update processor:
        <lst name="defaults">
          <str name="update.chain">lux-update-chain</str>
        </lst>
     For example, the configuration for the XmlUpdateRequestHandler should look like:
        <requestHandler name="/update" class="solr.XmlUpdateRequestHandler">
          <lst name="defaults">
            <str name="update.chain">lux-update-chain</str>
          </lst>                  
        </requestHandler>
     Also insert the Lux update chain into the configuration for solr.BinaryUpdateRequestHandler,
     solr.CSVRequestHandler and solr.JsonRequestHandler, or whichever handler you will be using.

3. Restart Solr.  Watch the Solr logs to make sure there are no errors.  You may see ClassNotFoundException.
If you do, that means the jars are not in the right folder: you may need to read up about Solr configuration:
see .  Or it may mean your
Solr install was borked already :).

## Lux source code ##

The source distribution comes with a Maven project file and an Eclipse .project file, so it is
easiest to build it using those tools.  As usual, "mvn package" will compile all the source code, 
run all the unit tests, and build the various artifacts, including the jar and war.

## Maven Artifacts ##

The binaries and source are available via maven using groupId: net.luxdb, artifactId: luxdb.  The latest version is 0.5