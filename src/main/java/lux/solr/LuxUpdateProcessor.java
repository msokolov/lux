package lux.solr;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.Charset;

import javax.xml.stream.XMLStreamException;

import lux.index.FieldName;
import lux.index.IndexConfiguration;
import lux.index.XmlIndexer;
import lux.index.field.FieldDefinition;
import lux.index.field.IDField;
import lux.xml.tinybin.TinyBinary;
import net.sf.saxon.Configuration;
import net.sf.saxon.om.NodeInfo;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.LongField;
import org.apache.lucene.index.IndexableField;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.SolrInputField;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.schema.IndexSchema;
import org.apache.solr.schema.SchemaField;
import org.apache.solr.update.AddUpdateCommand;
import org.apache.solr.update.processor.UpdateRequestProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles documents written to Solr via its HTTP APIs
 */
public class LuxUpdateProcessor extends UpdateRequestProcessor {

    private final SolrIndexConfig solrIndexConfig;
    private final IndexConfiguration indexConfig;
    private final Configuration saxonConfig;
    private final SolrQueryRequest req;
    private final Logger logger;
    
    public LuxUpdateProcessor (SolrIndexConfig config, SolrQueryRequest req, UpdateRequestProcessor next) {
        super(next);
        solrIndexConfig = config;
        indexConfig = solrIndexConfig.getIndexConfig();
        saxonConfig = solrIndexConfig.getCompiler().getProcessor().getUnderlyingConfiguration();
        this.req = req;
        logger = LoggerFactory.getLogger(getClass());
    }

    @Override
    public void processAdd (final AddUpdateCommand cmd) throws IOException {
        SolrInputDocument solrInputDocument = cmd.getSolrInputDocument();
        String xmlFieldName = indexConfig.getFieldName(FieldName.XML_STORE);
        String idFieldName = indexConfig.getFieldName(IDField.getInstance());
        
        // remove and stash the lux_xml and lux_docid field values so that AddUpdateCommand.getLuceneDocument
        // doesn't add the lux_xml field in the standard way
        SolrInputField xmlField = solrInputDocument.removeField(xmlFieldName);
        SolrInputField luxIdField = solrInputDocument.removeField(idFieldName);
        String uri = (String) solrInputDocument.getFieldValue(indexConfig.getFieldName(FieldName.URI));
        Document luceneDocument = cmd.getLuceneDocument();
        // restore the xml field value
        solrInputDocument.addField(xmlFieldName, xmlField);
        XmlIndexer xmlIndexer = solrIndexConfig.checkoutXmlIndexer();
        UpdateDocCommand luxCommand = null;
        if (xmlField != null) {
            Object xml = xmlField.getFirstValue();
            try {
                try {
                    if (xml instanceof String) {
                        xmlIndexer.index (new StringReader((String) xml), uri);
                    } else if (xml instanceof byte[]) {
                        TinyBinary xmlbin = new TinyBinary ((byte[]) xml, Charset.forName("utf-8"));
                        xmlIndexer.index(xmlbin.getTinyDocument(saxonConfig), uri);
                    } else if (xml instanceof NodeInfo) {
                        xmlIndexer.index((NodeInfo) xml, uri);
                    }
                    luceneDocument = xmlIndexer.createLuceneDocument();
                } catch (XMLStreamException e) {
                    logger.error ("Failed to parse " + uri, e);
                    // logger.debug (xml.toString());
                }
                addDocumentFields (xmlIndexer, solrIndexConfig.getSchema(), luceneDocument);
                if (luxIdField != null) {
                    Object id = luxIdField.getValue();
                    if (! (id instanceof Long)) {
                        // solr cloud distributes these as Strings
                        id = Long.valueOf(id.toString());
                    }
                    luceneDocument.add (new LongField(idFieldName, (Long) id, Store.YES));
                }
                luxCommand = new UpdateDocCommand(req, solrInputDocument, luceneDocument, uri);
            } catch(Exception e) {
                logger.error("An error occurred while indexing " + uri, e);
                throw new IOException(e);
            }
            finally {
                solrIndexConfig.returnXmlIndexer(xmlIndexer);
            }
            // logger.debug ("Indexed XML document " + uri);
        }
        if (next != null) {
            next.processAdd(luxCommand == null ? cmd : luxCommand);
        }
    }

    private void addDocumentFields (XmlIndexer indexer, IndexSchema indexSchema, Document doc) {
        if (indexConfig.isOption(IndexConfiguration.STORE_TINY_BINARY)) {
            // remove the serialized xml field value -- we will store a TinyBinary instead
            doc.removeField(indexConfig.getFieldName(FieldName.XML_STORE));
        }
        for (FieldDefinition field : indexConfig.getFields()) {
            String fieldName = indexConfig.getFieldName(field);
            if (field == indexConfig.getField(FieldName.URI) ||  
                field == indexConfig.getField(FieldName.XML_STORE))
            {
                if (doc.getField(fieldName) != null) {
                    // uri and xml are provided externally in LuxUpdateProcessor
                    continue;
                }
            }
            Iterable<?> values = field.getValues(indexer);
            SchemaField schemaField = indexSchema.getField(fieldName);
            if (values != null) {
                for (Object value : values) {
                    addField(doc, schemaField, value, 1.0f);
                }
            } else {
                for (IndexableField value : field.getFieldValues(indexer)) {
                    addField(doc, schemaField, value, 1.0f);
                }
            }
        }
    }
    
    // from solr..DocumentBuilder
    private static void addField(Document doc, SchemaField field, Object val, float boost) {
        if (val instanceof IndexableField) {
            doc.add((IndexableField) val);
        } else {
            for (IndexableField f : field.getType().createFields(field, val, boost)) {
                if (f != null) doc.add((Field) f); // null fields are not added
            }
        }
      }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
