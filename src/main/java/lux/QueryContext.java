package lux;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import lux.exception.LuxException;
import lux.query.parser.LuxSearchQueryParser;
import lux.xml.QName;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.s9api.XdmAtomicValue;
import net.sf.saxon.s9api.XdmValue;
import net.sf.saxon.trans.XPathException;

import org.apache.lucene.search.Query;

/**
 * Holds external query context: variable bindings and the context item.
 * TODO: convert java primitives to XdmValues?  Currently the Evaluator expects 
 * XdmValues.
 */
public class QueryContext {
    
    private HashMap<QName, Object> variables;
    
    private Object contextItem;

    /** A query context with no context item */
    public QueryContext() {
    }
    
    /** A query context with the context item defined 
     * @param contextItem defining the context item of a query */
    public QueryContext(Object contextItem) {
        this.contextItem = contextItem; 
    }
    
    /**
     * bind an external variable so that it will be available in the scope of queries evaluated using this context
     * @param varName the name of the variable to bind
     * @param value the value to bind to the variable; this must be of an XdmValue, or a java primitive
     * that can be converted to an XdmAtomicValue, 
     * or null to clear any existing binding.
     */
    public void bindVariable (QName varName, Object value) {
        if (variables == null) {
            variables = new HashMap<QName, Object>();
        }
        if (value == null) {
            variables.remove(varName);            
        } else {
        	XdmValue xdmValue = getXdmValue (value);
            variables.put(varName, xdmValue);
        }
    }
    
    private XdmValue getXdmValue(Object value) {
    	if (value instanceof XdmValue) {
    		return (XdmValue) value;
    	}
    	if (value instanceof String) {
    		return new XdmAtomicValue ((String) value);
    	}
    	if (value instanceof Integer) {
    		return new XdmAtomicValue ((Integer) value);
    	}
    	throw new LuxException ("No automatic conversion supplied for " + value.getClass().getName());
	}

	public Map<QName, Object> getVariableBindings() {
        if (variables == null) {
            return null;
        }
        return Collections.unmodifiableMap(variables);
    }
    
    public void setContextItem (Object contextItem) {
        this.contextItem = contextItem;
    }
    
    public Object getContextItem () {
        return contextItem;
    }
    
    public SequenceIterator<? extends Item> createSearchIterator (Item queryArg, LuxSearchQueryParser parser,
            Evaluator eval, String [] sortCriteria, int start) throws XPathException {
        Query query = parser.parse(queryArg, eval);
        try {
            return new SearchResultIterator(eval, query, sortCriteria, start);
        } catch (Exception e) {
            throw new XPathException (e);
        }
    }
    
}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
