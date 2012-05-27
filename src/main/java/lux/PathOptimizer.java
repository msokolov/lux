/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */

package lux;

import java.util.ArrayList;
import java.util.HashMap;

import lux.api.ValueType;
import lux.index.XmlField;
import lux.index.XmlIndexer;
import lux.lucene.LuxTermQuery;
import lux.lucene.SurroundTerm;
import lux.xpath.AbstractExpression;
import lux.xpath.BinaryOperation;
import lux.xpath.Dot;
import lux.xpath.FunCall;
import lux.xpath.LiteralExpression;
import lux.xpath.PathExpression;
import lux.xpath.PathStep;
import lux.xpath.PathStep.Axis;
import lux.xpath.Predicate;
import lux.xpath.QName;
import lux.xpath.Root;
import lux.xpath.Sequence;
import lux.xpath.Subsequence;
import lux.xquery.Variable;
import lux.xquery.XQuery;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.Query;

/**
 * Prepares an XPath expression tree for indexed execution against a
 * Lux data store.
 *
 * The general strategy here is to consider each expression in isolation,
 * determining whether it imposes any restriction on its context, and then
 * to compose such constraints into queries, to be executed by a searcher, and
 * the XPath/XQuery expressions evaluated against the resulting documents. 
 * 
 * Absolute expressions are targets for optimization; the optimizer attempts to form queries that 
 * retrieve the smallest possible subset of available documents for which the expression generates
 * a non-empty result sequence.  The queries for these sub-expressions are composed
 * according to the semantics of the combining expressions and functions.  Absolute
 * sequences (occurrences of /) generally are not composed together - they form independent subqueries.
 * 
 * When optimizing for path indexes, we attempt to compute the "vertical" or node distances between adjacent 
 * sub-expressions.  Child path steps introduce a zero distance when there is a named step on either side;
 * wildcard child steps introduce a distance of one, and descendant (or ancestor?) steps count as infinite distance,
 * although order is preserved.
 */
public class PathOptimizer extends ExpressionVisitorBase {

    private final ArrayList<XPathQuery> queryStack;
    private final XmlIndexer indexer;
    private final XPathQuery MATCH_ALL;
    private final XPathQuery UNINDEXED;
    
    private final static String attrQNameField = XmlField.ATT_QNAME.getName();
    private final static String elementQNameField = XmlField.ELT_QNAME.getName();
    
    public PathOptimizer(XmlIndexer indexer) {
        queryStack = new ArrayList<XPathQuery>();
        MATCH_ALL = XPathQuery.getMatchAllQuery(indexer.getOptions());
        push(MATCH_ALL);
        UNINDEXED = XPathQuery.getUnindexedQuery(indexer.getOptions());
        this.indexer = indexer;
    }
    

    /**
     * Prepares an XQuery module for indexed execution against a
     * Lux data store.  See {@link #optimize(AbstractExpression)}.
     * 
     * @param query the query to optimize
     * @return the optimized query
     */
    public XQuery optimize(XQuery query) {
        AbstractExpression main = query.getBody();
        // Don't attempt to optimize if no indexes are available, or if the query has no body
        if (main != null && (indexer.getOptions() & XmlIndexer.INDEXES) != 0) {
            main = optimize (main);
            return new XQuery(query.getDefaultElementNamespace(), query.getDefaultFunctionNamespace(), query.getDefaultCollation(), 
                    query.getNamespaceDeclarations(), query.getVariableDefinitions(), query.getFunctionDefinitions(), 
                    main, query.isPreserveNamespaces(), query.isInheritNamespaces());
        }
        // TODO optimize function definitions
        // do nothing
        return query;
    }
    
    /**
     * Prepares an XQuery expression for indexed execution against a
     * Lux data store.  Inserts calls to lux:search that execute queries
     * selecting a set of documents against which the expression, or some
     * of its sub-expressions, are evaluated.  The queries will always
     * select a superset of the documents that actually contribute results
     * so that the result is the same as if the expression was evaluated
     * against all the documents in collection() as its context (sequence
     * of context items?).
     *
     * @param expr the expression to optimize
     * @return the optimized expression
     */
    public AbstractExpression optimize(AbstractExpression expr) {
        expr = expr.accept (this);
        return optimizeExpression (expr, 0, 0);
    }
    
