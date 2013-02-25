package lux.compiler;

import static lux.compiler.XPathQuery.*;
import static lux.index.IndexConfiguration.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;

import lux.Compiler.SearchStrategy;
import lux.SearchResultIterator;
import lux.exception.LuxException;
import lux.index.FieldName;
import lux.index.IndexConfiguration;
import lux.index.field.FieldDefinition;
import lux.query.NodeTextQuery;
import lux.query.ParseableQuery;
import lux.query.SpanTermPQuery;
import lux.query.TermPQuery;
import lux.xml.QName;
import lux.xml.ValueType;
import lux.xpath.AbstractExpression;
import lux.xpath.AbstractExpression.Type;
import lux.xpath.BinaryOperation;
import lux.xpath.BinaryOperation.Operator;
import lux.xpath.Dot;
import lux.xpath.ExpressionVisitorBase;
import lux.xpath.FunCall;
import lux.xpath.LiteralExpression;
import lux.xpath.NodeTest;
import lux.xpath.PathExpression;
import lux.xpath.PathStep;
import lux.xpath.PathStep.Axis;
import lux.xpath.Predicate;
import lux.xpath.Root;
import lux.xpath.SearchCall;
import lux.xpath.Sequence;
import lux.xpath.Subsequence;
import lux.xquery.FLWOR;
import lux.xquery.FLWORClause;
import lux.xquery.ForClause;
import lux.xquery.FunctionDefinition;
import lux.xquery.LetClause;
import lux.xquery.OrderByClause;
import lux.xquery.SortKey;
import lux.xquery.Variable;
import lux.xquery.VariableBindingClause;
import lux.xquery.WhereClause;
import lux.xquery.XQuery;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.SortField;

/**
 * Prepares an XPath expression tree for indexed execution against a Lux data
 * store.
 * 
 * This class is part of the Lux internal API and is not intended to be called
 * by consumers of the API.
 * 
 * The general strategy here is to consider each expression in isolation,
 * determining whether it imposes any restriction on its context, and then to
 * compose such constraints into queries, to be executed by a searcher, with the
 * XPath/XQuery expressions evaluated against the resulting documents.
 * 
 * Absolute expressions are targets for optimization; the optimizer attempts to
 * form queries that retrieve the smallest possible subset of available
 * documents for which the expression generates a non-empty result sequence. The
 * queries for these sub-expressions are composed according to the semantics of
 * the combining expressions and functions. Absolute sequences (occurrences of
 * /) generally are not composed together - they form independent subqueries.
 * 
 * When optimizing for path indexes, we attempt to compute the "vertical" or
 * node distances between adjacent sub-expressions. Child path steps introduce a
 * zero distance when there is a named step on either side; wildcard child steps
 * introduce a distance of one, and descendant (or ancestor?) steps count as
 * infinite distance, although order is preserved.
 */
public class PathOptimizer extends ExpressionVisitorBase {

    private final ArrayList<XPathQuery> queryStack;
    private final HashMap<QName, XPathQuery> varQuery;
    private final HashMap<QName, AbstractExpression> varBindings;
    private final IndexConfiguration indexConfig;
    private final XPathQuery MATCH_ALL;

    private final String attrQNameField;
    private final String elementQNameField;
    private boolean optimizeForOrderedResults;

    private static final boolean DEBUG = false;

    public PathOptimizer(IndexConfiguration indexConfig) {
        queryStack = new ArrayList<XPathQuery>();
        varQuery = new HashMap<QName, XPathQuery>();
        varBindings = new HashMap<QName, AbstractExpression>();
        MATCH_ALL = XPathQuery.getMatchAllQuery(indexConfig);
        this.indexConfig = indexConfig;
        attrQNameField = indexConfig.getFieldName(FieldName.ATT_QNAME);
        elementQNameField = indexConfig.getFieldName(FieldName.ELT_QNAME);
        optimizeForOrderedResults = true;
    }

    /**
     * Prepares an XQuery module for indexed execution against a Lux data store.
     * See {@link #optimize(AbstractExpression)}.
     * 
     * @param query
     *            the query to optimize
     * @return the optimized query
     */
    public XQuery optimize(XQuery query) {
        queryStack.clear();
        push(MATCH_ALL);
        AbstractExpression main = query.getBody();
        // Don't attempt to optimize if no indexes are available, or if the
        // query has no body
        if (main != null && indexConfig.isIndexingEnabled()) {
            main = optimize(main);
            return new XQuery(query.getDefaultElementNamespace(), query.getDefaultFunctionNamespace(),
                    query.getDefaultCollation(), query.getModuleImports(), query.getNamespaceDeclarations(),
                    query.getVariableDefinitions(), optimizeFunctionDefinitions(query.getFunctionDefinitions()), main,
                    query.getBaseURI(), query.isPreserveNamespaces(), query.isInheritNamespaces(), query.isEmptyLeast());
        }
        return query;
    }

    // note: modifies its argument
    private FunctionDefinition[] optimizeFunctionDefinitions(FunctionDefinition[] functionDefinitions) {
        for (int i = 0; i < functionDefinitions.length; i++) {
            FunctionDefinition function = functionDefinitions[i];
            for (AbstractExpression var : function.getSubs()) {
                // bind the variables to null in case they are shadowed in the
                // function body
                setBoundExpression((Variable) var, null, null);
            }
            AbstractExpression body = optimize(function.getBody());
            function = new FunctionDefinition(function.getName(), function.getReturnType(),
                    (Variable[]) function.getSubs(), body);
            functionDefinitions[i] = function;
            for (AbstractExpression var : function.getSubs()) {
                descopeVariable(((Variable) var).getQName());
            }
        }
        return functionDefinitions;
    }

