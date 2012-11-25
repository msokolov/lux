package lux.functions;

import java.io.IOException;

import lux.Evaluator;
import lux.compiler.XPathQuery;
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

public class LuxExists extends SearchBase {
    
    public LuxExists() { }

    @Override
    public StructuredQName getFunctionQName() {
        return new StructuredQName("lux", FunCall.LUX_NAMESPACE, "exists");
    }

    @Override
    public SequenceType getResultType(SequenceType[] suppliedArgumentTypes) {
        return SequenceType.SINGLE_BOOLEAN;
    }    
    
    @Override public UnfailingIterator<BooleanValue> iterate (Query query, Evaluator saxon, long facts) throws XPathException {
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
        if ((facts & XPathQuery.BOOLEAN_FALSE) != 0) {
            exists = !exists;
        }
        return SingletonIterator.makeIterator(BooleanValue.get(exists));
    }
}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
