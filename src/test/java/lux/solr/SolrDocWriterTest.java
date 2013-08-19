package lux.solr;

import org.junit.Test;

public class SolrDocWriterTest extends BaseSolrTest {

    @Test
    public void testWriteDelete () throws Exception {
        for (int i = 1; i <= 10; i++) {
            assertQuery ("OK", "('OK',lux:insert('/doc/" + i + "'," + makeTestDocument(i) + "))");
        }
        assertQuery ("OK", "('OK',lux:commit())");
        assertQuery (10L, "count(collection())");
        
        // This kind of thing sometimes fails now b/c Saxon optimizes 
        // away these functions that return (), but seems to work here.
        // perhaps because their values are being "returned"?
        assertQuery (null, "(lux:delete('/doc/1'),lux:commit())");
        assertQuery (9L, "count(collection())");

        // This fools the optimizer just enough
        assertQuery ("OK", "('OK',lux:delete('/doc/2'),lux:commit())");
        assertQuery (8L, "count(collection())");

        // lux:delete('lux:/') deletes everything.  
        assertQuery ("OK", "('OK',lux:delete('lux:/'),lux:commit())");
        assertQuery (0L, "count(collection())");
    }
    
    private String makeTestDocument(int i) {
        return "<doc><title id='" + i + "'>" + (101-i) + "</title><test>cat</test></doc>";
    }

}