    /**
     * Prepares an XQuery expression for indexed execution against a Lux data
     * store. Inserts calls to lux:search that execute queries selecting a set
     * of documents against which the expression, or some of its
     * sub-expressions, are evaluated. The queries will always select a superset
     * of the documents that actually contribute results so that the result is
     * the same as if the expression was evaluated against all the documents in
     * collection() as its context (sequence of context items?).
     * 
     * @param expr
     *            the expression to optimize
     * @return the optimized expression
     */
    public AbstractExpression optimize(AbstractExpression expr) {
        // visit the expression tree, optimizing any absolute sub-expressions
        expr = expr.accept(this);
        // optimize the top level expression
        return optimizeExpression(expr, peek());
    }

    /**
     * @param expr
     *            the expression to optimize
     * @param j
     *            the query stack depth at which expr's query is to be found
     * @param facts
     *            additional facts to apply to the query
     */
    private AbstractExpression optimizeExpression(AbstractExpression expr, int i) {
        int j = queryStack.size() - i - 1;
        return optimizeExpression(expr, queryStack.get(j));
    }

    private AbstractExpression optimizeExpression(AbstractExpression expr, XPathQuery query) {
        if (expr instanceof SearchCall) {
            // TODO: when handling count(), exists(), etc: merging facts loses
            // info about their optimizations
            // append any additional constraints from where clauses
            // and ordering criteria from order by clauses
            // to an existing search call
            return mergeSearchCall((SearchCall) expr, query);
        }
        if (!expr.isAbsolute()) {
            return expr;
        }
        FunCall search = createSearchCall(FunCall.LUX_SEARCH, query);
        if (search.getReturnType().equals(ValueType.DOCUMENT)) {
            // Avoid the need to sort the results of this expression so that it
            // can be
            // embedded in a subsequence or similar and evaluated lazily.
            AbstractExpression tail = expr.getTail();
            if (tail != null) {
                return new Predicate(search, tail);
            }
        }
        AbstractExpression root = expr.getRoot();
        if (root instanceof Root) {
            // update variable bindings, but only if there is an (/) in there,
            // don't this for a search expression, which we sometimes consider to be a "Root"
            // of the expression tree
            for (Map.Entry<QName, AbstractExpression> entry : varBindings.entrySet()) {
                if (entry.getValue() == root) {
                    varBindings.put(entry.getKey(), search);
                }
            }
            return expr.replaceRoot(search);
        }
        return expr;
    }

    /**
     * Each absolute subexpression S is joined with a call to lux:search(),
     * effectively replacing it with search(QS)/S, where QS is the query derived
     * corresponding to S. In addition, if S returns documents, the foregoing
     * expression is wrapped by root(); root(search(QS)/S)
     * 
     * This should be called in visit() for all expressions shouldn't it?
     * 
     * @param expr
     *            an expression
     */
    private void optimizeSubExpressions(AbstractExpression expr) {
        AbstractExpression[] subs = expr.getSubs();
        for (int i = 0; i < subs.length; i++) {
            subs[i] = optimizeExpression(subs[i], subs.length - i - 1);
        }
    }

    private void combineTopQueries(int n, Occur occur) {
        combineTopQueries(n, occur, null);
    }

    /*
     * combines the top n queries on the stack, using the boolean operator
     * occur, and generalizing the return type to the given type. If valueType
     * is null, no type restriction is imposed; the return type derives from
     * type promotion among the constituent queries' return types.
     */
    private void combineTopQueries(int n, Occur occur, ValueType valueType) {
        if (n <= 0) {
            push(MATCH_ALL);
            return;
        }
        XPathQuery query = pop();
        if (n == 1) {
            ValueType type = valueType == null ? query.getResultType() : query.getResultType().promote(valueType);
            query = XPathQuery.getQuery(query.getParseableQuery(), query.getFacts(), type, indexConfig,
                    query.getSortFields());
        } else {
            for (int i = 0; i < n - 1; i++) {
                query = combineQueries(pop(), occur, query, valueType);
            }
        }
        push(query);
    }

    public XPathQuery peek() {
        return queryStack.size() == 0 ? null : queryStack.get(queryStack.size() - 1);
    }

    void push(XPathQuery query) {
        queryStack.add(query);
    }

    XPathQuery pop() {
        return queryStack.remove(queryStack.size() - 1);
    }

    private void debug(String tag, AbstractExpression expr) {
        for (int i = 0; i < queryStack.size(); i++) {
            System.err.print(' ');
        }
        String desc = expr.toString();
        if (desc.length() > 30) {
            desc = desc.substring(0, 30);
            desc = desc.replaceAll("\\s+", " ");
        }
        System.err.println(tag + ' ' + expr.getType() + ' ' + desc + "  depth=" + queryStack.size() + ", query: "
                + peek());
    }

    @Override
    public AbstractExpression visit(Root expr) {
        if (DEBUG) {
            debug("visit", expr);
        }
        push(MATCH_ALL);
        return expr;
    }

    /**
     * Conjoin the queries for the two expressions joined by the path. We handle
     * predicates as separate path queries, to be combined with AND, not as path
     * steps
     */
    @Override
    public AbstractExpression visit(PathExpression pathExpr) {
        if (DEBUG) {
            debug("visit", pathExpr);
        }
        XPathQuery rq = pop();
        XPathQuery lq = pop();
        AbstractExpression lhs = pathExpr.getLHS();
        XPathQuery query = combineAdjacentQueries(lhs, pathExpr.getRHS(), lq, rq, ResultOrientation.RIGHT);
        push(query);
        return pathExpr;
    }

