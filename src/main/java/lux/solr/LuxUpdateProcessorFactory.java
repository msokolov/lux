package lux.solr;

import static lux.index.IndexConfiguration.INDEX_FULLTEXT;
import static lux.index.IndexConfiguration.INDEX_PATHS;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import lux.index.FieldName;
import lux.index.IndexConfiguration;
import lux.index.analysis.WhitespaceGapAnalyzer;
import lux.index.field.FieldDefinition;
import lux.index.field.FieldDefinition.Type;
import lux.index.field.XPathField;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.KeywordAnalyzer;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Store;
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
import org.apache.solr.update.processor.UpdateRequestProcessor;
import org.apache.solr.update.processor.UpdateRequestProcessorFactory;
import org.apache.solr.util.plugin.SolrCoreAware;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LuxUpdateProcessorFactory extends UpdateRequestProcessorFactory implements SolrCoreAware {

    IndexConfiguration indexConfiguration;
    private Map<String,String> xpathFieldConfig;
    
    @Override
    public void init(@SuppressWarnings("rawtypes") final NamedList args) {
        // TODO: check if we are unnecessarily serializing the document
        // We don't need FieldName.STORE_XML to do that since the client passes us the xml_text field
        // but we declare the field to the indexer so that it gets defined in the schema
        indexConfiguration = makeIndexConfiguration(INDEX_FULLTEXT | INDEX_PATHS, args);
        NamedList<String> fields = (NamedList<String>) args.get("fields");
        if (fields != null) {
            xpathFieldConfig = new HashMap<String, String>();
            for (Entry<String,String> f : fields) {
                xpathFieldConfig.put(f.getKey(), f.getValue());
            }
        }
    }

    public static IndexConfiguration makeIndexConfiguration (final int options, @SuppressWarnings("rawtypes") final NamedList args) {
        IndexConfiguration config = IndexConfiguration.makeIndexConfiguration (options);
        if (args == null) {
            return config;
        }
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
        // TODO: namespace mapping - we should be able to declare namespace prefix bindings in solrconfig.xml
        return config;
    }
    

    /** Called when each core is initialized; we ensure that Lux fields are configured.
     */
    public void inform(SolrCore core) {
        IndexSchema schema = core.getSchema();
        // XML_STORE is not included in the indexer's field list; we just use what came in on the request
        informField (indexConfiguration.getField(FieldName.XML_STORE), schema);
        for (FieldDefinition xmlField : indexConfiguration.getFields()) {
            informField (xmlField, schema);
        }
        if (xpathFieldConfig != null) {
            for (Entry<String,String> f : xpathFieldConfig.entrySet()) {
                SchemaField field = core.getSchema().getField(f.getKey());
                FieldType fieldType = field.getType();
                if (fieldType == null) {
                    throw new SolrException (ErrorCode.SERVER_ERROR, "Field " + f.getKey() + " declared in lux config, but not defined in schema");
                }
                indexConfiguration.addField(new XPathField<String>(f.getKey(), f.getValue(), fieldType.getAnalyzer(), field.stored() ? Store.YES : Store.NO, Type.STRING));
            }
        }
        // must call this after making changes to the field map:
        schema.refreshAnalyzers();
    }
    
    private void informField (FieldDefinition xmlField, IndexSchema schema) {
        Map<String,SchemaField> fields = schema.getFields();
        Map<String,FieldType> fieldTypes = schema.getFieldTypes();
        Logger logger = LoggerFactory.getLogger(LuxUpdateProcessorFactory.class);
        String fieldName = indexConfiguration.getFieldName(xmlField);
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
        String fieldName = indexConfiguration.getFieldName(xmlField);
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
        return new LuxUpdateProcessor (indexConfiguration, next);
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