    /**
     * @param expr the expression to optimize
     * @param j the query stack depth at which expr's query is to be found
     * @param facts additional facts to apply to the query
     */
    private AbstractExpression optimizeExpression (AbstractExpression expr, int j, int facts) {
        if (expr.isAbsolute()) {
            FunCall search = getSearchCall(j, facts);
            if (search.getReturnType().equals(ValueType.DOCUMENT)) {
                // Avoid the need to sort the results of this expression so that it can be 
                // embedded in a subsequence or similar and evaluated lazily.
                AbstractExpression tail = expr.getTail();
                if (tail != null) {
                    return new Predicate (search, tail);
                }
            }
            expr = expr.replaceRoot (search);
        }
        return expr;
    }

    /**
     * Each absolute subexpression S is joined with a call to lux:search(), 
     * effectively replacing it with search(QS)/S, where QS is the query derived 
     * corresponding to S.  In addition, if S returns documents, the foregoing expression
     * is wrapped by root(); root(search(QS)/S)
     * 
     * @param expr an expression
     * @param facts assertions about the search to be injected, if any.  These are or-ed with 
     * facts found in the search query.
     */
    protected void optimizeSubExpressions(AbstractExpression expr, int facts) {
        AbstractExpression[] subs = expr.getSubs();
        for (int i = 0; i < subs.length; i ++) {
            subs[i] = optimizeExpression (subs[i], subs.length - i - 1, facts);
        }
    }    
    
    public AbstractExpression visit (Root expr) {
        push (MATCH_ALL);
        return expr;
    }
    
    /**
     * Conjoin the queries for the two expressions joined by the path.
     *
     * PathExpressions can join together Dot, Root, PathStep, FunCall, PathExpression,and maybe Variable?
     */
    @Override
    public AbstractExpression visit(PathExpression pathExpr) {
        XPathQuery rq = pop();
        XPathQuery lq = pop();        
        XPathQuery query = combineAdjacentQueries(pathExpr.getLHS(), pathExpr.getRHS(), lq, rq, ResultOrientation.RIGHT);
        push(query);
        return pathExpr;
    }
    
    @Override
    public AbstractExpression visit(Predicate predicate) {
        // Allow the base expression to be optimized later so we have an opportunity to combine the 
        // predicate query with the base query
        optimizeExpression (predicate.getFilter(), 0, 0);
        XPathQuery filterQuery = pop();
        XPathQuery baseQuery = pop();
        
        XPathQuery query = combineAdjacentQueries(predicate.getBase(), predicate.getFilter(), baseQuery, filterQuery, ResultOrientation.LEFT);
        // This is a counting expr if its base expr is
        query.setFact(XPathQuery.COUNTING, baseQuery.isFact(XPathQuery.COUNTING));
        push (query);
        return predicate;
    }
    
    private enum ResultOrientation { LEFT, RIGHT };

    /**
     * Combine queries from two adjacent subexpressions
     * @param left the left (upper) subexpression
     * @param right the right (lower) subexpression
     * @param lq the left query
     * @param rq the right query
     * @param orient the orientation of the combining expression - if RIGHT, then result items are 
     * drawn from the right subexpression (as for paths), and for LEFT, result items from the left
     * (as for predicates).
     * @return the combined query
     */
    private XPathQuery combineAdjacentQueries(AbstractExpression left, AbstractExpression right, XPathQuery lq, XPathQuery rq,
            ResultOrientation orient) {
        XPathQuery query;
        Integer rSlop=null, lSlop=null;
        if (indexer.isOption(XmlIndexer.INDEX_PATHS)) {
            SlopCounter slopCounter = new SlopCounter ();

            // count the left slop of the RHS
            right.accept (slopCounter);
            rSlop = slopCounter.getSlop();

            // count the right slop of the LHS
            if (rSlop != null) {
                slopCounter.reset ();
                slopCounter.setReverse (true);
                left.accept (slopCounter);
                lSlop = slopCounter.getSlop();
            }
        }
        ValueType resultType = (orient == ResultOrientation.RIGHT) ? rq.getResultType() : lq.getResultType();
        if (rSlop != null && lSlop != null) {
            // total slop is the distance between the two path components.
            query = lq.combine(rq, Occur.MUST, resultType, rSlop + lSlop);
        } else {
            query = combineQueries (lq, Occur.MUST, rq, resultType);
        }
        return query;
    }
    