    @Override
    public AbstractExpression visit(Predicate predicate) {
        if (DEBUG) {
            debug("visit", predicate);
        }
        // Allow the base expression to be optimized later so we have an
        // opportunity to combine the
        // predicate query with the base query
        predicate.setFilter(optimizeExpression(predicate.getFilter(), peek()));
        XPathQuery filterQuery = pop();
        XPathQuery baseQuery = pop();

        // In a path like /A[B]/C we need to generate /A/B AND /A/C, not /A/B/C
        // !
        // and from A[B[C]/D]/E we want A/B/C AND A/B/D and A/E
        // so leave the combined query on the stack, but save the base query for
        // path combination
        XPathQuery query = combineAdjacentQueries(predicate.getBase(), predicate.getFilter(), baseQuery, filterQuery,
                ResultOrientation.LEFT);
        query.setBaseQuery(baseQuery);
        push(query);
        optimizeComparison(predicate);
        if (indexConfig.isOption(INDEX_PATHS)) {
            // The combined query is singular if its base is
            peek().setFact(SINGULAR, baseQuery.isFact(SINGULAR));
        }
        return predicate;
    }

    private enum ResultOrientation {
        LEFT, RIGHT
    };

    /**
     * Combine queries from two adjacent subexpressions
     * 
     * @param left
     *            the left (upper) subexpression
     * @param right
     *            the right (lower) subexpression
     * @param lq
     *            the left query
     * @param rq
     *            the right query
     * @param orient
     *            the orientation of the combining expression - if RIGHT, then
     *            result items are drawn from the right subexpression (as for
     *            paths), and for LEFT, result items from the left (as for
     *            predicates).
     * @return the combined query
     */
    private XPathQuery combineAdjacentQueries(AbstractExpression left, AbstractExpression right, XPathQuery lq,
            XPathQuery rq, ResultOrientation orient) {
        XPathQuery query;
        Integer rSlop = null, lSlop = null;
        if (left instanceof Variable) {
            AbstractExpression binding = getBoundExpression(((Variable) left).getQName());
            if (binding != null) {
                left = binding;
            }
        }
        if (indexConfig.isOption(INDEX_PATHS)) {
            SlopCounter slopCounter = new SlopCounter();

            // count the left slop of the RHS
            right.accept(slopCounter);
            rSlop = slopCounter.getSlop();

            // count the right slop of the LHS
            if (rSlop != null) {
                slopCounter.reset();
                slopCounter.setReverse(true);
                left.accept(slopCounter);
                lSlop = slopCounter.getSlop();
            }
        }
        ValueType resultType = (orient == ResultOrientation.RIGHT) ? rq.getResultType() : lq.getResultType();
        XPathQuery baseQuery = lq.getBaseQuery();
        if (rSlop != null && lSlop != null) {
            // total slop is the distance between the two path components.
            XPathQuery base = baseQuery != null ? baseQuery : lq;
            query = base.combineSpanQueries(rq, Occur.MUST, resultType, rSlop + lSlop, indexConfig);
            if (baseQuery != null) {
                // when there is a base query (from a predicate), add lq as an
                // additional constraint
                query = combineQueries(lq, Occur.MUST, query, query.getResultType());
            }
        } else {
            query = combineQueries(lq, Occur.MUST, rq, resultType);
        }
        return query;
    }

    @Override
    public AbstractExpression visit(PathStep step) {
        if (DEBUG) {
            debug("visit", step);
        }
        QName name = step.getNodeTest().getQName();
        Axis axis = step.getAxis();
        XPathQuery currentQuery = peek();
        boolean isMinimal, isSingular = currentQuery.isFact(SINGULAR);
        if (axis == Axis.Descendant || axis == Axis.DescendantSelf || axis == Axis.Attribute) {
            if (step.getNodeTest().isWild() && !currentQuery.isEmpty()) {
                // This is basically a query that says - this document has an
                // element down below...
                // we don't generate wildcard queries for these - maybe we
                // should?
                isMinimal = false;
            } else {
                isMinimal = true;
            }
            if (axis != Axis.Attribute) {
                isSingular = false;
            }
        } else {
            boolean isElementStep = step.getNodeTest().getType().is(ValueType.ELEMENT);
            if (axis == Axis.Child) {
                boolean isPathIndexed = indexConfig.isOption(INDEX_PATHS);
                ValueType type = currentQuery.getResultType();
                boolean currentMinimal = currentQuery.isFact(MINIMAL);
                if (ValueType.NODE.is(type)) {
                    // special case for
                    // //descendant-or-self::node()/child::element(xxx)
                    isMinimal = currentMinimal;
                    isSingular = false;
                } else if (ValueType.DOCUMENT == type) {
                    // and for /child:element(xxx)
                    isMinimal = (isPathIndexed && currentMinimal) || step.getNodeTest().isWild();
                    // may have multiple non-element nodes as children of the
                    // document node
                    // can only ensure the query will return the correct number
                    // if we have paths
                    isSingular = (isPathIndexed || step.getNodeTest().isWild()) && isElementStep;
                } else {
                    // if we have path indexes, we can resolve a child element
                    // step minimally
                    isMinimal = isPathIndexed && isElementStep && currentMinimal;
                    isSingular = false;
                }
            } else if (axis == Axis.Ancestor || axis == Axis.AncestorSelf || axis == Axis.Self) {
                if (step.getNodeTest().isWild() && !currentQuery.isEmpty() && isElementStep) {
                    // This is basically a query that says - this document has
                    // an element up above...
                    // we don't generate wildcard queries for these - maybe we
                    // should?
                    // actually - we could do better with self::node(), which is
                    // completely vacuous
                    isMinimal = false;
                } else {
                    isMinimal = true;
                }
                // TOOD: is counting correct? We need more tests for that I
                // think
            } else {
                // No indexes that help with preceding/following axes
                isMinimal = false;
            }
        }
        XPathQuery query;
        long facts = currentQuery.getFacts();
        if (!isSingular) {
            facts &= ~SINGULAR;
        } else {
            facts |= SINGULAR;
        }
        if (!isMinimal) {
            facts &= ~MINIMAL;
        }
        if (name == null) {
            ValueType type = step.getNodeTest().getType();
            if (axis == Axis.Self && (type == ValueType.NODE || type == ValueType.VALUE)) {
                // if axis==self and the type is loosely specified, use the
                // prevailing type
                type = currentQuery.getResultType();
            } else if (axis == Axis.AncestorSelf && (type == ValueType.NODE || type == ValueType.VALUE)
                    && currentQuery.getResultType() == ValueType.DOCUMENT) {
                type = ValueType.DOCUMENT;
            }

            query = XPathQuery.getQuery(MATCH_ALL.getParseableQuery(), facts, type, indexConfig,
                    currentQuery.getSortFields());

        } else {
            ParseableQuery termQuery = nodeNameTermQuery(step.getAxis(), name);
            query = XPathQuery.getQuery(termQuery, facts, step.getNodeTest().getType(), indexConfig,
                    currentQuery.getSortFields());
        }
        push(query);
        return step;
    }

