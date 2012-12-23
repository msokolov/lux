package lux;

import java.io.IOException;
import java.io.StringReader;

import lux.compiler.PathOptimizer;
import lux.compiler.SaxonTranslator;
import lux.exception.LuxException;
import lux.functions.Commit;
import lux.functions.Count;
import lux.functions.DeleteDocument;
import lux.functions.Exists;
import lux.functions.FieldTerms;
import lux.functions.FieldValues;
import lux.functions.InsertDocument;
import lux.functions.Search;
import lux.functions.Transform;
import lux.functions.file.FileExtensions;
import lux.index.FieldName;
import lux.index.IndexConfiguration;
import lux.xpath.AbstractExpression;
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

/**
 * Compiles XQuery using Saxon's compiler and optimizes it for use with a Lucene index.
 * This class is thread-safe, and should be re-used for multiple queries.  
 */
public class Compiler {
    
    private final Logger logger;
    private final Processor processor;
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

    // for testing
    private XQuery lastOptimized;
    
    public enum SearchStrategy {
        NONE, LUX_SEARCH, SAXON_LICENSE
    }
    private SearchStrategy searchStrategy;
    // TODO: once we get a handle on an IndexWriter
    // keep track of the number of writes so we can reopen readers that are out of sync
    // private final AtomicInteger indexGeneration;
    // private final IndexWriter indexWriter;

    /** Creates a Compiler configured according to the given {@link IndexConfiguration}. 
     * A Saxon Processor is generated using the installed version of Saxon.  If a licensed version of Saxon 
     * (PE or EE) is installed, the presence of a license is asserted so as to enable the use of licensed Saxon features.
     * @param config the index configuration
     */
    public Compiler (IndexConfiguration config) {
        this (makeProcessor(), config);
    }
    
    /** Creates a Compiler using the provided {@link Processor} and {@link IndexConfiguration}.
     * @param processor the Saxon Processor
     * @param indexConfig the index configuration
     */
    public Compiler(Processor processor, IndexConfiguration indexConfig) {
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
        registerExtensionFunctions();
        if (indexConfig != null && indexConfig.isIndexingEnabled()) {
            uriFieldName = indexConfig.getFieldName(FieldName.URI);
        } else {
            uriFieldName = null;
        }
        //this.dialect = dialect;
        logger = LoggerFactory.getLogger(getClass());
        errorListener = new TransformErrorListener();
    }
    
    /**
     * Compiles the XQuery expression (main module) using a Saxon {@link XQueryCompiler}, then translates it into a mutable {@link AbstractExpression}
     * tree using a {@link SaxonTranslator}, optimizes it with a {@link PathOptimizer}, and then re-serializes and re-compiles.
     * @param exprString the XQuery source
     * @return the compiled XQuery expression
     * @throws LuxException if any error occurs while compiling, such as a static XQuery error or syntax error.
     */
    public XQueryExecutable compile(String exprString) throws LuxException {
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
    
    private static Processor makeProcessor () {
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
    
    private void registerExtensionFunctions() {
        // TODO: move this list into a single class in the lux.functions package
        processor.registerExtensionFunction(new Search());
        processor.registerExtensionFunction(new Count());
        processor.registerExtensionFunction(new Exists());
        processor.registerExtensionFunction(new FieldTerms());
        processor.registerExtensionFunction(new FieldValues());
        processor.registerExtensionFunction(new Transform());
        processor.registerExtensionFunction(new InsertDocument());
        processor.registerExtensionFunction(new DeleteDocument());
        processor.registerExtensionFunction(new Commit());

        FileExtensions.registerFunctions(processor);
    }
    
    private class EmptyEntityResolver implements EntityResolver {
        @Override
        public InputSource resolveEntity(String publicId, String systemId) throws SAXException, IOException {
            return new InputSource(new StringReader(""));
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

    /**
     * @return the strategy that defines the way in which optimizer-generated searches are to be encoded:
     * either as calls to lux:search(), or as calls to collection() with a uri beginning "lux:".
     */
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
    
    XQuery getLastOptimized () { 
        return lastOptimized; 
    }
    
}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