    public AbstractExpression visit(PathStep step) {
        QName name = step.getNodeTest().getQName();
        Axis axis = step.getAxis();
        boolean isMinimal;
        if (axis == Axis.Descendant || axis == Axis.DescendantSelf || axis == Axis.Attribute) {
            isMinimal = true;
        } 
        else if (axis == Axis.Child && getQuery().isFact(XPathQuery.MINIMAL) && ValueType.NODE.is(getQuery().getResultType())) {
            // special case for //descendant-or-self::node()/child::element(xxx)
            isMinimal = true;
        }
        else {
            isMinimal = false;
        }
        XPathQuery query;
        if (name == null) {
            ValueType type = step.getNodeTest().getType();
            if (axis == Axis.Self && (type == ValueType.NODE || type == ValueType.VALUE)) {
                // if axis==self and the type is loosely specified, use the prevailing type
                type = getQuery().getResultType();
            }
            else if (axis == Axis.AncestorSelf && (type == ValueType.NODE || type == ValueType.VALUE)
                    && getQuery().getResultType() == ValueType.DOCUMENT) {
                type = ValueType.DOCUMENT;
            }
            query = XPathQuery.getQuery(MATCH_ALL.getQuery(), getQuery().getFacts(), type, indexer.getOptions());
        } else {
            Query termQuery = nodeNameTermQuery(step.getAxis(), name);
            query = XPathQuery.getQuery(termQuery, isMinimal ? XPathQuery.MINIMAL : 0, step.getNodeTest().getType(), indexer.getOptions());
        }
       push(query);
       return step;
    }
    
    /**
     * If a function F is emptiness-preserving, in other words F(a,b,c...)
     * is empty ( =()) if *any* of its arguments are empty, and is
     * non-empty if *all* of its arguments are non-empty, then its
     * argument's queries are combined with Occur.MUST.  This is the
     * default case, and such functions are not mapped explicitly in
     * fnArgParity.
     *
     * Otherwise, no optimization is possible, and the argument queries are combined with Occur.SHOULD.
     *
     * count() (and maybe max(), min(), and avg()?) is optimized as a
     * special case.
     * @return 
     */

    public AbstractExpression visit(FunCall funcall) {
        QName name = funcall.getName();
        // Try special function optimizations, like count(), exists(), etc.
        FunCall luxfunc = optimizeFunCall (funcall);
        if (luxfunc != funcall) {
            return luxfunc;
        }
        // see if the function args can be converted to searches.
        optimizeSubExpressions(funcall, 0);
        Occur occur;
        if (! name.getNamespaceURI().equals (FunCall.FN_NAMESPACE)) {
            // There's something squirrely here - we know nothing about this function; it's best 
            // not to attempt any optimization, so we will throw away any filters coming from the
            // function arguments
            occur = Occur.SHOULD;
        }
        // a built-in XPath 2 function
        else if (fnArgParity.containsKey(name.getLocalPart())) {
            occur = fnArgParity.get(name.getLocalPart());
        } else {
            occur = Occur.MUST;
        }
        if (occur == Occur.SHOULD) {
            for (int i = funcall.getSubs().length; i > 0; --i) {
                pop();
            }
            push (XPathQuery.getQuery(MATCH_ALL.getQuery(), 0, ValueType.VALUE, indexer.getOptions()));
        } else {
            combineTopQueries(funcall.getSubs().length, occur, funcall.getReturnType());
        }
        return funcall;
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
        fnArgParity.put("empty", Occur.SHOULD);
        fnArgParity.put("not", Occur.SHOULD);
    };
    
