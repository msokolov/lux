package lux;

import lux.XCompiler.SearchStrategy;

import org.junit.BeforeClass;

public class CollectionSearchTest extends SearchTest {
    
    @BeforeClass
    public static void setup () throws Exception {
        SearchTest.setup();
        index.compiler.setSearchStrategy(SearchStrategy.COLLECTION);
    }
    
}
