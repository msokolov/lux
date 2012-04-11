package lux.xpath;

import java.util.LinkedList;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.BooleanClause.Occur;

import lux.XPathQuery;
import lux.api.ValueType;
import lux.xpath.PathStep.Axis;

public class PathOptimizer extends ExpressionVisitor {

    private LinkedList<XPathQuery> queryStack;
    //private AbstractExpression expr;
    
    private final String attrQNameField = "lux_att_name_ms";
    private final String elementQNameField = "lux_elt_name_ms";
    
    private static final XPathQuery MATCH_ALL_QUERY = XPathQuery.MATCH_ALL;
    
    public PathOptimizer() {
        queryStack = new LinkedList<XPathQuery>();
        queryStack.push(MATCH_ALL_QUERY);
    }
    
   
    /*
     public AbstractExpression optimized () {
        if (queryStack.isEmpty()) {
            return expr;
        }
        return new FunCall (FunCall.luxSearchQName, new LiteralExpression(getQuery().toString()), expr);
    }
    */
    
    public void visit(AbstractExpression expr) {
        System.out.println ("visit " + expr);
    }
    
    public void visit (Root expr) {
        push (MATCH_ALL_QUERY);
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
        XPathQuery lq = pop();
        XPathQuery rq = pop();
        XPathQuery query = combineQueries (lq,  Occur.MUST, rq, Occur.MUST, rq.getResultType());
        push(query);
    }
    
    public void visit(PathStep step) {
        QName name = step.getNodeTest().getQName();
        XPathQuery query;
        if (name == null) {           
            query = new XPathQuery (null, new MatchAllDocsQuery(), 0, step.getNodeTest().getType());
        } else {
            TermQuery termQuery = nodeNameTermQuery(step.getAxis(), name);
            query = new XPathQuery (null, termQuery, 0, step.getNodeTest().getType());
        }
       push(query);
    }
    
    public void visit(FunCall funcall) {
        if (funcall.getQName().equals(FunCall.notQName)) {
            ((BooleanQuery)getQuery().getQuery()).getClauses()[0].setOccur(Occur.MUST);
        }
    }
        
    private static final XPathQuery combineQueries(XPathQuery lq, Occur loccur, XPathQuery rq, Occur roccur, ValueType valueType) {
        return lq.combine(loccur, rq, roccur, valueType);
    }
    
    private TermQuery nodeNameTermQuery(Axis axis, QName name) {
        String nodeName = name.getClarkName(); //name.getLocalPart();
        String fieldName = (axis == Axis.Attribute) ? attrQNameField : elementQNameField;
        TermQuery termQuery = new TermQuery (new Term (fieldName, nodeName));
        return termQuery;
    }
    
    @Override
    public void visit(Dot dot) {
    }


    @Override
    public void visit(BinaryOperation op) {
        // TODO Auto-generated method stub
        
    }


    @Override
    public void visit(LiteralExpression literal) {
        // TODO Auto-generated method stub
        
    }


    @Override
    public void visit(Predicate predicate) {
        // TODO Auto-generated method stub
        
    }


    @Override
    public void visit(Sequence predicate) {
        // TODO Auto-generated method stub
        
    }


    @Override
    public void visit(SetOperation predicate) {
        // TODO Auto-generated method stub
        
    }


    @Override
    public void visit(UnaryMinus predicate) {
        // TODO Auto-generated method stub
        
    }

    public XPathQuery pop() {
        return queryStack.pop();
    }
    
    public void push(XPathQuery query) {
        queryStack.push(query);
    }
    
    public XPathQuery getQuery() {        
        return queryStack.getFirst();
    }


}
