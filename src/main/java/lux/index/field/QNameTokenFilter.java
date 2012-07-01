package lux.index.field;

import java.io.IOException;

import lux.xpath.QName;

import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.util.CharsRef;

final public class QNameTokenFilter extends TokenFilter {

    private final QNameAttribute qnameAtt = addAttribute(QNameAttribute.class);
    private final CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);
    private final PositionIncrementAttribute posAtt = addAttribute(PositionIncrementAttribute.class);
    private int qnameIndex = 0;
    private CharsRef term;

    protected QNameTokenFilter(TokenStream input) {
        super(input);
        term = new CharsRef();
    }

    @Override
    public boolean incrementToken() throws IOException {
        if (qnameIndex >= qnameAtt.getQNames().size()) {
            if (!input.incrementToken()) {
                return false;
            }
            // emit the input token
            qnameIndex = 0;
            term.copy(termAtt.buffer(), 0, termAtt.length());
        }
        else if (qnameIndex < qnameAtt.getQNames().size()) {
            // emit <qname>:<term>
            posAtt.setPositionIncrement(0);
            QName qname = qnameAtt.getQNames().get(qnameIndex++);
            termAtt.setEmpty();
            termAtt.append(qname.getEncodedName());
            termAtt.append(':');
            termAtt.append(term);            
        }
        return true;
    }

}
