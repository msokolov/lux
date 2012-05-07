package lux.saxon;

import lux.index.XmlIndexer;
import lux.xpath.AbstractExpression;
import lux.xpath.ExpressionVisitorBase;
import lux.xpath.FunCall;
import lux.xpath.LiteralExpression;
import lux.xpath.QName;

public class UnOptimizer extends ExpressionVisitorBase {

    private static final QName luxSearchQName = new QName ("lux", "search", "lux");
    private long indexOptions;
    
    public UnOptimizer (long indexOptions) {
        this.indexOptions = indexOptions;
    }
    
    public AbstractExpression unoptimize (AbstractExpression aex) {
        aex.accept(this);
        return aex;
    }
    
    @Override
    public AbstractExpression visit(FunCall func) {
        if (func.getQName().equals(luxSearchQName)) {
            if ((indexOptions & XmlIndexer.INDEX_PATHS) != 0) {
                func.getSubs()[0] = new LiteralExpression ("{}");
            } else {
                func.getSubs()[0] = new LiteralExpression ("*:*");
            }
        }
        return func;
    }

}
