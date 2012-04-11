package lux.xpath;

public class Root extends AbstractExpression {

    public Root () {
        super (Type.Root);
        subs = new AbstractExpression[0];
    }
    
    @Override
    public String toString() {
       return "/";
    }
    
    public boolean isAbsolute() {
        return true;
    }

    public void accept(Visitor<AbstractExpression> visitor) {
        visitor.visit(this);
    }
}
