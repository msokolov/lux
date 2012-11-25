package lux.xml;

import java.util.Arrays;

public final class Offsets {
    
    private int iOffset;
    private int iDelta;
    private int[] textOffsets;
    private int[] deltaLocations;
    private short[] deltas;
    
    public Offsets () {
        textOffsets = new int[1024];
        deltas = new short[1024];
        deltaLocations = new int[1024];
        reset ();
    }
    
    public void reset () {
        iOffset = iDelta = 0;
    }
    
    // store the character offsets of every character reference or entity in the document text,
    // along with the difference between the length of the entity reference and its replacement text.
    // eg, for an &amp; appearing at position 100, we would store (100, 4), since len('&amp;')=5, and len ('&')=1.

    public void addDelta(int deltaLocation, short delta) {
        if (iDelta >= deltas.length) {
            deltas = Arrays.copyOf(deltas, deltas.length + 1024);
            deltaLocations = Arrays.copyOf(deltaLocations, deltaLocations.length + 1024);
        }
        deltaLocations[iDelta] = deltaLocation;
        deltas[iDelta++] = delta;
    }

    // store the character offsets of all of the text nodes in the document: According to StAX javadocs,
    // these will either be bytes or they will be characters, depending on whether the parser
    // was fed a byte stream or a character stream!  However in practice we seem to get character
    // offsets in both cases??
    
    public void addOffset(int characterOffset) {
        // StAX documentation claims this may be a byte offset when fed a byte stream, but
        // that doesn't seem to be the case?
        if (iOffset >= textOffsets.length) {
            textOffsets = Arrays.copyOf(textOffsets, textOffsets.length + 1024);
        }
        textOffsets[iOffset++] = characterOffset;
    }
    
    /**
     * @param i the index of the text node
     * @return the character location in the input character stream of i'th text node.
     */
    public int getTextLocation (int i) {
        return textOffsets[i];
    }
    
    /** 
     * A delta is stored whenever the number of characters in the output token is not the same
     * as the number in the input character stream.
     * @param i the index of the delta
     * @return the character location in the input character stream of the i'th delta.
     */
    public int getDeltaLocation (int i) {
        return deltaLocations[i];
    }

    /**
     * @return the number of deltas found in the input stream
     */
    public int getDeltaCount() {
        return iDelta;
    }

    /**
     * @param i the index of the delta
     * @return the value of the i'th delta
     */
    public int getDelta(int i) {
        return deltas[i];
    }
    
}

/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/.
 */
