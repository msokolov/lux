package lux.xpath;

import java.util.ArrayList;
import java.util.HashMap;

import lux.XPathQuery;
import lux.api.ValueType;
import lux.xpath.PathStep.Axis;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.TermQuery;

/**
 * Prepares an XPath expression tree for indexed execution against a
 * Lux data store.
 *
 * The general strategy here is to consider each expression in isolation,
 * determining whether it imposes any restriction on its context, and then
 * to compose such constraints into queries, execute the queries and
 * evaluate the expressions against the resulting document. An expression
 * that can only be satisfied in the context of a document matching an
 * index query imposes such a constraint.  The queries are composed
 * according to the semantics of the expressions and functions.  Absolute
 * sequences (occurrences of /) generally inhibit query composition; a
 * query is generated for each absolute expression (more precisely, for
 * each expression containing a / - and some functions like lux:search(),
 * collection(), etc.
 */
public class PathOptimizer extends ExpressionVisitor {

    private ArrayList<XPathQuery> queryStack;
    //private AbstractExpression expr;
    
    private final String attrQNameField = "lux_att_name_ms";
    private final String elementQNameField = "lux_elt_name_ms";
    
    private static final XPathQuery MATCH_ALL_QUERY = XPathQuery.MATCH_ALL;
    
    public PathOptimizer() {
        queryStack = new ArrayList<XPathQuery>();
        push(MATCH_ALL_QUERY);
    }
    
    /**
     * Prepares an XPath expression tree for indexed execution against a
     * Lux data store.  Inserts calls to lux:search that execute queries
     * selecting a set of documents against which the expression, or some
     * of its sub-expressions, are evaluated.  The queries will always
     * select a superset of the documents that actually contribute results
     * so that the result is the same as if the expression was evaluated
     * against all the documents in collection() as its context (sequence
     * of context items?).
     *
     * @param expr the expression to optimize
     */
    public AbstractExpression optimize(AbstractExpression expr) {
        System.out.println ("visit " + expr);
        expr.accept (this);
        if (!queryStack.isEmpty() && expr.isAbsolute()) {
            return expr.replaceRoot(createSearchCall(pop()));
        }
        return expr;
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
        XPathQuery rq = pop();
        XPathQuery lq = pop();
        XPathQuery query = combineQueries (lq,  Occur.MUST, rq, Occur.MUST, rq.getResultType());
        push(query);
    }
    
    public void visit(PathStep step) {
        QName name = step.getNodeTest().getQName();
        Axis axis = step.getAxis();
        boolean isMinimal = (axis == Axis.Descendant|| axis == Axis.DescendantSelf|| axis == Axis.Attribute);
        XPathQuery query;
        if (name == null) {
            ValueType type = step.getNodeTest().getType();
            if (axis == Axis.Self && (type == ValueType.NODE || type == ValueType.VALUE)) {
                // if axis==self and the type is loosely specified, use the prevailing type
                // TODO: handle this when combining queries?
                type = getQuery().getResultType();
            }
            else if (axis == Axis.AncestorSelf && (type == ValueType.NODE || type == ValueType.VALUE)
                    && getQuery().getResultType() == ValueType.DOCUMENT) {
                type = ValueType.DOCUMENT;
            }
            query = new XPathQuery (null, new MatchAllDocsQuery(), getQuery().getFacts(), type);
        } else {
            TermQuery termQuery = nodeNameTermQuery(step.getAxis(), name);
            query = new XPathQuery (null, termQuery, isMinimal ? XPathQuery.MINIMAL : 0, step.getNodeTest().getType());
        }
       push(query);
    }
    
    /**
     * TODO: some operations *commute with lux:search*.  In those cases, we do better to wrap the search around
     * this expression, rather than around each of its children.  Basically we want to "pull up" the search operation 
     * to the highest allowable level.
     * 
     * Each absolute subexpression S is wrapped by a call to search(), effectively replacing it
     * with search(QS)/S, where QS is the query derived corresponding to S
     * @param expr an expression
     */
    protected void injectSearch(AbstractExpression expr) {
        AbstractExpression[] subs = expr.getSubs();
        int n = 0;
        for (int i = 0; i < subs.length; i ++) {
            if (subs[i].isAbsolute()) {
                int j = subs.length - i - 1;
                FunCall search = getSearchCall(j);
                queryStack.add (j, MATCH_ALL_QUERY);
                subs[i] = subs[i].replaceRoot (search);
                ++n;
            }
        }
        for (int i = 0; i < n; i++) {
            push (MATCH_ALL_QUERY);
        }
    }


    
    /**
     * If a function F is emptiness-preserving, in other words F(a,b,c...)
     * is empty ( =()) if *any* of its arguments are empty, and is
     * non-empty if *all* of its arguments are non-empty, then its
     * argument's queries are combined with Occur.MUST.  This is the
     * default case, and such functions are not mapped explicitly in
     * fnArgParity.
     *
     * If the converse is true, ie the function returns empty when *any* of
     * its args are non-empty and non-empty when *all* of its args are
     * empty, we map it as Occur.MUST_NOT.  
     *
     * If the function returns empty when all of its arguments are empty,
     * and may return a value when not all of its arguments are empty, then
     * we would combine the arguments queries with Occur.SHOULD.  But does
     * this occur for any of the standard XPath 2 functions?
     *
     * In other cases, like deep-equal(), which always returns something
     * whether its arguments exist or not, no restriction can be imposed on
     * the allowable context items, so no optimization is attempted, and a
     * match-all query is generated.
     *
     * count() (and maybe max(), min(), and avg()?) is optimized as a
     * special case.
     */

