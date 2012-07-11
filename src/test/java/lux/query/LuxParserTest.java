package lux.query;

import static org.junit.Assert.*;
import lux.index.XmlIndexer;
import lux.index.field.XmlField;
import lux.query.parser.LuxQueryParser;

import org.apache.lucene.index.Term;
import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.junit.Test;

public class LuxParserTest {
    
    private static final String LUX_ATT_TEXT = XmlField.ATTRIBUTE_TEXT.getName();

    private static final String LUX_ELT_TEXT = XmlField.ELEMENT_TEXT.getName();

    private static final String LUX_TEXT = XmlField.XML_TEXT.getName();
    
    private LuxQueryParser parser;
    
    @Test
    public void testParseLuxQuery () throws Exception {
        parser = new LuxQueryParser(XmlIndexer.LUCENE_VERSION, "", XmlField.ELEMENT_TEXT.getAnalyzer());
        
        // standard Lucene field term
        assertQuery (new TermQuery(new Term("", "term")), "term");
        assertQuery (new TermQuery(new Term("field", "term")), "field:term");

        // full text query
        assertQuery (new TermQuery(new Term(LUX_TEXT, "term")), "<:term");
        assertQuery (new TermQuery(new Term(LUX_TEXT, "term")), "node<:term");

        // element text query
        assertQuery (new TermQuery(new Term(LUX_ELT_TEXT, "element:term")), "<element:term");
        assertQuery (new TermQuery(new Term(LUX_ELT_TEXT, "element:term")), "node<element:term");

        // TODO: namespaces
        // assertQuery (new TermQuery(new Term("lux_node", ":element{nsuri}\\:term")), ":ns:element:term");
        // assertQuery (new TermQuery(new Term("lux_node", "element{nsuri}\\:term")), "node:ns:element:term");
    
        // attribute text query
        assertQuery (new TermQuery(new Term(LUX_ATT_TEXT, "attribute:term")), "<@attribute:term");
        assertQuery (new TermQuery(new Term(LUX_ATT_TEXT, "attribute:term")), "node<@attribute:term");

    }
    
    @Test
    public void testParseLuxQueryPhrase () throws Exception {
        parser = new LuxQueryParser(XmlIndexer.LUCENE_VERSION, "", XmlField.ELEMENT_TEXT.getAnalyzer());
        
        // standard Lucene field term
        assertQuery (makePhraseQuery ("", "big", "dog"), "\"big dog\"");
        assertQuery (makePhraseQuery ("field", "big", "dog"), "field:\"big dog\"");

        // full text query
        assertQuery (makePhraseQuery(LUX_TEXT, "big", "dog"), "<:\"big dog\"");
        assertQuery (makePhraseQuery(LUX_TEXT, "big", "dog"), "node<:\"big dog\"");

        // element text query
        assertQuery (makePhraseQuery(LUX_ELT_TEXT, "element:big", "element:dog"), "<element:\"big dog\"");
        assertQuery (makePhraseQuery(LUX_ELT_TEXT, "element:big", "element:dog"), "node<element:\"big dog\"");

        // TODO: namespaces
        // assertQuery (makePhraseQuery("lux_node", "{nsuri}element:big", "{nsuri}element:dog"), "<ns:element:\"big dog\"");
        // assertQuery (makePhraseQuery("lux_node", "{nsuri}element:big", "{nsuri}element:dog"), "node<ns:element:\"big dog\"");
    
        // attribute text query
        assertQuery (makePhraseQuery(LUX_ATT_TEXT, "attribute:big", "attribute:dog"), "<@attribute:\"big dog\"");
        assertQuery (makePhraseQuery(LUX_ATT_TEXT, "attribute:big", "attribute:dog"), "node<@attribute:\"big dog\"");        

    }
    
    private PhraseQuery makePhraseQuery(String field, String ... terms) {
        PhraseQuery pq = new PhraseQuery();
        for (String term : terms) {
            pq.add(new Term (field, term));
        }
        return pq;
    }

    // TODO phrase query

    private void assertQuery (Query q, String s) throws ParseException {
        assertEquals (s, q, parser.parse(s));        
    }
}
