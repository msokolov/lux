package lux.xpath;

public class Root extends AbstractExpression {

    public Root () {
        super (Type.Root);
    }
    
    @Override
    public String toString() {
       return "/";
    }
    
    public boolean isAbsolute() {
        return true;
    }

    public AbstractExpression accept(ExpressionVisitor visitor) {
        return visitor.visit(this);
    }
    
    /** 
     * replace this with the search function call
     * @param search the search function call to use
     * @return the search function call
     */
    public AbstractExpression replaceRoot(FunCall search) {        
        return search;
    }

}
