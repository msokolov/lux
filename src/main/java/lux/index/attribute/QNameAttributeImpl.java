package lux.index.attribute;

import java.util.ArrayList;
import java.util.List;

import lux.xml.QName;

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

    @Override
    public boolean hasNext() {
        return iQName >= 0 && iQName < qnames.size();
    }
    
    @Override
    public QName next() {
        return qnames.get(iQName++);
    }

    @Override
    public void addQName(QName qname) {
        qnames.add(qname);
    }
    
    @Override
    public void clearQNames () {
        qnames.clear();
    }

    @Override
    public boolean onFirst() {
        return iQName == 0;
    }

}
