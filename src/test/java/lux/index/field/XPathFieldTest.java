package lux.index.field;

import static org.junit.Assert.*;
import lux.Evaluator;
import lux.IndexTestSupport;
import lux.QueryStats;
import lux.XdmResultSet;
import lux.index.XmlIndexer;
import lux.index.field.FieldDefinition.Type;
import net.sf.saxon.s9api.XdmSequenceIterator;

import org.apache.lucene.document.Field.Store;
import org.apache.lucene.store.RAMDirectory;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Tests for features related to XPathFields.
 */
public class XPathFieldTest {
    
    private static IndexTestSupport indexTestSupport;
    private static Evaluator eval;
    
    @BeforeClass
    public static void init() throws Exception {
        XmlIndexer indexer = new XmlIndexer();
        indexer.getConfiguration().addField(new XPathField<Integer>("string-length", "string-length(.)", null, Store.YES, Type.INT));
        indexer.getConfiguration().addField(new XPathField<Integer>("string-length-string", "string-length(.)", null, Store.YES, Type.STRING));
        indexer.getConfiguration().addField(new XPathField<Integer>("string-length-long", "string-length(.)", null, Store.YES, Type.LONG));
        indexer.getConfiguration().addField(new XPathField<String>("name", "name(*)", null, Store.YES, Type.STRING));
        // if the root element has a numeric id, index it as an int:
        indexer.getConfiguration().addField(new XPathField<Integer>("id", "*[@id]/string-length()", null, Store.YES, Type.INT));
        indexTestSupport = new IndexTestSupport(indexer, new RAMDirectory());
        indexTestSupport.indexAllElements("lux/reader-test.xml");
        indexTestSupport.reopen(); // commit, make the commit visible
        eval = indexTestSupport.makeEvaluator();
    }
    
    @Before
    public void setup() {
    	eval.setQueryStats(new QueryStats());
    }
    
    @Test
    public void testSortByInt() throws Exception {
        XdmResultSet result = eval.evaluate("count(collection())");
        assertEquals ("5", result.getXdmValue().getUnderlyingValue().getStringValue());
        // sort by XPathField value, as int
        String s1 = getStringResult("for $doc in collection() order by string-length($doc) return name($doc/*)");
        assertEquals("entities entities title token test", s1);
        // retrieving stored field values
        String s2 = getStringResult("for $doc in collection() order by string-length($doc) return $doc/lux:field-values('string-length')");
        assertEquals("2 3 4 16 95", s2);
        // using lux:search sort argument
        String s3 = getStringResult("for $doc in lux:search('*:*', 'string-length int') return name($doc/*)");
        assertEquals("entities entities title token test", s3);
        // in descending order
        String s4 = getStringResult("for $doc in lux:search('*:*', 'string-length int descending') return name($doc/*)");
        assertEquals("test token title entities entities", s4);
    }
    
    @Test
    public void testSortByString() throws Exception {
        String s = getStringResult("for $doc in lux:search('*:*', 'string-length-string string') return lux:field-values('string-length', $doc)");
        assertEquals("16 2 3 4 95", s);
    }
    
    @Test
    public void testSortMixed() throws Exception {
        String s = getStringResult("for $doc in lux:search('*:*', 'name') return name($doc/*)");
        assertEquals("entities entities test title token", s);
        String s2 = getStringResult("for $doc in lux:search('*:*', 'name, string-length int') return lux:field-values('string-length',$doc)");
        assertEquals("2 3 95 4 16", s2);
        String s3 = getStringResult("for $doc in lux:search('*:*', 'name, string-length descending') return lux:field-values('string-length',$doc)");
        assertEquals("3 2 95 4 16", s3);
    }
    
    @Test
    public void testSortMissingValues () throws Exception {
        // default is 'empty least'
        String s = getStringResult("for $doc in lux:search('*:*', 'id int, name ascending') return (name($doc/*), lux:field-values('id', $doc))");
        assertEquals("entities title token entities 2 test 95", s);
        // explicit 'empty least'
        s = getStringResult("for $doc in lux:search('*:*', 'id int, name ascending empty least') return (name($doc/*), lux:field-values('id', $doc))");
        assertEquals("entities title token entities 2 test 95", s);
    }
    
    @Test
    public void testSortEmptyGreatest () throws Exception {
        // 'empty greatest'
        String s1 = getStringResult("for $doc in lux:search('*:*', 'id int empty greatest, name ascending') return (name($doc/*), lux:field-values('id', $doc))");
        assertEquals("entities 2 test 95 entities title token", s1);
        // 'empty greatest' long
        s1 = getStringResult("for $doc in lux:search('*:*', 'string-length-long long empty greatest, name ascending') return (name($doc/*), lux:field-values('string-length-long', $doc))");
        assertEquals("entities 2 entities 3 title 4 token 16 test 95", s1);
    }
    
