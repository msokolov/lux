package lux.functions;

import java.io.IOException;

import lux.XPathQuery;
import lux.api.ValueType;
import lux.saxon.Saxon;
import lux.xpath.FunCall;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.SingletonIterator;
import net.sf.saxon.value.BooleanValue;
import net.sf.saxon.value.SequenceType;

import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.Scorer;

public class LuxExists extends LuxSearch {
    
    public LuxExists(Saxon saxon) {
        super(saxon);
    }

    @Override
    public StructuredQName getFunctionQName() {
        return new StructuredQName("lux", FunCall.LUX_NAMESPACE, "exists");
    }

    @Override
    public SequenceType getResultType(SequenceType[] suppliedArgumentTypes) {
        return SequenceType.SINGLE_BOOLEAN;
    }    
    
    @SuppressWarnings("rawtypes")
    @Override public SequenceIterator<Item> iterate (XPathQuery query) throws XPathException {
        long t = System.currentTimeMillis();
        boolean exists = false;
        try {
            DocIdSetIterator iter = saxon.getContext().getSearcher().search(query);
            exists = (iter.nextDoc() != Scorer.NO_MORE_DOCS);
        } catch (IOException e) {
            throw new XPathException (e);
        }
        saxon.getQueryStats().totalTime = System.currentTimeMillis() - t;
        if (exists) {
            ++ saxon.getQueryStats().docCount;
        }
        if (query.getResultType()== ValueType.BOOLEAN_FALSE) {
            exists = !exists;
        }
        return SingletonIterator.makeIterator((Item)BooleanValue.get(exists));
    }
}
