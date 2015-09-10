package lux.solr;

import org.apache.solr.BaseDistributedSearchTestCase;
import org.junit.Ignore;

/**
 * Basic test of Lux operation in a distributed ("cloud") setup.  Inserts some test
 * documents and performs basic queries: ordered (by docid), sorted by field, and
 * sorted by relevance.  TODO: Test both query parsers (user-supplied lux:search(string)).
 */
@Ignore
public class CloudTest extends BaseDistributedSearchTestCase {

    public CloudTest () {
        // shard by hashing the uri
        id = "lux_uri";
        fixShardCount = true;
        shardCount = 2;
    }

    @Override
    public String getSolrHome () {
        return "zk-solr";
    }

    @Override
    public void doTest() throws Exception {
        del("*:*");

        CloudIndexSupport indexSupport = new CloudIndexSupport(controlClient, clients);
        indexSupport.setDocLimit(500);
        indexSupport.indexAllElements("lux/hamlet.xml");

        // exclude certain result components from comparisons:
        initComparisonRegime();

        // In the cloud, we run a full search and then discard unused docs in the retrieved page,
        // so response.numFound == num docs matching the query.
        // In local operation, we only retrieve as many docs as we need, and terminate the search
        // early, so we don't compute numFound, but just report the number we actually looked at
        // Is this going to be a problem?
        // We can't skip only response.numFound :( but the only other things in there are start
        // and docs[] anyway.
        handle.put("response", SKIP); // in cloud only

        // Note the "control" ie the non-cloud index is listed *second* in the failed comparison
        // logged if an assert fails
        // OK
        query("qt", "/xquery", "q", "/FM");

        // Document ordering will only be the same in cloud if timestamps are very exactly synchronized across shards;
        // so this works in test on a single node, but is not guaranteed in general:
        // query("qt", "/xquery", "q", "collection()[250]/base-uri()");
        query("qt", "/xquery", "q", "(for $doc in collection() order by $doc/lux:key('lux_uri')) return $doc)[250]/base-uri()");
        query("qt", "/xquery", "q", "(//SPEECH)[250]");

        // order by lux:key() FIXME
        // query ("qt", "/xquery", "q", "(for $sp in /SPEECH order by $sp/lux:key('title') return $sp)[30]");

        // Test order by int-valued key, and make sure that the order is numeric, not string
        query ("qt", "/xquery", "q", "subsequence(for $doc in collection() order by $doc/lux:key('lux_docid') return $doc/base-uri(), 1, 20)");
        query ("qt", "/xquery", "q", "subsequence(for $doc in collection() order by $doc/lux:key('lux_uri') return $doc/lux:key('title'), 1, 20)");
        query ("qt", "/xquery", "q", "subsequence(for $doc in collection() order by $doc/lux:key('lux_docid') return $doc/lux:key('title'), 1, 20)");
        query ("qt", "/xquery", "q", "(for $doc in collection() order by $doc/lux:key('lux_docid') return $doc/lux:key('title'))");

        // order by value
        query ("qt", "/xquery", "q", "(for $act in /ACT order by $act/@act descending return $act/TITLE)[1]");

        // join two queries
        query ("qt", "/xquery", "q", "count(for $act in /ACT, $actdesc in //ACT return $act is $actdesc)");

        // runtime error (no context item)
        query ("qt", "/xquery", "q", "(for $sp in //SPEECH return .)[30]");

        // runtime error #2 : multi-valued sortkey
        // FIXME: this does not cause an error in the single-server case
        // and it causes a weird exception in the cloud case
        // query ("qt", "/xquery", "q", "(for $sp in //SPEECH order by $sp/lux:key('title_multi') return $sp)[30]");

        // lux:count()
        query ("qt", "/xquery", "q", "count(collection())");
        query ("qt", "/xquery", "q", "count(/SPEECH)");

        // lux:exists()
        query ("qt", "/xquery", "q", "exists(/ACT)");

        // test an expression dependent on document ordering
        // StackOverflow in net.sf.saxon.expr.ForExpression.optimize()!!!  This is filed as Saxon bug #1910; see saxonica.plan.io
        // query ("qt", "/xquery", "q", "count(for $act in /ACT, $actdesc in //ACT return $act intersect $actdesc)");

        // some tests that rely on document identity and ordering:
        query("qt", "/xquery", "q", "count(//SPEECH[contains(., 'philosophy')] intersect /SPEECH[contains(., 'mercy')])");
        query("qt", "/xquery", "q", "count(/ACT/SCENE intersect subsequence(//SCENE, 1, 31))");

        // testing doc():
        query("qt", "/xquery", "q", "doc('lux://lux/hamlet.xml-4')");
        query("qt", "/xquery", "q", "doc('lux://lux/hamlet.xml-10')");
        query("qt", "/xquery", "q", "doc('lux://lux/hamlet.xml-439')");

        // lux:key():
        query("qt", "/xquery", "q", "doc('lux://lux/hamlet.xml-4')/lux:key('lux_uri')");
        query("qt", "/xquery", "q", "doc('lux://lux/hamlet.xml-10')/lux:key('lux_uri')");
        query("qt", "/xquery", "q", "doc('lux://lux/hamlet.xml-10')/lux:key('nonexistent')");
        query("qt", "/xquery", "q", "doc('lux://lux/hamlet.xml-439')/lux:key('title')");
        query("qt", "/xquery", "q", "doc('lux://lux/hamlet.xml-439')/lux:key('actnum')");
        query("qt", "/xquery", "q", "doc('lux://lux/hamlet.xml-439')/lux:key('scnlong')");
        query("qt", "/xquery", "q", "doc('lux://lux/hamlet.xml-439')/lux:key('doctype')");
        query("qt", "/xquery", "q", "doc('lux://lux/hamlet.xml-439')/lux:key('title')");

        // TODO: lux:field-terms()
        query("qt", "/xquery", "q", "subsequence(lux:field-terms('title'), 1, 20)");
        query("qt", "/xquery", "q", "subsequence(lux:field-terms('title'), 200, 300)");
        query("qt", "/xquery", "q", "subsequence(lux:field-terms('title', 'M'), 1, 10)");
        query("qt", "/xquery", "q", "subsequence(lux:field-terms('title', 'M'), 100, 30)");
        query("qt", "/xquery", "q", "lux:field-terms('doctype')");
    }

    private void initComparisonRegime() {
        // set some fields to ignore when comparing query results
        handle.clear();
        handle.put("QTime", SKIPVAL);
        handle.put("timestamp", SKIPVAL);
        handle.put("_version_", SKIPVAL);
        handle.put("shards", SKIP);   // in cloud response only
        handle.put("distrib", SKIP);  // in control only
        handle.put("maxScore", SKIPVAL); // in cloud only
    }

}
