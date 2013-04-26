package lux.index.analysis;

import java.io.IOException;

import org.apache.lucene.analysis.BaseCharFilter;
import org.apache.lucene.analysis.CharStream;

/** exposes the offset map so it can be set externally. 
 * It seems as if it would be better to be able to reset() and reuse this?
 * But we have to make a new BaseCharFilter for every text node.
 * */

public class OffsetCharFilter extends BaseCharFilter {
    
    public OffsetCharFilter(CharStream in) {
        super(in);
    }

    public void addOffset (int off, int cumulativeDiff) {
        addOffCorrectMap(off, cumulativeDiff);
    }

    @Override
    public int read(char[] cbuf, int off, int len) throws IOException {
        return input.read(cbuf, off, len);
    }

}

/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/.
 */
