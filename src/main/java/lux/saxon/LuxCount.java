package lux.saxon;

import java.io.IOException;

import lux.XPathQuery;
import lux.xpath.FunCall;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.SingletonIterator;
import net.sf.saxon.value.Int64Value;
import net.sf.saxon.value.SequenceType;

import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.Scorer;

public class LuxCount extends LuxSearch {
    
    public LuxCount(Saxon saxon) {
        super(saxon);
    }
    
    @Override
    public StructuredQName getFunctionQName() {
        return new StructuredQName("lux", FunCall.LUX_NAMESPACE, "count");
    }

    @Override
    public SequenceType getResultType(SequenceType[] suppliedArgumentTypes) {
        return SequenceType.SINGLE_INTEGER;
    }
    
    @SuppressWarnings("rawtypes")
    @Override public SequenceIterator<Item> iterate (XPathQuery query) throws XPathException {
        int count = 0;
        long t = System.currentTimeMillis();
        try {
            DocIdSetIterator counter = saxon.getContext().getSearcher().search(query);
            while (counter.nextDoc() != Scorer.NO_MORE_DOCS) {
                ++count;
            }
        } catch (IOException e) {
            throw new XPathException (e);
        }
        saxon.getQueryStats().totalTime = System.currentTimeMillis() - t;
        saxon.getQueryStats().docCount += count;
        return SingletonIterator.makeIterator((Item)new Int64Value(count));
    }

}