    /**
     * Possibly convert this function call to a lux: function.  If we do that, all the arguments' queries
     * will be removed from the stack.  Otherwise leave them there for the caller.
     * @param funcall a function call to be optimized
     * @return an optimized function, or the original function call
     */
    
    public FunCall optimizeFunCall (FunCall funcall) {
        AbstractExpression[] subs = funcall.getSubs();
        if (subs.length != 1 || ! subs[0].isAbsolute()) {            
            return funcall;
        }
        XPathQuery query = pop();
        // can only use these function optimizations when we are sure that its argument expression
        // is properly indexed - MINIMAL here guarantees that every document matching the query will 
        // produce a non-empty result in the function's argument
        if (query.isMinimal()) {
            int functionFacts = 0;
            ValueType returnType = null;
            QName qname = null;
            if (funcall.getName().equals(FunCall.FN_COUNT) && query.getResultType().is(ValueType.DOCUMENT)) {
                functionFacts = XPathQuery.COUNTING;
                returnType = ValueType.INT;
                qname = FunCall.LUX_COUNT;
            } 
            else if (funcall.getName().equals(FunCall.FN_EXISTS)) {
                functionFacts = XPathQuery.BOOLEAN_TRUE;
                returnType = ValueType.BOOLEAN;
                qname = FunCall.LUX_EXISTS;
            }
            else if (funcall.getName().equals(FunCall.FN_EMPTY)) {
                functionFacts = XPathQuery.BOOLEAN_FALSE;
                returnType = ValueType.BOOLEAN_FALSE;
                qname = FunCall.LUX_EXISTS;
            }
            else if (funcall.getName().equals(FunCall.FN_COLLECTION)) {
                functionFacts = XPathQuery.DOCUMENT_RESULTS;
                returnType = ValueType.DOCUMENT;
                // TODO: treat argument as lucene filter query?
                qname = FunCall.LUX_SEARCH;
            }
            if (qname != null) {
                long facts;
                if (query.isImmutable()) {
                    facts = functionFacts | XPathQuery.MINIMAL;
                } else {
                    query.setType(ValueType.INT);
                    query.setFact(functionFacts, true);
                    facts = query.getFacts();
                }
                // apply no restrictions to the enclosing scope:
                push (MATCH_ALL);
                return new FunCall (qname, returnType, 
                        new LiteralExpression (query.toString()),
                        new LiteralExpression (facts));
            }
        }
        // No optimization, but indicate that this function returns an int?
        // FIXME: this is just wrong? It must have come from some random test case, 
        // but isn't generally true.  If we really need it, move this logic up to the caller - it has nothing
        // to do with optimizing this funcall.
        if (query.isImmutable()) {
            query = XPathQuery.getQuery(query.getQuery(), query.getFacts(), ValueType.INT, indexer.getOptions());
        } else {
            query.setType(ValueType.INT);
        }
        push (query);
        return funcall;
    }

    private XPathQuery combineQueries(XPathQuery rq, Occur occur, XPathQuery lq, ValueType resultType) {
        XPathQuery query;
        // TODO - explain the difference between these two overrides!!!
        if (indexer.isOption(XmlIndexer.INDEX_PATHS)) {
            query = lq.combine(rq, occur, resultType, -1);
        } else {
            query = lq.combine(occur, rq, occur, resultType);
        }
        return query;
    }
    
    private Query nodeNameTermQuery(Axis axis, QName name) {
        if (indexer.isOption (XmlIndexer.INDEX_PATHS)) {
            String nodeName = name.getEncodedName();
            if (axis == Axis.Attribute) {
                nodeName = '@' + nodeName;
            }
            Term term = new Term ("", nodeName);
            return new SurroundTerm (term);
        } else {
            String nodeName = name.getEncodedName();
            String fieldName = (axis == Axis.Attribute) ? attrQNameField : elementQNameField;
            Term term = new Term (fieldName, nodeName);
            return new LuxTermQuery (term);
        }
    }
    
