package lux.query;

import static org.junit.Assert.assertEquals;
import lux.query.RangePQuery.Type;

import org.apache.lucene.search.BooleanClause.Occur;
import org.junit.Test;

public class ParseableQueryTest {
    
    @Test
    public void testEquals() {
        for (int i = 0; i <= 12; i++) {
            ParseableQuery iq = makeQuery (i);
            for (int j = 0; j <= 12; j++) {
                ParseableQuery jq = makeQuery (j);
                assertEquals (i==j, iq.equals(jq));
                assertEquals (i==j, jq.equals(iq));
            }
        }
    }
    
    private ParseableQuery makeQuery (int i) {
        switch (i) {
        case 0: return LuxParserTest.makeTermPQuery("field", "word");
        case 1: return LuxParserTest.makeTermPQuery("field2", "word");
        case 2: return LuxParserTest.makeTermPQuery("field", "word2");
        case 3: return LuxParserTest.makeTermPQuery("word");
        case 4: return LuxParserTest.makeBooleanPQuery(Occur.MUST, makeQuery (0), makeQuery(1));
        case 5: return LuxParserTest.makeBooleanPQuery(Occur.SHOULD, makeQuery (0), makeQuery(1));
        case 6: return LuxParserTest.makeBooleanPQuery(Occur.MUST, makeQuery (1), makeQuery(0));
        case 7: return LuxParserTest.makeBooleanPQuery(Occur.MUST, makeQuery (1), makeQuery(2));
        case 8: return LuxParserTest.makeSpanNearPQuery("field", 1, true, "word", "word");
        case 9: return LuxParserTest.makeSpanNearPQuery("field", 1, true, "word", "word2");
        case 10: return LuxParserTest.makeSpanNearPQuery("field", 0, true, "word", "word2");
        case 11: return LuxParserTest.makeNumericRangePQuery("field", Type.INT, "10", "10", true, true);
        case 12: return LuxParserTest.makeNumericRangePQuery("field", Type.INT, "10", "11", true, true);
        }
        return MatchAllPQuery.getInstance();
    }

}
