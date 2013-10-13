package lux.solr;

import java.io.File;

import org.apache.solr.client.solrj.embedded.JettySolrRunner;
import org.apache.solr.client.solrj.embedded.LuxJettySolrRunner;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.cloud.AbstractFullDistribZkTestBase;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.util.NamedList;

/**
 * Basic test of Lux operation in a distributed ("cloud") setup.  Inserts some test
 * documents and performs basic queries: ordered (by docid), sorted by field, and 
 * sorted by relevance.  TODO: Test both query parsers (user-supplied lux:search(string)).
 * 
 * Test is ignored by default because it's slow, and doesn't seem to work properly on
 * travis-ci.org
 */
public class ZkCloudTest extends AbstractFullDistribZkTestBase {
    
    public ZkCloudTest () {
        // shard by hashing the uri
        id = "lux_uri";
        // fixShardCount = true;
        // shardCount = 2;
    }
    
    /**
     * Subclasses can override this to change a test's solr home
     * TODO: understand why we need this, yet properties seem to come from solr/conf/solrconfig.xml??
     */
    @Override
    public String getSolrHome() {
      return "zk-solr";
    }
    
    @Override
    protected String getCloudSolrConfig() {
        return "solrconfig-tlog.xml";
    }
    
    @Override
    public void createServers (int n) throws Exception {
        super.createServers(n);
    }

    @Override
    protected void destroyServers() throws Exception {
        super.destroyServers();
    }

    @Override
    public void doTest() throws Exception {
        del("*:*");
        // exclude certain result components from comparisons:
        initComparisonRegime();
        
        String addTen = "for $i in 1 to 10 " +
                "let $doc := <doc>{$i}</doc> " +
                "return lux:insert(concat('/doc/', $i), $doc)";
        query("qt", "/xquery", "q", addTen);
        query("qt", "/xquery", "q", "count(collection())");
        query("qt", "/xquery", "q", "lux:commit()");
        //query("qt", "/update", "commit", "true");
        query("qt", "/xquery", "q", "count(collection())");
        
        verifyShardCounts(10);

        // delete 
        query ("qt", "/xquery", "q", "(lux:delete('/doc/1'), lux:commit(), count(collection()))");
        verifyShardCounts(9);
        
        // delete all
        query ("qt", "/xquery", "q", "(lux:delete('lux:/'), count(collection()))");
        verifyShardCounts(9);
        query ("qt", "/xquery", "q", "(lux:commit(), count(collection()))");
        verifyShardCounts(0);
        
    }

    private void verifyShardCounts(int expectedTotal) throws Exception {
        int total = 0;
        for (int i = 0; i < this.sliceCount; i++) {
            // query the leader of each slice (aka shard -- the nomenclature is all confused)
            String count = sliceQuery(i, "qt", "/xquery", "q", "count(collection())");
            int c = Integer.valueOf (count);
            // LoggerFactory.getLogger(getClass()).info("shard " + i + " has " + c + " documents");
            total += c;
        }
        // each "slice" has some of the documents, they are replicated into a total number of 
        // indexes == shardCount.
        assertEquals (expectedTotal, total);
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
    
    protected void query (String ... queryParams) throws Exception {

        final ModifiableSolrParams params = params(queryParams);

        QueryResponse rsp = cloudClient.query(params);

        // compare with control response
        params.set("distrib", "false");
        final QueryResponse controlRsp = controlClient.query(params);
        validateControlData(controlRsp);
        compareResponses(rsp, controlRsp);
    }
    
    protected String sliceQuery(int slice, String ... q) throws Exception {
        
        final ModifiableSolrParams params = new ModifiableSolrParams();

        for (int i = 0; i < q.length; i += 2) {
          params.add(q[i], q[i + 1]);
        }
        params.set("distrib", "false");
        CloudJettyRunner jetty  = shardToLeaderJetty.get ("shard" + (slice + 1));
        final QueryResponse rsp = getClient(jetty.coreNodeName).query(params);
        NamedList<?> xpathResults = (NamedList<?>) rsp.getResponse().get("xpath-results");
        assertNotNull ("no xquery results in response: " + rsp.getResponse(), xpathResults);
        return xpathResults.getVal(0).toString();
    }
    
    @Override
    public JettySolrRunner createJetty(File solrHome, String dir, String shardList, String solrConfigOverride, String schemaOverride) throws Exception {

        JettySolrRunner jetty = new LuxJettySolrRunner(solrHome.getAbsolutePath(), context, 0, solrConfigOverride, schemaOverride, false, getExtraServlets());
        jetty.setShards(shardList);
        jetty.setDataDir(getDataDir(dir));
        jetty.start();
        
        return jetty;
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */

