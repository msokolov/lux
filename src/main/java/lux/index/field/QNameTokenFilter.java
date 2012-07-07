package lux.index.field;

import java.io.IOException;

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
    private final boolean emitTextNodeTerm;
    
    private int qnameIndex = 0;
    private CharsRef term;

    protected QNameTokenFilter(TokenStream input, boolean emitTextNodeTerm) {
        super(input);
        term = new CharsRef();
        this.emitTextNodeTerm = emitTextNodeTerm;        
    }

    protected QNameTokenFilter(TokenStream input) {
        this (input, true);
    }

    @Override
    public boolean incrementToken() throws IOException {
        // TODO: don't emit unprefixed text term here - we'll store that in a separate field
        if (qnameIndex >= qnameAtt.getQNames().size()) {
            if (!input.incrementToken()) {
                return false;
            }
            qnameIndex = 0;
            term.copy(termAtt.buffer(), 0, termAtt.length());
            // emit the input token
            if (emitTextNodeTerm == true) {
                return true;
            }
            // otherwise fall through below and emit the first qname:term token
        }
        else {
            // set posIncr = 0 if this is not the first token emitted for this term
            posAtt.setPositionIncrement(0);
        }
        // emit <qname>:<term>
        QName qname = qnameAtt.getQNames().get(qnameIndex++);
        termAtt.setEmpty();
        termAtt.append(qname.getEncodedName());
        termAtt.append(':');
        termAtt.append(term);            
        return true;
    }

}
