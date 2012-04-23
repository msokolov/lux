package lux.saxon;

import lux.xpath.FunCall;
import net.sf.saxon.s9api.ItemType;
import net.sf.saxon.s9api.OccurrenceIndicator;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.SequenceType;

public class LuxExists extends LuxSearch {
    
    public LuxExists(Saxon saxon) {
        super(saxon);
    }
    
    @Override
    public QName getName() {
        return new QName("lux", FunCall.LUX_NAMESPACE, "exists");
    }
    
    @Override
    public SequenceType getResultType() {
        return SequenceType.makeSequenceType(ItemType.BOOLEAN, OccurrenceIndicator.ONE);
    }

}
