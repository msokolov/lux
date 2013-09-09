package lux.functions;

import java.io.IOException;

import lux.Evaluator;
import lux.xpath.FunCall;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.SingletonIterator;
import net.sf.saxon.tree.iter.UnfailingIterator;
import net.sf.saxon.value.Int64Value;
import net.sf.saxon.value.SequenceType;

import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Scorer;

/**
 * <code>function lux:count($query as item()) as xs:integer</code>
 * <p>
 * This function counts the number of results of a search.  It is faster and uses less memory 
 * than calling fn:count() on the search results themselves because it does not need to load
 * the result documents in memory.  See {@link Search} for an explanation of the supported
 * $query formats.
 * </p>
 */
public class Count extends SearchBase {
    
    public Count() { }
    
    @Override
    public StructuredQName getFunctionQName() {
        return new StructuredQName("lux", FunCall.LUX_NAMESPACE, "count");
    }

    @Override
    public SequenceType getResultType(SequenceType[] suppliedArgumentTypes) {
        return SequenceType.SINGLE_INTEGER;
    }
    
    @Override
    public boolean trustResultType () {
        return true;
    }
    
    @Override 
    public UnfailingIterator<Int64Value> iterate (Query query, Evaluator saxon, String sortCriteria, int start) throws XPathException {
        int count = 0;
        long t = System.currentTimeMillis();
        try {
            DocIdSetIterator counter = saxon.getSearcher().search(query);
            while (counter.nextDoc() != Scorer.NO_MORE_DOCS) {
                ++count;
            }
        } catch (IOException e) {
            throw new XPathException (e);
        }
        saxon.getQueryStats().totalTime = System.currentTimeMillis() - t;
        saxon.getQueryStats().docCount += count;
        return SingletonIterator.makeIterator(new Int64Value(count));
    }

    @Override
    protected SequenceIterator<Int64Value> iterate(String query, QueryParser queryParser, Evaluator eval, String sortCriteria, int start) throws XPathException {
        // TODO implement sharded counting
        return null;
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
