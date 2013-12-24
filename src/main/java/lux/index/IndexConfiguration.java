package lux.index;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import lux.index.analysis.DefaultAnalyzer;
import lux.index.analysis.ElementVisibility;
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

    public static final Version LUCENE_VERSION = Version.LUCENE_46;

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
    
    /** enables the lux_elt_name and lux_att_name fields, causing element and attribute 
     * QNames to be indexed.  If paths are indexed, this isn't really needed. */
    public final static int INDEX_QNAMES =      0x00000020;
    
    /** enables the lux_path field, causing element and attribute QName paths to be indexed. */
    public final static int INDEX_PATHS =       0x00000040;
    
    /** enables the lux_text, lux_elt_text, and lux_att_text fields,
     * causing element and attribute text to be indexed. */
    public final static int INDEX_FULLTEXT =    0x00000080;
    
    /** enables the lux_path_value field (if INDEX_PATHS is set), and the lux_qname_value field (if
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
    public final FieldDefinition URI = new URIField();
    
    /** field that stores xml documents */
    private final FieldDefinition XML_STORE = new DocumentField();

    /** field that stores xml documents */
    private final FieldDefinition TINY_BINARY_STORE = new TinyBinaryField();

    /** element QName field */    
    private final FieldDefinition ELT_QNAME = new ElementQNameField();

    /** attribute QName field */    
    private final FieldDefinition ATT_QNAME = new AttributeQNameField();
    
    /** path field */
    private final FieldDefinition PATH = new PathField();

    /** element text field indexes all the text along with element QNames. */
    private final FieldDefinition ELEMENT_TEXT = new ElementTextField();

    /** attribute text field indexes all the text along with attribute QNames. */
    private final FieldDefinition ATTRIBUTE_TEXT = new AttributeTextField();

    /** full text field that indexes all the text in a document (not including attribute values). */
    private final FieldDefinition XML_TEXT = new XmlTextField();
    
    // not fully supported?
    private final FieldDefinition PATH_VALUE = new PathValueField();
    private final FieldDefinition QNAME_VALUE = new QNameValueField();

    private long options;
    
    private final HashMap<FieldRole, FieldDefinition> fieldsByRole; // maintains which field fulfills a given role
    private final HashMap<String, FieldDefinition> fieldsByName; // map of fields by their lucene field name
    private MultiFieldAnalyzer fieldAnalyzers;
    private final HashMap<String,String> namespaceMap;

    // element visibility
    private HashMap<String,ElementVisibility> eltVis;
    private ElementVisibility defVis;
    
    /** @return the analyzers associated with the fields to be indexed */
    public MultiFieldAnalyzer getFieldAnalyzers() {
        return fieldAnalyzers;
    }
    
    public IndexConfiguration (long options) {
        namespaceMap = new HashMap<String, String>();
        fieldsByRole = new HashMap<FieldRole, FieldDefinition>();
        fieldsByName = new HashMap<String, FieldDefinition>();
        fieldAnalyzers = new MultiFieldAnalyzer();
        fieldAnalyzers.put(null, new DefaultAnalyzer());

        eltVis = new HashMap<String, ElementVisibility>();
        defVis = ElementVisibility.OPAQUE;
        
        addField (URI);
        this.options = options | NAMESPACE_AWARE;
        init();
    }
    
    public IndexConfiguration () {
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
                addField (new PathOccurrenceField());
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
                    addField(new TinyBinarySolrField());
                } else {
                    addField(TINY_BINARY_STORE);
                }
            } else {
            	addField(XML_STORE);
            }
        }
        addField  (new IDField());
    }
    
    /** adds a new field 
     * @param field the field to add
     */
    public void addField (FieldDefinition field) {
        FieldRole role = field.getFieldRole ();
        FieldDefinition existing = null;
        if (role != null) {
            existing = fieldsByRole.get(role);
        }
        if (existing == null) {
            existing = fieldsByName.get(field.getName());
        }
        if (existing != null) {
            if (existing != field) {
                throw new IllegalStateException ("Duplicate field name: " + field);
            }
            return;
        }
        if (role != null) {
            fieldsByRole.put(role, field);
        }
        fieldsByName.put(field.getName(), field);
        // get query analyzer
        fieldAnalyzers.put(field.getName(), field.getQueryAnalyzer());
    }
    
    /** 
     * Get the effective name of a field, given its canonical name.  Fields may be renamed, or aliased, for 
     * compatibility with existing schemas.
     * @param role
     * @return the effective name of the field 
     */
    public String getFieldName (FieldRole role) {
        FieldDefinition field = fieldsByRole.get(role);
        if (field == null) {
            return "";
        }
        return field.getName();
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
        String currentName = field.getName();
        if (currentName.equals(name)) {
            return;
        }
        field.setName(name);
        fieldsByName.remove(currentName);
        fieldsByName.put(name, field);
    }

    /**
     * @return a list of all the fields whose values are to provided by this indexer.
     */
    public Collection<FieldDefinition> getFields () {
        return fieldsByName.values();
    }
    
    public FieldDefinition getField (FieldRole fieldName) {
        return fieldsByRole.get(fieldName);
    }
    
    public FieldDefinition getField (String fieldName) {
        return fieldsByName.get(fieldName);
    }
    
    public String getDefaultFieldName () {
        FieldDefinition textField = fieldsByRole.get(FieldRole.XML_TEXT);
        if (textField != null) {
            return textField.getName();
        }
        return "";
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
    
    public String getUriFieldName () {
        return URI.getName();
    }

    public String getXmlFieldName () {
        return XML_STORE.getName();
    }

    public String getTextFieldName () {
        return XML_TEXT.getName();
    }

    public String getElementTextFieldName () {
        return ELEMENT_TEXT.getName();
    }

    public String getAttributeTextFieldName () {
        return ATTRIBUTE_TEXT.getName();
    }
    
    /**
     * @param clarkName the name of an element in clark-notation: {namespace}local-name, or simply local-name 
     * if the element name is in no namespace.
     * @return the explicitly-specified visibility of the element name, or null if the element has the default
     * visibility.
     */
    public ElementVisibility getElementVisibility (String clarkName) {
        return eltVis.get(clarkName);
    }

    /** sets the visibility of elements with the given name
     * @param clarkName the name of an element in clark-notation: {namespace}local-name, or simply local-name 
     * if the element name is in no namespace.
     * @param vis the visibility of the element's content from the perspective of containing elements.
     * visibility.
     */
    public void setElementVisibility (String clarkName, ElementVisibility vis) {
        eltVis.put(clarkName, vis);
    }

    /** @return the visibility of elements not explicitly specified using setElementVisibility.
     * Always {@link ElementVisibility#OPAQUE}.
     */
    public ElementVisibility getDefaultVisibility() {
        return defVis;
    }
    
    /*
    public void setDefaultVisibility(ElementVisibility vis) {
        this.defVis = vis;
    }
    */

    public Map<String,ElementVisibility> getVisibilityMap () {
        return Collections.unmodifiableMap(eltVis);
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
