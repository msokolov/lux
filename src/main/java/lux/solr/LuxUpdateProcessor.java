package lux.solr;

import static lux.index.IndexConfiguration.*;

import java.io.IOException;
import java.io.StringReader;
import java.util.Map;

import javax.xml.stream.XMLStreamException;

import lux.index.FieldName;
import lux.index.IndexConfiguration;
import lux.index.XmlIndexer;
import lux.index.analysis.WhitespaceGapAnalyzer;
import lux.index.field.FieldDefinition;
import lux.index.field.FieldDefinition.Type;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.KeywordAnalyzer;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.Fieldable;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrException.ErrorCode;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.core.SolrCore;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.schema.BinaryField;
import org.apache.solr.schema.FieldType;
import org.apache.solr.schema.IndexSchema;
import org.apache.solr.schema.SchemaField;
import org.apache.solr.schema.StrField;
import org.apache.solr.schema.TextField;
import org.apache.solr.update.AddUpdateCommand;
import org.apache.solr.update.DeleteUpdateCommand;
import org.apache.solr.update.processor.UpdateRequestProcessor;
import org.apache.solr.update.processor.UpdateRequestProcessorFactory;
import org.apache.solr.util.plugin.SolrCoreAware;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LuxUpdateProcessor extends UpdateRequestProcessorFactory implements SolrCoreAware {

    private IndexConfiguration config;
    
    @Override
    public void init(@SuppressWarnings("rawtypes") final NamedList args) {
        // TODO: check if we are unnecessarily serializing the document
        // We don't need FieldName.STORE_XML to do that since the client passes us the xml_text field
        // but we declare the field to the indexer so that it gets defined in the schema
        config = new IndexConfiguration(INDEX_FULLTEXT | INDEX_PATHS);
        if (args != null) {
            SolrParams params = SolrParams.toSolrParams(args);
            // accept override from configuration so we can piggyback on existing 
            // document storage
            String xmlFieldName = params.get("xmlFieldName", null);
            if (xmlFieldName != null) {
                config.renameField(config.getField(FieldName.XML_STORE), xmlFieldName);
            }
            String uriFieldName = params.get("uriFieldName", null);
            if (uriFieldName != null) {
                config.renameField(config.getField(FieldName.URI), uriFieldName);
            }
            String textFieldName = params.get("textFieldName", null);
            if (textFieldName != null) {
                config.renameField(config.getField(FieldName.XML_TEXT), textFieldName);
            }
            // add fields to config??
            // TODO: namespace-awareness?; namespace mapping
            // TODO: read xpath index config
        }
    }
    

    /** Called when each core is initialized; we ensure that Lux fields are configured.
     */
    public void inform(SolrCore core) {
        IndexSchema schema = core.getSchema();
        // XML_STORE is not included in the indexer's field list; we just use what came in on the request
        informField (config.getField(FieldName.XML_STORE), schema);
        for (FieldDefinition xmlField : config.getFields()) {
            informField (xmlField, schema);
        }
        // must call this after making changes to the field map:
        schema.refreshAnalyzers();
    }
    
    private void informField (FieldDefinition xmlField, IndexSchema schema) {
        Map<String,SchemaField> fields = schema.getFields();
        Map<String,FieldType> fieldTypes = schema.getFieldTypes();
        Logger logger = LoggerFactory.getLogger(LuxUpdateProcessor.class);
        String fieldName = config.getFieldName(xmlField);
        if (fields.containsKey(fieldName)) {
            // The Solr schema defines this field
            logger.info("Field already defined: " + fieldName);
            return;
        }
        // look up the type of this field using the mapping in this class
        FieldType fieldType = getFieldType(xmlField, schema);
        if (! fieldTypes.containsKey(fieldType.getTypeName())) {
            // The Solr schema does not define this field type, so add it
            logger.info("Defining fieldType: " + fieldType.getTypeName());
            fieldTypes.put(fieldType.getTypeName(), fieldType);
        } else {
            fieldType = fieldTypes.get(fieldType.getTypeName());
        }
        // Add the field to the schema
        logger.info("Defining field: " + fieldName + " of type " + fieldType.getTypeName());
        fields.put(fieldName, new SchemaField (fieldName, fieldType, xmlField.getSolrFieldProperties(), ""));
    }
    
    private FieldType getFieldType(FieldDefinition xmlField, IndexSchema schema) {
        // FIXME - we should store a field type name in XmlField and just look that up instead
        // of trying to infer from the analyzer
        Analyzer analyzer = xmlField.getAnalyzer();
        String fieldName = config.getFieldName(xmlField);
        if (analyzer == null) {
            if (! (xmlField.isStored() == Store.YES)) {
                throw new SolrException(ErrorCode.BAD_REQUEST, "invalid xml field: " + fieldName + "; no analyzer and not stored");
            }
            return new StoredStringField ();
        }
        if (xmlField.getType() == Type.TOKENS) {
            return new FieldableField();
        }
        if (analyzer == null || analyzer instanceof KeywordAnalyzer) {
            return new StringField();
        }
        if (analyzer instanceof WhitespaceGapAnalyzer) {
            return new PathField ();
        }
        throw new SolrException(ErrorCode.BAD_REQUEST, "invalid xml field: " + fieldName + "; unknown analyzer type: " + analyzer);
    }
    
    class StoredStringField extends StrField {
        StoredStringField () {
            typeName = "lux_stored_string";
        }
    }
    
    class StringField extends StrField {
        StringField () {
            typeName = "string";
        }
    }
    
    class PathField extends TextField {

        PathField () {
            typeName = "lux_text_ws";
            setAnalyzer(new WhitespaceGapAnalyzer()); 
            setQueryAnalyzer(new WhitespaceGapAnalyzer());
        }
        
        protected Field.Index getFieldIndex(SchemaField field, String internalVal) {
            return Field.Index.ANALYZED;
        }
        
    }
    
    /**
     * enable pass-through of a Fieldable to Solr; this enables analysis to be performed outside of Solr
     */
    class FieldableField extends BinaryField {
        FieldableField () {
            typeName = "fieldable";
        }

        public Field createField(SchemaField field, Object val, float boost) {
            return (Field) val;
        }
    }

    @Override
    public UpdateRequestProcessor getInstance(SolrQueryRequest req, SolrQueryResponse rsp, UpdateRequestProcessor next) {
        return new LuxProcessorInstance (next);
    }
    
    public class LuxProcessorInstance extends UpdateRequestProcessor {

        public LuxProcessorInstance (UpdateRequestProcessor next) {
            super(next);
        }
        
        private XmlIndexer getIndexer (IndexConfiguration config) {
            // TODO: pool these
            return new XmlIndexer(config);
        }

        public void processAdd (AddUpdateCommand cmd) throws IOException {
            XmlIndexer xmlIndexer = getIndexer(config);
            Object o = cmd.getSolrInputDocument().getFieldValue(config.getFieldName(FieldName.XML_STORE));
            if (o != null) {
                String xml = (String) o;
                String uri = (String) cmd.getSolrInputDocument().getFieldValue(config.getFieldName(FieldName.URI));
                try {
                    xmlIndexer.read (new StringReader(xml), uri);
                } catch (XMLStreamException e) {
                    log.error ("Failed to parse " + FieldName.XML_STORE, e);
                }
                for (FieldDefinition field : config.getFields()) {
                    if (field == config.getField(FieldName.URI) ||  
                            field == config.getField(FieldName.XML_STORE))
                    {
                        // uri and xml are provided externally
                        continue;
                    }
                    Iterable<?> values = field.getValues(xmlIndexer);
                    if (values != null) {
                        for (Object value : values) {
                            cmd.getSolrInputDocument().addField(config.getFieldName(field), value);
                        }
                    } else {
                        for (Fieldable value : field.getFieldValues(xmlIndexer)) {
                            cmd.getSolrInputDocument().addField(config.getFieldName(field), value);
                        }
                    }
                }
            }
            if (next != null) {
                next.processAdd(cmd);
            }
        }

        public void processDelete (DeleteUpdateCommand cmd) throws IOException {
            if (next != null) {
                next.processDelete(cmd);
            }
        }
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
