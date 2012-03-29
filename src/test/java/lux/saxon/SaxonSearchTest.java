package lux.saxon;

import lux.SearchTest;
import lux.api.Evaluator;

public class SaxonSearchTest extends SearchTest {

    @Override
    public Evaluator getEvaluator() {
        Evaluator eval = new Saxon();
        eval.setContext(new SaxonContext(searcher));
        return eval;
    }

}
