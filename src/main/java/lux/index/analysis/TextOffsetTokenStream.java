package lux.index.analysis;

import java.io.IOException;


import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmNodeKind;

/**
 * TODO: explain this mess!
 */
public abstract class TextOffsetTokenStream extends XmlTokenStreamBase {

    private int iText;
    private int iDelta;
    private Offsets offsets;
    
    private CharSequenceStream charSequenceStream;

    public TextOffsetTokenStream(XdmNode doc, Offsets offsets) {
        super(doc);
        //charSequenceStream = new CharSequenceStream(null);
        //charStream = new OffsetCharFilter(charSequenceStream);
        this.offsets = offsets;
        iText = 0;
        iDelta = 0;
    }

    protected boolean resetTokenizer(CharSequence text) {
        //charSequenceStream.reset(text);  can't reset a BaseCharFilter :(
        charSequenceStream = new CharSequenceStream(text);
        charStream = new OffsetCharFilter(charSequenceStream);
        try {
            tokenizer.reset(charStream);
            if (curNode.getNodeKind() == XdmNodeKind.TEXT) {
                int delta = offsets.getTextLocation(iText++);
                charStream.addOffset(0, delta);
                // skip over any deltas preceding this text
                while (iDelta < offsets.getDeltaCount() && offsets.getDeltaLocation(iDelta) < delta) {
                    ++iDelta;
                }
                // apply all the deltas occurring within this text
                while (iDelta < offsets.getDeltaCount()) {
                    // accumulate the deltas
                    delta += offsets.getDelta(iDelta);
                    // calculate the offset within this text where the delta is
                    int dOff = offsets.getDeltaLocation(iDelta) - delta;
                    if (dOff > text.length()) {
                        break;
                    }
                    // the offset at dOff is the difference between the original position and dOff
                    charStream.addOffset(dOff, offsets.getDeltaLocation(iDelta) - dOff);
                    ++iDelta;
                }
            }
            return incrementWrappedTokenStream();
        } catch (IOException e) {
            return false;
        }
    }
}