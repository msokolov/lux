package lux;

import java.io.IOException;
import java.io.StringReader;

import lux.compiler.PathOptimizer;
import lux.compiler.SaxonTranslator;
import lux.exception.LuxException;
import lux.functions.Commit;
import lux.functions.DeleteDocument;
import lux.functions.FieldTerms;
import lux.functions.FieldValues;
import lux.functions.InsertDocument;
import lux.functions.LuxCount;
import lux.functions.LuxExists;
import lux.functions.LuxSearch;
import lux.functions.Transform;
import lux.functions.file.FileExtensions;
import lux.index.FieldName;
import lux.index.IndexConfiguration;
import lux.xpath.FunCall;
import lux.xquery.XQuery;
import net.sf.saxon.Configuration;
import net.sf.saxon.Configuration.LicenseFeature;
import net.sf.saxon.lib.CollectionURIResolver;
import net.sf.saxon.lib.FeatureKeys;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XPathCompiler;
import net.sf.saxon.s9api.XQueryCompiler;
import net.sf.saxon.s9api.XQueryExecutable;
import net.sf.saxon.s9api.XsltCompiler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

public class XCompiler {
    private final Logger logger;
    private final Processor processor;
    private final Dialect dialect;
    private final CollectionURIResolver defaultCollectionURIResolver;
    private final String uriFieldName;
    private final IndexConfiguration indexConfig;
    // Warning: error listener may receive errors from multiple threads since the compiler
    // is shared.  This is a limitation of the Saxon API, which provides a threadsafe compiler
    // class whose error reporting is not thread-safe.
    private final TransformErrorListener errorListener;
    private final boolean isSaxonLicensed;
    private XQueryCompiler xqueryCompiler;
    private XPathCompiler xpathCompiler;
    private XsltCompiler xsltCompiler;
    
    public enum SearchStrategy {
        NONE, LUX_SEARCH, SAXON_LICENSE
    }
    private SearchStrategy searchStrategy;
    // TODO: once we get a handle on an IndexWriter
    // keep track of the number of writes so we can reopen readers that are out of sync
    // private final AtomicInteger indexGeneration;
    // private final IndexWriter indexWriter;

    /** Creates a Compiler configured according to the capabilities of a wrapped instance of a Saxon Processor.
     * Saxon-HE allows us to optimize result sorting and lazy evaluation.  Saxon-PE and -EE provide
     * PTree storage mechanism, and their own optimizations.
     */
    public XCompiler (IndexConfiguration indexConfig) {
        this (makeProcessor(), indexConfig);
    }
    
    protected XCompiler(Processor processor, IndexConfiguration indexConfig) {
        dialect = Dialect.XQUERY_1;
        this.indexConfig = indexConfig;
        // indexGeneration = new AtomicInteger(0);
        
        this.processor = processor;
        Configuration config = processor.getUnderlyingConfiguration();
        config.setDocumentNumberAllocator(new DocIDNumberAllocator());
        config.setConfigurationProperty(FeatureKeys.XQUERY_PRESERVE_NAMESPACES, false);
        config.getParseOptions().setEntityResolver(new EmptyEntityResolver());
        isSaxonLicensed = config.isLicensedFeature(LicenseFeature.PROFESSIONAL_EDITION)
                || config.isLicensedFeature(LicenseFeature.ENTERPRISE_XQUERY);
        if (indexConfig == null || !indexConfig.isIndexingEnabled()) {
            searchStrategy = SearchStrategy.NONE;
        } else if (isSaxonLicensed) {
            searchStrategy = SearchStrategy.SAXON_LICENSE;
        } else {
            searchStrategy = SearchStrategy.LUX_SEARCH;
        }
        defaultCollectionURIResolver = config.getCollectionURIResolver();
        registerExtensionFunctions(processor);
        if (indexConfig != null && indexConfig.isIndexingEnabled()) {
            uriFieldName = indexConfig.getFieldName(FieldName.URI);
        } else {
            uriFieldName = null;
        }
        //this.dialect = dialect;
        logger = LoggerFactory.getLogger(getClass());
        errorListener = new TransformErrorListener();
    }

    public enum Dialect {
        XPATH_1,
        XPATH_2,
        XQUERY_1
    }
    
    static Processor makeProcessor () {
        try {
            if (Class.forName("com.saxonica.config.EnterpriseConfiguration") != null) {
                return new Processor (true);
            }
        } catch (ClassNotFoundException e) { }
        try {
            if (Class.forName("com.saxonica.config.ProfessionalConfiguration") != null) {
                //return new Processor (new Config());
                return new Processor (true);
            }
        } catch (ClassNotFoundException e) { }
        return new Processor (new Config());
    }
    