    /**
     * If a function F is emptiness-preserving, in other words F(a,b,c...) is
     * empty ( =()) if *any* of its arguments are empty, and is non-empty if
     * *all* of its arguments are non-empty, then its argument's queries can be
     * combined with Occur.MUST. At first I thought that was the way most
     * functions work, but it's not: string(()) = '', not ()
     * 
     * Otherwise, no optimization is possible in the general case, which we
     * indicate by combining with Occur.SHOULD.
     * 
     * count(), exists() and not() (and maybe max(), min(), and avg()?) are
     * optimized as special cases.
     * 
     * @param funcall
     *            the function call expression to optimize
     * @return the same function call expression, after having possibly
     *         optimized its arguments
     */

    @Override
    public AbstractExpression visit(FunCall funcall) {
        if (DEBUG) {
            debug("visit", funcall);
        }
        QName name = funcall.getName();
        // Try special function optimizations, like count(), exists(), etc.
        AbstractExpression luxfunc = optimizeFunCall(funcall);
        if (luxfunc != funcall) {
            return luxfunc;
        }
        // see if the function args can be converted to searches.
        optimizeSubExpressions(funcall);
        Occur occur;
        String namespaceURI = name.getNamespaceURI();
        if (!(namespaceURI.equals(FunCall.FN_NAMESPACE) || namespaceURI.equals(FunCall.XS_NAMESPACE) || namespaceURI
                .equals(FunCall.LUX_NAMESPACE))) {
            // We know nothing about this function; it's best
            // not to attempt any optimization, so we will throw away any
            // filters coming from the
            // function arguments
            occur = Occur.SHOULD;
        }
        // a built-in XPath 2 function
        else if (isomorphs.contains(name.getLocalPart())) {
            occur = Occur.MUST;
        } else {
            // for functions in fn: and xs: namespaces not listed below, we
            // assume that they
            // are *not* optimizable, and their arguments' queries are ignored
            occur = Occur.SHOULD;
        }
        AbstractExpression[] args = funcall.getSubs();
        if (occur == Occur.SHOULD) {
            combineTopQueries(args.length, occur, funcall.getReturnType());
            push(pop().setFact(IGNORABLE, true)); // FIXME?????
            /*
             * for (int i = args.length; i > 0; --i) { pop(); } push
             * (XPathQuery.getQuery(MATCH_ALL.getParseableQuery(), IGNORABLE,
             * ValueType.VALUE, indexConfig, null));
             */
        } else {
            combineTopQueries(args.length, occur, funcall.getReturnType());
        }
        if (name.equals(FunCall.LUX_FIELD_VALUES)) {
            if (args.length > 0) {
                AbstractExpression arg = args[0];
                if (arg.getType() == Type.LITERAL) {
                    // save away the field name as a possible sort key
                    String fieldName = ((LiteralExpression) arg).getValue().toString();
                    FieldDefinition fieldDefinition = indexConfig.getField(fieldName);
                    int sortType;
                    if (fieldDefinition != null) {
                        sortType = fieldDefinition.getType().getLuceneSortFieldType();
                    } else {
                        sortType = FieldDefinition.Type.STRING.getLuceneSortFieldType();
                        // TODO: log a warning
                    }
                    peek().setSortFields(new SortField[] { new SortField(fieldName, sortType) });
                }
            }
        }
        return funcall;
    }

    // These functions are emptiness-preserving - if their argument is empty,
    // then their
    // result is also empty
    // these are not
    // isomorphs.add ("string");
    // isomorphs.add ("exists");

    protected static HashSet<String> isomorphs = new HashSet<String>();

    static {
        isomorphs.add("exists"); // FIXME - this enables optos that we like, but
                                 // it is not really an isomorph
                                 // since it returns a boolean
        isomorphs.add("data");
        isomorphs.add("root");
        isomorphs.add("collection");
        isomorphs.add("doc");
        isomorphs.add("uri-collection");
        isomorphs.add("unparsed-text");
    };

    /**
     * Possibly convert this function call to a lux: function. If we do that,
     * all the arguments' queries will be removed from the stack. Otherwise
     * leave them there for the caller.
     * 
     * @param funcall
     *            a function call to be optimized
     * @return an optimized function, or the original function call
     */

