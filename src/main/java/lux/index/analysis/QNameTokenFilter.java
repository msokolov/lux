package lux.index.analysis;

import java.io.IOException;

import lux.index.attribute.QNameAttribute;
import lux.xpath.QName;

import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.util.CharsRef;

/**
 * Expand the input term by adding additional terms at the same position, prefixed by the node names (QNames)
 * found in the QNameAttribute.
 */
final public class QNameTokenFilter extends TokenFilter {

    private final QNameAttribute qnameAtt = addAttribute(QNameAttribute.class);
    private final CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);
    private final PositionIncrementAttribute posAtt = addAttribute(PositionIncrementAttribute.class);
    private CharsRef term;

    protected QNameTokenFilter(TokenStream input) {
        super(input);
        term = new CharsRef();
    }
    
    @Override
    public boolean incrementToken() throws IOException {
        if ((! qnameAtt.hasNext()) || qnameAtt.onFirst()) {
            if (!input.incrementToken()) {
                return false;
            }
            term.copy(termAtt.buffer(), 0, termAtt.length());
        }
        else {
            // set posIncr = 0 if this is not the first token emitted for this term
            posAtt.setPositionIncrement(0);
        }
        // emit <qname>:<term>
        QName qname = qnameAtt.next();
        termAtt.setEmpty();
        termAtt.append(qname.getEncodedName());
        termAtt.append(':');
        termAtt.append(term);            
        return true;
    }

}
