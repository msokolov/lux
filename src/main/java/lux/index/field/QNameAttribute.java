package lux.index.field;

import java.util.List;

import lux.xpath.QName;

import org.apache.lucene.util.Attribute;

/**
 * Mark a token as occurring within the scope of a list of QNames
 */
public interface QNameAttribute extends Attribute {
    /**
     * @return the *modifiable* list of QNames associated with the token
     */
    List<QName> getQNames();
    
    /**
     * @param qname the QName to associate with the current token
     */
    void addQName (QName qname);

}
