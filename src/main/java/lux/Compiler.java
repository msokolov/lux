package lux;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;

import javax.xml.transform.ErrorListener;

import lux.compiler.PathOptimizer;
import lux.compiler.SaxonTranslator;
import lux.exception.LuxException;
import lux.functions.Commit;
import lux.functions.Count;
import lux.functions.DeleteDocument;
import lux.functions.Eval;
import lux.functions.Exists;
import lux.functions.ExtensionFunctions;
import lux.functions.FieldTerms;
import lux.functions.FieldValues;
import lux.functions.Highlight;
import lux.functions.InsertDocument;
import lux.functions.Search;
import lux.functions.Transform;
import lux.functions.file.FileExtensions;
import lux.index.FieldName;
import lux.index.IndexConfiguration;
import lux.xml.GentleXmlReader;
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

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private final boolean isSaxonLicensed;

    // for testing
    private XQuery lastOptimized;
    
    public enum SearchStrategy {
        NONE, LUX_SEARCH, SAXON_LICENSE
    }
    private SearchStrategy searchStrategy;

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

        GentleXmlReader parser = new GentleXmlReader();
        config.getParseOptions().setEntityResolver(parser);
        // tried this, but it seems to lead to concurrent usage of the same parser:
        //config.getParseOptions().setXMLReader(parser);
        // the question is: does Saxon re-use a single instance of this parser??
        config.setSourceParserClass("lux.xml.GentleXmlReader");
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
        uriFieldName = indexConfig.getFieldName(FieldName.URI);
        //this.dialect = dialect;
        logger = LoggerFactory.getLogger(getClass());
    }
    
    /**
     * Compiles the XQuery expression (main module) using a Saxon {@link XQueryCompiler}, then translates it into a mutable {@link AbstractExpression}
     * tree using a {@link SaxonTranslator}, optimizes it with a {@link PathOptimizer}, and then re-serializes and re-compiles.
     * @param exprString the XQuery source
     * @return the compiled XQuery expression
     * @throws LuxException if any error occurs while compiling, such as a static XQuery error or syntax error.
     */
    public XQueryExecutable compile(String exprString) throws LuxException {
        return compile (exprString, null, null);
    }
    
    public XQueryExecutable compile(String exprString, ErrorListener errorListener) throws LuxException {
        return compile (exprString, errorListener, null);
    }
    
    public XQueryExecutable compile(String exprString, ErrorListener errorListener, URI baseURI) throws LuxException {
        XQueryExecutable xquery;
        XQueryCompiler xQueryCompiler = getXQueryCompiler();
        if (errorListener != null) {
            xQueryCompiler.setErrorListener(errorListener);
        }
        if (baseURI != null) {
            xQueryCompiler.setBaseURI(baseURI);
        }
        try {
            xquery = xQueryCompiler.compile(exprString);
        } catch (SaxonApiException e) {
            throw new LuxException (e);
        }
        SaxonTranslator translator = makeTranslator();
        XQuery abstractQuery = translator.queryFor (xquery);
        /*if (searchStrategy == SearchStrategy.NONE) {
            String expanded = new Expandifier().expandify(abstractQuery).toString();
            return xquery;
        }*/
        PathOptimizer optimizer = new PathOptimizer(indexConfig);
        optimizer.setSearchStrategy(searchStrategy);
        XQuery optimizedQuery = null;
        try {
            optimizedQuery = optimizer.optimize(abstractQuery);
        } catch (LuxException e) {
            if (logger.isDebugEnabled()) {
                logger.debug ("An error occurred while optimizing: " + abstractQuery.toString());
            }
            throw (e);
        }
        lastOptimized = optimizedQuery;
        if (logger.isDebugEnabled()) {
            logger.debug("optimized xquery: " + optimizedQuery.toString());
        }
        try {
            xquery = xQueryCompiler.compile(optimizedQuery.toString());
        } catch (SaxonApiException e) {
            throw new LuxException (e);
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
        Processor p = new Processor (new Config());
        if (! StringUtils.isEmpty(System.getProperty("org.expath.pkg.saxon.repo"))) {
            initializeEXPath(p);
        }
        return p;
    }

    private static void initializeEXPath(Processor p) {
        Logger log = LoggerFactory.getLogger(Compiler.class);
        // initialize the EXPath package manager
        Class<?> pkgInitializerClass;
        try {
            pkgInitializerClass = Class.forName("org.expath.pkg.saxon.PkgInitializer");
            Object pkgInitializer = null;
            try {
                pkgInitializer = pkgInitializerClass.newInstance();
            } catch (InstantiationException e) {
                log.error (e.getMessage());
                return;
            } catch (IllegalAccessException e) {
                log.error (e.getMessage());
                return;
            }
            Method initialize = pkgInitializerClass.getMethod("initialize", Configuration.class);
            initialize.invoke(pkgInitializer, p.getUnderlyingConfiguration());
        } catch (ClassNotFoundException e) {
            log.error("EXPath repository declared, but EXPath Saxon package support classes are not available");
        } catch (SecurityException e) {
            log.error (e.getMessage());
        } catch (NoSuchMethodException e) {
            log.error (e.getMessage());
        } catch (IllegalArgumentException e) {
            log.error (e.getMessage());
        } catch (IllegalAccessException e) {
            log.error (e.getMessage());
        } catch (InvocationTargetException e) {
            log.error (e.getMessage());
        }
    }
    
    private void registerExtensionFunctions() {
        // TODO: move this list into a single class in the lux.functions package
        processor.registerExtensionFunction(new Search());
        processor.registerExtensionFunction(new Count());
        processor.registerExtensionFunction(new Exists());
        processor.registerExtensionFunction(new FieldTerms());
        processor.registerExtensionFunction(new FieldValues());
        processor.registerExtensionFunction(new Transform());
        processor.registerExtensionFunction(new Eval());
        processor.registerExtensionFunction(new InsertDocument());
        processor.registerExtensionFunction(new DeleteDocument());
        processor.registerExtensionFunction(new Commit());
        processor.registerExtensionFunction(new Highlight());

        FileExtensions.registerFunctions(processor);
        ExtensionFunctions.registerFunctions(processor);
    }
    
    public XsltCompiler getXsltCompiler () {
        return processor.newXsltCompiler();
    }

    public XQueryCompiler getXQueryCompiler () {
        XQueryCompiler xqueryCompiler = processor.newXQueryCompiler();
        xqueryCompiler.declareNamespace("lux", FunCall.LUX_NAMESPACE);
        return xqueryCompiler;
    }

    public XPathCompiler getXPathCompiler () {
        XPathCompiler xpathCompiler = processor.newXPathCompiler();
        xpathCompiler.declareNamespace("lux", FunCall.LUX_NAMESPACE);
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

    public boolean isSaxonLicensed() {
        return isSaxonLicensed;
    }
    
    /**
     * @return the last query that was compiled, in its translated and optimized form.
     */
    public XQuery getLastOptimized () { 
        return lastOptimized; 
    }
    
}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