    @Test
    public void testSortOptimization () throws Exception {
        // order by an int field
    	String s = getStringResult("(for $doc in collection() order by lux:field-values('string-length', $doc) return name($doc/*))[1]");
        assertEquals ("entities", s);
        assertEquals (1, eval.getQueryStats().docCount);
    }
    
    @Test
    public void testSortContextOptimization () throws Exception {
        // order by an int field
    	String s = getStringResult("(for $doc in collection() order by $doc/lux:field-values('string-length') return name($doc/*))[1]");
        assertEquals ("entities", s);
        assertEquals (1, eval.getQueryStats().docCount);
    }
    
    @Test
    public void testSortByConstantFieldValue () throws Exception {
        // order by a constant value that might look the same - ordering by docid:
        // What if we produce two sequences, each ordered by a constant involving two different
        // field-values calls?  They should both be in document order, but the orders will be different...so
        // we do in fact have to enforce *some* consistent ordering, and docid ordering is the only one that
        // makes any sense.
        String name = getStringResult("collection()[1]/ * /name()");
        eval.getQueryStats().docCount = 0;
        String s = getStringResult("(for $doc in collection() order by lux:field-values('id', collection()[1]) return name($doc/*))[1]");
        assertEquals (name, s);
        // each of 5 documents is retrieved once, so they can be sorted, and then the collection()[1] document is retrieved each time:
        // - the collection()[1] is not recognized as a constant by the optimizer
        assertEquals (10, eval.getQueryStats().docCount);
    }
    
    @Test @Ignore
    public void testSortByXPath() throws Exception {
        // order by the source path for the field
    	String s = getStringResult("(for $doc in collection() order by string-length($doc) return name($doc/*))[1]");
        assertEquals ("entities", s);
        assertEquals (1, eval.getQueryStats().docCount);
    }
    
    @Test
    public void testInvalidSortBy() throws Exception {
        // unknown ordering keyword xxx
        XdmResultSet result = eval.evaluate("for $doc in lux:search('*:*', 'string-length-string xxx') return lux:field-values('string-length', $doc)");
        assertEquals (1, result.getErrors().size());
        assertEquals ("lux.exception.LuxException: invalid keyword 'xxx' in: string-length-string xxx", result.getErrors().get(0).getMessage());

        // conflicting ordering keywords "ascending descending"
        result = eval.evaluate("for $doc in lux:search('*:*', 'string-length-string ascending descending') return lux:field-values('string-length', $doc)");
        assertEquals (1, result.getErrors().size());
        assertEquals ("lux.exception.LuxException: too many ordering keywords in: string-length-string ascending descending", result.getErrors().get(0).getMessage());

        // missing keyword after empty
        result = eval.evaluate("for $doc in lux:search('*:*', 'string-length-string empty') return lux:field-values('string-length', $doc)");
        assertEquals (1, result.getErrors().size());
        assertEquals ("lux.exception.LuxException: missing keyword after 'empty' in: string-length-string empty", result.getErrors().get(0).getMessage());
    
        // bad keyword after empty
        result = eval.evaluate("for $doc in lux:search('*:*', 'string-length-string empty blah') return lux:field-values('string-length', $doc)");
        assertEquals (1, result.getErrors().size());
        assertEquals ("lux.exception.LuxException: missing or invalid keyword after 'empty' in: string-length-string empty blah", result.getErrors().get(0).getMessage());
    
        // reverse relevance
        result = eval.evaluate("for $doc in lux:search('*:*', 'lux:score ascending') return lux:field-values('string-length', $doc)");
        assertEquals (1, result.getErrors().size());
        assertEquals ("lux.exception.LuxException: not countenanced: attempt to sort by irrelevance", result.getErrors().get(0).getMessage());
    }

    @Test
    public void testSortByLong() throws Exception {
        // treat int field as long - generates an error
        XdmResultSet result = eval.evaluate("for $doc in lux:search('*:*', 'string-length long') return name($doc/*)");
        assertEquals (1, result.getErrors().size());
        String s = getStringResult("for $doc in lux:search('*:*', 'string-length-long long') return name($doc/*)");
        assertEquals("entities entities title token test", s);
    }

    // return null if no results, else concatenate the string values of all the results,
    // separated by a single space
    private String getStringResult (String query) {
        XdmResultSet result = eval.evaluate(query);
        if (! result.getErrors().isEmpty()) {
            fail(result.getErrors().get(0).getMessage());
        }
        XdmSequenceIterator iter = result.getXdmValue().iterator();
        if (!iter.hasNext()) {
            return null;
        }
        StringBuilder buf = new StringBuilder ();
        buf.append (iter.next().getStringValue());
        while (iter.hasNext()) {
            buf.append (' ');
            buf.append (iter.next().getStringValue());
        }
        return buf.toString();
    }

}
