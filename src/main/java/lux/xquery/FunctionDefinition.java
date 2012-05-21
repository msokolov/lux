package lux.xquery;

import lux.api.ValueType;
import lux.xpath.AbstractExpression;
import lux.xpath.FunCall;
import lux.xpath.QName;

public class FunctionDefinition extends FunCall {

    private final AbstractExpression body;
    
    public FunctionDefinition (QName name, ValueType returnType, Variable[] args, AbstractExpression body) {
        super (name, returnType, args);
        this.body = body;
    }
    
    @Override public void toString (StringBuilder buf) {
        buf.append ("declare function ");
        super.toString(buf);
        buf.append ("{ ");
        body.toString (buf);
        buf.append (" };\n");
    }
}
