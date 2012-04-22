package lux.solr;

public abstract class SaxonSolrTest extends LuxSolrTest {
    public String getXPathEngine () {
        return "saxon";
    }
    
    public String getSolrSearchPath () {
        return "/xpaths";
    }
}
