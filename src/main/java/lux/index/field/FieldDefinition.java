package lux.index.field;

import lux.index.XmlIndexer;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.KeywordAnalyzer;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Index;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.Field.TermVector;
import org.apache.solr.schema.FieldProperties;

/**
 * represents a field in the index corresponding to some XML content.
 * 
 * An XmlField has a name, which may be configured, but must remain the sane for all uses
 * of a single index.
 * 
 * XmlField provides methods for retrieving Lucene field configuration, and for retrieving values
 * from the XmlIndexer to be passed to Lucene.
 * 
 * TODO: revise:
 * Built-in fields include QName, Path, and FullText.  We plan to allow for
 * Value and Text fields tied to QName and Path, and eventually also for
 * some xpath indexing, and typed indexes.
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

    // a datatype - placeholder for future implementation; for now everything is STRING
    public enum Type {
        TOKENS, STRING, INT
    };
    
    private final Type type;    
    
    // an Analyzer for text fields; if null, the field is not indexed
    private final Analyzer analyzer;

    private final Store isStored;
    
    private final TermVector termVector;
    
    /**
     * Represents a Solr/Lucene field
     * @param name the name of the field
     * @param analyzer the analyzer associated with the field.  This will be used to analyze string field values,
     * and to analyze queries.  If the field values are not strings (eg if they are a TokenStream), the analyzer is used only for queries. 
     * @param isStored whether the field values are to be stored
     * @param type the type of the field values: STRING, TOKENS, INT.
     */
    public FieldDefinition (String name, Analyzer analyzer, Store isStored, Type type, TermVector termVector, boolean renameable) {
        this.name = name;
        this.analyzer = analyzer;
        this.isStored = isStored;
        this.type = type;
        this.termVector = termVector;
        this.renameable = renameable;
    }
    
    /**
     * construct an non-renameable field
     */
    public FieldDefinition (String name, Analyzer analyzer, Store isStored, Type type, TermVector termVector) {
        this (name, analyzer, isStored, type, termVector, false);
    }
    
    /** Wraps the values as Field, which includes the values and the Lucene indexing options.
     * @param xmlIndexer the indexer that holds the field values
     * @return the accumulated values of the field, as Fieldables
     */
    public abstract Iterable<Field> getFieldValues (XmlIndexer xmlIndexer);

    /** The Solr XmlUpdateProcessor calls this.  If it returns null, the caller should use the values
     * from getFieldValues() instead.
     * @param indexer the indexer that holds the field values
     * @return the accumulated values of the field, as primitive objects
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
     * @return The type of data stored in the field.  This may be STRING, INT, or TOKENS.  TOKENS represent 
     * pre-tokenized fields that define their own analyzers.
     */
    public Type getType () {
        return type;
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
    
    public TermVector getTermVector () {
        return termVector;
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
    
    public String toString () {
        return name;
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
