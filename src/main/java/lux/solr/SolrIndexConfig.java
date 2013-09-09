package lux.solr;

import static lux.index.IndexConfiguration.*;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ArrayBlockingQueue;

import lux.Compiler;
import lux.exception.LuxException;
import lux.index.FieldName;
import lux.index.IndexConfiguration;
import lux.index.XmlIndexer;
import lux.index.analysis.WhitespaceGapAnalyzer;
import lux.index.field.FieldDefinition;
import lux.index.field.FieldDefinition.Type;
import lux.index.field.XPathField;
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
        NamedList<?> initArgs = null;
        for (PluginInfo info : core.getSolrConfig().getPluginInfos(UpdateRequestProcessorChain.class.getName())) {
            // FIXME: if there are multiple processors, we prefer the 'default' one, otherwise
            // just take the last?  This is a  bit lame, but it provides back-compat.  We should at least
            // raise a warning if this is ambiguous
            initArgs = info.initArgs;
            if ("true".equals(info.attributes.get("default"))) {
                break;
            }
        }
        SolrInfoMBean configBean = core.getInfoRegistry().get(SolrIndexConfig.class.getName());
        SolrIndexConfig indexConfig;
        if (configBean != null) {
            indexConfig = (SolrIndexConfig) configBean;
        } else {
        	int options = (INDEX_PATHS | INDEX_FULLTEXT | STORE_DOCUMENT | SOLR);
            indexConfig = SolrIndexConfig.makeIndexConfiguration(options, initArgs);
            indexConfig.inform(core);
            core.getInfoRegistry().put(indexConfig.getName(), indexConfig);
        }
        return indexConfig;
    }

    @SuppressWarnings("unchecked")
    public static SolrIndexConfig makeIndexConfiguration (int options, final NamedList<?> args) {
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
        IndexConfiguration indexConfig = IndexConfiguration.makeIndexConfiguration (options);
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
                indexConfig.renameField(indexConfig.getField(FieldName.XML_STORE), value.toString());
                LoggerFactory.getLogger(SolrIndexConfig.class).info("XML storage field name: {}", value.toString());
            }
            else if ("uriFieldName".equals(name)) {
                LoggerFactory.getLogger(SolrIndexConfig.class).info("URI field name: {}", value.toString());
                indexConfig.renameField(indexConfig.getField(FieldName.URI), value.toString());
            }
            else if ("textFieldName".equals(name)) {
                LoggerFactory.getLogger(SolrIndexConfig.class).info("XML text field name: {}", value.toString());
                indexConfig.renameField(indexConfig.getField(FieldName.XML_TEXT), value.toString());
            }
        }
    }

    public void inform(SolrCore core) {

        schema = core.getSchema();
        // XML_STORE is not listed explicitly by the indexer
        informField (indexConfig.getField(FieldName.XML_STORE));
        for (FieldDefinition xmlField : indexConfig.getFields()) {
            informField (xmlField);
        }
        if (xpathFieldConfig != null) {
            addXPathFields();
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
    
    private void informField (FieldDefinition xmlField) {
        Map<String,SchemaField> fields = schema.getFields();
        Map<String,FieldType> fieldTypes = schema.getFieldTypes();
        Logger logger = LoggerFactory.getLogger(LuxUpdateProcessorFactory.class);
        String fieldName = indexConfig.getFieldName(xmlField); // has this field been renamed?
        FieldDefinition actualField = indexConfig.getField(fieldName); // has this field been redefined?
        if (fields.containsKey(fieldName)) {
            // The Solr schema defines this field
            logger.info("Field already defined: " + fieldName);
            // TODO: in this case, construct an (index-time) Analyzer from the 
            // field type found in the schema and install it in the the 
            // xmlField FieldDefinition.
            return;
        }
        // look up the type of this field using the mapping in this class
        FieldType fieldType = getFieldType(actualField);
        if (! fieldTypes.containsKey(fieldType.getTypeName())) {
            // The Solr schema does not define this field type, so add it
            logger.info("Defining fieldType: " + fieldType.getTypeName());
            fieldTypes.put(fieldType.getTypeName(), fieldType);
        } else {
            fieldType = fieldTypes.get(fieldType.getTypeName());
        }
        // Add the field to the schema
        logger.info("Defining field: " + fieldName + " of type " + fieldType.getTypeName());
        fields.put(fieldName, new SchemaField (fieldName, fieldType, actualField.getSolrFieldProperties(), ""));
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
        String fieldName = indexConfig.getFieldName(xmlField);
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

    @Override
    public String getName() {
        return "lux.solr.SolrIndexConfig";
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
