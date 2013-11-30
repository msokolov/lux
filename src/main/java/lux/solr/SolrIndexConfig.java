package lux.solr;

import static lux.index.IndexConfiguration.*;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ArrayBlockingQueue;

import lux.Compiler;
import lux.exception.LuxException;
import lux.index.FieldRole;
import lux.index.IndexConfiguration;
import lux.index.XmlIndexer;
import lux.index.analysis.DefaultAnalyzer;
import lux.index.analysis.WhitespaceGapAnalyzer;
import lux.index.field.FieldDefinition;
import lux.index.field.FieldDefinition.Type;
import lux.index.field.XPathField;
import lux.index.field.XmlTextField;
import net.sf.saxon.s9api.Serializer;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Store;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrException.ErrorCode;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.core.PluginInfo;
import org.apache.solr.core.SolrCore;
import org.apache.solr.core.SolrInfoMBean;
import org.apache.solr.schema.BinaryField;
import org.apache.solr.schema.CopyField;
import org.apache.solr.schema.FieldType;
import org.apache.solr.schema.IndexSchema;
import org.apache.solr.schema.SchemaField;
import org.apache.solr.schema.StrField;
import org.apache.solr.schema.TextField;
import org.apache.solr.schema.TrieIntField;
import org.apache.solr.schema.TrieLongField;
import org.apache.solr.update.processor.UpdateRequestProcessorChain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Wraps a {@link IndexConfiguration}, adding field definitions from information in Solr's configuration files:
 * solrconfig.xml and schema.xml
 */
public class SolrIndexConfig implements SolrInfoMBean {
    private static final String SOURCE_URL = "https://github.com/msokolov/lux";
    private final IndexConfiguration indexConfig;
    private NamedList<String> xpathFieldConfig;
    private Compiler compiler;
    private ArrayBlockingQueue<XmlIndexer> indexerPool;
    private ArrayBlockingQueue<Serializer> serializerPool;
    private IndexSchema schema;
    
    public SolrIndexConfig (final IndexConfiguration indexConfig) {
        this.indexConfig = indexConfig;
        indexerPool = new ArrayBlockingQueue<XmlIndexer>(8);
        serializerPool = new ArrayBlockingQueue<Serializer>(8);
        // FIXME: possibly we need a pool of compilers as well?  The issue is they hold the Saxon Processor,
        // and that in turn holds uri resolver, which needs to get transient pointers to per-request objects
        // like the searcher, so it can read documents from the index.  ATM different requests will overwrite
        // that pointer in a shared processor.  At the best, this causes some leakage across request (ie transaction)
        // boundaries
        compiler = new Compiler (indexConfig);
    }
    
    public Compiler getCompiler () {
        return compiler;
    }
    
    public XmlIndexer checkoutXmlIndexer () {
        // In tests it didn't seem to make any appreciable difference whether this
        // pool was present or not, but it salves my conscience
        XmlIndexer indexer = indexerPool.poll();
        if (indexer == null) {
            indexer = new XmlIndexer (indexConfig, compiler);
        }
        return indexer;
    }
    
    public void returnXmlIndexer (XmlIndexer doneWithIt) {
        indexerPool.offer(doneWithIt);
        // if the pool was full, we just drop the indexer as garbage
    }
    
    public Serializer checkoutSerializer() {
        Serializer serializer = serializerPool.poll();
        if (serializer == null) {
            serializer = new Serializer();
            serializer.setOutputProperty(Serializer.Property.ENCODING, "utf-8");
            serializer.setOutputProperty(Serializer.Property.BYTE_ORDER_MARK, "no");
            serializer.setOutputProperty(Serializer.Property.OMIT_XML_DECLARATION, "yes");
        }
        return serializer;
    }
    
    public void returnSerializer (Serializer doneWithIt) {
        serializerPool.offer(doneWithIt);
        // if the pool was full, we just drop the serializer
    }
    
    public static SolrIndexConfig registerIndexConfiguration (SolrCore core) {
        // Read the init args from the LuxUpdateProcessorFactory's configuration
        PluginInfo info = core.getSolrConfig().getPluginInfo(UpdateRequestProcessorChain.class.getName());
        String configName = SolrIndexConfig.class.getName();
        SolrInfoMBean configBean = core.getInfoRegistry().get(configName);
        SolrIndexConfig indexConfig;
        if (configBean != null) {
            indexConfig = (SolrIndexConfig) configBean;
        } else {
        	int options = (INDEX_PATHS | INDEX_FULLTEXT | STORE_DOCUMENT | SOLR);
            indexConfig = SolrIndexConfig.makeIndexConfiguration(options, info.initArgs, configName);
            indexConfig.inform(core);
            core.getInfoRegistry().put(configName, indexConfig);
        }
        return indexConfig;
    }

