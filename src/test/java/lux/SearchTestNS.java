package lux;

import static org.junit.Assert.*;
import lux.exception.LuxException;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.spans.SpanNearQuery;
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.search.spans.SpanTermQuery;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith (MultiThreadedRunner.class)
public class SearchTestNS extends BaseSearchTest {
    
    @BeforeClass
    public static void setup() throws Exception {
        setup ("lux/reader-test-ns.xml", "lux/reader-test.xml");
        // index.printAllTerms();
    }
    
    @Test
    public void testSearchUnboundNS () throws Exception {
        try {
            assertSearch ("2", "count(lux:search('<x\\:title:test'))", null, 2);
            assertFalse ("Failed to raise exception", true);
        } catch (LuxException e) {
            assertEquals ("Cannot parse '<x\\:title:test': unbound namespace prefix 'x'", e.getMessage());
        }
        // no namespace
        assertSearch ("2", "count(lux:search('<title:test'))", null, 2);
    }

    @Test
    public void testSearchNsUri () throws Exception {
        // namespace uri may be supplied explicitly
        // In Lucene 4.1, slashes must be escaped
        assertSearch ("2", "count(lux:search('<title\\{http\\:\\/\\/lux.net\\{test\\}\\}:test'))", null, 2);
    }
    
    @Test
    public void testSearchBoundNsPrefix() throws Exception {
        // Search string may use prefixes declared in surrounding context
        // This test should be run with a namespace-aware index
        assertSearch ("2", "declare namespace x='http://lux.net{test}'; count(lux:search('<x\\:title:test'))", null, 2);
    }
    
    @Test
    public void testSearchWildcardNamespace () throws Exception {
        // wildcarded namespace
        assertSearch ("4", "count(lux:search('<\\*\\:title:test'))", null, 4);
    }
    
    @Test
    public void testGeneratePathQuery () throws Exception {
        assertSearch ("2", "count(/entities)", null, 2);
        assertSearch ("0", "declare namespace x='x'; count(/x:title)", null, 0);
        // this actually has the correct namespace
        assertSearch ("1", "declare namespace x='http://lux.net{test}'; count(/x:title)", null, 1);
        assertSearch ("1", "declare namespace x='#2'; count(/x:entities)", null, 1);
    }
    
    @Test
    public void testAttributePredicateInPath1() throws Exception {
        // we were generating an incorrect query when an attribute appears in the middle of a path
        // in a predicate: in any case we don't optimize around the variable as we should
        assertSearch ("TEST", "declare variable $id as xs:string external; " +
                "let $test := collection()/test[@id=$id] " +
                "return $test/title/string()", null, 1);
    }
    
    @Test
    public void testAttributePredicateInPath2() throws Exception {
        // Variation on the above - this was generating SpanNear(Boolean(... which is an error
        assertSearch (null, "declare variable $id as xs:string external; " +
                "let $test := collection()/test[@id=$id] " +
                "return $test/title/other/string()", null, 0);
    }
    
    @Test
    public void testAttributePredicate() throws Exception {
        // Verifying that the Lucene query actually works:
        Query q1 = new TermQuery(new Term("lux_att_text", "id:test"));
        TopDocs results = index.searcher.search(q1, 10);
        assertEquals (2, results.totalHits);
        SpanNearQuery q2 = new SpanNearQuery (new SpanQuery[] {
                new SpanTermQuery(new Term("lux_path", "{}")),
                new SpanTermQuery(new Term("lux_path", "test")),
                new SpanTermQuery(new Term("lux_path", "title"))
        }, 0, true);
        results = index.searcher.search(q2, 10);
        assertEquals (1, results.totalHits);
        SpanNearQuery q3 = new SpanNearQuery (new SpanQuery[] {
                new SpanTermQuery(new Term("lux_path", "{}")),
                new SpanTermQuery(new Term("lux_path", "test")),
                new SpanTermQuery(new Term("lux_path", "@id"))
        }, 0, true);
        results = index.searcher.search(q3, 10);
        assertEquals (1, results.totalHits);
        BooleanQuery bq = new BooleanQuery ();
        BooleanQuery bqinner = new BooleanQuery ();
        bqinner.add(q1, Occur.MUST);
        bqinner.add(q3, Occur.MUST);
        bq.add(bqinner, Occur.MUST);
        bq.add(q2, Occur.MUST);
        results = index.searcher.search(bq, 10);
        assertEquals (1, results.totalHits);
        assertEquals (6, results.scoreDocs[0].doc);
        assertSearch ("1", "count (/test)", null, 1);
        assertSearch ("1", "count (/test/@id)", null, 1);
        assertSearch ("test", "/test/@id/string()", null, 1);
        // The actual test:
        // this was throwing a parsing exception
        assertSearch ("1", "count (/test[@id='test']/title)", null, 1);
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
