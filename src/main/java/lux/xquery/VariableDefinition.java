package lux.xquery;

import lux.xpath.AbstractExpression;

public class VariableDefinition {
    private final AbstractExpression variable;
    private final AbstractExpression value;
    
    public VariableDefinition (AbstractExpression abstractExpression, AbstractExpression abstractExpression2) {
        this.variable = abstractExpression;
        this.value = abstractExpression2;
    }
    
    public void toString (StringBuilder buf) {
        buf.append ("declare variable ");
        variable.toString(buf);
        buf.append (" := ");
        value.toString (buf);
        buf.append (";\n");
    }
}