    private AbstractExpression optimizeFunCall(FunCall funcall) {
        AbstractExpression[] subs = funcall.getSubs();
        QName fname = funcall.getName();

        // If this function's single argument is a call to lux:search, get its
        // query. We may remove the call
        // to lux:search if this function can perform the search itself without
        // retrieving documents
        AbstractExpression searchArg = null;
        if (fname.equals(FunCall.FN_COUNT) || fname.equals(FunCall.FN_EXISTS) || fname.equals(FunCall.FN_EMPTY)) {
            if (subs.length == 1 && subs[0].getType() == Type.FUNCTION_CALL
                && ((FunCall) subs[0]).getName().equals(FunCall.LUX_SEARCH)) {
                searchArg = subs[0].getSubs()[0];
            }
        } else if (fname.equals(FunCall.LUX_SEARCH) && !(funcall instanceof SearchCall)) {
            // searchArg = subs[0];
            // TODO: if searchArg is a literal string or element, we may be able to create
            // a ParseableQuery out of it that can be merged with enclosing info ... otherwise,
            if (subs.length == 1) {
                return new SearchCall(subs[0]);
            } else {
                return funcall;
            }
        } else if (subs.length == 1 && !subs[0].isAbsolute()) {
            return funcall;
        }
        if (fname.equals(FunCall.FN_COLLECTION) && subs.length == 0) {
            // Optimize when no arguments to collection()
            // TODO: figure out why we don't need to push queries all over here?
            push(MATCH_ALL);
            return new Root();
        }
        XPathQuery query = pop();
        // can only use these function optimizations when we are sure that its
        // argument expression
        // is properly indexed - MINIMAL here guarantees that every document
        // matching the query will
        // produce a non-empty result in the function's argument
        if (query.isMinimal()) {
            int functionFacts = 0;
            ValueType returnType = null;
            QName qname = null;
            if (fname.equals(FunCall.FN_COUNT) && query.isFact(SINGULAR)) {
                functionFacts = SINGULAR;
                returnType = ValueType.INT;
                qname = FunCall.LUX_COUNT;
            } else if (fname.equals(FunCall.FN_EXISTS)) {
                functionFacts = BOOLEAN_TRUE;
                returnType = ValueType.BOOLEAN;
                qname = FunCall.LUX_EXISTS;
            } else if (fname.equals(FunCall.FN_EMPTY)) {
                functionFacts = BOOLEAN_FALSE;
                returnType = ValueType.BOOLEAN_FALSE;
                qname = FunCall.LUX_EXISTS;
            } else if (fname.equals(FunCall.FN_CONTAINS)) {
                // also see optimizeComparison
                if (!subs[0].isAbsolute()) {
                    // don't query if the sequence arg isn't absolute??
                    // if the arg is relative, presumably the contains is in a
                    // predicate somewhere
                    // and may have been optimized there?
                    push(query);
                    return funcall;
                }
                functionFacts = BOOLEAN_TRUE;
                returnType = ValueType.BOOLEAN;
                qname = FunCall.LUX_SEARCH;
            }
            if (qname != null) {
                // We will insert a searching function. apply no restrictions to
                // the enclosing scope:
                if (searchArg != null) {
                    // create a searching function call using the argument to
                    // the enclosed lux:search call
                    push(MATCH_ALL);
                    return new FunCall(qname, returnType, searchArg, new LiteralExpression(MINIMAL));
                }
                if (query.isImmutable()) {
                    // combine functionFacts with query
                    if (functionFacts != 0) {
                        query = XPathQuery.getQuery(query.getParseableQuery(), query.getFacts() | functionFacts,
                                returnType, indexConfig, query.getSortFields());
                    }
                } else {
                    query.setType(returnType);
                    query.setFact(functionFacts, true);
                }
                if (! (funcall.getRoot() instanceof SearchCall)) {
                    push(MATCH_ALL);
                    return createSearchCall(qname, query);
                }
            }
        }
        push(query);
        return funcall;
    }

    private XPathQuery combineQueries(XPathQuery lq, Occur occur, XPathQuery rq, ValueType resultType) {
        XPathQuery query;
        if (indexConfig.isOption(INDEX_PATHS)) {
            query = lq.combineSpanQueries(rq, occur, resultType, -1, indexConfig);
        } else {
            query = lq.combineBooleanQueries(occur, rq, occur, resultType, indexConfig);
        }
        return query;
    }

    private ParseableQuery nodeNameTermQuery(Axis axis, QName name) {
        String nodeName = name.getEncodedName();
        if (indexConfig.isOption(INDEX_PATHS)) {
            if (axis == Axis.Attribute) {
                nodeName = '@' + nodeName;
            }
            Term term = new Term(indexConfig.getFieldName(FieldName.PATH), nodeName);
            return new SpanTermPQuery(term);
        } else {
            String fieldName = (axis == Axis.Attribute) ? attrQNameField : elementQNameField;
            Term term = new Term(fieldName, nodeName);
            return new TermPQuery(term);
        }
    }

    @Override
    public AbstractExpression visit(Dot dot) {
        if (DEBUG) {
            debug("visit", dot);
        }
        push(MATCH_ALL);
        return dot;
    }

