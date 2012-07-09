package lux.index.analysis;

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

}