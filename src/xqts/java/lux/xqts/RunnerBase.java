package lux.xqts;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Map;

import javax.xml.stream.XMLStreamException;

import lux.Compiler;
import lux.Evaluator;
import lux.QueryContext;
import lux.index.IndexConfiguration;
import lux.index.XmlIndexer;
import lux.search.LuxSearcher;
import lux.xml.QName;
import lux.xqts.TestCase.VariableBinding;
import net.sf.saxon.Configuration;
import net.sf.saxon.lib.FeatureKeys;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmAtomicValue;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.s9api.XdmValue;

import org.apache.commons.io.IOUtils;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.store.RAMDirectory;
import org.junit.AfterClass;

public class RunnerBase {

    protected static Catalog catalog;
    private static RAMDirectory dir;
    private static LuxSearcher searcher;
    protected static Evaluator eval;
    protected static Configuration saxonConfig;
    protected static Processor processor;
    protected static int collectionSize;
    protected static int numtests;
    protected static int numfailed;
    private static int numignored;
    protected boolean terminateOnException = true;
    protected boolean printDetailedDiagnostics = false;
    // ignore test results - just time the running 
    protected boolean benchmark = false;
    // use Saxon without any Lux optimization
    protected boolean benchmarkSaxon = false;
    
    protected static void setup(int indexOptions, String sourceDirectory) throws Exception {
        dir = new RAMDirectory();
        IndexConfiguration indexConfig = IndexConfiguration.makeIndexConfiguration(indexOptions);
        Compiler compiler = new Compiler (indexConfig);
        searcher = new LuxSearcher(dir);
        eval = new Evaluator(compiler, searcher, null);
        processor = eval.getCompiler().getProcessor();
        saxonConfig = processor.getUnderlyingConfiguration();
        saxonConfig.getParseOptions().setEntityResolver(null);
        catalog = new Catalog ("/users/sokolov/workspace/XQTS_1_0_3", processor);
        XmlIndexer indexer = new XmlIndexer (indexConfig);
        indexDirectory (indexer, catalog, sourceDirectory);
        saxonConfig.setErrorListener(new ErrorIgnorer ());
        saxonConfig.setConfigurationProperty(FeatureKeys.XQUERY_PRESERVE_NAMESPACES, true);
        saxonConfig.setConfigurationProperty(FeatureKeys.XQUERY_INHERIT_NAMESPACES, true);
        numtests = 0;
        numignored = 0;
        numfailed = 0;
    }

    private static void indexDirectory(XmlIndexer indexer, Catalog catalog2, String sourceDirectory) throws IOException {
        IndexWriter indexWriter = indexer.getIndexWriter(dir);
        File catalogSourceDir = new File(catalog.getDirectory() + '/' + sourceDirectory);
        int count = 0;
        System.out.println ("indexing test sources...");
        File[] listing = catalogSourceDir.listFiles();
        // swap the order - Saxon iterates over directories in descending alpha order? and we need to match that
        // so the results will be comparable
        /*for (int i = 0; i < listing.length/2; i++) {
            File swap = listing[i];
            listing[i] = listing [listing.length - i - 1];
            listing [listing.length - i - 1] = swap;
        }*/
        for (File source : listing) {
            if (! source.getName().endsWith(".xml")) {
                // skip the dtds and schemas and xquery files
                continue;
            }
            try {
                indexer.indexDocument (indexWriter, source.getPath(), new FileInputStream(source));
            } catch (XMLStreamException e) {
                System.err.println ("Failed to index " + source.getPath() + ": " + e.getMessage());
            }
            ++count;
        }
        collectionSize = count;
        System.out.println ("indexed " + count + " documents");
        indexWriter.commit();
        indexWriter.close();
    }

    @AfterClass
    public static void cleanup() throws Exception {
        //searcher.close();
        //dir.close();
        System.out.println ("Ran " + numtests + " tests");
        System.out.println (numfailed + " tests failed; " + numignored + " ignored");
    }

    protected void bindExternalVariables(TestCase test1, QueryContext context) throws IOException, FileNotFoundException, SaxonApiException {
        if (test1.getExternalVariables() != null) {
            for (Map.Entry<String,VariableBinding> binding : test1.getExternalVariables().entrySet()) {
                String filename = binding.getValue().value;
                XdmItem item;
                if (binding.getValue().type == VariableBinding.Type.FILE) {
                    if (filename.endsWith(".xq")) {
                        String text = IOUtils.toString (new FileInputStream(filename));
                        XdmValue result = eval.evaluate(text).getXdmValue();
                        item = (XdmItem) result.iterator().next();
                    } else if (filename.endsWith(".xml")) {
                        item = eval.build(new InputStreamReader(new FileInputStream (filename)), filename);
                    } else {
                        item = new XdmAtomicValue(IOUtils.toString(new FileInputStream (filename)));
                    }
                } else {
                    item = new XdmAtomicValue(filename);
                }
                context.bindVariable(new QName(binding.getKey()), item);                 
            }
        }
    }
    
}