    private void registerExtensionFunctions(Processor processor) {
        // TODO: move this list into a single class in the lux.functions package
        processor.registerExtensionFunction(new LuxSearch());
        processor.registerExtensionFunction(new LuxCount());
        processor.registerExtensionFunction(new LuxExists());
        processor.registerExtensionFunction(new FieldTerms());
        processor.registerExtensionFunction(new FieldValues());
        processor.registerExtensionFunction(new Transform());
        processor.registerExtensionFunction(new InsertDocument());
        processor.registerExtensionFunction(new DeleteDocument());
        processor.registerExtensionFunction(new Commit());

        FileExtensions.registerFunctions(processor);
    }
    
    class EmptyEntityResolver implements EntityResolver {
        @Override
        public InputSource resolveEntity(String publicId, String systemId) throws SAXException, IOException {
            return new InputSource(new StringReader(""));
        }
    }
    
    public XQueryExecutable compile(String exprString) throws LuxException {
        switch (dialect) {
        /*
            case XPATH_1: case XPATH_2:
            return compileXPath(exprString);
         */
        case XQUERY_1:
            return compileXQuery(exprString);
        default:
            throw new LuxException ("Unsupported query dialect: " + dialect);
        }
    }

    // for testing
    private XQuery lastOptimized;
    XQuery getLastOptimized () { return lastOptimized; }
    
    private XQueryExecutable compileXQuery(String exprString) throws LuxException {
        XQueryExecutable xquery;
        try {
            xquery = getXQueryCompiler().compile(exprString);
        } catch (SaxonApiException e) {
            throw new LuxException (e);
        }
        if (searchStrategy == SearchStrategy.NONE) {
            return xquery;
        }
        SaxonTranslator translator = makeTranslator();
        XQuery abstractQuery = translator.queryFor (xquery);
        PathOptimizer optimizer = new PathOptimizer(indexConfig);
        optimizer.setSearchStrategy(searchStrategy);
        XQuery optimizedQuery = optimizer.optimize(abstractQuery);
        lastOptimized = optimizedQuery;
        try {
            xquery = getXQueryCompiler().compile(optimizedQuery.toString());
        } catch (SaxonApiException e) {
            throw new LuxException (e);
        }
        if (logger.isDebugEnabled()) {
            logger.debug("optimized xquery: " + optimizedQuery.toString());
        }
        return xquery;
    }

    public void declareNamespace (String prefix, String namespace) {
        switch (dialect) {
        case XPATH_1: case XPATH_2:
            getXPathCompiler().declareNamespace(prefix, namespace);
            break;
        case XQUERY_1:
            getXQueryCompiler().declareNamespace(prefix, namespace);
            break;
        default:
            break;
        }
    }
        
    public XsltCompiler getXsltCompiler () {
        if (xsltCompiler == null) {
            xsltCompiler = processor.newXsltCompiler();
            xsltCompiler.setErrorListener(errorListener);
        }
        errorListener.clear();
        return xsltCompiler;
    }

    public XQueryCompiler getXQueryCompiler () {
        if (xqueryCompiler == null) {
            xqueryCompiler = processor.newXQueryCompiler();
            xqueryCompiler.declareNamespace("lux", FunCall.LUX_NAMESPACE);
            xqueryCompiler.setErrorListener(errorListener);
        }
        errorListener.clear();
        return xqueryCompiler;
    }

    public XPathCompiler getXPathCompiler () {
        if (xpathCompiler == null) {
            xpathCompiler = processor.newXPathCompiler();
            xpathCompiler.declareNamespace("lux", FunCall.LUX_NAMESPACE);
            xpathCompiler.declareNamespace("fn", FunCall.FN_NAMESPACE);
        }
        return xpathCompiler;
    }

    public IndexConfiguration getIndexConfiguration () {
        return indexConfig;
    }

    public Processor getProcessor() {
        return processor;
    }
    
    public SaxonTranslator makeTranslator () {
        return new SaxonTranslator(processor.getUnderlyingConfiguration());
    }
    
    public CollectionURIResolver getDefaultCollectionURIResolver() {
        return defaultCollectionURIResolver;
    }

    public String getUriFieldName() {
        return uriFieldName;
    }

    public SearchStrategy getSearchStrategy() {
        return searchStrategy;
    }

    public void setSearchStrategy(SearchStrategy searchStrategy) {
        this.searchStrategy = searchStrategy;
    }

    public TransformErrorListener getErrorListener() {
        return errorListener;
    }

    public boolean isSaxonLicensed() {
        return isSaxonLicensed;
    }
    
}