    @Override
    public AbstractExpression visit(BinaryOperation op) {
        if (DEBUG) {
            debug("visit", op);
        }
        optimizeSubExpressions(op);
        XPathQuery rq = pop();
        XPathQuery lq = pop();
        ValueType type = lq.getResultType().promote(rq.getResultType());
        Occur occur = Occur.SHOULD;
        boolean minimal = false;
        switch (op.getOperator()) {

        case AND:
            occur = Occur.MUST;
        case OR:
            minimal = true;
            type = ValueType.BOOLEAN;
            break;

        case ADD:
        case SUB:
        case DIV:
        case MUL:
        case IDIV:
        case MOD:
            type = ValueType.ATOMIC;
            // Casting an empty sequence to an operand of an operator expecting
            // atomic values raises an error
            // and to be properly error-preserving we use SHOULD here
            // occur = Occur.MUST
            break;

        case EQUALS:
        case NE:
        case LT:
        case GT:
        case LE:
        case GE:
            type = ValueType.BOOLEAN;
            occur = Occur.MUST;
            break;

        case AEQ:
        case ANE:
        case ALT:
        case ALE:
        case AGT:
        case AGE:
            // atomic comparisons - see note above under arithmetic operators
            type = ValueType.BOOLEAN;
            break;

        case IS:
        case BEFORE:
        case AFTER:
            // occur = Occur.MUST;
            break;

        case INTERSECT:
            occur = Occur.MUST;
        case UNION:
            minimal = true;
            break;

        case EXCEPT:
            push(combineQueries(lq, Occur.MUST, rq, type));
            return op;
        }
        XPathQuery query = combineQueries(lq, occur, rq, type);
        if (minimal == false) {
            query = query.setFact(MINIMAL, false);
        }
        push(query);
        return op;
    }

    private void optimizeComparison(Predicate predicate) {
        if (!indexConfig.isOption(INDEX_FULLTEXT)) {
            return;
        }
        // TODO: test different literal values to ensure we don't run into
        // trouble during text analysis
        LiteralExpression value = null;
        AbstractExpression path = null;
        AbstractExpression filter = predicate.getFilter();
        if (filter.getType() == Type.BINARY_OPERATION) {
            BinaryOperation op = (BinaryOperation) predicate.getFilter();
            if (!(op.getOperator() == Operator.EQUALS || op.getOperator() == Operator.AEQ)) {
                return;
            }
        } else if (filter.getType() == Type.FUNCTION_CALL) {
            if (!((FunCall) filter).getName().equals(FunCall.FN_CONTAINS)) {
                return;
            }
        } else {
            return;
        }
        if (filter.getSubs()[0].getType() == Type.LITERAL) {
            value = (LiteralExpression) filter.getSubs()[0];
            path = filter.getSubs()[1];
        } else if (filter.getSubs()[1].getType() == Type.LITERAL) {
            value = (LiteralExpression) filter.getSubs()[1];
            path = filter.getSubs()[0];
        } else {
            // TODO: handle variables
            return;
        }
        AbstractExpression last = path.getLastContextStep();
        if (last.getType() != Type.PATH_STEP) {
            // get the context from the base of the predicate if the filter
            // doesn't
            // contain any path steps
            last = predicate.getBase().getLastContextStep();
        }
        if (last.getType() == Type.PATH_STEP) {
            String v = value.getValue().toString();
            if (filter.getType() == Type.FUNCTION_CALL) {
                if (v.matches("\\w+")) {
                    // when optimizing contains(), we have to do a wildcard
                    // query;
                    // we can only do this if the term contains only word
                    // characters
                    createTermQuery((PathStep) last, path, "*" + v + "*");
                }
            } else {
                createTermQuery((PathStep) last, path, v);
            }
        }
    }

    private void createTermQuery(PathStep context, AbstractExpression path, String value) {
        NodeTest nodeTest = context.getNodeTest();
        QName nodeName = nodeTest.getQName();
        ParseableQuery termQuery = null;
        if (nodeName == null || "*".equals(nodeName.getPrefix()) || "*".equals(nodeName.getLocalPart())) {
            termQuery = makeTextQuery(value, indexConfig);
        } else if (nodeTest.getType() == ValueType.ELEMENT) {
            termQuery = makeElementValueQuery(nodeName, value, indexConfig);
        } else if (nodeTest.getType() == ValueType.ATTRIBUTE) {
            termQuery = makeAttributeValueQuery(nodeName, value, indexConfig);
        }
        if (termQuery != null) {
            XPathQuery query = XPathQuery.getQuery(termQuery, MINIMAL, nodeTest.getType(), indexConfig, null);
            XPathQuery baseQuery = pop();
            query = combineQueries(query, Occur.MUST, baseQuery, baseQuery.getResultType());
            push(query);
        }
    }

    public static NodeTextQuery makeElementValueQuery(QName qname, String value, IndexConfiguration config) {
        return new NodeTextQuery(new Term(config.getFieldName(IndexConfiguration.ELEMENT_TEXT), value),
                qname.getEncodedName());
    }

    public static ParseableQuery makeAttributeValueQuery(QName qname, String value, IndexConfiguration config) {
        return new NodeTextQuery(new Term(config.getFieldName(IndexConfiguration.ATTRIBUTE_TEXT), value),
                qname.getEncodedName());
    }

    public static ParseableQuery makeTextQuery(String value, IndexConfiguration config) {
        return new NodeTextQuery(new Term(config.getFieldName(IndexConfiguration.XML_TEXT), value));
    }

    @Override
    public AbstractExpression visit(LiteralExpression literal) {
        if (DEBUG) {
            debug("visit", literal);
        }
        push(MATCH_ALL);
        return literal;
    }

    @Override
    public AbstractExpression visit(Variable variable) {
        if (DEBUG) {
            debug("visit", variable);
        }
        QName qName = variable.getQName();
        XPathQuery q = varQuery.get(qName);
        if (q != null) {
            push(q);
        } else {
            // if the variables are function arguments
            push(MATCH_ALL);
        }
        AbstractExpression value = varBindings.get(qName);
        variable.setValue(value);
        return variable;
    }

