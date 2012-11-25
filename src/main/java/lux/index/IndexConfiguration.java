package lux.index;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

import lux.index.field.AttributeQNameField;
import lux.index.field.AttributeTextField;
import lux.index.field.DocumentField;
import lux.index.field.ElementQNameField;
import lux.index.field.ElementTextField;
import lux.index.field.FieldDefinition;
import lux.index.field.PathField;
import lux.index.field.PathValueField;
import lux.index.field.QNameValueField;
import lux.index.field.URIField;
import lux.index.field.XmlTextField;

import org.apache.lucene.util.Version;

public class IndexConfiguration {

    public static final Version LUCENE_VERSION = Version.LUCENE_34;

    public final static int BUILD_DOCUMENT=     0x00000001;
    public final static int SERIALIZE_XML=      0x00000002;
    public final static int NAMESPACE_UNAWARE=  0x00000004;
    public final static int STORE_XML=          0x00000008;
    public final static int STORE_PTREE=        0x00000010;
    public final static int INDEX_QNAMES=       0x00000020;
    public final static int INDEX_PATHS=        0x00000040;
    public final static int INDEX_FULLTEXT=     0x00000080;
    public final static int INDEX_VALUES=       0x00000100;
    public final static int COMPUTE_OFFSETS=    0x00000200;
    public final static int INDEXES = INDEX_QNAMES | INDEX_PATHS | INDEX_FULLTEXT | INDEX_VALUES;
    public final static int DEFAULT_OPTIONS = STORE_XML | INDEX_QNAMES | INDEX_PATHS | INDEX_FULLTEXT;

    public static final FieldDefinition URI = URIField.getInstance();
    public static final FieldDefinition ELT_QNAME = ElementQNameField.getInstance();
    public static final FieldDefinition ATT_QNAME = AttributeQNameField.getInstance();
    public static final FieldDefinition PATH = PathField.getInstance();
    public static final FieldDefinition ELEMENT_TEXT = ElementTextField.getInstance();
    public static final FieldDefinition ATTRIBUTE_TEXT = AttributeTextField.getInstance();
    public static final FieldDefinition XML_TEXT = XmlTextField.getInstance();
    
    // not fully supported?
    private static final FieldDefinition PATH_VALUE = PathValueField.getInstance();
    private static final FieldDefinition XML_STORE = DocumentField.getInstance();
    private static final FieldDefinition QNAME_VALUE = QNameValueField.getInstance();
    
    private long options;
    
    private final List<FieldDefinition> fields;
    private final HashMap<FieldDefinition, String> fieldNames;
    private MultiFieldAnalyzer fieldAnalyzers;

    public MultiFieldAnalyzer getFieldAnalyzers() {
        return fieldAnalyzers;
    }

    public IndexConfiguration (long options) {
        fields = new ArrayList<FieldDefinition>();
        fieldNames = new HashMap<FieldDefinition, String>();
        fieldAnalyzers = new MultiFieldAnalyzer();
        addField (URI);
        this.options = options;
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
            addField(PATH);
            if (isOption (INDEX_VALUES)) {
                addField(PATH_VALUE);                
            }
        }
        if (isOption (INDEX_FULLTEXT)) {
            addField (XML_TEXT);
            addField (ELEMENT_TEXT);
            addField (ATTRIBUTE_TEXT);
            if (XML_TEXT.getTermVector().withOffsets() || 
                    ELEMENT_TEXT.getTermVector().withOffsets() ||
                    ATTRIBUTE_TEXT.getTermVector().withOffsets()) {
                // We may not need to bother computing offsets at all
                options |= COMPUTE_OFFSETS;
            }
        }
        if (isOption (STORE_XML)) {
            addField(XML_STORE);
        }
    }
    
    public void addField (FieldDefinition field) {
        fields.add(field);
        fieldAnalyzers.put(getFieldName(field), field.getAnalyzer());
    }
    
    public String getFieldName (FieldName field) {
        return getFieldName (field.getField());
    }
    
    public String getFieldName (FieldDefinition field) {
        String alias = fieldNames.get(field);
        if (alias != null) {
            return alias;
        }
        return field.getDefaultName();
    }
    
    public void renameField (FieldDefinition field, String name) {
        if (! field.isRenameable()) {
            throw new IllegalArgumentException("Attempt to rename field " + field + " whose name is fixed");
        }
        fieldNames.put(field, name);
    }

    public Collection<FieldDefinition> getFields () {
        return fields;
    }
    
    public FieldDefinition getField (FieldName fieldName) {
        return fieldName.getField();
    }
    
    /**
     * 
     * @param option an option flag; one of: NAMESPACE_AWARE, STORE_XML, STORE_PTREE, INDEX_QNAMES, INDEX_PATHS, INDEX_FULLTEXT
     * @return whether the option is set
     */
    public boolean isOption (int option) {
        return (options & option) != 0;
    }

    public boolean isIndexingEnabled() {
        return (options & INDEXES) != 0;
    }

}
