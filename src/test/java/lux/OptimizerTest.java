package lux;

import static lux.query.LuxParserTest.makeSpanNearQuery;
import static org.junit.Assert.assertEquals;

import java.io.ByteArrayInputStream;

import lux.BasicQueryTest.SearchExtractor;
import lux.xpath.AbstractExpression;
import lux.xquery.XQuery;

import org.apache.lucene.queryparser.xml.ParserException;
import org.apache.lucene.search.Query;
import org.junit.Before;
import org.junit.Test;

public class OptimizerTest {
    
    private Evaluator eval;
    
    @Before
    public void setup () {
        eval = new Evaluator();
    }

    @Test
    public void testDeepPath () throws Exception {
        // this was failing because SlopCounter terminated at Literals (like the 4 in ACT[4])
        Query expected = makeSpanNearQuery("lux_path", 0, true, "{}", "PLAY", "ACT", "SCENE", "SPEECH", "LINE");
        assertQuery (expected, "string(/PLAY/ACT[4]/SCENE[1]/SPEECH[1]/LINE[3])");
    }

    private void assertQuery(Query expectedOpt, String query) throws ParserException {
        // TODO Auto-generated method stub
        Compiler compiler = eval.getCompiler();
        compiler.compile(query);
        XQuery optimizedQuery = compiler.getLastOptimized();
        AbstractExpression optimizedExpression = optimizedQuery.getBody();
        SearchExtractor extractor = new SearchExtractor();
        optimizedExpression.accept(extractor);
        String xmlQueryString = extractor.queries.get(0).toString();
        Query opt = eval.getXmlQueryParser().parse(new ByteArrayInputStream(xmlQueryString.getBytes())); 
        assertEquals (expectedOpt, opt);
    }
}
