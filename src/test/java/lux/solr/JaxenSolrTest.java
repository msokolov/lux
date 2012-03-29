package lux.solr;

import org.junit.Test;

public class JaxenSolrTest extends LuxSolrTest {
    
    public String getXPathEngine () {
        return "jaxen";
    }
    
    public String getSolrSearchPath () {
        return "/xpathj";
    }
    
    @Test public void testAtomicResult () throws Exception {
        assertXPathSearchCount (10, 100, "xs:double", "1.0", "number(/doc/test[1])");
    }
}
