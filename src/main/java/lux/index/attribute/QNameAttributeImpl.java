package lux.index.attribute;

import java.util.ArrayList;
import java.util.List;

import lux.xpath.QName;

import org.apache.lucene.util.AttributeImpl;

/**
 * @see QNameAttribute
 *
 */
public class QNameAttributeImpl extends AttributeImpl implements QNameAttribute {

    private int iQName;
    private List<QName> qnames;
    
    public QNameAttributeImpl () {
        qnames = new ArrayList<QName>();
    }
    
    /**
     * We do *not* clear the state here, since it persists across multiple tokens.  Rather we rely on 
     * QNameTextTokenStream to manage our state.
     */
    @Override
    public void clear() {
        iQName = 0;
    }

    @Override
    public void copyTo(AttributeImpl target) {
        target.clear();
        ((QNameAttributeImpl) target).qnames.addAll (qnames);
    }

    public boolean hasNext() {
        return iQName >= 0 && iQName < qnames.size();
    }
    
    public QName next() {
        return qnames.get(iQName++);
    }

    public void addQName(QName qname) {
        qnames.add(qname);
    }
    
    public void clearQNames () {
        qnames.clear();
    }

    public boolean onFirst() {
        return iQName == 0;
    }

}
