package lux.query;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import lux.index.IndexConfiguration;
import lux.query.parser.LuxQueryParser;

import org.apache.lucene.index.Term;
import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.queryParser.ext.ExtendableQueryParser;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.spans.SpanNearQuery;
import org.apache.lucene.search.spans.SpanOrQuery;
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.search.spans.SpanTermQuery;
import org.junit.Before;
import org.junit.Test;

/**
 * Test parsing, where we take a string and convert it to a set of Lucene queries.
 * 
 * And unparsing, where we take a ParseableQuery (not a Lucene query, but analogous),
 * and generate a string.
 *
 */
public class LuxParserTest {
    
    private static final String LUX_ATT_TEXT = "lux_att_text";
    private static final String LUX_ELT_TEXT = "lux_elt_text";
    private static final String LUX_TEXT = "lux_text";
    private static final String LUX_PATH = "lux_path";
    
    private ExtendableQueryParser parser;
    private IndexConfiguration indexConfig;
    
    @Before
    public void setup () {
        indexConfig = new IndexConfiguration();
        parser = LuxQueryParser.makeLuxQueryParser(indexConfig);
    }
    
    @Test
    public void testEscapeQParser () {
        assertEquals ("", LuxQueryParser.escapeQParser(""));
        assertEquals ("dog", LuxQueryParser.escapeQParser("dog"));
        assertEquals ("big\\:dog", LuxQueryParser.escapeQParser("big:dog"));
        assertEquals ("\\\\\\!\\^\\(\\)\\-\\+\\{\\}\\[\\]\\|\\:\\\"", LuxQueryParser.escapeQParser("\\!^()-+{}[]|:\""));
        assertEquals ("\"Tom & Jerry\"", LuxQueryParser.escapeQParser("Tom & Jerry"));
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
    public void testUnparseQuery () throws Exception {
        // full text query
        assertUnparseQuery ("term", makeTermPQuery("term"));
        assertUnparseQuery ("field:term", makeTermPQuery("field", "term"));
        
        assertUnparseQuery ("<:term", new QNameTextQuery(new Term(LUX_TEXT, "term")));
        // field name is simply ignored when no QName is present:
        assertUnparseQuery ("<:term", new QNameTextQuery(new Term("field", "term")));
        try {
            assertUnparseQuery ("<:term", new QNameTextQuery(new Term("field", "term"), "element"));
            assertFalse (true);
        }  catch (IllegalStateException e) { }
        assertUnparseQuery ("<element:term", new QNameTextQuery(new Term(LUX_ELT_TEXT, "term"), "element"));
        // ERROR:?
        // assertUnparseQuery ("field:term", new QNameTextQuery(new Term("field", "term"), "element"));

        // element text query
        assertUnparseQuery ("lux_elt_text:element\\:term", makeTermPQuery(LUX_ELT_TEXT, "element:term"));

        // TODO: namespaces
        // assertQueryString (new TermPQuery(new Term("lux_node", ":element{nsuri}\\:term")), ":ns:element:term");
        // assertQueryString (new TermPQuery(new Term("lux_node", "element{nsuri}\\:term")), "node:ns:element:term");
    
        // attribute text query
        assertUnparseQuery ("lux_att_text:attribute\\:term", makeTermPQuery(LUX_ATT_TEXT, "attribute:term"));
        assertUnparseQuery ("<@attribute:term", new QNameTextQuery(new Term(LUX_ATT_TEXT, "term"), "attribute"));
    }
    
    @Test
    // Really just tests Lucene QueryParser, and our test code
    public void testParseBooleanQuery () throws Exception {
                BooleanQuery bq = makeBooleanQuery (Occur.MUST, makeTermQuery("big"), makeTermQuery("dog"), 
                makePhraseQuery(LUX_TEXT, "barks", "loud"));
        assertParseQuery (bq, "big AND dog AND \"barks loud\"");
        
        bq = makeBooleanQuery (Occur.MUST, 
                makeBooleanQuery(Occur.SHOULD, makeTermQuery("small"), makeTermQuery("big")),
                makeTermQuery("dog"), makePhraseQuery(LUX_TEXT, "barks", "loud"));
        assertParseQuery (bq, "(small OR big) AND dog AND \"barks loud\""); 
    }

    @Test
    public void testUnparseBooleanQuery () throws Exception {
        assertUnparseQuery ("+big +dog", 
                            new BooleanPQuery (Occur.MUST, makeTermPQuery("big"), makeTermPQuery("dog")));
        assertUnparseQuery ("big dog", 
                            new BooleanPQuery (Occur.SHOULD, makeTermPQuery("big"), makeTermPQuery("dog")));
    }
    
    @Test
    public void testParseMatchAllQuery () throws Exception {
        assertParseQuery (new MatchAllDocsQuery(), "*:*");
        assertUnparseQuery("*:*", new MatchAllPQuery());
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
    
    @Test
    public void testParseSpanMatchAllQuery () throws Exception {
        assertParseQuery (makeSpanTermQuery(LUX_PATH, "{}"), "(lux_within:1 lux_path:\\{\\})");
        assertParseQuery (makeSpanTermQuery(LUX_PATH, "{}"), "(lux_near:1 lux_path:\\{\\})");
        assertParseQuery (makeSpanTermQuery(LUX_PATH, "{}"), "(lux_near:3 lux_path:\\{\\})");
        assertUnparseQuery("lux_path:\\{\\}", new SpanMatchAll());
    }

    @Test
    public void testParseSpanTermQuery () throws Exception {
        // Test the LuxQueryParser extension that generates SpanQueries.
        assertParseQuery (makeSpanTermQuery("field", "value"), "(lux_within:1 field:value)");
        // no special attempt to preserve the "span-ness" is made unless the term query
        // is wrapped in a SpanNearQuery or SpanOrQuery
        assertUnparseQuery ("field:value", makeSpanTermPQuery("field", "value"));
    }
    
    @Test 
    public void testParseSpanQuery () throws Exception {
        assertParseQuery (makeSpanOrQuery (LUX_TEXT, "big", "dog"), "(lux_near:1 big dog)");
        assertParseQuery (makeSpanOrQuery (LUX_TEXT, "big", "dog"), "(lux_near:2 big dog)");
        assertParseQuery (makeSpanOrQuery (LUX_TEXT, "big", "dog"), "(lux_within:1 big dog)");
        assertParseQuery (makeSpanOrQuery (LUX_TEXT, "big", "dog"), "(lux_within:2 big dog)");

        assertParseQuery (makeSpanNearQuery (LUX_TEXT, 1, false, "big", "dog"), "(+lux_near:1 big dog)");
        assertParseQuery (makeSpanNearQuery (LUX_TEXT, 2, false, "big", "dog"), "(+lux_near:2 big dog)");
        assertParseQuery (makeSpanNearQuery (LUX_TEXT, 1, true, "big", "dog"), "(+lux_within:1 big dog)");
        assertParseQuery (makeSpanNearQuery (LUX_TEXT, 2, true, "big", "dog"), "(+lux_within:2 big dog)");
    }

    @Test 
    public void testUnparseSpanQuery () throws Exception {
        assertUnparseQuery ("(+lux_near:1 big dog)", makeSpanNearPQuery (LUX_TEXT, 1, false, "big", "dog"));
        assertUnparseQuery ("(+lux_near:2 big dog)", makeSpanNearPQuery (LUX_TEXT, 2, false, "big", "dog"));
        assertUnparseQuery ("(+lux_within:1 big dog)", makeSpanNearPQuery (LUX_TEXT, 1, true, "big", "dog"));
        assertUnparseQuery ("(+lux_within:2 big dog)", makeSpanNearPQuery (LUX_TEXT, 2, true, "big", "dog"));

        assertUnparseQuery ("(lux_within:1 big dog)", makeSpanOrPQuery (LUX_TEXT, "big", "dog"));
    }

    // query construction helpers:

    private TermQuery makeTermQuery (String text) {
        return makeTermQuery (LUX_TEXT, text);
    }

    private TermQuery makeTermQuery (String field, String text) {
        return new TermQuery (new Term (field, text));
    }
    
    private TermPQuery makeTermPQuery (String text) {
        return makeTermPQuery (LUX_TEXT, text);
    }

    private TermPQuery makeTermPQuery (String field, String text) {
        return new TermPQuery (new Term (field, text));
    }

    private SpanTermQuery makeSpanTermQuery (String field, String text) {
        return new SpanTermQuery (new Term(field, text));
    }
    
    private SpanTermPQuery makeSpanTermPQuery (String field, String text) {
        return new SpanTermPQuery (new Term(field, text));
    }
    
    private BooleanQuery makeBooleanQuery(Occur occur, Query ... queries) {
        BooleanQuery bq = new BooleanQuery ();
        for (Query query : queries) {
            bq.add(query, occur);
        }
        return bq;
    }
    
    private PhraseQuery makePhraseQuery(String field, String ... terms) {
        PhraseQuery pq = new PhraseQuery();
        for (String term : terms) {
            pq.add(new Term (field, term));
        }
        return pq;
    }
    
    private SpanQuery makeSpanOrQuery(String field, String ... terms) {
        SpanQuery [] clauses = new SpanQuery [terms.length];
        for (int i = 0; i < terms.length; i++) {
            clauses[i] = makeSpanTermQuery(field, terms[i]);
        }
        return new SpanOrQuery(clauses);
    }
    
    private SpanQuery makeSpanNearQuery(String field, int slop, boolean inOrder, String ... terms) {
        SpanQuery [] clauses = new SpanQuery [terms.length];
        for (int i = 0; i < terms.length; i++) {
            clauses[i] = makeSpanTermQuery(field, terms[i]);
        }
        return new SpanNearQuery(clauses, slop, inOrder);
    }
    
    private ParseableQuery makeSpanNearPQuery(String field, int slop, boolean inOrder, String ... terms) {
        ParseableQuery [] clauses = new ParseableQuery [terms.length];
        for (int i = 0; i < terms.length; i++) {
            clauses[i] = new SpanTermPQuery(new Term (field, terms[i]));
        }
        return new SpanNearPQuery(slop, inOrder, clauses);
    }
    
    private ParseableQuery makeSpanOrPQuery(String field, String ... terms) {
        ParseableQuery [] clauses = new ParseableQuery [terms.length];
        for (int i = 0; i < terms.length; i++) {
            clauses[i] = new SpanTermPQuery(new Term (field, terms[i]));
        }
        return new SpanBooleanPQuery(Occur.SHOULD, clauses);
    }

    // assertions:

    private void assertParseQuery (Query q, String s) throws ParseException {
        assertEquals (q, parser.parse(s));        
    }
    
    private void assertUnparseQuery(String expected, ParseableQuery q) {
        assertEquals (expected, q.toQueryString("lux_text", indexConfig));
    }
    
}
