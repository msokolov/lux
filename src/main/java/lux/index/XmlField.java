package lux.index;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.KeywordAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Field.Index;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.util.Version;

/**
 * represents a field in the index corresponding to some XML content.
 * Built-in fields include QName, Path, and FullText.  We plan to allow for Value and Text
 * fields tied to QName and Path, and eventually also for some xpath indexing, and typed indexes.
 */
public class XmlField {
    // the name of the field as it appears in queries, and in the index
    private final String name;
    
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
    
    public final static XmlField ELT_QNAME = new XmlField ("lux_elt_name", new KeywordAnalyzer(), Store.NO);
    public final static XmlField ATT_QNAME = new XmlField ("lux_att_name", new KeywordAnalyzer(), Store.NO);
    public final static XmlField PATH = new XmlField ("lux_path", new WhitespaceGapAnalyzer(), Store.NO);
    public final static XmlField XML_STORE = new XmlField ("xml_text", null, Store.YES);
    public static final XmlField FULL_TEXT = new XmlField ("lux_text", new StandardAnalyzer(Version.LUCENE_34), Store.YES);

    public String getName() {
        return name;
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
    
}
