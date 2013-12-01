package lux.solr;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.Charset;

import javax.xml.stream.XMLStreamException;

import lux.index.FieldRole;
import lux.index.IndexConfiguration;
import lux.index.XmlIndexer;
import lux.index.field.FieldDefinition;
import lux.xml.tinybin.TinyBinary;
import net.sf.saxon.Configuration;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.IndexableField;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.SolrInputField;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.schema.IndexSchema;
import org.apache.solr.schema.SchemaField;
import org.apache.solr.update.AddUpdateCommand;
import org.apache.solr.update.DeleteUpdateCommand;
import org.apache.solr.update.processor.UpdateRequestProcessor;
import org.slf4j.LoggerFactory;

/**
 * Handles documents written to Solr via its HTTP APIs
 */
public class LuxUpdateProcessor extends UpdateRequestProcessor {

    private final SolrIndexConfig solrIndexConfig;
    private final IndexConfiguration indexConfig;
    private final Configuration saxonConfig;
    private final SolrQueryRequest req;
    
    public LuxUpdateProcessor (SolrIndexConfig config, SolrQueryRequest req, UpdateRequestProcessor next) {
        super(next);
        solrIndexConfig = config;
        indexConfig = solrIndexConfig.getIndexConfig();
        saxonConfig = solrIndexConfig.getCompiler().getProcessor().getUnderlyingConfiguration();
        this.req = req;
    }

    @Override
    public void processAdd (final AddUpdateCommand cmd) throws IOException {
        SolrInputDocument solrInputDocument = cmd.getSolrInputDocument();
        String xmlFieldName = indexConfig.getFieldName(FieldRole.XML_STORE);

        // remove and stash the xml field value
        SolrInputField xmlField = solrInputDocument.removeField(xmlFieldName);

        Document luceneDocument = cmd.getLuceneDocument();

        UpdateDocCommand luxCommand = null;
        if (xmlField != null) {
            // restore the xml field value
            solrInputDocument.put (xmlFieldName, xmlField);
            XmlIndexer xmlIndexer = solrIndexConfig.checkoutXmlIndexer();
            Object xml = xmlField.getFirstValue();
            try {
                String uri = (String) solrInputDocument.getFieldValue(indexConfig.getFieldName(FieldRole.URI));
                try {
                    if (xml instanceof String) {
                        xmlIndexer.index (new StringReader((String) xml), uri);
                    } else if (xml instanceof byte[]) {
                        TinyBinary xmlbin = new TinyBinary ((byte[]) xml, Charset.forName("utf-8"));
                        xmlIndexer.index(xmlbin.getTinyDocument(saxonConfig), uri);
                    }
                } catch (XMLStreamException e) {
                    LoggerFactory.getLogger(LuxUpdateProcessor.class).error ("Failed to parse " + FieldRole.XML_STORE, e);
                }
                addDocumentFields (xmlIndexer, solrIndexConfig.getSchema(), luceneDocument);
                luxCommand = new UpdateDocCommand(req, solrInputDocument, luceneDocument, uri);
            } finally {
                solrIndexConfig.returnXmlIndexer(xmlIndexer);
            }
        }
        if (next != null) {
            next.processAdd(luxCommand == null ? cmd : luxCommand);
        }
    }
    
    public static void addDocumentFields (XmlIndexer indexer, IndexSchema indexSchema, Document doc) {
        IndexConfiguration indexConfig = indexer.getConfiguration();
        if (indexConfig.isOption(IndexConfiguration.STORE_TINY_BINARY)) {
            // remove the serialized xml field value -- we will store a TinyBinary instead
            doc.removeField(indexConfig.getFieldName(FieldRole.XML_STORE));
        }
        for (FieldDefinition field : indexConfig.getFields()) {
            String fieldName = field.getName();
            if (field == indexConfig.getField(FieldRole.URI) ||  
                field == indexConfig.getField(FieldRole.XML_STORE))
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
        for (IndexableField f : field.getType().createFields(field, val, boost)) {
          if (f != null) doc.add((Field) f); // null fields are not added
        }
      }

    @Override
    public void processDelete (DeleteUpdateCommand cmd) throws IOException {
        if (next != null) {
            next.processDelete(cmd);
        }
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
