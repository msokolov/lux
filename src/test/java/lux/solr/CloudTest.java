package lux.solr;

import org.apache.solr.BaseDistributedSearchTestCase;
import org.junit.Ignore;

/**
 * Basic test of Lux operation in a distributed ("cloud") setup.  Inserts some test
 * documents and performs basic queries: ordered (by docid), sorted by field, and 
 * sorted by relevance.  TODO: Test some queries with multiple subqueries.  Test deep
 * pagination (eg retrieve the 1000th doc). Test both query parsers (user-supplied lux:search(string)).
 * test count() and exists().
 */
public class CloudTest extends BaseDistributedSearchTestCase {
    
    public CloudTest () {
        // shard by hashing the uri
        id = "lux_uri";
        fixShardCount = true;
        shardCount = 2;
    }

    // FIXME: this test is pretty basic atm.  It just verifies that we have wired everything
    // up OK and we can actually do queries.  Currently the default (shard/docid) document order
    // is not reflected in Saxon's docids, so we will get document ordering problems.  Also, there 
    // is lots of other untested stuff, like actually retrieving documents, sorting, etc
    @Override
    public void doTest() throws Exception {
        del("*:*");
        
        CloudIndexSupport indexSupport = new CloudIndexSupport(controlClient, clients);
        indexSupport.setDocLimit(3);
        indexSupport.indexAllElements("lux/hamlet.xml");
        
        // set some fields to ignore when comparing query results
        handle.clear();
        handle.put("QTime", SKIPVAL);
        handle.put("timestamp", SKIPVAL);
        handle.put("_version_", SKIPVAL);
        handle.put("shards", SKIP);   // in cloud response only
        handle.put("distrib", SKIP);  // in control only
        handle.put("maxScore", SKIPVAL); // in cloud only 

        //query("q", "*:*", "rows", 20);
        query("qt", "/xquery", "q", "/FM");
        //query ("qt", "/xquery", "q", "count(collection())");
    }

}
