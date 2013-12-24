package lux.index.analysis;

import java.io.IOException;

import lux.xml.Offsets;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmNodeKind;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;

/**
 * <p>This TokenStream records the offsets (character positions in the original text) of every token.
 * It records the start offset of each text node, and whenever there is a difference between the 
 * length of the serialized XML and the length of the text, it records the offset just after the 
 * discrepancy.  For example if a character entity (like &amp;amp;) occurs in the XML, this is translated
 * to "&amp;" in the text, and a character offset is recorded for the character just following the "&amp;".
 * </p>
 */
public abstract class TextOffsetTokenStream extends XmlTokenStreamBase {

    private int iText;
    private int iDelta;
    private Offsets offsets;
    
    private CharSequenceStream charSequenceStream;

    public TextOffsetTokenStream(String fieldName, Analyzer analyzer, TokenStream wrapped, XdmNode doc, Offsets offsets, Processor processor) {
        super(fieldName, analyzer, wrapped, processor);
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
            updateOffsets (offsetCharFilter, text.length());
        } else {
            charStream = charSequenceStream;
        }
        try {
            reset ();
            // this is what we had before refactoring:
            return incrementWrappedTokenStream();
            // but shouldn't it really be this?:
            // return incrementToken();
        } catch (IOException e) {
            return false;
        }
    }

    private void updateOffsets (OffsetCharFilter offsetCharFilter, int length) {
        if (curNode.getNodeKind() == XdmNodeKind.TEXT && offsets != null) {
            int location = offsets.getTextLocation(iText++); // location in the original XML
            offsetCharFilter.addOffset(0, location);
            // skip over any deltas preceding this text
            int deltaLocation = offsets.getDeltaLocation(iDelta);
            while (iDelta < offsets.getDeltaCount() && deltaLocation < location) {
                deltaLocation = offsets.getDeltaLocation(++iDelta);
            }
            // apply all the deltas occurring within this text
            while (iDelta < offsets.getDeltaCount()) {
                // accumulate the deltas
                location += offsets.getDelta(iDelta);
                // calculate the offset within this text (not the original XML-encoded text) where the delta is
                int dOff = deltaLocation - location;
                if (dOff > length) {
                    break;
                }
                // the offset at dOff is the difference between the original position and dOff
                offsetCharFilter.addOffset(dOff, location);
                deltaLocation = offsets.getDeltaLocation(++iDelta);
            }
        }
    }
}

/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/.
 */
