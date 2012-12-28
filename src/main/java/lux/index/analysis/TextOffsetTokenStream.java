package lux.index.analysis;

import java.io.IOException;

import lux.xml.Offsets;
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

    @Override
    protected boolean resetTokenizer(CharSequence text) {
        charSequenceStream = new CharSequenceStream(text);
        OffsetCharFilter offsetCharFilter = null;
        if (offsets != null) {
            charStream = offsetCharFilter = new OffsetCharFilter(charSequenceStream);
        } else {
            charStream = charSequenceStream;
        }
        try {
            tokenizer.reset(charStream);
            if (curNode.getNodeKind() == XdmNodeKind.TEXT && offsets != null) {
                int delta = offsets.getTextLocation(iText++);
                offsetCharFilter.addOffset(0, delta);
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
                    offsetCharFilter.addOffset(dOff, offsets.getDeltaLocation(iDelta) - dOff);
                    ++iDelta;
                }
            }
            return incrementWrappedTokenStream();
        } catch (IOException e) {
            return false;
        }
    }
}

/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/.
 */
