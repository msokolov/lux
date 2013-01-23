package lux.solr;

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
import org.apache.solr.common.util.NamedList;
import org.apache.solr.core.SolrCore;
import org.apache.solr.schema.BinaryField;
import org.apache.solr.schema.FieldType;
import org.apache.solr.schema.IndexSchema;
import org.apache.solr.schema.SchemaField;
import org.apache.solr.schema.StrField;
import org.apache.solr.schema.TextField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Wraps a {@link IndexConfiguration}, adding field definitions from information in Solr's configuration files:
 * solrconfig.xml and schema.xml
 *
 */
public class SolrIndexConfig {
    private final IndexConfiguration indexConfig;
    private NamedList<String> xpathFieldConfig;
    
    public SolrIndexConfig (final IndexConfiguration indexConfig) {
        this.indexConfig = indexConfig;
    }

    @SuppressWarnings("unchecked")
    public static SolrIndexConfig makeIndexConfiguration (int options, final NamedList<?> args) {
        if (args != null) {
            if ("yes".equals(args.get("strip-namespaces"))) {
                options |= IndexConfiguration.STRIP_NAMESPACES;
            }
            if ("yes".equals(args.get("namespace-aware"))) {
                options |= IndexConfiguration.NAMESPACE_AWARE;
            }
        }
        SolrIndexConfig config = new SolrIndexConfig(IndexConfiguration.makeIndexConfiguration (options));
        if (args != null) {
            config.renameFields (args);
            NamedList<String> fields = (NamedList<String>) args.get("fields");
            if (fields != null) {
                config.applyFieldConfiguration(fields);
            }
            NamedList<String> namespaces = (NamedList<String>) args.get("namespaces");
            if (namespaces != null) {
                for (Entry<String,String> ns : namespaces) {
                    config.getIndexConfig().defineNamespaceMapping(ns.getKey(), ns.getValue());
                }
            }
        }
        return config;
    }

    public void applyFieldConfiguration (NamedList<String> fields) {
        if (fields != null) {
            xpathFieldConfig = new NamedList<String>();
            for (Entry<String,String> f : fields) {
                xpathFieldConfig.add(f.getKey(), f.getValue());
            }
        }
    }
    
    private void renameFields (@SuppressWarnings("rawtypes") final NamedList args) {
        NamedList<?> aliases = (NamedList<?>) args.get ("fieldAliases");
        if (aliases == null) {
            return;
        }
        for (int i = 0; i < aliases.size(); i++) {
            String name = aliases.getName(i);
            Object value = aliases.getVal(i);
            if ("xmlFieldName".equals(name)) {
                indexConfig.renameField(indexConfig.getField(FieldName.XML_STORE), value.toString());
                LoggerFactory.getLogger(getClass()).info("XML storage field name: {}", value.toString());
            }
            else if ("uriFieldName".equals(name)) {
                LoggerFactory.getLogger(getClass()).info("URI field name: {}", value.toString());
                indexConfig.renameField(indexConfig.getField(FieldName.URI), value.toString());
            }
            else if ("textFieldName".equals(name)) {
                LoggerFactory.getLogger(getClass()).info("XML text field name: {}", value.toString());
                indexConfig.renameField(indexConfig.getField(FieldName.XML_TEXT), value.toString());
            }
        }
    }

    public void inform(SolrCore core) {
        
        IndexSchema schema = core.getSchema();
        // XML_STORE is not included in the indexer's field list; we just use what came in on the request
        informField (indexConfig.getField(FieldName.XML_STORE), schema);
        for (FieldDefinition xmlField : indexConfig.getFields()) {
            informField (xmlField, schema);
        }
        if (xpathFieldConfig != null) {
            addXPathFields(core.getSchema());
        }
        SchemaField uniqueKeyField = schema.getUniqueKeyField();
        if (uniqueKeyField == null) {
            LoggerFactory.getLogger(getClass()).error("schema does not define any unique field");
        } else if (! uniqueKeyField.getName().equals(indexConfig.getFieldName(FieldName.URI))) {
            LoggerFactory.getLogger(getClass()).error("schema defines a different unique field than the uri field declared in lux configuration");            
        }
        // must call this after making changes to the field map:
        schema.refreshAnalyzers();
        
    }
    
    private void informField (FieldDefinition xmlField, IndexSchema schema) {
        Map<String,SchemaField> fields = schema.getFields();
        Map<String,FieldType> fieldTypes = schema.getFieldTypes();
        Logger logger = LoggerFactory.getLogger(LuxUpdateProcessorFactory.class);
        String fieldName = indexConfig.getFieldName(xmlField);
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
    
    /** Add the xpathFields to the indexConfig using information about the field drawn from the schema. */
    private void addXPathFields(IndexSchema schema) {
        for (Entry<String,String> f : xpathFieldConfig) {
            SchemaField field = schema.getField(f.getKey());
            FieldType fieldType = field.getType();
            if (fieldType == null) {
                throw new SolrException(ErrorCode.SERVER_ERROR, "Field " + f.getKey() + " declared in lux config, but not defined in schema");
            }
            XPathField<String> xpathField = new XPathField<String>(f.getKey(), f.getValue(), fieldType.getAnalyzer(), field.stored() ? Store.YES : Store.NO, Type.STRING);
            
            indexConfig.addField(xpathField);
        }
    }

    private FieldType getFieldType(FieldDefinition xmlField, IndexSchema schema) {
        // FIXME - we should store a field type name in XmlField and just look that up instead
        // of trying to infer from the analyzer
        Analyzer analyzer = xmlField.getAnalyzer();
        String fieldName = indexConfig.getFieldName(xmlField);
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
        
        @Override
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

        @Override
        public Field createField(SchemaField field, Object val, float boost) {
            return (Field) val;
        }
    }

    public IndexConfiguration getIndexConfig() {
        return indexConfig;
    }
}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */