package lux.query;

import static org.junit.Assert.assertEquals;
import lux.index.IndexConfiguration;
import lux.query.parser.LuxQueryParser;

import org.apache.lucene.index.Term;
import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.queryParser.ext.ExtendableQueryParser;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.junit.Before;
import org.junit.Test;

public class LuxParserTest {
    
    private static final String LUX_ATT_TEXT = "lux_att_text";

    private static final String LUX_ELT_TEXT = "lux_elt_text";

    private static final String LUX_TEXT = "lux_text";
    
    private ExtendableQueryParser parser;
    private IndexConfiguration indexConfig;
    
    @Before
    public void setup () {
        indexConfig = new IndexConfiguration();
        parser = LuxQueryParser.makeLuxQueryParser(indexConfig);
    }
    
    @Test
    public void testParseLuxQuery () throws Exception {
        
        // standard Lucene field term
        assertParseQuery (new TermQuery(new Term(LUX_TEXT, "term")), "term");
        assertParseQuery (new TermQuery(new Term("field", "term")), "field:term");

        // full text query
        assertParseQuery (new TermQuery(new Term(LUX_TEXT, "term")), "<:term");
        assertParseQuery (new TermQuery(new Term(LUX_TEXT, "term")), "node<:term");

        // element text query
        assertParseQuery (new TermQuery(new Term(LUX_ELT_TEXT, "element:term")), "<element:term");
        assertParseQuery (new TermQuery(new Term(LUX_ELT_TEXT, "element:term")), "node<element:term");

        // TODO: namespaces
        // assertQuery (new TermQuery(new Term("lux_node", ":element{nsuri}\\:term")), ":ns:element:term");
        // assertQuery (new TermQuery(new Term("lux_node", "element{nsuri}\\:term")), "node:ns:element:term");
    
        // attribute text query
        assertParseQuery (new TermQuery(new Term(LUX_ATT_TEXT, "attribute:term")), "<@attribute:term");
        assertParseQuery (new TermQuery(new Term(LUX_ATT_TEXT, "attribute:term")), "node<@attribute:term");
    }
    
    @Test 
    public void testSerializeQuery () throws Exception {
        // full text query
        assertUnparseQuery ("term", new TermPQuery(new Term(LUX_TEXT, "term")));
        assertUnparseQuery ("field:term", new TermPQuery(new Term("field", "term")));

        // element text query
        assertUnparseQuery ("<element:term", new TermPQuery(new Term(LUX_ELT_TEXT, "element:term")));

        // TODO: namespaces
        // assertQueryString (new TermPQuery(new Term("lux_node", ":element{nsuri}\\:term")), ":ns:element:term");
        // assertQueryString (new TermPQuery(new Term("lux_node", "element{nsuri}\\:term")), "node:ns:element:term");
    
        // attribute text query
        assertUnparseQuery ("<@attribute:term", new TermPQuery(new Term(LUX_ATT_TEXT, "attribute:term")));

        // TODO: Spans, Phrases, etc
    }

    @Test
    public void testParseLuxQueryPhrase () throws Exception {
        // standard Lucene field term
        assertParseQuery (makePhraseQuery (LUX_TEXT, "big", "dog"), "\"big dog\"");
        assertParseQuery (makePhraseQuery ("field", "big", "dog"), "field:\"big dog\"");

        // full text query
        assertParseQuery (makePhraseQuery(LUX_TEXT, "big", "dog"), "<:\"big dog\"");
        assertParseQuery (makePhraseQuery(LUX_TEXT, "big", "dog"), "node<:\"big dog\"");

        // element text query
        assertParseQuery (makePhraseQuery(LUX_ELT_TEXT, "element:big", "element:dog"), "<element:\"big dog\"");
        assertParseQuery (makePhraseQuery(LUX_ELT_TEXT, "element:big", "element:dog"), "node<element:\"big dog\"");

        // TODO: namespaces
        // assertQuery (makePhraseQuery("lux_node", "{nsuri}element:big", "{nsuri}element:dog"), "<ns:element:\"big dog\"");
        // assertQuery (makePhraseQuery("lux_node", "{nsuri}element:big", "{nsuri}element:dog"), "node<ns:element:\"big dog\"");
    
        // attribute text query
        assertParseQuery (makePhraseQuery(LUX_ATT_TEXT, "attribute:big", "attribute:dog"), "<@attribute:\"big dog\"");
        assertParseQuery (makePhraseQuery(LUX_ATT_TEXT, "attribute:big", "attribute:dog"), "node<@attribute:\"big dog\"");        

    }
    
    private PhraseQuery makePhraseQuery(String field, String ... terms) {
        PhraseQuery pq = new PhraseQuery();
        for (String term : terms) {
            pq.add(new Term (field, term));
        }
        return pq;
    }
    
    @Test 
    public void testSerializePhraseQuery () throws Exception {
        // full text query
        assertUnparseQuery ("w(big dog)", makeSpanQuery (LUX_TEXT, 1, true, "big", "dog"));
        assertUnparseQuery ("2w(big dog)", makeSpanQuery (LUX_TEXT, 2, true, "big", "dog"));
        assertUnparseQuery ("n(big dog)", makeSpanQuery (LUX_TEXT, 1, false, "big", "dog"));
        assertUnparseQuery ("2n(big dog)", makeSpanQuery (LUX_TEXT, 2, false, "big", "dog"));
    }

    private ParseableQuery makeSpanQuery(String field, int slop, boolean inOrder, String ... terms) {
        ParseableQuery [] clauses = new ParseableQuery [terms.length];
        for (int i = 0; i < terms.length; i++) {
            clauses[i] = new TermPQuery(new Term (field, terms[i]));
        }
        return new SurroundSpanQuery(slop, inOrder, clauses);
    }
    
    private void assertParseQuery (Query q, String s) throws ParseException {
        assertEquals (q.toString(), parser.parse(s).toString());        
    }
    
    private void assertUnparseQuery(String expected, ParseableQuery q) {
        assertEquals (expected, q.toSurroundString("lux_text", indexConfig));
    }
    
}
