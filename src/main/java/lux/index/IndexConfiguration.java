package lux.index;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import lux.index.analysis.DefaultAnalyzer;
import lux.index.field.AttributeQNameField;
import lux.index.field.AttributeTextField;
import lux.index.field.DocumentField;
import lux.index.field.ElementQNameField;
import lux.index.field.ElementTextField;
import lux.index.field.FieldDefinition;
import lux.index.field.IDField;
import lux.index.field.PathField;
import lux.index.field.PathOccurrenceField;
import lux.index.field.PathValueField;
import lux.index.field.QNameValueField;
import lux.index.field.TinyBinaryField;
import lux.index.field.TinyBinarySolrField;
import lux.index.field.URIField;
import lux.index.field.XmlTextField;
import lux.xml.tinybin.TinyBinary;

import org.apache.lucene.util.Version;

/**
 * Maintains a list of field definitions and index options that inform indexing and search.
 * The configuration options and core indexing setup are immutable, but new fields may be added, 
 * fields may be renamed, and namespace mappings may be defined.
 */
public class IndexConfiguration {

    public static final Version LUCENE_VERSION = Version.LUCENE_44;

    /** causes a document node to be built during indexing. Must be set if any XPathFields are to be defined. */
    public final static int BUILD_DOCUMENT =    0x00000001;
    
    /** Configure for use in solr; eg TinyBinarySolrField instead of TinyBinaryField*/
    public final static int SOLR =              0x00000002;
    
    /** causes QNames indexes to include the full namespace uri.  If not set, QNames are indexed lexically,
     * as {prefix}:{localname} without regard for any prefix mappings.  Currently namespace-unaware indexing
     * and search is not fully supported.
     */
    public final static int NAMESPACE_AWARE =   0x00000004;
    
    /** causes a document to be stored in the index. This should generally always be enabled */
    public final static int STORE_DOCUMENT =    0x00000008;
    
    /** indicates that documents are to be stored in {@link TinyBinary} format.  If this is not set,
     * documents are stored as serialized XML. */
    public final static int STORE_TINY_BINARY =    0x00000010;
    
    /** enables the {@link #ELT_QNAME} and {@link #ATT_QNAME} fields, causing element and attribute 
     * QNames to be indexed.  If paths are indexed, this isn't really needed. */
    public final static int INDEX_QNAMES =      0x00000020;
    
    /** enables the {@link #PATH} field, causing element and attribute QName paths to be indexed. */
    public final static int INDEX_PATHS =       0x00000040;
    
    /** enables the {@link #XML_TEXT}, {@link #ELEMENT_TEXT}, and {@link #ATTRIBUTE_TEXT} fields,
     * causing element and attribute text to be indexed. */
    public final static int INDEX_FULLTEXT =    0x00000080;
    
    /** enables the {@link #PATH_VALUE} field (if INDEX_PATHS is set), and the {@link #QNAME_VALUE} field (if
     * INDEX_QNAMES is set), causing values to be indexed.  This is an experimental feature that is not
     * fully supported.
     */
    public final static int INDEX_VALUES =      0x00000100;

    /** enables the computation and storage of term offsets in the index. Currently there is no reason to enable
     * this flag.  In the future term offsets may be used to accelerate highlighting. */
    public final static int COMPUTE_OFFSETS =   0x00000200;
    
    /** causes all namespace information to be stripped from incoming documents */
    public final static int STRIP_NAMESPACES =  0x00000400;
    
    /** experimental: index each occurrence of each path as an unparsed string,
     * rather than indexing unique paths and tokenizing */
    public final static int INDEX_EACH_PATH = 	0x00000800;
    
    /** mask covering all of the indexing options */
    public final static int INDEXES = INDEX_QNAMES | INDEX_PATHS | INDEX_FULLTEXT | INDEX_VALUES;
    
    /** the default indexing options */
    public final static int DEFAULT_OPTIONS = STORE_DOCUMENT | INDEX_QNAMES | INDEX_PATHS | INDEX_FULLTEXT | NAMESPACE_AWARE;

    /** unique identifier field that identifies a document */
    public static final FieldDefinition URI = URIField.getInstance();
    
    /** field that stores xml documents */
    private static final FieldDefinition XML_STORE = DocumentField.getInstance();

    /** field that stores xml documents */
    private static final FieldDefinition TINY_BINARY_STORE = TinyBinaryField.getInstance();

    /** element QName field */    
    public static final FieldDefinition ELT_QNAME = ElementQNameField.getInstance();

    /** attribute QName field */    
    public static final FieldDefinition ATT_QNAME = AttributeQNameField.getInstance();
    
    /** path field */
    public static final FieldDefinition PATH = PathField.getInstance();

    /** element text field indexes all the text along with element QNames. */
    public static final FieldDefinition ELEMENT_TEXT = ElementTextField.getInstance();

    /** attribute text field indexes all the text along with attribute QNames. */
    public static final FieldDefinition ATTRIBUTE_TEXT = AttributeTextField.getInstance();

    /** full text field that indexes all the text in a document (not including attribute values). */
    public static final FieldDefinition XML_TEXT = XmlTextField.getInstance();
    
    // not fully supported?
    public static final FieldDefinition PATH_VALUE = PathValueField.getInstance();
    public static final FieldDefinition QNAME_VALUE = QNameValueField.getInstance();

    /** The default configuration instance */
    public static final IndexConfiguration DEFAULT = new IndexConfiguration();
    
    private long options;
    
