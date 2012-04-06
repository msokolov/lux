package lux.xpath;

public class Root extends AbstractExpression {

    public Root () {
        super (Type.Root);
    }
    
    @Override
    public String toString() {
       return "/";
    }

}