    @Override
    public AbstractExpression visit(Subsequence subsequence) {
        if (DEBUG) {
            debug("visit", subsequence);
        }
        optimizeSubExpressions(subsequence);
        AbstractExpression length = subsequence.getLengthExpr();
        AbstractExpression start = subsequence.getStartExpr();
        // For now, we just ignore the queries from the start and length
        // expressions.
        if (length != null) {
            // pop the query from the length expression, if there is one
            pop();
        }
        pop(); // pop the query from the start expression
        if (start == FunCall.LastExpression
                || (start.equals(LiteralExpression.ONE) && length.equals(LiteralExpression.ONE))) {
            // selecting the first or last item from a sequence - this has
            // no effect on the query, its minimality or return type, so
            // just leave the main sub-expression query; don't combine with
            // the start or length queries
            return subsequence;
        }
        // we don't have an index that can compute how many matches there are
        push(pop().setFact(MINIMAL | SINGULAR, false));
        return optimizeStart(subsequence);
    }

    // push the start expression from an outer Subsequence into a descendant
    // search FunCall
    // ... but how deep?
    private AbstractExpression optimizeStart(Subsequence subsequence) {
        AbstractExpression sequence = subsequence.getSequence();
        AbstractExpression start = subsequence.getStartExpr();
        AbstractExpression length = subsequence.getLengthExpr();
        // Any (/) in the expression must have been replaced with a search
        FunCall search = (FunCall) sequence.getRoot();
        if (search != null && !start.equals(LiteralExpression.ONE)) {
            AbstractExpression[] args = search.getSubs();
            if (args.length >= 4) {
                // there is already a start arg provided
                return subsequence;
            }
            boolean isSingular;
            if (args.length < 1) {
                isSingular = true; // this must be a user-supplied search call
            } else {
                LiteralExpression factsArg = (LiteralExpression) args[1];
                long facts = ((Long) factsArg.getValue());
                isSingular = (facts & SINGULAR) != 0;
            }
            if (isSingular) {
                AbstractExpression[] newArgs = new AbstractExpression[4];
                int i = 0;
                while (i < args.length) {
                    newArgs[i] = args[i];
                    ++i;
                }
                while (i < 3) {
                    newArgs[i++] = LiteralExpression.EMPTY;
                }
                newArgs[i] = start;
                search.setArguments(newArgs);
                if (length == null || length.equals(LiteralExpression.EMPTY)) {
                    return search;
                }
                subsequence.setStartExpr(LiteralExpression.ONE);
            }
        }
        return subsequence;
    }

    // merge any sorting criteria from the query at position i on the stack
    // with the search call. TODO: also merge any query with the existing
    // query
    private SearchCall mergeSearchCall(SearchCall search, XPathQuery query) {
        search.combineQuery(query, indexConfig);
        return search;
    }

    private FunCall createSearchCall(QName functionName, XPathQuery query) {
        if (functionName.equals(FunCall.LUX_SEARCH)) {
            // searchCall.setFnCollection (!optimizeForOrderedResults);
            return new SearchCall(query, indexConfig);
        }
        return new FunCall(functionName, query.getResultType(), query.toXmlNode(indexConfig.getDefaultFieldName()),
                new LiteralExpression(query.getFacts()));
    }

    @Override
    public AbstractExpression visit(Sequence sequence) {
        if (DEBUG) {
            debug("visit", sequence);
        }
        optimizeSubExpressions(sequence);
        combineTopQueries(sequence.getSubs().length, Occur.SHOULD);
        return sequence;
    }

    /*
     * Unoptimized expressions should call this to ensure the proper state of
     * the query stack
     */
    private void popChildQueries(AbstractExpression expr) {
        for (int i = 0; i < expr.getSubs().length; i++) {
            pop();
        }
        push(MATCH_ALL);
    }

    @Override
    public AbstractExpression visitDefault(AbstractExpression expr) {
        if (DEBUG) {
            debug("visit", expr);
        }
        optimizeSubExpressions(expr);
        popChildQueries(expr);
        return expr;
    }

    /**
     * <p>
     * Optimizing FLWOR expressions is more complicated than path expressions
     * and other simpler XPath expressions because the relationship among the
     * clauses is not a simple dependency, but is mediated by variables.
     * </p>
     * 
     * <p>
     * The strategy is to use constraints from each clause and its dependent
     * for, where and return clauses to filter that clause's sequence. Dependent
     * let and order by clauses are not considered when filtering enclosing
     * clauses. Order by clauses *do* contribute to ordering relations in their
     * enclosing for clause and may be folded together with them when
     * searchable.
     * </p>
     * 
     * <p>
     * Additionally, constraints may come in via variable references, but this
     * is not handled explicitly as part of the FLWOR optimization; rather it is
     * handled in the visit method for each clause.
     */
    @Override
    public AbstractExpression visit(FLWOR flwor) {
        if (DEBUG) {
            debug("visit", flwor);
        }
        // combine any constraint from the return clause with the constraint
        // from the
        // for and where clauses
        flwor.getSubs()[0] = optimizeExpression(flwor.getReturnExpression(), peek());
        // XPathQuery returnQuery = pop();
        peek().setSortFields(null); // ignore any ordering expressions found in
                                    // the return clause
        int length = flwor.getClauses().length;
        // iterate in reverse order so we can unwind the query stack from its
        // "top"
        // which corresponds to the "bottom" of the expression tree.

        for (int i = length - 1; i >= 0; i--) {
            FLWORClause clause = flwor.getClauses()[i];
            AbstractExpression seq = clause.getSequence();
            if (clause instanceof VariableBindingClause) {
                QName varName = ((VariableBindingClause) clause).getVariable().getQName();
                descopeVariable(varName);
            }
            if (clause instanceof LetClause) {
                XPathQuery top = pop(); // get top two queries
                XPathQuery letQuery = pop();
                // the let query will have been marked as ignorable, but in this
                // context it is not.
                letQuery.setFact(IGNORABLE, false);
                // merge into let query (leave the top query alone - don't
                // combine let-constraints with it)
                letQuery = combineQueries(letQuery, Occur.MUST, top, letQuery.getResultType());
                clause.setSequence(optimizeExpression(seq, letQuery));
                push(top); // restore accumulating return query to top of stack
            } else {
                combineTopQueries(2, Occur.MUST);
            }
            if (clause instanceof ForClause) {
                clause.setSequence(optimizeExpression(seq, peek()));
            }
            // TODO: optimize where and order by clauses?
        }
        // push (combineQueries (pop(), Occur.MUST, returnQuery,
        // returnQuery.getResultType()));
        return flwor;
    }

