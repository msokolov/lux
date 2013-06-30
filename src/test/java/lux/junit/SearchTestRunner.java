package lux.junit;

import java.io.IOException;

import javax.xml.stream.XMLStreamException;

import lux.Evaluator;
import lux.IndexTestSupport;
import lux.QNameQueryTest;
import lux.index.IndexConfiguration;
import lux.index.XmlIndexer;
import net.sf.saxon.s9api.SaxonApiException;

import org.apache.lucene.store.RAMDirectory;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.model.InitializationError;

public class SearchTestRunner extends QueryTestRunner {
	
    protected IndexTestSupport index;
    protected IndexTestSupport baselineIndex;
    protected XmlIndexer indexer;
    protected XmlIndexer baselineIndexer;
    
    public SearchTestRunner(Class<? extends QNameQueryTest> klass) throws InitializationError {
        super (klass);
    	try {
			setup ();
		} catch (Exception e) {
			throw new InitializationError(e);
		}
    }

    protected QueryTestCase newTestCase (String name, String queryText, QueryTestResult expectedResult) {
		return new SearchTestCase (name, queryText, expectedResult);
	}
    
    public void run (RunNotifier notifier) {
    	try {
    		super.run(notifier);
    	} finally {
    		try {
				tearDown();
			} catch (Exception e) {
				e.printStackTrace();
			}
    	}
    }

    protected void setup () throws XMLStreamException, IOException, SaxonApiException {
        indexer = new XmlIndexer(IndexConfiguration.DEFAULT_OPTIONS);
        index = new IndexTestSupport("lux/hamlet.xml", indexer, new RAMDirectory());
        baselineIndexer = new XmlIndexer(0);
        baselineIndex = index; // it's OK to share the same index - we just ignore the extra fields?
        eval = new Evaluator(eval.getCompiler(), index.getSearcher(), null);

    }
    
    protected void tearDown () throws Exception {
        index.close();
        if (baselineIndex != index) {
            baselineIndex.close();
        }
        // TODO: get this benchmarking stuff working again
        /*
        if (repeatCount > 1) {
            printAllTimes();
        }
        */
    }

    /*
    private static void printAllTimes() {
        int n = baseTimes.size();
        System.out.println(String.format("query\t%s\t%s\t%%change", baseTimes.get(0).condition, testTimes.get(0).condition));
        for (int i = 0; i < n; i++) {
            //System.out.println (baseTimes.get(i));
            //System.out.println (testTimes.get(i));
            System.out.println (testTimes.get(i).comparison(baseTimes.get(i)));
        }
    }

	*/
    
}