    private final HashMap<String, FieldDefinition> fields;
    private final HashMap<FieldDefinition, String> fieldNames;
    private MultiFieldAnalyzer fieldAnalyzers;
    private final HashMap<String,String> namespaceMap;

    /** @return the analyzers associated with the fields to be indexed */
    public MultiFieldAnalyzer getFieldAnalyzers() {
        return fieldAnalyzers;
    }
    
    /** 
     * @param options
     * @return a new IndexCOnfiguration with the given options, unless the options are the default options,
     * in which case {@link #DEFAULT} is returned.
     */
    public static IndexConfiguration makeIndexConfiguration (long options) {
        long opt = options | NAMESPACE_AWARE;
        if (opt == DEFAULT_OPTIONS) {
            return DEFAULT;
        }
        return new IndexConfiguration(opt);
    }

    protected IndexConfiguration (long options) {
        namespaceMap = new HashMap<String, String>();
        fields = new HashMap<String, FieldDefinition>();
        fieldNames = new HashMap<FieldDefinition, String>();
        fieldAnalyzers = new MultiFieldAnalyzer();
        fieldAnalyzers.put(null, new DefaultAnalyzer());
        addField (URI);
        this.options = options | NAMESPACE_AWARE;
        init();
    }
    
    protected IndexConfiguration () {
        this (DEFAULT_OPTIONS);
    }
    
    private void init () {
        if (isOption (INDEX_QNAMES)) {
            addField(ELT_QNAME);
            addField(ATT_QNAME);
            if (isOption (INDEX_VALUES)) {
                addField(QNAME_VALUE);
            }
        }
        if (isOption (INDEX_PATHS)) {
            if (isOption (INDEX_EACH_PATH)) {
                addField (PathOccurrenceField.getInstance());
            } else {
                addField(PATH);
            }
            if (isOption (INDEX_VALUES)) {
                addField(PATH_VALUE);                
            }
        }
        if (isOption (INDEX_FULLTEXT)) {
            addField (XML_TEXT);
            if (isOption (INDEX_QNAMES) || isOption(INDEX_PATHS)) {
                addField (ELEMENT_TEXT);
                addField (ATTRIBUTE_TEXT);
            }
            /*
            if (// FIXME: do we need offsets ever?  Perhaps if we make use of a better highlighter
                    XML_TEXT.getTermVector().withOffsets() || 
                    ELEMENT_TEXT.getTermVector().withOffsets() ||
                    ATTRIBUTE_TEXT.getTermVector().withOffsets()
                    ) {
                // We may not need to bother computing offsets at all
                options |= COMPUTE_OFFSETS;
            }
            */
        }
        if (isOption (STORE_DOCUMENT)) {
            if (isOption (STORE_TINY_BINARY )) {
                if (isOption(SOLR)) {
                    addField(TinyBinarySolrField.getInstance());
                } else {
                    addField(TINY_BINARY_STORE);
                }
            } else {
            	addField(XML_STORE);
            }
        }
        addField (IDField.getInstance());
    }
    
    /** adds a new field 
     * @param field the field to add
     */
    public void addField (FieldDefinition field) {
        FieldDefinition existing = fields.get(field.getDefaultName());
        if (existing != null) {
            if (existing != field) {
                throw new IllegalStateException ("Duplicate field name: " + field);
            }
            return;
        }
        fields.put(field.getDefaultName(), field);
        fieldAnalyzers.put(getFieldName(field), field.getAnalyzer());
    }
    
    /** 
     * Get the effective name of a field, given its canonical name.  Fields may be renamed, or aliased, for 
     * compatibility with existing schemas.
     * @param field a field's canonical name
     * @return the effective name of the field 
     */
    public String getFieldName (FieldName field) {
        return getFieldName (field.getField());
    }

    /** 
     * Get the effective name of a field, given its definition.  Fields may be renamed, or aliased, for 
     * compatibility with existing schemas.
     * @param field a field definition
     * @return the effective name of the field
     */
    public String getFieldName (FieldDefinition field) {
        String alias = fieldNames.get(field);
        if (alias != null) {
            return alias;
        }
        return field.getDefaultName();
    }
    
    /**
     * rename an existing field; the new name is used in the index.
     * @param field the definition of a field
     * @param name the new name to use
     */
    public void renameField (FieldDefinition field, String name) {
        if (! field.isRenameable()) {
            throw new IllegalArgumentException("Attempt to rename field " + field + " whose name is fixed");
        }
        String currentName = fieldNames.get (field);
        if (currentName == null) {
            currentName = field.getDefaultName();
        }
        if (currentName != null) {
            if (currentName.equals(name)) {
            	return;
            }
            fields.remove(currentName);
        }
        fieldNames.put(field, name);
        fields.put(name, field);
    }

    public Collection<FieldDefinition> getFields () {
        return fields.values();
    }
    
    public FieldDefinition getField (FieldName fieldName) {
        return fieldName.getField();
    }
    
    public FieldDefinition getField (String fieldName) {
        return fields.get(fieldName);
    }
    
    public String getDefaultFieldName () {
        return getFieldName (XML_TEXT);
    }
    
    /**
     * @param option an option bit constant
     * @return whether the option is set
     */
    public boolean isOption (int option) {
        return (options & option) != 0;
    }

    public boolean isIndexingEnabled() {
        return (options & INDEXES) != 0;
    }
    
    public Map<String,String> getNamespaceMap () {
        return Collections.unmodifiableMap(namespaceMap);
    }
    
    public void defineNamespaceMapping (String prefix, String namespaceURI) {
        namespaceMap.put(prefix, namespaceURI);
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
