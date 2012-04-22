package lux.saxon;

import lux.xpath.FunCall;
import net.sf.saxon.s9api.ItemType;
import net.sf.saxon.s9api.OccurrenceIndicator;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.SequenceType;
import net.sf.saxon.s9api.XdmAtomicValue;
import net.sf.saxon.s9api.XdmValue;

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
    
    @Override
    public SequenceType[] getArgumentTypes() {
        return new SequenceType[] { 
                SequenceType.makeSequenceType(ItemType.STRING, OccurrenceIndicator.ONE),
                SequenceType.makeSequenceType(ItemType.INTEGER, OccurrenceIndicator.ZERO_OR_ONE)
                };
    }
    
    @Override
    public XdmValue call(XdmValue[] arguments) throws SaxonApiException {
        Boolean isPositive = Boolean.valueOf(arguments[1].itemAt(0).getStringValue());
        XdmValue result = super.call(new XdmValue[] {arguments[0] });
        if (isPositive) {
            return result;
        }
        Boolean value = ((XdmAtomicValue)result).getBooleanValue();
        return new XdmAtomicValue(!value);
    }

}