    @SuppressWarnings("unchecked")
    public static SolrIndexConfig makeIndexConfiguration (int options, final NamedList<?> args, String configName) {
        if (args != null) {
            if ("yes".equals(args.get("strip-namespaces"))) {
                options |= STRIP_NAMESPACES;
            }
            if ("yes".equals(args.get("namespace-aware"))) {
                options |= NAMESPACE_AWARE;
            }
            Object format = args.get("xml-format");
            if (format != null) {
            	if ("tiny".equals(format)) {
            		options |= STORE_TINY_BINARY;
            	} else if (! "xml".equals(format)) {
            		throw new LuxException("invalid xml-format: " + format + ", must be one of: (xml,tiny)");
            	}
            }
        }
        IndexConfiguration indexConfig = new IndexConfiguration(options);
        if (args != null) {
            renameFields (indexConfig, args);
        }
        SolrIndexConfig config = new SolrIndexConfig(indexConfig);
        if (args != null) {
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
    
    private static void renameFields (IndexConfiguration indexConfig, @SuppressWarnings("rawtypes") final NamedList args) {
        NamedList<?> aliases = (NamedList<?>) args.get ("fieldAliases");
        if (aliases == null) {
            return;
        }
        for (int i = 0; i < aliases.size(); i++) {
            String name = aliases.getName(i);
            Object value = aliases.getVal(i);
            if ("xmlFieldName".equals(name)) {
                indexConfig.renameField(indexConfig.getField(FieldRole.XML_STORE), value.toString());
                LoggerFactory.getLogger(SolrIndexConfig.class).info("{} XML storage field name: {}", value.toString());
            }
            else if ("uriFieldName".equals(name)) {
                LoggerFactory.getLogger(SolrIndexConfig.class).info("URI field name: {}", value.toString());
                indexConfig.renameField(indexConfig.getField(FieldRole.URI), value.toString());
            }
            else if ("textFieldName".equals(name)) {
                LoggerFactory.getLogger(SolrIndexConfig.class).info("XML text field name: {}", value.toString());
                indexConfig.renameField(indexConfig.getField(FieldRole.XML_TEXT), value.toString());
            }
        }
    }

    public void inform(SolrCore core) {
        schema = core.getSchema();
        // XML_STORE is not listed explicitly by the indexer
        informField (indexConfig.getField(FieldRole.XML_STORE), core);
        // This must be run before informField() registers default analyzers with the Schema
        registerXmlTextFields();
        for (FieldDefinition xmlField : indexConfig.getFields()) {
            informField (xmlField, core);
        }
        if (xpathFieldConfig != null) {
            addXPathFields();
        }
        SchemaField uniqueKeyField = schema.getUniqueKeyField();
        if (uniqueKeyField == null) {
            LoggerFactory.getLogger(getClass()).error("{} schema does not define any unique field", core.getName());
        } else if (! uniqueKeyField.getName().equals(indexConfig.getFieldName(FieldRole.URI))) {
            LoggerFactory.getLogger(getClass()).error("{} schema defines a different unique field than the uri field declared in lux configuration", core.getName());            
        }
        // must call this after making changes to the field map:
        schema.refreshAnalyzers();
    }

    private void registerXmlTextFields() {
        // Update the XML_TEXT analyzer and set up any copyFields 
        String xmlFieldName = indexConfig.getFieldName(FieldRole.XML_TEXT);
        SchemaField schemaField = schema.getFieldOrNull(xmlFieldName);
        Analyzer xmlAnalyzer = null;
        if (schemaField != null) {
            xmlAnalyzer = schemaField.getType().getAnalyzer();
            if (xmlAnalyzer != null) {
                for (FieldRole role : new FieldRole [ ] { FieldRole.XML_TEXT, FieldRole.ELEMENT_TEXT, FieldRole.ATTRIBUTE_TEXT }) {
                    FieldDefinition field = indexConfig.getField(role);
                    field.setAnalyzer(xmlAnalyzer); // this analyzer is used when indexing
                    indexConfig.getFieldAnalyzers().put(field.getName(), xmlAnalyzer); // this analyzer is used when parsing queries
                }
            }
        }
        for (CopyField copyField : schema.getCopyFieldsList(xmlFieldName)) {
            // register fields copied from lux_text with the indexer so that we feed them an XdmNode
            SchemaField destination = copyField.getDestination();
            Analyzer analyzer = destination.getType().getAnalyzer();
            if (analyzer == null) {
                if (xmlAnalyzer != null) {
                    analyzer = xmlAnalyzer; // why would you copy it then?
                } else {
                    analyzer = new DefaultAnalyzer();
                }
            }
            // TODO: should there be additional element and attribute text fields as well?
            XmlTextField xmlCopyField = new XmlTextField (destination.getName(), analyzer);
            indexConfig.addField(xmlCopyField);
        }
    }
    
    private void informField (FieldDefinition xmlField, SolrCore core) {
        Map<String,SchemaField> schemaFields = schema.getFields();
        Map<String,FieldType> fieldTypes = schema.getFieldTypes();
        Logger logger = LoggerFactory.getLogger(LuxUpdateProcessorFactory.class);
        String fieldName = xmlField.getName();
        if (schemaFields.containsKey(fieldName) && xmlField.getType() != Type.TOKENS) {
            // The Solr schema has a definition for this field, and it's not a TOKENS field:
            // TOKENS fields need to install their own special field type since they wrap the
            // analyzer defined by the schema
            return;
        }
        // look up the type of this field using the mapping in this class
        FieldType fieldType = getFieldType(xmlField);
        if (! fieldTypes.containsKey(fieldType.getTypeName())) {
            // The Solr schema does not define this field type, so add it
            logger.info("{} defining fieldType: {}", core.getName(), fieldType.getTypeName());
            fieldTypes.put(fieldType.getTypeName(), fieldType);
        } else {
            fieldType = fieldTypes.get(fieldType.getTypeName());
        }
        // Add the field to the schema
        logger.info(core.getName() + " defining field: {} of type {}", fieldName, fieldType.getTypeName());
        schemaFields.put(fieldName, new SchemaField (fieldName, fieldType, xmlField.getSolrFieldProperties(), ""));
    }
    
    /** Add the xpathFields to the indexConfig using information about the field drawn from the schema. */
    private void addXPathFields() {
        for (Entry<String,String> f : xpathFieldConfig) {
            SchemaField field = schema.getField(f.getKey());
            FieldType fieldType = field.getType();
            if (fieldType == null) {
                throw new SolrException(ErrorCode.SERVER_ERROR, "Field " + f.getKey() + " declared in lux config, but not defined in schema");
            }
            XPathField xpathField = new XPathField(f.getKey(), f.getValue(), fieldType.getAnalyzer(), field.stored() ? Store.YES : Store.NO,
                    Type.TEXT);
            
            indexConfig.addField(xpathField);
        }
    }

    private FieldType getFieldType(FieldDefinition xmlField) {
        // TODO - we should store a field type name in XmlField and just look that up instead
        // of trying to infer from the analyzer
        Analyzer analyzer = xmlField.getAnalyzer();
        String fieldName = xmlField.getName();
        if (analyzer == null) {
            if (! (xmlField.isStored() == Store.YES)) {
                throw new SolrException(ErrorCode.BAD_REQUEST, "invalid xml field: " + fieldName + "; no analyzer and not stored");
            }
            switch (xmlField.getType()) {
            case STRING:
                return new StoredStringField ();
            case INT:
                return new NamedIntField();
            case LONG:
                return new NamedLongField();
            case BYTES:
                return new NamedBinaryField();
            default:
                throw new SolrException (ErrorCode.BAD_REQUEST, "invalid stored field: " + fieldName + " with type: " + xmlField.getType());
            }
        }
        if (xmlField.getType() == Type.TOKENS) {
            return new FieldableField(xmlField);
        }
        if (analyzer == null || analyzer instanceof KeywordAnalyzer) {
            return new StringField();
        }
        if (analyzer instanceof WhitespaceGapAnalyzer) {
            return new PathField ();
        }
        throw new SolrException(ErrorCode.BAD_REQUEST, "invalid xml field: " + fieldName + "; unknown analyzer type: " + analyzer);
    }
    
    public IndexSchema getSchema () {
        return schema;
    }
    
    // subclasses of built-in Solr field types exist purely so we can name them.
    // Is that actually necessary?
    
    class StoredStringField extends StrField {
        StoredStringField () {
            typeName = "lux_stored_string";
        }
    }
    
    class NamedIntField extends TrieIntField {
        public NamedIntField() {
            typeName = "int";
        }
    }
    
    class NamedLongField extends TrieLongField {
        public NamedLongField() {
            typeName = "long";
        }
    }
    
    class NamedBinaryField extends BinaryField {
        public NamedBinaryField() {
            typeName = "binary";
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
        
        /*
         * REVIEW: do we need this?
        @Override
        protected Field.Index getFieldIndex(SchemaField field, String internalVal) {
            return Field.Index.ANALYZED;
        }
        */
        
    }
    
    /**
     * enable pass-through of a Fieldable to Solr; this enables analysis to be performed outside of Solr
     */
    class FieldableField extends TextField {
        
        // TODO: do we need to worry about toInternal()??
        
        FieldableField (FieldDefinition xmlField) {
            typeName = xmlField.getName() + "-fieldable-type";
            this.analyzer = xmlField.getAnalyzer();
            this.queryAnalyzer = xmlField.getAnalyzer();
        }

        @Override
        public Field createField(SchemaField field, Object val, float boost) {
            return (Field) val;
        }
    }

    public IndexConfiguration getIndexConfig() {
        return indexConfig;
    }

    @Override
    public String getName() {
        return SolrIndexConfig.class.getName();
    }

    @Override
    public String getVersion() {
        return "1.0";
    }

    @Override
    public String getDescription() {
        return "Lux index configuration";
    }

    @Override
    public Category getCategory() {
        return Category.OTHER;
    }

    @Override
    public String getSource() {
        return SOURCE_URL;
    }

    private static URL[] docs;
    
    @Override
    public URL[] getDocs() {
        if (docs == null) {
            try {
                docs = new URL [] { new URL(SOURCE_URL) };
            } catch (MalformedURLException e) { }
        }
        return docs;
    }

    @Override
    public NamedList<?> getStatistics() {
        return null;
    }
}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
