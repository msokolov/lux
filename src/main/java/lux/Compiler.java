package lux;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.transform.ErrorListener;

import lux.compiler.EXPathSupport;
import lux.compiler.PathOptimizer;
import lux.compiler.SaxonTranslator;
import lux.exception.LuxException;
import lux.functions.ExtensionFunctions;
import lux.functions.LuxFunctionLibrary;
import lux.functions.file.FileExtensions;
import lux.index.FieldName;
import lux.index.IndexConfiguration;
import lux.index.field.FieldDefinition;
import lux.index.field.XPathField;
import lux.xml.GentleXmlReader;
import lux.xpath.AbstractExpression;
import lux.xpath.FunCall;
import lux.xpath.NodeTest;
import lux.xpath.PathStep;
import lux.xpath.PathStep.Axis;
import lux.xpath.PropEquiv;
import lux.xquery.XQuery;
import net.sf.saxon.Configuration;
import net.sf.saxon.Configuration.LicenseFeature;
import net.sf.saxon.lib.CollectionURIResolver;
import net.sf.saxon.lib.FeatureKeys;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XPathCompiler;
import net.sf.saxon.s9api.XPathExecutable;
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
    private final HashMap<PropEquiv,ArrayList<AbstractExpression>> fieldLeaves;
    private final HashMap<AbstractExpression, XPathField> fieldExpressions;
    private final HashMap<String,String> namespaceBindings;
    private final PropEquiv tempEquiv;

    public enum SearchStrategy {
        NONE, // the query is evaluated without any modification 
        LUX_UNOPTIMIZED, // collection() is inserted for Root()
        LUX_SEARCH, // full suite of Lux optimizations are applied, consistent with available indexes 
        SAXON_LICENSE, // Only optimizations compatible with Saxon-PE/EE are applied  
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
        
        namespaceBindings = new HashMap<String, String>();
        namespaceBindings.put ("lux", Evaluator.LUX_NAMESPACE);
        
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
        fieldLeaves = new HashMap<PropEquiv, ArrayList<AbstractExpression>>();
        fieldExpressions = new HashMap<AbstractExpression, XPathField>();
        tempEquiv = new PropEquiv(null);
        compileFieldExpressions ();
    }
    
    /**
     * Compiles the XQuery expression (main module) using a Saxon {@link XQueryCompiler}, then translates it into a mutable {@link AbstractExpression}
     * tree using a {@link SaxonTranslator}, optimizes it with a {@link PathOptimizer}, and then re-serializes and re-compiles.
     * @param exprString the XQuery source
     * @return the compiled XQuery expression
     * @throws LuxException if any error occurs while compiling, such as a static XQuery error or syntax error.
     */
    public XQueryExecutable compile(String exprString) throws LuxException {
        return compile (exprString, null, null, null);
    }
    
    public XQueryExecutable compile(String exprString, ErrorListener errorListener) throws LuxException {
        return compile (exprString, errorListener, null, null);
    }
    
    public XQueryExecutable compile(String exprString, ErrorListener errorListener, QueryStats stats) throws LuxException {
        return compile (exprString, errorListener, null, stats);
    }
    
    /**
     * Compiles an XQuery expression, returning a Saxon XQueryExecutable.
     * @param exprString the expression to compile
     * @param errorListener receives any errors generated while compiling; may be null, in which case
     * any errors generated will be lost
     * @param baseURI the base URI of the compiled query
     * @param stats accumulates statistics about the query execution for debugging and logging (if not null)
     * @return the compiled, executable query object
     * @throws LuxException when a compilation error occurs.  The message is typically unhelpful; meaningful errors
     * are stored in the errorListener
     */
    public XQueryExecutable compile(String exprString, ErrorListener errorListener, URI baseURI, QueryStats stats) throws LuxException {
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
        if (searchStrategy == SearchStrategy.NONE) {
        	return xquery;
        }
        XQuery abstractQuery = translator.queryFor (xquery);
        PathOptimizer optimizer = new PathOptimizer(this);
        optimizer.setSearchStrategy(searchStrategy);
        XQuery optimizedQuery = null;
        try {
            optimizedQuery = optimizer.optimize(abstractQuery);
            if (stats != null) {
                stats.optimizedXQuery = optimizedQuery;
            }
        } catch (LuxException e) {
            if (logger.isDebugEnabled()) {
                logger.debug ("An error occurred while optimizing: " + abstractQuery.toString());
            }
            throw (e);
        }
        String queryString = optimizedQuery.toString();
        if (logger.isDebugEnabled()) {
            logger.debug("optimized xquery: " + queryString);
        }
        try {
            xquery = xQueryCompiler.compile(queryString);
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
            EXPathSupport.initializeEXPath(p);
        }
        return p;
    }

    private void registerExtensionFunctions() {
        LuxFunctionLibrary.registerFunctions(processor);
        FileExtensions.registerFunctions(processor);
        ExtensionFunctions.registerFunctions(processor);
    }
    
    public XsltCompiler getXsltCompiler () {
        return processor.newXsltCompiler();
    }

    public XQueryCompiler getXQueryCompiler () {
        XQueryCompiler xqueryCompiler = processor.newXQueryCompiler();
        for (java.util.Map.Entry<String, String> binding : namespaceBindings.entrySet()) {
            xqueryCompiler.declareNamespace(binding.getKey(), binding.getValue());
        }
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
    
    public List<AbstractExpression> getFieldLeaves(AbstractExpression leafExpr) {
    	List<AbstractExpression> allLeaves = new ArrayList<AbstractExpression>();
    	// get leaves that are equivalent to leafExpr
    	addMatchingLeaves (leafExpr, allLeaves);
    	if (leafExpr instanceof PathStep) {
        	// also get leaves that are geq leafExpr
    		PathStep.Axis axis = ((PathStep) leafExpr).getAxis();
    		NodeTest nodeTest = ((PathStep) leafExpr).getNodeTest();
    		PathStep step;
    		for (Axis extAxis : axis.extensions) {
        		// try various generalizations: self->ancestor-or-self, etc
    			step = new PathStep (extAxis, nodeTest);
    	    	addMatchingLeaves (step, allLeaves);
    		}
    		if (! nodeTest.isWild()) {
    			// try matching indexes with "*"
    			nodeTest = new NodeTest (nodeTest.getType());
    			step = new PathStep (axis, nodeTest);
    	    	addMatchingLeaves (step, allLeaves);
        		for (Axis extAxis : axis.extensions) {
        			step = new PathStep (extAxis, nodeTest);
        	    	addMatchingLeaves (step, allLeaves);
        		}
    		}
    	}
    	return allLeaves;
	}
    
    private void addMatchingLeaves (AbstractExpression expr, List<AbstractExpression> allLeaves) {
    	tempEquiv.setExpression(expr);
    	ArrayList<AbstractExpression> leaves = fieldLeaves.get(tempEquiv);
    	if (leaves != null) {
    		allLeaves.addAll (leaves);
    	}
    }

	public FieldDefinition getFieldForExpr(AbstractExpression fieldExpr) {
		return fieldExpressions.get(fieldExpr);
	}
	
	/**
	 * bind the prefix to the namespace, making the binding available to compiled expressions 
	 * @param prefix if empty, the default namespace is bound 
	 * @param namespace if empty or null, any existing binding for the prefix is removed
	 */
	public void bindNamespacePrefix (String prefix, String namespace) {
	    if (StringUtils.isEmpty(namespace)) {
            namespaceBindings.remove(prefix);
	    } else {
            namespaceBindings.put(prefix, namespace);
	    }
	}
	
	/**
	 *  Save an AbstractExpression version of each XPathField's xpath, for use when optimizing.
	 *  This must be called whenever the underlying indexConfiguration's collection of XPath fields
	 *  changes.  TODO: consider moving the addField method to Compiler?
	 */
	public void compileFieldExpressions () {
		SaxonTranslator translator = new SaxonTranslator(processor.getUnderlyingConfiguration());
		XPathCompiler xPathCompiler = getXPathCompiler();
		for (Map.Entry<String,String> e : indexConfig.getNamespaceMap().entrySet()) {
			xPathCompiler.declareNamespace(e.getKey(), e.getValue());
		}
		for (FieldDefinition field : indexConfig.getFields()) {
			if (field instanceof XPathField) {
				String xpath = ((XPathField) field).getXPath();
				XPathExecutable xpathExec;
				try {
					xpathExec = xPathCompiler.compile(xpath);
				} catch (SaxonApiException e) {
					throw new LuxException("Error compiling index expression " + xpath + " for field " + field.getDefaultName());
				}
				AbstractExpression xpathExpr = translator.exprFor(xpathExec.getUnderlyingExpression().getInternalExpression());
				AbstractExpression leaf = xpathExpr.getLastContextStep();
				PropEquiv leafEquiv = new PropEquiv(leaf);
				if (fieldLeaves.containsKey(leaf)) {
					fieldLeaves.get(leafEquiv).add(leaf);
				} else {
					ArrayList<AbstractExpression> leaves = new ArrayList<AbstractExpression>();
					leaves.add (leaf);
					fieldLeaves.put(leafEquiv, leaves);
				}
				fieldExpressions.put(xpathExpr, (XPathField) field);
			}
		}
	}
    
}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
