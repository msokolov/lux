package lux.solr;

import org.apache.solr.BaseDistributedSearchTestCase;

/**
 * Basic test of Lux operation in a distributed ("cloud") setup.  Inserts some test
 * documents and performs basic queries: ordered (by docid), sorted by field, and 
 * sorted by relevance.  TODO: Test some queries with multiple subqueries.  Test deep
 * pagination (eg retrieve the 250th result). Test both query parsers (user-supplied lux:search(string)).
 * test count() and exists().
 */
public class CloudTest extends BaseDistributedSearchTestCase {
    
    public CloudTest () {
        // shard by hashing the uri
        id = "lux_uri";
        fixShardCount = true;
        shardCount = 2;
    }
    
    @Override
    public void doTest() throws Exception {
        del("*:*");
        
        CloudIndexSupport indexSupport = new CloudIndexSupport(controlClient, clients);
        indexSupport.setDocLimit(300);
        indexSupport.indexAllElements("lux/hamlet.xml");
        
        // set some fields to ignore when comparing query results
        handle.clear();
        handle.put("QTime", SKIPVAL);
        handle.put("timestamp", SKIPVAL);
        handle.put("_version_", SKIPVAL);
        handle.put("shards", SKIP);   // in cloud response only
        handle.put("distrib", SKIP);  // in control only
        handle.put("maxScore", SKIPVAL); // in cloud only 
        // In the cloud, we run a full search and then discard unused docs in the retrieved page,
        // so response.numFound == num docs matching the query.
        // In local operation, we only retrieve as many docs as we need, and terminate the search
        // early, so we don't compute numFound, but just report the number we actually looked at
        // Is this going to be a problem?
        // We can't skip only response.numFound :( but the only other things in there are start
        // and docs[] anyway.
        handle.put("response", SKIP); // in cloud only 
        
        // OK
        query("qt", "/xquery", "q", "/FM");
        query("qt", "/xquery", "q", "(//SPEECH)[250]");
        
        // order by lux:key()
        query ("qt", "/xquery", "q", "(for $sp in //SPEECH order by $sp/lux:key('title') return $sp)[30]");

        // order by value
        query ("qt", "/xquery", "q", "(for $act in /ACT order by $act/@act descending return $act/TITLE)[1]");

        // join two queries 
        query ("qt", "/xquery", "q", "count(for $act in /ACT, $actdesc in //ACT return $act is $actdesc)");
        
        // runtime error (no context item)
        query ("qt", "/xquery", "q", "(for $sp in //SPEECH return .)[30]");

        //  runtime error #2 (no context item), but we get the sortkey problem first
        query ("qt", "/xquery", "q", "(for $sp in //SPEECH order by $sp/lux:key('title') return .)[30]");

        // FIXME: lux:count()
        // query ("qt", "/xquery", "q", "count(collection())");

        // FIXME: lux:exists()
        // query ("qt", "/xquery", "q", "exists(/ACT)");
        
        // FIXME: StackOverflow in net.sf.saxon.expr.ForExpression.optimize()!!!
        // query ("qt", "/xquery", "q", "count(for $act in /ACT, $actdesc in //ACT return $act intersect $actdesc)");

    }

}