    @Override
    public AbstractExpression visit(Dot dot) {
        // FIXME - should have value type=VALUE?
        push(MATCH_ALL);
        return dot;
    }

    @Override
    public AbstractExpression visit(BinaryOperation op) {
        optimizeSubExpressions(op, 0);
        XPathQuery rq = pop();
        XPathQuery lq = pop();
        ValueType type = lq.getResultType().promote (rq.getResultType());
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
            push (combineQueries(lq, Occur.MUST, rq, type));
            return op;
        }
        XPathQuery query = combineQueries(lq, occur, rq, type);
        if (minimal == false) {
            query.setFact(XPathQuery.MINIMAL, false);
        }
        push (query);
        return op;
    }

    @Override
    public AbstractExpression visit(LiteralExpression literal) {
        push (UNINDEXED);
        return literal;
    }
    
    @Override
    public AbstractExpression visit(Variable variable) {
        // TODO - optimize through variable references?
        push (UNINDEXED);
        return variable;
    }

    @Override
    public AbstractExpression visit(Subsequence subsequence) {
        optimizeSubExpressions (subsequence, 0);
        AbstractExpression start = subsequence.getStartExpr();
        AbstractExpression length = subsequence.getLengthExpr();
        // TODO: encode pagination information in the call to lux:search created here
        XPathQuery lengthQuery = null;
        if (length != null) {
            lengthQuery = pop ();
        }
        XPathQuery startQuery = pop();
        if (start == FunCall.LastExpression || (start.equals(LiteralExpression.ONE) && length.equals(LiteralExpression.ONE))) {
            // selecting the first or last item from a sequence - this has
            // no effect on the query, its minimality or return type, so
            // just leave the main sub-expression query; don't combine with
            // the start or length queries
            return subsequence;
        }
        XPathQuery baseQuery = pop();
        XPathQuery query = combineQueries (baseQuery, Occur.SHOULD, startQuery, baseQuery.getResultType());
        if (lengthQuery != null) {
            query = combineQueries (query, Occur.SHOULD, lengthQuery, query.getResultType());
        }
        query.setFact(XPathQuery.MINIMAL, false);
        push (query);
        return subsequence;
    }
    
    private FunCall getSearchCall (int i, int facts) {
        int j = queryStack.size() - i - 1;
        XPathQuery query = queryStack.get(j);
        queryStack.set (j, MATCH_ALL);
        return createSearchCall(query, facts);
    }
    
    private FunCall createSearchCall(XPathQuery query, int facts) {
        return new FunCall (FunCall.LUX_SEARCH, query.getResultType(), 
                new LiteralExpression (query.toString()),
                new LiteralExpression (query.getFacts() | facts));
    }

    @Override
    public AbstractExpression visit(Sequence sequence) {
        optimizeSubExpressions(sequence, 0);
        combineTopQueries (sequence.getSubs().length, Occur.SHOULD);
        return sequence;
    }

    private void combineTopQueries (int n, Occur occur) {
        combineTopQueries (n, occur, null);
    }

    /*
     * combines the top n queries on the stack, using the boolean operator occur, and generalizing
     * the return type to the given type.  If valueType is null, no type restriction is imposed; the 
     * return type derives from type promotion among the constituent queries' return types.
     */
    private void combineTopQueries (int n, Occur occur, ValueType valueType) {
        if (n <= 0) {
            push (MATCH_ALL);
            return;
        }
        XPathQuery query = pop();
        if (n == 1) {
            ValueType type = valueType == null ? query.getResultType() : query.getResultType().promote(valueType);
            query = XPathQuery.getQuery(query.getQuery(), query.getFacts(), type, indexer.getOptions());
        } else {
            for (int i = 0; i < n-1; i++) {
                query = combineQueries (pop(), occur, query, valueType);
            }
        }
        push (query);
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

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
