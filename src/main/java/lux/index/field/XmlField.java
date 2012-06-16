package lux.index.field;

import lux.index.XmlIndexer;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.KeywordAnalyzer;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Index;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.Fieldable;
import org.apache.solr.schema.FieldProperties;

/**
 * represents a field in the index corresponding to some XML content.
 * Built-in fields include QName, Path, and FullText.  We plan to allow for
 * Value and Text fields tied to QName and Path, and eventually also for
 * some xpath indexing, and typed indexes.
 */
public abstract class XmlField {
    // the name of the field as it appears in queries, and in the index
    private String name;
    
    // a collation for ordering strings - placeholder for future implementation
    // private String collation;
    
    // a datatype - placeholder for future implementation; for now everything is STRING
    public enum Type {
        TOKENS, STRING, INT
    };
    
    public enum NameKind {
        STATIC, PREFIX
    }
    
    private final Type type;    
    
    // an Analyzer for text fields; if null, the field is not indexed
    private final Analyzer analyzer;

    private final Store isStored;
    
    private final NameKind nameKind;
    
    public XmlField (String name, Analyzer analyzer, Store isStored, Type type) {
        this (name, analyzer, isStored, type, NameKind.STATIC);
    }
    
    public XmlField (String name, Analyzer analyzer, Store isStored, Type type, NameKind nameKind) {
        this.name = name;
        this.analyzer = analyzer;
        this.isStored = isStored;
        this.type = type;
        this.nameKind = nameKind;
    }

    /**
     * @param indexer
     * @return the accumulated values of the field 
     */
    public abstract Iterable<Fieldable> getFieldValues (XmlIndexer indexer);
    
    // TODO: Formalize the relationship of each of these fields to the corresponding StAXHandler that extracts its values.
    // Also come up with a naming convention that makes that pattern clearer.
    // ELT_QNAME, ATT_QNAME, PATH <-> lux.index.XmlPathMapper
    // XML_STORE <-> lux.xml.Serializer
    // PATH_VALUE <-> ? lux.index.XPathValueMapper ?
    // TODO: make uri field unique.  Also - can we fallback and re-use "uri" if it exists???
    public static final XmlField URI = URIField.getInstance();
    public static final XmlField ELT_QNAME = ElementQNameField.getInstance();
    public static final XmlField ATT_QNAME = AttributeQNameField.getInstance();
    public static final XmlField PATH = PathField.getInstance();
    public static final XmlField PATH_VALUE = PathValueField.getInstance();
    public static final XmlField XML_STORE = DocumentField.getInstance();
    public static final XmlField FULL_TEXT = FullTextField.getInstance();
    public static final XmlField QNAME_VALUE = QNameValueField.getInstance();
    public static final XmlField QNAME_TEXT = QNameTextField.getInstance();
    
    /** Note that field name uniqueness is not enforced by the API, but if two fields with different 
     * options share the same name, unpredictable behavior will ensue!  This is an historical quirk 
     * of Lucene, which allows
     * indexing a field in different ways at different times without enforcing a consistent schema.
     * @return the unique name of the field, used in queries and when adding values during indexing
     */
    public String getName () {
        return name;
    }
    
    /**
     * @return The type of data stored in the field.  This may be STRING, INT, or TOKENS.  TOKENS represent 
     * pre-tokenized fields that define their own analyzers.
     */
    public Type getType () {
        return type;
    }
    
    /**
     * The field name may be changed (!), so that they can be read from configuration.  However, field names
     * must be stable for a given index installation.  This function is intended for internal use only.
     * @param name
     */
    public void setName (String name) {
        this.name = name;
    }

    public Analyzer getAnalyzer() {
        return analyzer;
    }

    public Index getIndexOptions() {
        return analyzer != null ? Index.ANALYZED : Index.NOT_ANALYZED;
    }

    public Store isStored() {
        return isStored;
    }
    
    public NameKind getNameKind () {
        return nameKind;
    }
    
    /**
     * Attempts to guess the Solr field properties (see {@link FieldProperties}) based on the available
     * information. Subclasses may need to override to get the correct behavior.  Norms are omitted from
     * all fields; all fields except uri are assumed to be multi-valued.
     * @return the Solr field properties to use when creating a Solr Schema field dynamically
     */
    public int getSolrFieldProperties () {
        int options = 0;
        if (analyzer != null) {
            options |= 1; // INDEXED
            if (analyzer instanceof KeywordAnalyzer) {
                options |= 0x20;    // OMIT_TF_POSITIONS 
                //options |= 0x2000;  // OMIT_POSITIONS
            }
            else {
                options |= 2; // TOKENIZED
            }
        }
        if (isStored == Field.Store.YES) {
            options |= 4; // STORED
        }
        if (this != URI) {
            options |= 0x200; // MULTIVALUED
        }
        // TODO: when we have some text fields we will want to retain norms for them
        options |= 0x10; // OMIT_NORMS
        return options;
    }
    
    public String toString () {
        return getName();
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
