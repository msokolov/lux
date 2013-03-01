package lux.index.field;

import lux.exception.LuxException;
import lux.index.XmlIndexer;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.search.SortField;
import org.apache.solr.schema.FieldProperties;

/**
 * represents a field in the index corresponding to some XML content.
 * 
 * An XmlField has a name, which may be configured, but must remain the same for all uses
 * of a single index.
 * 
 * XmlField provides methods for retrieving Lucene field configuration, and for retrieving values
 * from the XmlIndexer to be passed to Lucene.
 * 
 * see {@link lux.index.IndexConfiguration} for a list of current built-in fields.
 */
public abstract class FieldDefinition {
    // the default name of the field as it appears in queries, and in the index
    // the XmlIndexer maintains a list of field names so that these intrinsic names
    // can be overridden by configuration
    private final String name;
    
    // indicate whether assumptions are being made about the name of this field.
    // Some fields are treated in a special way so that the names of the Lucene fields
    // can be altered by configuration (for example so as to be compatible with an 
    // existing schema).  Other fields are not expected to be renamed, and their field
    // names are assumed to always have certain value.
    private final boolean renameable;
    
    // a collation for ordering strings - placeholder for future implementation
    // private String collation;
    
    public boolean isRenameable() {
        return renameable;
    }

    /** Represents the type of data fed to the index for a given field.
     * TOKENS-type fields are expected to provide a TokenStream, where the
     * other types provide each values as a Java object.
     */
    public enum Type {
        TOKENS, STRING, BYTES, INT, LONG, TEXT;
        public SortField.Type getLuceneSortFieldType () {
            switch (this) {
            case STRING: return SortField.Type.STRING;
            case INT: return SortField.Type.INT;
            case LONG: return SortField.Type.LONG;
            default: return SortField.Type.DOC; // ignore??
            }
        }
    };
    
    private final Type type;    
    
    // an Analyzer for text fields; if null, the field is not indexed
    private final Analyzer analyzer;

    private final Store isStored;
    
    /**
     * Represents a Solr/Lucene field
     * @param name the name of the field
     * @param analyzer the analyzer associated with the field.  This will
     * be used to analyze string field values, and to analyze queries.  If
     * the field values are not strings (eg if they are a TokenStream), the
     * analyzer is used only for queries.
     * @param isStored whether the field values are to be stored
     * @param type the type of the field values: STRING, TOKENS, INT.
     * @param renameable whether the field is allowed to be renamed
     */
    public FieldDefinition (String name, Analyzer analyzer, Store isStored, Type type, boolean renameable) {
        this.name = name;
        this.analyzer = analyzer;
        this.isStored = isStored;
        this.type = type;
        this.renameable = renameable;
    }
    
    /**
     * construct an non-renameable field
     * @param name the name of the field
     * @param analyzer the analyzer associated with the field.  This will
     * be used to analyze string field values, and to analyze queries.  If
     * the field values are not strings (eg if they are a TokenStream), the
     * analyzer is used only for queries.
     * @param isStored whether the field values are to be stored
     * @param type the type of the field values: STRING, TOKENS, INT.
     */
    public FieldDefinition (String name, Analyzer analyzer, Store isStored, Type type) {
        this (name, analyzer, isStored, type, false);
    }
    
    /** Wraps the values as Field, which includes the values and the Lucene indexing options.
     * Subclasses must implement getValues() or override this method
     * @param indexer the indexer that holds the field values
     * @return the accumulated values of the field, as {@link IndexableField}s
     */
    public Iterable<? extends IndexableField> getFieldValues(XmlIndexer indexer) {
        Iterable<?> values = getValues(indexer);
        if (values == null) {
            throw new LuxException(getClass().getName() + ".getValues() returned null: did you neglect to implement it?");
        }
        return new FieldValues (indexer.getConfiguration(), this, values);
    }


    /** The Solr XmlUpdateProcessor calls this.  If it returns null, the caller should use the values
     * from getFieldValues() instead.
     * @param indexer the indexer that holds the field values
     * @return the accumulated values of the field, as primitive objects (String or Integer). If 
     */
    public Iterable<?> getValues (XmlIndexer indexer) {
        return null;
    }
    
    /** Note that field name uniqueness is not enforced by the API, but if two fields with different 
     * options share the same name, unpredictable behavior will ensue!  This is an historical quirk 
     * of Lucene, which allows
     * indexing a field in different ways at different times without enforcing a consistent schema.
     * @return the unique name of the field, used in queries and when adding values during indexing
     */
    public String getDefaultName () {
        return name;
    }
    
    /**
     * @return The type of data stored in the field.
     */
    public Type getType () {
        return type;
    }

    public Analyzer getAnalyzer() {
        return analyzer;
    }

    public Store isStored() {
        return isStored;
    }
    
    public boolean isSingleValued () {
        return false;
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
        if (!isSingleValued()) {
            options |= 0x200; // MULTIVALUED
        }
        if (type != Type.TOKENS) {
            options |= 0x10; // OMIT_NORMS
        }
        return options;
    }
    
    @Override
    public String toString () {
        return name;
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
