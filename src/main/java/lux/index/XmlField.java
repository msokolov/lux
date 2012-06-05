package lux.index;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.KeywordAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Index;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.util.Version;
import org.apache.solr.schema.FieldProperties;

/**
 * represents a field in the index corresponding to some XML content.
 * Built-in fields include QName, Path, and FullText.  We plan to allow for
 * Value and Text fields tied to QName and Path, and eventually also for
 * some xpath indexing, and typed indexes.
 */
public class XmlField {
    // the name of the field as it appears in queries, and in the index
    private String name;
    
    // a collation for ordering strings - placeholder for future implementation
    // private String collation;
    
    // a datatype - placeholder for future implementation; for now everything is STRING
    /*
    public enum Type {
        STRING, INT
    };
    private Type type;
    */
    
    // an Analyzer for text fields; if null, the field is not indexed
    private final Analyzer analyzer;

    private final Store isStored;
    
    public XmlField (String name, Analyzer analyzer, Store isStored) {
        this.name = name;
        this.analyzer = analyzer;
        this.isStored = isStored;
    }
    
    // TODO: make uri field unique.  Also - can we fallback and re-use "uri" if it exists???
    public final static XmlField URI = new XmlField ("lux_uri", new KeywordAnalyzer(), Store.YES);
    public final static XmlField ELT_QNAME = new XmlField ("lux_elt_name", new KeywordAnalyzer(), Store.NO);
    public final static XmlField ATT_QNAME = new XmlField ("lux_att_name", new KeywordAnalyzer(), Store.NO);
    public final static XmlField PATH = new XmlField ("lux_path", new WhitespaceGapAnalyzer(), Store.NO);
    public final static XmlField XML_STORE = new XmlField ("lux_xml", null, Store.YES);
    public static final XmlField FULL_TEXT = new XmlField ("lux_text", new StandardAnalyzer(Version.LUCENE_34), Store.YES);

    public String getName() {
        return name;
    }
    
    /**
     * The field name may be changed, so that it can be read from configuration.  However, field names
     * must be stable for a given index installation.
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
