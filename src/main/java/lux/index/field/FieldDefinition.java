package lux.index.field;

import lux.exception.LuxException;
import lux.index.FieldRole;
import lux.index.XmlIndexer;
import lux.query.RangePQuery;

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

    private final FieldRole role;
    
    private String name;

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
        TOKENS(SortField.Type.DOC, RangePQuery.Type.STRING), 
        STRING(SortField.Type.STRING, RangePQuery.Type.STRING), 
        BYTES(SortField.Type.BYTES, null), 
        INT(SortField.Type.INT, RangePQuery.Type.INT), 
        LONG(SortField.Type.LONG, RangePQuery.Type.LONG), 
        TEXT(SortField.Type.DOC, RangePQuery.Type.STRING),
        SOLR_FIELD(SortField.Type.STRING, RangePQuery.Type.STRING) // TODO: understand the implications here???
        ;
        
        private SortField.Type sortFieldType;
        private lux.query.RangePQuery.Type rangeTermType;
        
        Type (SortField.Type sortFieldType, lux.query.RangePQuery.Type rangeTermType) {
        	this.sortFieldType = sortFieldType;
        	this.rangeTermType = rangeTermType;
        }
        
        public SortField.Type getLuceneSortFieldType () {
        	return sortFieldType;
        }
        
        public RangePQuery.Type getRangeTermType () {
        	return rangeTermType;
        }
    };
    
    private final Type type;    
    
    // an Analyzer for text fields; if null, the field is not indexed
    private Analyzer analyzer;

    private final Store isStored;
    
    /**
     * Represents a Solr/Lucene field
     * @param role the role of the field; may be null if the field has no special role.
     * @param analyzer the analyzer associated with the field.  This will
     * be used to analyze string field values, and to analyze queries.  If
     * the field values are not strings (eg if they are a TokenStream), the
     * analyzer is used only for queries.
     * @param isStored whether the field values are to be stored
     * @param type the type of the field values: STRING, TOKENS, INT.
     * @param renameable whether the field is allowed to be renamed
     */
    public FieldDefinition (FieldRole role, Analyzer analyzer, Store isStored, Type type, boolean renameable) {
        this.role = role;
        if (role != null) {
            this.name = role.getFieldName();
        }
        this.analyzer = analyzer;
        this.isStored = isStored;
        this.type = type;
        this.renameable = renameable;
        if (analyzer != null && ! (type == Type.STRING || type == Type.TEXT || type == Type.TOKENS || type == Type.SOLR_FIELD)) {
            throw new LuxException ("Unexpected combination of analyzer and field " + name + " of type: " + type);
        }
    }
    
    /**
     * construct a field definition fulfilling a specific role known to the indexer and optimizer
     * 
     * @param role the role of the field
     * @param analyzer the analyzer associated with the field.  This will
     * be used to analyze string field values, and to analyze queries.
     * @param isStored whether the field values are to be stored
     * @param type the type of the field values: STRING, TOKENS, INT.
     */
    public FieldDefinition (FieldRole role, Analyzer analyzer, Store isStored, Type type) {
        this (role, analyzer, isStored, type, false);
    }
    
    /**
     * creates a field definition without any special role
     * @param analyzer
     * @param isStored
     * @param type
     */
    public FieldDefinition(Analyzer analyzer, Store isStored, Type type) {
        this (null, analyzer, isStored, type);
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
        return new FieldValues (this, values);
    }


    /** The Solr XmlUpdateProcessor calls this.  If it returns null, the caller should use the values
     * from getFieldValues() instead.
     * @param indexer the indexer that holds the field values
     * @return the accumulated values of the field, as primitive objects (String or Integer). If 
     */
    public Iterable<?> getValues (XmlIndexer indexer) {
        return null;
    }
    
    /**
     * @return The type of data stored in the field.
     */
    public Type getType () {
        return type;
    }
    
    public void setAnalyzer (Analyzer analyzer) {
        this.analyzer = analyzer;
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
        if (type != Type.BYTES) {
            options |= 1; // INDEXED
        }
        if (analyzer != null) {
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

    /** @return An immutable identifier for the field used to refer to it in code */
    public FieldRole getFieldRole() {
        return role;
    }
    
    /** @return The field name as it appears in queries, and in the index.  Defaults to the FieldName.
    * the XmlIndexer maintains a list of field names so that these intrinsic names
    * can be overridden by configuration
    */
    public String getName () {
        return name;
    }
    
    /** @param luceneFieldName the name of the Lucene field to associate with this definition
     * @see #getName */
    public void setName(String luceneFieldName) {
        this.name = luceneFieldName;
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
