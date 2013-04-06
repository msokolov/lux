package lux.solr;

import java.io.IOException;
import java.io.StringReader;

import javax.xml.stream.XMLStreamException;

import lux.index.FieldName;
import lux.index.IndexConfiguration;
import lux.index.XmlIndexer;
import lux.index.field.FieldDefinition;

import org.apache.lucene.index.IndexableField;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.update.AddUpdateCommand;
import org.apache.solr.update.DeleteUpdateCommand;
import org.apache.solr.update.processor.UpdateRequestProcessor;
import org.slf4j.LoggerFactory;

public class LuxUpdateProcessor extends UpdateRequestProcessor {

    private final IndexConfiguration indexConfig;
    
    public LuxUpdateProcessor (IndexConfiguration config, UpdateRequestProcessor next) {
        super(next);
        indexConfig = config;
    }
    
    private XmlIndexer getIndexer (IndexConfiguration config) {
        // TODO: pool these across requests - the UpdateProcessor is allocated per-request,
        // but XmlIndexer contains various expensive components that should be reused
        return new XmlIndexer(config);
    }

    @Override
    public void processAdd (AddUpdateCommand cmd) throws IOException {
        XmlIndexer xmlIndexer = getIndexer(indexConfig);
        SolrInputDocument solrInputDocument = cmd.getSolrInputDocument();
        Object o = solrInputDocument.getFieldValue(indexConfig.getFieldName(FieldName.XML_STORE));
        if (o != null) {
            String xml = (String) o;
            String uri = (String) solrInputDocument.getFieldValue(indexConfig.getFieldName(FieldName.URI));
            try {
                xmlIndexer.index (new StringReader(xml), uri);
            } catch (XMLStreamException e) {
                LoggerFactory.getLogger(LuxUpdateProcessor.class).error ("Failed to parse " + FieldName.XML_STORE, e);
            }
            addDocumentFields (xmlIndexer, solrInputDocument);
        }
        if (next != null) {
            next.processAdd(cmd);
        }
    }
    
    public static void addDocumentFields (XmlIndexer indexer, SolrInputDocument doc) {
        IndexConfiguration indexConfig = indexer.getConfiguration();
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
            if (values != null) {
                for (Object value : values) {
                    doc.addField(fieldName, value);
                }
            } else {
                for (IndexableField value : field.getFieldValues(indexer)) {
                    doc.addField(fieldName, value);
                }
            }
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
