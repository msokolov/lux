# Lux configuration details

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

