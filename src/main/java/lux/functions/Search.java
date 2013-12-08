package lux.functions;

import lux.Evaluator;
import lux.SearchResultIterator;
import lux.query.parser.LuxQueryParser;
import lux.query.parser.XmlQueryParser;
import lux.xpath.FunCall;
import net.sf.saxon.expr.StaticProperty;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.pattern.NodeKindTest;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.SequenceType;

import org.apache.lucene.search.Query;

/**
 * <code>function lux:search($query as item(), $sort as xs:string?, $start as xs:int?) as document-node()*</code>
 * <p>Executes a Lucene search query and returns documents.  If the query argument is an element or document 
 * node, it is parsed using the {@link XmlQueryParser}; otherwise its string value is parsed using the {@link LuxQueryParser}.
 * For details about the query syntaxes, see the parser documentation.</p>
 * <p>$sort defines sort criteria: multiple criteria are separated by commas; each criterion is a field
 * name (or lux:score) with optional keywords appended: ascending|descending, empty least|empty greatest.
 * If no sort key is provided, documents are ordered by Lucene docID, which is defined to be XQuery document order.
 * </p>
 * <p>$start indiciates the (1-based) index of the first result to return. Skipped results don't need to be loaded 
 * in memory, so providing $start allows for more efficient processing of queries that require "deep paging".
 */
public class Search extends SearchBase {
    
    public Search () {
    }
    
    @Override
    public StructuredQName getFunctionQName() {
        return new StructuredQName("lux", FunCall.LUX_NAMESPACE, "search");
    }

    @Override
    public SequenceType[] getArgumentTypes() {
        return new SequenceType[] { 
                SequenceType.SINGLE_ITEM,       // query: as element node or string
                SequenceType.OPTIONAL_STRING,   // sort key stanza
                SequenceType.OPTIONAL_INTEGER   // start - index of first result (1-based)
                };
    }
    
    @Override
    public SequenceType getResultType(SequenceType[] suppliedArgumentTypes) {
        return SequenceType.makeSequenceType(NodeKindTest.DOCUMENT, StaticProperty.ALLOWS_ZERO_OR_MORE);
    }
    
    @Override
    public int getMaximumNumberOfArguments() {
        return 4;
    }
    
    /**
     * Iterate over the search results
     *
     * @param query the query to execute
     * @param eval 
     * @return an iterator with the results of executing the query and applying the
     * expression to its result.
     * @throws XPathException
     */
    @Override
    public SequenceIterator<NodeInfo> iterate(final Query query, Evaluator eval, String sortCriteria, int start) throws XPathException {        
        try {
            return new SearchResultIterator (eval, query, sortCriteria, start);
        } catch (Exception e) {
            throw new XPathException (e);
        }
    }
    
}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