    public void visit(FunCall funcall) {
        injectSearch(funcall);
        Occur occur;
        QName name = funcall.getQName();
        if (! name.getNamespaceURI().isEmpty()) {            
            // we know nothing about this function
            occur = Occur.SHOULD;
        }
        // a built-in XPath 2 function
        if (fnArgParity.containsKey(name.getLocalPart())) {
            occur = fnArgParity.get(name.getLocalPart());
        } else {
            occur = Occur.MUST;
        }
        combineTopQueries(funcall.getSubs().length, occur, funcall.getReturnType());
        if (name.getLocalPart().equals ("count")) {
            getQuery().setFact (XPathQuery.COUNTING, true);
        }
    }

    // FIXME: fill out the rest of this table
    protected static HashMap<String, Occur> fnArgParity = new HashMap<String, Occur>();
    static {
        fnArgParity.put("collection", null);
        fnArgParity.put("doc", null);
        fnArgParity.put("uri-collection", null);
        fnArgParity.put("unparsed-text", null);
        fnArgParity.put("generate-id", null);
        fnArgParity.put("deep-equal", null);
        fnArgParity.put("error", null);
        fnArgParity.put("empty", Occur.MUST_NOT);
        // fnArgParity.put("not", Occur.MUST);
    };
    
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
        // FIXME - should have value type=VALUE?
        push(XPathQuery.MATCH_ALL);
    }

    @Override
    public void visit(BinaryOperation op) {
        System.out.println ("visit binary op " + op);
        injectSearch(op);
        XPathQuery rq = pop();
        XPathQuery lq = pop();
        ValueType type = null;
        Occur occur = Occur.SHOULD;
        boolean minimal = false;
        switch (op.getOperator()) {
        
        case AND: 
            occur = Occur.MUST;
        case OR:
            minimal = true;
        case EQUALS: case NE: case LT: case GT: case LE: case GE:
            type = ValueType.BOOLEAN;
            break;
            
        case ADD: case SUB: case DIV: case MUL: case IDIV: case MOD:
            type = ValueType.ATOMIC;
            // Casting an empty sequence to an operand of an operator expeciting atomic values raises an error
            // and to be properly error-preserving we use SHOULD here
            // occur = Occur.MUST
            break;
            
        case AEQ: case ANE: case ALT: case ALE: case AGT: case AGE:
            type = ValueType.BOOLEAN;
            // occur = Occur.MUST;
            break;
            
        case IS: case BEFORE: case AFTER:
            // occur = Occur.MUST;
            break;
            
        case INTERSECT:
            occur = Occur.MUST;
        case UNION:
            minimal = true;
            break;
            
        case EXCEPT:
            type = lq.getResultType().promote (rq.getResultType());
            push (combineQueries(lq, Occur.MUST, rq, Occur.MUST_NOT, type));
            return;
        }
        XPathQuery query = lq.combine(rq, occur);
        if (type != null) {
            query.setType(type);
        }
        if (minimal == false) {
            query.setFact(XPathQuery.MINIMAL, false);
        }
        push (query);
    }

    @Override
    public void visit(LiteralExpression literal) {
        push (XPathQuery.MATCH_NONE);
    }

    @Override
    public void visit(Predicate predicate) {
        XPathQuery filterQuery = pop();
        if (predicate.getFilter().isAbsolute()) {
            predicate.getFilter().replaceRoot(createSearchCall(filterQuery));
            filterQuery = XPathQuery.MATCH_ALL;
        }
        XPathQuery baseQuery = pop();
        XPathQuery query = baseQuery.combine(filterQuery, Occur.MUST);        
        // This is a counting expr if its base expr is
        query.setFact(XPathQuery.COUNTING, baseQuery.isFact(XPathQuery.COUNTING));
        push (query);
        System.out.println ("visit predicate " + predicate);
    }
    
    private FunCall getSearchCall (int i) {
        XPathQuery query = queryStack.get(queryStack.size() - i - 1);
        return createSearchCall(query);
    }
    
    private FunCall createSearchCall(XPathQuery query) {
        return new FunCall (FunCall.luxSearchQName, ValueType.DOCUMENT, 
                new LiteralExpression(query.toString()));
    }

    @Override
    public void visit(Sequence sequence) {
        injectSearch(sequence);
        combineTopQueries (sequence.getSubs().length, Occur.SHOULD);
    }

    private void combineTopQueries (int n, Occur occur) {
        combineTopQueries (n, occur, null);
    }

    private void combineTopQueries (int n, Occur occur, ValueType valueType) {
        XPathQuery query = pop();
        for (int i = 0; i < n-1; i++) {
            query = pop().combine(query, occur);
        }
        if (valueType != null && valueType != query.getResultType()) {
            if (query.isImmutable())
                query = new XPathQuery(null, query.getQuery(), query.getFacts(), valueType);
            else
                query.setType(valueType);
        }
        push (query);
    }

    @Override
    public void visit(SetOperation expr) {
        // TODO Auto-generated method stub
        
    }


    @Override
    public void visit(UnaryMinus predicate) {
        // TODO Auto-generated method stub
        
    }

    public XPathQuery getQuery() {        
        return queryStack.get(queryStack.size()-1);
    }
    
    void push (XPathQuery query) {
        queryStack.add(query);
    }
    
    XPathQuery pop () {
        return queryStack.remove(queryStack.size()-1);
    }

}
