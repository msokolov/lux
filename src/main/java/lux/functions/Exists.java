package lux.functions;

import java.io.IOException;

import lux.Evaluator;
import lux.solr.CloudSearchIterator;
import lux.xpath.FunCall;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.SingletonIterator;
import net.sf.saxon.tree.iter.UnfailingIterator;
import net.sf.saxon.value.BooleanValue;
import net.sf.saxon.value.SequenceType;

import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Scorer;

/**
 * <code>function lux:exists($query as item()) as xs:integer</code>
 *<p>
 * This function tests whether a search has any results.  It is faster and uses less memory 
 * than calling fn:exists() on the search results themselves because it does not need to load
 * any result documents in memory.  See {@link Search} for an explanation of the supported
 * $query formats.
 * </p>
 */
public class Exists extends SearchBase {
    
    public Exists() { }

    @Override
    public StructuredQName getFunctionQName() {
        return new StructuredQName("lux", FunCall.LUX_NAMESPACE, "exists");
    }

    @Override
    public SequenceType getResultType(SequenceType[] suppliedArgumentTypes) {
        return SequenceType.SINGLE_BOOLEAN;
    }
    
    @Override
    public boolean trustResultType () {
        return true;
    }
    
    @Override 
    public UnfailingIterator<BooleanValue> iterate (Query query, Evaluator saxon, String sortCriteria, int start) throws XPathException {
        long t = System.currentTimeMillis();
        boolean exists = false;
        try {
            DocIdSetIterator iter = saxon.getSearcher().search(query);
            exists = (iter.nextDoc() != Scorer.NO_MORE_DOCS);
        } catch (IOException e) {
            throw new XPathException (e);
        }
        saxon.getQueryStats().totalTime = System.currentTimeMillis() - t;
        if (exists) {
            ++ saxon.getQueryStats().docCount;
        }
        return SingletonIterator.makeIterator(BooleanValue.get(exists));
    }

    @Override
    protected UnfailingIterator<BooleanValue> iterateDistributed(String query, QueryParser queryParser, Evaluator eval, String sortCriteria, int start) throws XPathException {
        try {
            long count = new CloudSearchIterator (eval, query, queryParser, sortCriteria, start).count();
            return SingletonIterator.makeIterator(BooleanValue.get(count > 0));
        } catch (Exception e) {
            throw new XPathException (e);
        }
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
