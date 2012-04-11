package lux.xpath;

import org.apache.lucene.search.Query;

import lux.XPathQuery;

public class PathOptimizer implements Visitor<AbstractExpression> {

    private XPathQuery query;
    private AbstractExpression expr;
    
    public AbstractExpression optimized () {
        if (query == null) {
            return expr;
        }
        return new FunCall (FunCall.luxSearchQName, new LiteralExpression(query.toString()), expr);
    }
    
    public void visit(AbstractExpression expr) {
        System.out.println ("visit " + expr);
        this.expr = expr;
        visitSubs(expr);
    }

    private void visitSubs(AbstractExpression expr) {
        for (AbstractExpression sub : expr.getSubs()) {
            visit(sub);
        }
    }
    
    public void visit (Root expr) {
        PathOptimizer sub = new PathOptimizer();
        sub.visitSubs(expr);
        this.expr = sub.optimized();
    }
    
    // An absolute path is a PathExpression whose left-most expression is a Root.
    // divide the expression tree into regions bounded on the right and left by Root
    // then optimize these absolute paths as searches, and then optimize some functions 
    // like count(), exists(), not() with arguments that are searches
    //
    // also we may want to collapse some path expressions when they return documents;
    // like //a/root().  in this case 
    public void visit(PathExpression pathExpr) {
        System.out.println ("visit path " + pathExpr);
        visitSubs(pathExpr);
    }
    
    public void visit(PathStep step) {
        
    }

}
