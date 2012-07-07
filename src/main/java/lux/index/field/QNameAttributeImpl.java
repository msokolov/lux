package lux.index.field;

import java.util.ArrayList;
import java.util.List;

import lux.xpath.QName;

import org.apache.lucene.util.AttributeImpl;

/**
 * @see QNameAttribute
 *
 */
public class QNameAttributeImpl extends AttributeImpl implements QNameAttribute {

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
    }

    @Override
    public void copyTo(AttributeImpl target) {
        target.clear();
        ((QNameAttributeImpl) target).qnames.addAll (qnames);
    }

    public List<QName> getQNames() {
        return qnames;
    }

    public void addQName(QName qname) {
        qnames.add(qname);
    }

}
