package lux.xqts;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Map;

import javax.xml.stream.XMLStreamException;

import lux.api.QueryContext;
import lux.index.XmlIndexer;
import lux.lucene.LuxSearcher;
import lux.saxon.Saxon;
import lux.saxon.Saxon.Dialect;
import lux.saxon.SaxonExpr;
import lux.xpath.QName;
import lux.xqts.TestCase.VariableBinding;
import net.sf.saxon.lib.FeatureKeys;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmAtomicValue;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.s9api.XdmNode;

import org.apache.commons.io.IOUtils;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.store.RAMDirectory;
import org.junit.AfterClass;

public class RunnerBase {

    protected static Catalog catalog;
    private static RAMDirectory dir;
    private static LuxSearcher searcher;
    protected static Saxon eval;
    protected static int collectionSize;
    protected static int numtests;
    protected static int numfailed;
    private static int numignored;
    protected boolean terminateOnException = true;
    protected boolean printDetailedDiagnostics = false;
    
    protected static void setup(int indexOptions, String sourceDirectory) throws Exception {
        eval = new Saxon(Dialect.XQUERY_1);
        eval.getConfig().getParseOptions().setEntityResolver(null);
        dir = new RAMDirectory();
        // This indexer does nothing, the effect of which is to disable Lux search optimizations for
        // absolute expressions depending on the context. This makes it possible to evaluate tests that
        // make use of the dynamic context.  Thus we're only really testing the Translator here, and not
        // the Optimizer or Query components of Lux.
        XmlIndexer indexer = new XmlIndexer (indexOptions);
        catalog = new Catalog ("/users/sokolov/workspace/XQTS_1_0_3", eval.getProcessor());
        indexDirectory (indexer, catalog, sourceDirectory);
        searcher = new LuxSearcher(dir);
        eval.setIndexer (indexer);
        eval.setSearcher(searcher);
        eval.getConfig().setErrorListener(new ErrorIgnorer ());
        eval.getConfig().setConfigurationProperty(FeatureKeys.XQUERY_PRESERVE_NAMESPACES, true);
        eval.getConfig().setConfigurationProperty(FeatureKeys.XQUERY_INHERIT_NAMESPACES, true);
        numtests = 0;
        numignored = 0;
        numfailed = 0;
    }

    private static void indexDirectory(XmlIndexer indexer, Catalog catalog2, String sourceDirectory) throws IOException {
        IndexWriter indexWriter = indexer.getIndexWriter(dir);
        File dir = new File(catalog.getDirectory() + '/' + sourceDirectory);
        int count = 0;
        System.out.println ("indexing test sources...");
        File[] listing = dir.listFiles();
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
                        SaxonExpr expr = (SaxonExpr) eval.compile(text);
                        item = (XdmItem) expr.evaluate(null).iterator().next();
                    } else if (filename.endsWith(".xml")) {
                        item = (XdmNode) eval.getBuilder().build(new InputStreamReader(new FileInputStream (filename)), filename);
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