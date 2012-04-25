package lux.saxon;

import lux.xpath.FunCall;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.value.SequenceType;

public class LuxExists extends LuxSearch {
    
    public LuxExists(Saxon saxon) {
        super(saxon);
    }

    @Override
    public StructuredQName getFunctionQName() {
        return new StructuredQName("lux", FunCall.LUX_NAMESPACE, "exists");
    }

    @Override
    public SequenceType getResultType(SequenceType[] suppliedArgumentTypes) {
        return SequenceType.SINGLE_BOOLEAN;
    }    

}