    private void descopeVariable(QName varName) {
        varQuery.remove(varName);
        varBindings.remove(varName);
    }

    @Override
    public OrderByClause visit(OrderByClause orderByClause) {
        LinkedList<SortField> sortFields = new LinkedList<SortField>();
        ArrayList<SortKey> sortKeys = orderByClause.getSortKeys();
        boolean foundUnindexedSort = false;
        int stackOffset = queryStack.size() - sortKeys.size();
        for (int i = 0; i < sortKeys.size(); i++) {
            // pop the queries off the stack that correspond to each sort key in
            // this order by clause.
            // Accumulate a list of contiguous indexed order keys. Merge them
            // with the
            // query on the stack as an ordering criterion (not as a filter)

            // Pull queries from middle of stack since they get pushed in
            // reverse order
            XPathQuery q = queryStack.remove(stackOffset);
            if (q.getSortFields() == null) {
                // once we find an unindexed sort field, stop adding sort
                // indexes to the query
                foundUnindexedSort = true;
            } else if (!foundUnindexedSort) {
                SortKey sortKey = sortKeys.get(i);
                AbstractExpression key = sortKey.getKey();
                if (key instanceof FunCall) {
                    // special case - it would be nice if Saxon figured this out
                    // when compiling
                    FunCall keyFun = (FunCall) key;
                    if (keyFun.getName().equals(FunCall.LUX_FIELD_VALUES) && keyFun.getSubs().length < 2) {
                        throw new LuxException(
                                "lux:field-values($key) depends on the context where there is no context defined");
                    }
                }
                String order = ((LiteralExpression) sortKey.getOrder()).getValue().toString();
                SortField sortField = q.getSortFields()[0];
                if (!sortKey.isEmptyLeast()) {
                    // empty greatest
                    sortField = new SortField(sortField.getField(), SearchResultIterator.MISSING_LAST, order.toString()
                            .equals("descending"));
                } else if (order.toString().equals("descending")) {
                    // reverse sort order
                    sortField = new SortField(sortField.getField(), sortField.getType(), true);
                }
                // add at the beginning: fields pop off the stack in reverse
                // order
                sortFields.add(sortField);
                sortKeys.remove(i);
                --i; // don't advance: we shrank the list
            }
        }
        if (sortFields.isEmpty()) {
            push(MATCH_ALL);
        } else {
            XPathQuery query = XPathQuery.getQuery(MATCH_ALL.getParseableQuery(), MATCH_ALL.getFacts(),
                    MATCH_ALL.getResultType(), indexConfig, sortFields.toArray(new SortField[sortFields.size()]));
            push(query);
        }
        return orderByClause;
    }

    @Override
    public ForClause visit(ForClause forClause) {
        visitVariableBinding(forClause.getVariable(), forClause.getSequence());
        return forClause;
    }

    @Override
    public WhereClause visit(WhereClause whereClause) {
        peek().setSortFields(null);
        return whereClause;
    }

    @Override
    public LetClause visit(LetClause letClause) {
        visitVariableBinding(letClause.getVariable(), letClause.getSequence());
        // Mark let clause queries as ignorable so that expressions that depend
        // on the let variable don't require the variable to be non-empty by
        // default
        peek().setFact(IGNORABLE, true);
        return letClause;
    }

    private void visitVariableBinding(Variable var, AbstractExpression binding) {
        XPathQuery q = peek();
        q.setSortFields(null);
        setBoundExpression(var, binding, q);
    }

    private void setBoundExpression(Variable var, AbstractExpression expr, XPathQuery q) {
        // remember the variable binding for use in optimizations
        QName name = var.getQName();
        if (varQuery.containsKey(name)) {
            // variable is shadowing a same-named variable in an outer scope
            name = makeUniqueName(name);
            if (name == null) {
                return;
            }
            var.setName(name);
        }
        varBindings.put(name, expr);
        varQuery.put(name, q);
    }

    public AbstractExpression getBoundExpression(QName name) {
        AbstractExpression binding;
        binding = varBindings.get(name);
        while (binding instanceof Variable) {
            // variable bound to another variable?
            binding = varBindings.get(((Variable) binding).getQName());
        }
        return binding;
    }

    private QName makeUniqueName(QName name) {
        for (int i = 1; i < 100; i++) {
            QName newName = new QName(name.getNamespaceURI(), name.getLocalPart() + i);
            if (!varQuery.containsKey(newName)) {
                return newName;
            }
        }
        // degenerate case: 100 nested variables with the same name
        return null;
    }

    /**
     * @return whether to generate code that relies on our ability to assert
     *         that lux:search() returns results in "document order" - ie
     *         ordered by document ID.
     */
    public boolean isOptimizedForOrderedResults() {
        return optimizeForOrderedResults;
    }

    public void setSearchStrategy(SearchStrategy searchStrategy) {
        this.optimizeForOrderedResults = (searchStrategy == SearchStrategy.LUX_SEARCH);
    }

}

/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/.
 */
