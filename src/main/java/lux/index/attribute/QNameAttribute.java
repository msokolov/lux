package lux.index.attribute;

import lux.xpath.QName;

import org.apache.lucene.util.Attribute;

/**
 * Mark a token as occurring within the scope of a list of QNames
 */
public interface QNameAttribute extends Attribute {
    
    /**
     *@return whether there are any more QNames
     */
    boolean hasNext();
    
    /**
     * @return whether the next QName is the first QName
     */
    boolean onFirst();
    
    /**
     * @return the next QName
     */
    QName next();
    
    /** add another QName, and reset the counter.
     * @param qname the QName to associate with the current token
     */
    void addQName (QName qname);
    
    /** clear the current set of QNames. We don't do this in clear() since we want
     * these to persist across a range of tokens
     */
    void clearQNames ();

}
