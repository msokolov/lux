package lux.saxon;

import lux.xpath.FunCall;
import net.sf.saxon.s9api.ItemType;
import net.sf.saxon.s9api.OccurrenceIndicator;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.SequenceType;

public class LuxCount extends LuxSearch {
    
    public LuxCount(Saxon saxon) {
        super(saxon);
    }
    
    public QName getName() {
        return new QName("lux", FunCall.LUX_NAMESPACE, "count");
    }
    
    public SequenceType getResultType() {
        return SequenceType.makeSequenceType(ItemType.INTEGER, OccurrenceIndicator.ONE);
    }

}
