package lux.query;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;

import lux.index.IndexConfiguration;
import lux.index.analysis.DefaultAnalyzer;
import lux.query.parser.LuxQueryParser;
import lux.query.parser.XmlQueryParser;

import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.xml.ParserException;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.NumericRangeQuery;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TermRangeQuery;
import org.apache.lucene.search.WildcardQuery;
import org.apache.lucene.search.spans.SpanNearQuery;
import org.apache.lucene.search.spans.SpanOrQuery;
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.search.spans.SpanTermQuery;
import org.junit.Before;
import org.junit.Ignore;
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
    
    private LuxQueryParser parser;
    private XmlQueryParser xmlQueryParser;
    private IndexConfiguration indexConfig;
    
    @Before
    public void setup () {
        indexConfig = IndexConfiguration.DEFAULT;
        parser = LuxQueryParser.makeLuxQueryParser(indexConfig);
        parser.bindNamespacePrefix("ns", "nsuri");
        xmlQueryParser = new XmlQueryParser("lux_text", new DefaultAnalyzer());
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
    public void testParseTermQuery () throws Exception {
        
        // standard Lucene field term
        assertParseQuery (makeTermQuery(LUX_TEXT, "term"), "term");
        assertParseQuery (makeTermQuery("field", "term"), "field:term");

        assertParseQuery (makeTermQuery(LUX_TEXT, "term", 2), "term^2");
        assertParseQuery (makeTermQuery("field", "term", 3), "field:term^3");

        // full text query
        assertParseQuery (makeTermQuery(LUX_TEXT, "term"), "<:term");
        assertParseQuery (makeTermQuery(LUX_TEXT, "term"), "node<:term");

        // element text query
        assertParseQuery (makeTermQuery(LUX_ELT_TEXT, "element:term"), "<element:term");
        assertParseQuery (makeTermQuery(LUX_ELT_TEXT, "element:term"), "node<element:term");
        assertParseQuery (makeTermQuery(LUX_ELT_TEXT, "element:term", 2), "<element:term^2");
        assertParseQuery (makeTermQuery(LUX_ELT_TEXT, "element:term", 3.5f), "node<element:term^3.5");

        // attribute text query
        assertParseQuery (makeTermQuery(LUX_ATT_TEXT, "attribute:term"), "<@attribute:term");
        assertParseQuery (makeTermQuery(LUX_ATT_TEXT, "attribute:term"), "node<@attribute:term");
    
        // degenerate query
        assertParseQuery (new MatchAllDocsQuery(), "<field:....");
    }
    
    @Test 
    public void testUnparseTermQuery () throws Exception {
        // full text query
        assertUnparseQuery ("lux_text:term", makeTermPQuery("term"));
        assertUnparseQuery ("field:term", makeTermPQuery("field", "term"));

        assertUnparseQuery ("lux_text:term^2.0", makeTermPQuery(LUX_TEXT, "term", 2));
        assertUnparseQuery ("field:term^3.5", makeTermPQuery("field", "term", 3.5f));
        
        assertUnparseQuery ("<:term", new NodeTextQuery(new Term(LUX_TEXT, "term")));
        // field name is simply ignored when no QName is present:
        assertUnparseQuery ("<:term", new NodeTextQuery(new Term("field", "term")));
        try {
            assertUnparseQuery ("<:term", new NodeTextQuery(new Term("field", "term"), "element"));
            assertFalse (true);
        }  catch (IllegalStateException e) { }
        assertUnparseQuery ("<element:term", new NodeTextQuery(new Term(LUX_ELT_TEXT, "term"), "element"));
        // ERROR:?
        // assertUnparseQuery ("field:term", new QNameTextQuery(new Term("field", "term"), "element"));

        // element text query
        assertUnparseQuery ("lux_elt_text:element\\:term", makeTermPQuery(LUX_ELT_TEXT, "element:term"));
    
        // attribute text query
        assertUnparseQuery ("lux_att_text:attribute\\:term", makeTermPQuery(LUX_ATT_TEXT, "attribute:term"));
        assertUnparseQuery ("<@attribute:term", new NodeTextQuery(new Term(LUX_ATT_TEXT, "term"), "attribute"));
    }
    
    @Test
    public void testTermQueryXml () throws Exception {
        assertQueryXMLRoundtrip(makeTermQuery("field:term"), makeTermPQuery("field:term"));
        assertQueryXMLRoundtrip(makeTermQuery("lux_elt_text", "element:term"), makeTermPQuery(LUX_ELT_TEXT, "element:term"));
    }
    
    @Test
    public void testParseNamespaceAware () throws Exception {
        indexConfig = IndexConfiguration.makeIndexConfiguration(IndexConfiguration.NAMESPACE_AWARE);
        parser = LuxQueryParser.makeLuxQueryParser(indexConfig);
        /*
         * If no namespace mapping is found, throw an error
         */
        try {
            assertParseQuery (makeTermQuery("lux_elt_text", "element{nsuri}\\:term"), "<ns\\:element:term");
            assertFalse ("expected exception not thrown", true);
        } catch (ParseException e) {
            assertEquals ("Cannot parse '<ns\\:element:term': unbound namespace prefix 'ns'", e.getMessage());
        }
        parser.bindNamespacePrefix("ns", "nsuri");
        assertParseQuery (makeTermQuery("lux_elt_text", "element{nsuri}:term"), "<ns\\:element:term");
        assertParseQuery (makeTermQuery("lux_elt_text", "element{nsuri}:term"), "node<ns\\:element:term");

        assertUnparseQuery("lux_elt_text:element\\{nsuri\\}\\:term", makeTermPQuery(LUX_ELT_TEXT, "element{nsuri}:term"));
    }
    
    @Test @Ignore("disabled namespace unawareness")
    public void testParseNamespaceUnaware () throws Exception {
        /*
         * Use the prefix when no mapping is found
         */
        assertParseQuery (makeTermQuery("lux_elt_text", "ns:element:term"), "<ns\\:element:term");

        parser.bindNamespacePrefix("ns", "nsuri");
        assertParseQuery (makeTermQuery("lux_elt_text", "element{nsuri}:term"), "<ns\\:element:term");
        assertParseQuery (makeTermQuery("lux_elt_text", "element{nsuri}:term"), "node<ns\\:element:term");

        assertUnparseQuery("lux_elt_text:element\\{nsuri\\}\\:term", makeTermPQuery(LUX_ELT_TEXT, "element{nsuri}:term"));
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
        assertUnparseQuery ("+lux_text:big +lux_text:dog", 
                            new BooleanPQuery (Occur.MUST, makeTermPQuery("big"), makeTermPQuery("dog")));
        assertUnparseQuery ("lux_text:big lux_text:dog", 
                            new BooleanPQuery (Occur.SHOULD, makeTermPQuery("big"), makeTermPQuery("dog")));
        assertUnparseQuery ("+(lux_text:big lux_text:small) +lux_text:dog", 
                new BooleanPQuery (Occur.MUST, new BooleanPQuery (Occur.SHOULD, makeTermPQuery("big"), makeTermPQuery("small")), 
                            makeTermPQuery("dog")));
    }
    
    @Test
    public void testParseMatchAllQuery () throws Exception {
        assertParseQuery (new MatchAllDocsQuery(), "*:*");
        assertUnparseQuery("*:*", new MatchAllPQuery());
    }

    @Test
    public void testParsePhrase () throws Exception {
        // standard Lucene field term
        assertParseQuery (makePhraseQuery (LUX_TEXT, "big", "dog"), "\"big dog\"");
        assertParseQuery (makePhraseQuery ("field", "big", "dog"), "field:\"big dog\"");

        // full text query
        assertParseQuery (makePhraseQuery(LUX_TEXT, "big", "dog"), "<:\"big dog\"");
        assertParseQuery (makePhraseQuery(LUX_TEXT, "big", "dog"), "node<:\"big dog\"");

        // element text query
        assertParseQuery (makePhraseQuery(LUX_ELT_TEXT, "element:big", "element:dog"), "<element:\"big dog\"");
        assertParseQuery (makePhraseQuery(LUX_ELT_TEXT, "element:big", "element:dog"), "node<element:\"big dog\"");

        // namespaces
        assertParseQuery (makePhraseQuery(LUX_ELT_TEXT, "element{nsuri}:big", "element{nsuri}:dog"), "<ns\\:element:\"big dog\"");
        assertParseQuery (makePhraseQuery(LUX_ELT_TEXT, "element{nsuri}:big", "element{nsuri}:dog"), "node<ns\\:element:\"big dog\"");
    
        // attribute text query
        assertParseQuery (makePhraseQuery(LUX_ATT_TEXT, "attribute:big", "attribute:dog"), "<@attribute:\"big dog\"");
        assertParseQuery (makePhraseQuery(LUX_ATT_TEXT, "attribute:big", "attribute:dog"), "node<@attribute:\"big dog\"");
    }
    
    @Test
    public void testUnparsePhrase () throws Exception {
        assertUnparseQuery ("<:\"big dog\"", new NodeTextQuery(new Term(LUX_TEXT, "big dog")));
        assertUnparseQuery ("<element:\"big dog\"", new NodeTextQuery(new Term(LUX_ELT_TEXT, "big dog"), "element"));
    }
    
    @Test
    public void testParseSpanMatchAllQuery () throws Exception {
        assertParseQuery (makeSpanTermQuery(LUX_PATH, "{}"), "(lux_within:1 lux_path:\\{\\})");
        assertParseQuery (makeSpanTermQuery(LUX_PATH, "{}"), "(lux_near:1 lux_path:\\{\\})");
        assertParseQuery (makeSpanTermQuery(LUX_PATH, "{}"), "(lux_near:3 lux_path:\\{\\})");
        assertUnparseQuery("*:*", new SpanMatchAll());
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
        // The parser doesn't generate a degenerate BooleanQuery...
        // assertParseQuery (makeSpanOrQuery (LUX_TEXT), "(lux_near:1)");
        assertParseQuery (makeSpanTermQuery (LUX_TEXT, "big"), "(lux_near:1 big)");
        assertParseQuery (makeSpanOrQuery (LUX_TEXT, "big", "dog"), "(lux_near:1 big dog)");
        assertParseQuery (makeSpanOrQuery (LUX_TEXT, "big", "dog"), "(lux_near:2 big dog)");
        assertParseQuery (makeSpanOrQuery (LUX_TEXT, "big", "dog"), "(lux_within:1 big dog)");
        assertParseQuery (makeSpanOrQuery (LUX_TEXT, "big", "dog"), "(lux_within:2 big dog)");

        assertParseQuery (makeSpanNearQuery (LUX_TEXT, 1, false), "(+lux_near:1)");
        assertParseQuery (makeSpanTermQuery (LUX_TEXT, "big"), "(+lux_near:1 big)");
        assertParseQuery (makeSpanNearQuery (LUX_TEXT, 1, false, "big", "dog"), "(+lux_near:1 big dog)");
        assertParseQuery (makeSpanNearQuery (LUX_TEXT, 2, false, "big", "dog"), "(+lux_near:2 big dog)");
        assertParseQuery (makeSpanNearQuery (LUX_TEXT, 1, true, "big", "dog"), "(+lux_within:1 big dog)");
        assertParseQuery (makeSpanNearQuery (LUX_TEXT, 2, true, "big", "dog"), "(+lux_within:2 big dog)");
    }

    @Test 
    public void testUnparseSpanQuery () throws Exception {
        assertUnparseQuery ("(+lux_near:1 lux_text:big lux_text:dog)", makeSpanNearPQuery (LUX_TEXT, 1, false, "big", "dog"));
        assertUnparseQuery ("(+lux_near:2 lux_text:big lux_text:dog)", makeSpanNearPQuery (LUX_TEXT, 2, false, "big", "dog"));
        assertUnparseQuery ("(+lux_within:1 lux_text:big lux_text:dog)", makeSpanNearPQuery (LUX_TEXT, 1, true, "big", "dog"));
        assertUnparseQuery ("(+lux_within:2 lux_text:big lux_text:dog)", makeSpanNearPQuery (LUX_TEXT, 2, true, "big", "dog"));
        assertUnparseQuery ("(+lux_near:1)", makeSpanNearPQuery (LUX_TEXT, 1, false));

        assertUnparseQuery ("(lux_within:0 lux_text:big lux_text:dog)", makeSpanOrPQuery (LUX_TEXT, "big", "dog"));
        assertUnparseQuery ("(lux_within:0)", makeSpanOrPQuery (LUX_TEXT));
    }
    
    @Test
    public void testParseSpanAndBoolean () throws Exception {
        // test two spans in a boolean
        assertParseQuery (makeBooleanQuery(Occur.MUST, makeSpanNearQuery(LUX_PATH, 0, false, "a", "b"), makeSpanNearQuery(LUX_TEXT, 0, false, "cat", "dog")),
                "+(+lux_near:0 lux_path:a lux_path:b) +(+lux_near:0 lux_text:cat lux_text:dog)");
        assertUnparseQuery ("+lux_path:b +lux_text:dog", makeBooleanPQuery(Occur.MUST, makeSpanTermPQuery(LUX_PATH, "b"), makeSpanTermPQuery(LUX_TEXT, "dog")));
        // test booleans in a span - should throw a parse error
    }
    
    @Test
    public void testParseRangeQuery () throws Exception {
        // test two spans in a boolean
        TermRangeQuery termRangeQuery = makeTermRangeQuery(LUX_PATH, "a", "b", true, true);
        assertParseQuery (termRangeQuery, "lux_path:[a TO b]");
        ParseableQuery termRangePQuery = makeTermRangePQuery(LUX_PATH, "a", "b", true, true);
        assertUnparseQuery ("lux_path:[a TO b]", termRangePQuery);
        assertQueryXMLRoundtrip (termRangeQuery, termRangePQuery);
    }
    
    @Test
    public void testParseNumericRangeQuery () throws Exception {
        // test two spans in a boolean
        NumericRangeQuery<Integer> numericRangeQuery = makeNumericRangeQuery(LUX_PATH, 1, 2, true, false);
        // Lucene's original query parser has no support for numeric range queries.
        // If we want to support this, we would have to switch to the newer "pluggable" query parser
        // assertParseQuery (numericRangeQuery, "lux_path:[1 TO 2}");
        ParseableQuery numericRangePQuery = makeNumericRangePQuery(LUX_PATH, "int", "1", "2", true, false);
        assertUnparseQuery ("lux_path:[1 TO 2}", numericRangePQuery);
        assertQueryXMLRoundtrip (numericRangeQuery, numericRangePQuery);

        numericRangeQuery = makeNumericRangeQuery(LUX_PATH, 1, null, true, false);
        numericRangePQuery = makeNumericRangePQuery(LUX_PATH, "int", "1", null, true, false);
        assertUnparseQuery ("lux_path:[1 TO *}", numericRangePQuery);
        assertQueryXMLRoundtrip (numericRangeQuery, numericRangePQuery);

    }

    // query construction helpers:
    
    public static TermQuery makeTermQuery (String text) {
        return makeTermQuery (LUX_TEXT, text);
    }

    public static TermQuery makeTermQuery (String field, String text) {
        return new TermQuery (new Term (field, text));
    }

    public static Query makeTermQuery(String field, String term, float boost) {
        TermQuery q = makeTermQuery (field, term);
        q.setBoost(boost);
        return q;
    }
    
    public static WildcardQuery makeWildcardQuery (String field, String text) {
        return new WildcardQuery (new Term (field, text));
    }    
    
    public static TermPQuery makeTermPQuery (String text) {
        return makeTermPQuery (LUX_TEXT, text);
    }

    public static TermPQuery makeTermPQuery (String field, String text) {
        return new TermPQuery (new Term (field, text));
    }

    public static TermPQuery makeTermPQuery (String field, String text, float boost) {
        TermPQuery q = new TermPQuery (new Term (field, text), boost);
        return q;
    }

    public static SpanTermQuery makeSpanTermQuery (String field, String text) {
        return new SpanTermQuery (new Term(field, text));
    }
    
    public static SpanTermPQuery makeSpanTermPQuery (String field, String text) {
        return new SpanTermPQuery (new Term(field, text));
    }
    
    public static BooleanQuery makeBooleanQuery(Occur occur, Query ... queries) {
        BooleanQuery bq = new BooleanQuery ();
        for (Query query : queries) {
            bq.add(query, occur);
        }
        return bq;
    }
    
    public static BooleanPQuery makeBooleanPQuery(Occur occur, ParseableQuery ... queries) {
        return new BooleanPQuery (occur, queries);
    }
    
    public static PhraseQuery makePhraseQuery(String field, String ... terms) {
        PhraseQuery pq = new PhraseQuery();
        for (String term : terms) {
            pq.add(new Term (field, term));
        }
        return pq;
    }
    
    public static SpanQuery makeSpanOrQuery(String field, String ... terms) {
        SpanQuery [] clauses = new SpanQuery [terms.length];
        for (int i = 0; i < terms.length; i++) {
            clauses[i] = makeSpanTermQuery(field, terms[i]);
        }
        return new SpanOrQuery(clauses);
    }
    
    public static SpanQuery makeSpanNearQuery(String field, int slop, boolean inOrder, String ... terms) {
        SpanQuery [] clauses = new SpanQuery [terms.length];
        for (int i = 0; i < terms.length; i++) {
            clauses[i] = makeSpanTermQuery(field, terms[i]);
        }
        return new SpanNearQuery(clauses, slop, inOrder);
    }
    
    public static ParseableQuery makeSpanNearPQuery(String field, int slop, boolean inOrder, String ... terms) {
        ParseableQuery [] clauses = new ParseableQuery [terms.length];
        for (int i = 0; i < terms.length; i++) {
            clauses[i] = new SpanTermPQuery(new Term (field, terms[i]));
        }
        return new SpanNearPQuery(slop, inOrder, clauses);
    }
    
    public static ParseableQuery makeSpanOrPQuery(String field, String ... terms) {
        ParseableQuery [] clauses = new ParseableQuery [terms.length];
        for (int i = 0; i < terms.length; i++) {
            clauses[i] = new SpanTermPQuery(new Term (field, terms[i]));
        }
        return new SpanBooleanPQuery(Occur.SHOULD, clauses);
    }
    
    public static TermRangeQuery makeTermRangeQuery(String field, String lower, String upper, boolean includeLower, boolean includeUpper) {
        return TermRangeQuery.newStringRange(field, lower, upper, includeLower, includeUpper);
    }
    
    public static NumericRangeQuery<Integer> makeNumericRangeQuery(String field, Integer lower, Integer upper, boolean includeLower, boolean includeUpper) {
        return NumericRangeQuery.newIntRange(field, lower, upper, includeLower, includeUpper);
    }
    
    public static ParseableQuery makeTermRangePQuery(String field, String lower, String upper, boolean includeLower, boolean includeUpper) {
        return new RangePQuery(field, "string", lower, upper, includeLower, includeUpper);
    }

    public static ParseableQuery makeNumericRangePQuery(String field, String type, String lower, String upper, boolean includeLower, boolean includeUpper) {
        return new RangePQuery(field, type, lower, upper, includeLower, includeUpper);
    }

    // assertions:

    private void assertParseQuery (Query q, String s) throws ParseException {
        assertEquals (q, parser.parse(s));        
    }
    
    private void assertUnparseQuery(String expected, ParseableQuery q) {
        assertEquals (expected, q.toQueryString("lux_text", indexConfig));
    }

    private void assertQueryXMLRoundtrip(Query termRangeQuery, ParseableQuery termRangePQuery) throws ParserException {
        String xmlQuery = termRangePQuery.toXmlNode("lux_text", indexConfig).toString();
        Query q = xmlQueryParser.parse(new ByteArrayInputStream (xmlQuery.getBytes()));
        assertEquals (termRangeQuery, q);
    }

    
}
