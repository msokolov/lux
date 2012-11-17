package lux.functions;

import java.io.IOException;

import lux.saxon.Saxon;
import lux.saxon.SearchResultIterator;
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
 * Executes a Lucene search query and returns documents.
 * 
 */
public class LuxSearch extends SearchBase {
    
    public LuxSearch () {
    }
    
    @Override
    public StructuredQName getFunctionQName() {
        return new StructuredQName("lux", FunCall.LUX_NAMESPACE, "search");
    }

    @Override
    public SequenceType getResultType(SequenceType[] suppliedArgumentTypes) {
        return SequenceType.makeSequenceType(NodeKindTest.DOCUMENT, StaticProperty.ALLOWS_ZERO_OR_MORE);
    }


    /**
     * Iterate over the search results
     *
     * @param query the query to execute
     * @param saxon 
     * @return an iterator with the results of executing the query and applying the
     * expression to its result.
     * @throws XPathException
     */
    public SequenceIterator<NodeInfo> iterate(final Query query, Saxon saxon, long facts) throws XPathException {        
        try {
            return new SearchResultIterator (saxon, query);
        } catch (IOException e) {
            throw new XPathException (e);
        }
    }
    
}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
