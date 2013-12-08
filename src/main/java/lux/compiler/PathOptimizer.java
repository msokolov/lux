package lux.compiler;

import static lux.compiler.XPathQuery.*;
import static lux.index.IndexConfiguration.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import lux.Compiler;
import lux.Compiler.SearchStrategy;
import lux.SearchResultIterator;
import lux.exception.LuxException;
import lux.index.FieldRole;
import lux.index.IndexConfiguration;
import lux.index.field.FieldDefinition;
import lux.query.BooleanPQuery;
import lux.query.NodeTextQuery;
import lux.query.ParseableQuery;
import lux.query.RangePQuery;
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
import lux.xquery.VariableContext;
import lux.xquery.WhereClause;
import lux.xquery.XQuery;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.SortField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private final ArrayList<XPathQuery> rangeQueries;
    private final HashMap<QName, VarBinding> varBindings;
    private final IndexConfiguration indexConfig;
    private final Compiler compiler;
    private final XPathQuery MATCH_ALL;
    private final String attrQNameField;
    private final String elementQNameField;
    private boolean optimizeForOrderedResults;
    private Logger log;

    private static final boolean DEBUG = false;
    
    public PathOptimizer(Compiler compiler) {
        queryStack = new ArrayList<XPathQuery>();
        rangeQueries = new ArrayList<XPathQuery>();
        varBindings = new HashMap<QName, VarBinding>();
        this.compiler = compiler;
        this.indexConfig = compiler.getIndexConfiguration();
        MATCH_ALL = XPathQuery.getMatchAllQuery(indexConfig);
        attrQNameField = indexConfig.getFieldName(FieldRole.ATT_QNAME);
        elementQNameField = indexConfig.getFieldName(FieldRole.ELT_QNAME);
        optimizeForOrderedResults = true;
        log = LoggerFactory.getLogger(PathOptimizer.class);
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
        push(XPathQuery.MATCH_ALL);
        AbstractExpression main = query.getBody();
        if (main != null) {
            if (indexConfig.isIndexingEnabled()) {
                main = optimize(main);
            } else {
                // Don't attempt to optimize if no indexes are available
                main = main.replaceRoot(new FunCall(FunCall.FN_COLLECTION, ValueType.DOCUMENT));
            }
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
                setUnboundVariable((Variable) var);
            }
            AbstractExpression body = optimize(function.getBody());
            function = new FunctionDefinition(function.getName(), 
                    function.getReturnType(), function.getCardinality(), function.getReturnTypeName(),
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
        AbstractExpression root = expr.getRoot();
        FunCall search;
        if (root instanceof FunCall) {
        	search = (FunCall) root;
        } else {
        	search = createSearchCall(FunCall.LUX_SEARCH, query);
        }
        // This optimization attempts to take advantage of the fact that a path like:
        // lux:search($q)/.../root() is equivalent to lux:search($q)[...]
        // The advantage of the latter is that it is already marked as *in document order*
        if (query.getResultType().equals(ValueType.DOCUMENT) && search.getReturnType().equals(ValueType.DOCUMENT)) {
            // Avoid the need to sort the results of this expression so that it
            // can be
            // embedded in a subsequence or similar and evaluated lazily.
            if (root == expr.getHead()) {
                AbstractExpression tail = expr.getTail();
                if (tail != null) {
                    return new Predicate(search, tail);
                }
            }
        }
        if (root instanceof Root) {
            // update variable bindings, but only if there is an (/) in there,
            // don't this for a search expression, which we sometimes consider to be a "Root"
            // of the expression tree
            for (Map.Entry<QName, VarBinding> entry : varBindings.entrySet()) {
                VarBinding binding = entry.getValue();
                if (binding.getExpr() == root) {
                    varBindings.put(entry.getKey(), new VarBinding(binding.getVar(), search, binding.getQuery(), binding.getContext(), binding.getShadowedBinding()));
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
            push(XPathQuery.MATCH_ALL);
            return;
        }
        XPathQuery query = pop();
        if (n == 1) {
            ValueType type = valueType == null ? query.getResultType() : query.getResultType().promote(valueType);
            query = XPathQuery.getQuery(query.getBooleanQuery(), query.getPathQuery(), query.getFacts(), type, indexConfig,
                    query.getSortFields());
        } else {
            for (int i = 0; i < n - 1; i++) {
                query = combineQueries(pop(), occur, query, valueType);
            }
        }
        if (valueType == ValueType.DOCUMENT) {
        	query.setFact(SINGULAR, true);
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
        if (DEBUG) {
        	log.debug("lhs: {} rhs: {}", lq, rq);
        }
        XPathQuery query = combineAdjacentQueries(pathExpr.getLHS(), pathExpr.getRHS(), lq, rq, ResultOrientation.RIGHT);
        if (DEBUG) {
        	log.debug("combined: {}", query);
        }
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
        AbstractExpression filter = predicate.getFilter();
        predicate.setFilter(optimizeExpression(filter, peek()));
        XPathQuery filterQuery = pop();
        XPathQuery baseQuery = pop();
        if (DEBUG) {
        	log.debug("base: {} [ {} ]", baseQuery, filterQuery);
        }
        if (filter.equals(LiteralExpression.TRUE)) {
        	push (baseQuery);
        	return predicate.getBase();
        }
        // In a path like /A[B]/C we need to generate /A/B AND /A/C, not /A/B/C
        // and from A[B[C]/D]/E we want A/B/C AND A/B/D and A/E
        // so leave the combined query on the stack, but save the base query for
        // path combination, so:
        
        // the query for a predicate is a query that matches the combination of base and filter,
        // and has a baseQuery that matches only the base
        XPathQuery query = combineAdjacentQueries(predicate.getBase(), filter, baseQuery, filterQuery,
                ResultOrientation.LEFT);
        ParseableQuery contextQuery = baseQuery.getPathQuery();
        if (DEBUG) {
        	log.debug("combined: {}", query);
        	log.debug("context: {}", contextQuery);
        }
        query.setPathQuery(contextQuery);
        push(query);
        // The combined query is singular if its base is
        peek().setFact(SINGULAR, baseQuery.isFact(SINGULAR));
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
/*
        if (rSlop != null && lSlop != null) {
            // total slop is the distance between the two path components.
            // the path relation is between baseQuery and rq
            // for example, baseQuery[lq]/rq or baseQuery[lq][rq]
            boolean isPredicate = orient == ResultOrientation.LEFT;
            query = lq.combineSpanQueries(rq, Occur.MUST, isPredicate, resultType, rSlop + lSlop, indexConfig);
        } else {
            query = combineQueries(lq, Occur.MUST, rq, resultType);
        }
        */
        boolean isPredicate = orient == ResultOrientation.LEFT;
        int slop = (rSlop != null && lSlop != null) ? rSlop + lSlop : -1;
        query = lq.combineSpanQueries(rq, Occur.MUST, isPredicate, resultType, slop, indexConfig);
        return query;
    }
    
    // AND the base-queries of the two queries, retaining the result type of q1
    /*
    private XPathQuery combineBaseQueries (XPathQuery q1, XPathQuery q2) {
    	XPathQuery base1 = q1.getBaseQuery();
    	XPathQuery base2 = q2.getBaseQuery();
    	if (base1 == null) {
    		return base2;
    	}
    	if (base2 == null) {
    		return base1;
    	}
    	return base1.combineBooleanQueries(Occur.MUST, base2, Occur.MUST, base1.getResultType(), indexConfig);
    }*/

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
                isSingular = false;
            } else {
                // No indexes that help with preceding/following axes
                isMinimal = false;
            }
        }
        XPathQuery query;
        // do we need currentQuery facts at all??
        long facts = currentQuery.getFacts() & ~EMPTY & ~IGNORABLE;
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
            query = XPathQuery.getQuery(MATCH_ALL.getBooleanQuery(), null, facts, type, indexConfig,
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
     * *all* of its arguments are non-empty, then its arguments' queries can be
     * combined with Occur.MUST. At first I thought that was the way most
     * functions work, but it's not: string(()) = '', not ()
     * 
     * Otherwise, no optimization is possible in the general case, which we
     * indicate by combining with Occur.SHOULD.
     * 
     * count(), exists() and empty() (and maybe later max(), min(), and
     * avg()? and string()?) are optimized as special cases.
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
        // By default, do not attempt any optimization; throw away any filters coming from the function arguments
        Occur occur = Occur.SHOULD;;
        if (name.equals(FunCall.FN_ROOT) || name.equals(FunCall.FN_DATA) || name.equals(FunCall.FN_EXISTS) ||
             name.getNamespaceURI().equals(FunCall.XS_NAMESPACE)) {
        	// require that the function argument's query match if it is a special function,
            // or an atomic type constructor (functions in the xs: namespace)
        	occur = Occur.MUST;
        } 
        AbstractExpression[] args = funcall.getSubs();
        combineTopQueries(args.length, occur, funcall.getReturnType());
        if (occur == Occur.SHOULD) {
            XPathQuery argq = pop();
            argq = argq.setFact(IGNORABLE, true);
            argq = argq.setFact(MINIMAL, false);
            push(argq);
        }
        if (name.equals(FunCall.LUX_KEY) || name.equals(FunCall.LUX_FIELD_VALUES)) {
            if (args.length > 0) {
                AbstractExpression arg = args[0];
                AbstractExpression sortContext;
                if (args.length > 1) {
                	sortContext = args[1];
                } else {
                	sortContext = funcall.getSuper();
                	if (sortContext == null) {
                	    return funcall;
                	}
                }
                VariableContext binding = sortContext.getBindingContext();
                if (binding == null || ! (binding instanceof ForClause)) {
                    return funcall;
                }
                if (arg.getType() == Type.LITERAL) {
                    // save away the field name as a possible sort key
                    String fieldName = ((LiteralExpression) arg).getValue().toString();
                    FieldDefinition fieldDefinition = indexConfig.getField(fieldName);
                    SortField.Type sortType;
                    if (fieldDefinition != null) {
                        sortType = fieldDefinition.getType().getLuceneSortFieldType();
                    } else {
                        sortType = FieldDefinition.Type.STRING.getLuceneSortFieldType();
                        log.warn("Sorting by unknown field: {}", fieldName);
                    }
                    peek().setSortFields(new SortField[] { new SortField(fieldName, sortType) });
                }
            }
        }
        return funcall;
    }

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
        if (subs.length == 1 && isSearchCall(subs[0])) {
            if (fname.equals(FunCall.FN_COUNT) || fname.equals(FunCall.FN_EXISTS) || fname.equals(FunCall.FN_EMPTY)) {
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
            push(XPathQuery.MATCH_ALL);
            return new Root();
        }
        XPathQuery query = pop();
        // can only use these function optimizations when we are sure that its
        // argument expression
        // is properly indexed - MINIMAL here guarantees that every document
        // matching the query will
        // produce a non-empty result in the function's argument
        if (searchArg != null || query.isMinimal()) {
        	// TODO simplify the logic here -- there are two different conditions overlapping
            int functionFacts = 0;
            ValueType returnType = null;
            QName qname = null;
            if (fname.equals(FunCall.FN_COUNT) && query.isFact(SINGULAR)) {
                functionFacts = SINGULAR;
                returnType = ValueType.INT;
                qname = FunCall.LUX_COUNT;
            } else if (fname.equals(FunCall.FN_EXISTS)) {
                returnType = ValueType.BOOLEAN;
                qname = FunCall.LUX_EXISTS;
            } else if (fname.equals(FunCall.FN_EMPTY)) {
                functionFacts = BOOLEAN_FALSE;
                returnType = ValueType.BOOLEAN;
                qname = FunCall.LUX_EXISTS;
            }
            if (qname != null) {
                // We will insert a searching function. apply no restrictions to
                // the enclosing scope:
                if (searchArg != null) {
                    // create a searching function call using the argument to
                    // the enclosed lux:search call
                    push(XPathQuery.MATCH_ALL);
                    return new FunCall(qname, returnType, searchArg);
                }
                query = query.setType(returnType);
                query =  query.setFact(functionFacts, true);
                AbstractExpression root = subs[0].getRoot();
                if (! isSearchCall(root)) {
                    push(XPathQuery.MATCH_ALL);
                    return createSearchCall(qname, query);
                }
            }
        }
        push(query);
        return funcall;
    }

    private boolean isSearchCall(AbstractExpression root) {
        return root instanceof SearchCall || (root instanceof FunCall && ((FunCall) root).getName().equals(FunCall.LUX_SEARCH));
    }

    private XPathQuery combineQueries(XPathQuery lq, Occur occur, XPathQuery rq, ValueType resultType) {
        XPathQuery query;
        if (indexConfig.isOption(INDEX_PATHS)) {
            query = lq.combineSpanQueries(rq, occur, false, resultType, -1, indexConfig);
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
            Term term = new Term(indexConfig.getFieldName(FieldRole.PATH), nodeName);
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
        // only if both args are nodes will this be a node
        ValueType argType = lq.getResultType().promote(rq.getResultType());
        ValueType resultType = null;
        Occur occur = Occur.SHOULD;
        boolean minimal = false;
        boolean required = false;
        AbstractExpression rangeOptimized = null;
        switch (op.getOperator()) {

        case AND:
            occur = Occur.MUST;
        case OR:
            minimal = true;
            required = true;
            resultType = ValueType.BOOLEAN;
            break;

        case ADD:
        case SUB:
        case DIV:
        case MUL:
        case IDIV:
        case MOD:
            resultType = ValueType.ATOMIC;
            break;

        case AEQ:
        case ANE:
        case ALT:
        case ALE:
        case AGT:
        case AGE:
        case EQUALS:
        case NE:
        case LT:
        case GT:
        case LE:
        case GE:
            if (lq.getResultType().isNode || rq.getResultType().isNode) {
                required = true;
                occur = Occur.MUST;
            }
            rangeOptimized = optimizeRangeComparison (lq, rq, op);
            resultType = ValueType.BOOLEAN;
            break;

        case IS:
        case BEFORE:
        case AFTER:
            // occur = Occur.MUST;
            required = true;
            resultType = ValueType.BOOLEAN;
            break;

        case INTERSECT:
            occur = Occur.MUST;
        case UNION:
            minimal = true;
            required = true;
            resultType = argType;
            break;

        case EXCEPT:
            push(combineQueries(lq, Occur.MUST, rq, argType));
            resultType = argType;
            required = true;
            return op;
            
        case TO:
            resultType = ValueType.INTEGER;
            break;
        }
        XPathQuery query = combineQueries(lq, occur, rq, resultType);
        if (rangeOptimized != null) {
            query.setFact(MINIMAL|SINGULAR, true);
            query.setFact(IGNORABLE, false);
            push(query);
        }
        else {
            if (minimal == false) {
                query = query.setFact(MINIMAL, false);
            }
            if (!required) {
                query = query.setFact(IGNORABLE, true);
            }
            push(query);
            if (op.getOperator() == Operator.EQUALS || op.getOperator() == Operator.AEQ) {
                optimizeBinaryOperation (op);
            }
        }
        return rangeOptimized == null ? op : rangeOptimized;
    }

    /** Attempt to replace the XQuery comparison with an equivalent indexed query.
     * When optimization is possible, push the resulting query on the stack; otherwise 
     * the stack is left unchanged, and it is the caller's responsibility to push something there.
     * @param lq
     * @param rq
     * @param op
     * @return when optimization is possible, return the literal value 'true()', otherwise null.
     */
    private AbstractExpression optimizeRangeComparison(XPathQuery lq, XPathQuery rq, BinaryOperation op) {
        LiteralExpression value = null;
        AbstractExpression op1 = op.getOperand1(), op2 = op.getOperand2(), expr = null;
        // if there is no context item, lux:key() returns ()
        if (op1.getType() == Type.LITERAL) {
            value = (LiteralExpression) op1;
            expr = op2;
        } else if (op2.getType() == Type.LITERAL) {
            value = (LiteralExpression) op2;
            expr = op1;
        } else {
            return null;
        }
        /* resolve variable
         *  TODO: when the bound expression depends on the context expression (is Dot, in this simplified view), 
         *  we actually are interested in the context of the variable, not in the bound expression, so this isn't
         *  actually doing what we think it is...
         */
        if (expr.getType() == Type.VARIABLE) {
            VarBinding varBinding = varBindings.get(((Variable) expr).getQName());
            if (varBinding == null) {
                return null;
            }
            if (expr == op1) {
                lq = varBinding.getQuery();
            } else {
                rq = varBinding.getQuery();
            }
            expr = varBinding.getExpr();
            if (expr == null) {
                return null;
            }
        } 
        // rewrite minimax(AtomizedSequence(... expr )) to expr
        expr = rewriteMinMax (expr);
        // Check for a call to lux:key()
        boolean directKeyMatch;
        FieldDefinition field = fieldMatching(expr);
        if (field != null) {
        	directKeyMatch = true;
        }
        else {
            directKeyMatch = false;
            // analyze the xpath for equivalence to indexed fields
            // TODO: match multiple fields
            field = matchField (expr, op);
        }
        if (field == null) {
            // no matching field found
            return null;
        }
        FieldDefinition.Type fieldType = field.getType();
        if (! isComparableType(value.getValueType(), fieldType)) {
            // will throw a run-time error if it gets executed
            return null;
        }
        String fieldName = field.getName();
        RangePQuery.Type rangeTermType = fieldType.getRangeTermType();
        ParseableQuery rangeQuery;
        String v = value.getValue().toString();
        Operator operator = op.getOperator();
        switch (operator) {
        case AEQ: case EQUALS:
        case ANE: case NE:
            if ("string".equals(rangeTermType)) {
                rangeQuery = new TermPQuery (new Term(fieldName, v)); 
            } else {
                rangeQuery = new RangePQuery (fieldName, rangeTermType, v, v, true, true); 
            }
            if (operator == Operator.ANE || operator == Operator.NE) {
                rangeQuery = new BooleanPQuery(Occur.MUST_NOT, rangeQuery);
            }
            break;
        case ALE: case LE:
            rangeQuery = new RangePQuery (fieldName, rangeTermType, null, v, false, true); break;
        case ALT: case LT:
            rangeQuery = new RangePQuery (fieldName, rangeTermType, null, v, false, false); break;
        case AGE: case GE:
            rangeQuery = new RangePQuery (fieldName, rangeTermType, v, null, true, false); break;
        case AGT: case GT:
            rangeQuery = new RangePQuery (fieldName, rangeTermType, v, null, false, false); break;
        default:
            return null;
        }
        rangeQueries.add (new XPathQuery(rangeQuery, MINIMAL|SINGULAR, ValueType.BOOLEAN));
        // If we are sure that the expression will evaluate to true when the query matches,
        // just return true()
        return directKeyMatch ? LiteralExpression.TRUE : op;
    }
    
    /**
     * find fields whose leaves match the expression and walk up the expression trees in parallel
     * checking if the entire field expression matches
     * 
     * keep a list of possible matches; these are expressions tied to
     * fields - on a successful match we will have the topmost node of the
     * expression tree, and can look the field up from there?
     * 
     */
    private FieldDefinition matchField(AbstractExpression expr, BinaryOperation comparison) {
    	AbstractExpression leafExpr = expr.getLastContextStep();
    	if (leafExpr instanceof Dot) {
    		leafExpr = getBaseContextStep (expr);
    	}
    	// TODO: this just picks some matching index -- it should either pick the one that is most
    	// restrictive, or use all of them
        for (AbstractExpression fieldLeaf : compiler.getFieldLeaves (leafExpr)) {
            AbstractExpression fieldExpr = matchUpwards (leafExpr, fieldLeaf, comparison);
            if (fieldExpr != null) {
                return compiler.getFieldForExpr (fieldExpr);
            }
        }
        return null;
    }
    
    // return the last context step of the base expression of the enclosing predicate
    private AbstractExpression getBaseContextStep (final AbstractExpression expr) {
        AbstractExpression e;
        for (e = expr; 
                e != null && e.getType() != Type.PREDICATE; 
                e = e.getSuper()) {
        }
    	if (e == null) {
    		return expr;
    	}
    	return ((Predicate)e).getBase().getLastContextStep();
    }

    /**
     * @param queryExpr query expression that is being optimized 
     * @param fieldExpr XPath field expression to search for
     * @param enclosingExpr starting point of the search.  Used to avoid re-scanning subtrees that
     * have already been visited.
     * @return the root of the fieldExpr, if it matches a subtree of the queryExpr, or null if it doesn't.
     */
    private AbstractExpression matchUpwards (AbstractExpression queryExpr, AbstractExpression fieldExpr, AbstractExpression enclosingExpr) {
        AbstractExpression fieldSuper = getEquivSuper(fieldExpr), querySuper = getEquivSuper(queryExpr);
        if (fieldSuper == null) {
            return fieldExpr;
        }
        if (querySuper == enclosingExpr) {
        	querySuper = getEquivSuper (querySuper);
        }
        if (querySuper == null) {
            return null;
        }
        if (! querySuper.matchDown (fieldSuper, fieldExpr)) {
            return null;
        }
        return matchUpwards (querySuper, fieldSuper, enclosingExpr);
    }
    
    private AbstractExpression getEquivSuper (AbstractExpression expr) {
    	// walk up the tree, skipping nodes that preserve query-equivalence
    	// TODO: if this gets more complex, break into a method on AE
    	AbstractExpression sup = expr.getSuper();
    	if (sup != null && sup.getType() == Type.FUNCTION_CALL) {
    		// data(), exists(),  
    		if (sup.isRestrictive()) {
    			return getEquivSuper (sup);
    		}
    	}
    	return sup;
    }

    private FieldDefinition fieldMatching(AbstractExpression expr) {
        if (expr.getType() == Type.FUNCTION_CALL) {
            FunCall funcall = (FunCall) expr;
            QName funcName = funcall.getName();
            if (funcName.equals(FunCall.LUX_KEY) || funcName.equals(FunCall.LUX_FIELD_VALUES)) {
                AbstractExpression arg = funcall.getSubs()[0];
                if (arg instanceof LiteralExpression) {
                    String fieldName = ((LiteralExpression) arg).getValue().toString();
                    return indexConfig.getField(fieldName);
                }
            }
        }
        return null;
    }
	
	// Undo an unhelpful rewrite for general comparisons supplied by Saxon.
    // Range indexes will match docs s.t. any
    // instance of the range expressions matches the criterion
    private AbstractExpression rewriteMinMax (AbstractExpression expr) {
        if (expr.getType() == Type.FUNCTION_CALL) {
            FunCall funcall = (FunCall) expr;
            QName funcName = funcall.getName();
            if (funcName.equals(FunCall.FN_MIN) || funcName.equals(FunCall.FN_MAX)) {
            	AbstractExpression arg = funcall.getSubs()[0];
				if (arg instanceof FunCall) {
				    FunCall fnarg = (FunCall) arg;
				    if (fnarg.getName().equals(FunCall.LUX_KEY) || fnarg.getName().equals(FunCall.LUX_FIELD_VALUES)) {
	                    return fnarg;
	                }
            	}
            }
        }
        return expr;
    }
    
    private boolean isComparableType(ValueType valueType, FieldDefinition.Type fieldType) {
        if (valueType.isNode || valueType == ValueType.VALUE || valueType == ValueType.ATOMIC) {
            // These are plausible: will be determined at run-time
            return true;
        }
        switch (fieldType) {
        case STRING:
            return valueType == ValueType.STRING || valueType == ValueType.UNTYPED_ATOMIC;
        case INT: case LONG:
            return valueType.isNumeric;
        default:
            return false;
        }
    }

    private PathStep getLastPathStep (AbstractExpression path) {
        AbstractExpression last = path.getLastContextStep();
        if (last.getType() == Type.DOT) {
            // NOTE: we rely on Saxon to collapse paths s.t. a path *only ends in Dot if
            // the entire path is Dot.*  In this case we get the context from the base 
            // of the enclosing predicate, if any
            AbstractExpression p = path;
            while (p != null && ! (p instanceof Predicate)) {
                p = p.getSuper();
            }
            if (p == null) {
                return null;
            }
            last = ((Predicate)p).getBase().getLastContextStep();
        }
        if (last instanceof PathStep) {
            return (PathStep) last;
        }
        return null;
    }

    private void optimizeBinaryOperation (BinaryOperation op) {
        if (!indexConfig.isOption(INDEX_FULLTEXT)) {
            return;
        }
        LiteralExpression value = null;
        AbstractExpression path = null;
        if (!(op.getOperator() == Operator.EQUALS || op.getOperator() == Operator.AEQ)) {
            return;
        }
        AbstractExpression op1 = op.getOperand1(), op2 = op.getOperand2();
        if (op1.getType() == Type.LITERAL) {
            value = (LiteralExpression) op1;
            path = op2;
        } else if (op2.getType() == Type.LITERAL) {
            value = (LiteralExpression) op2;
            path = op1;
        } else {
            // TODO: handle variables
            // TODO: handle sequences of literals
            return;
        }
        PathStep step = getLastPathStep (path);
        if (step == null) {
            return;
        }
        String v = value.getValue().toString();
        ParseableQuery termQuery = null;
        termQuery = createTermQuery(step, v);
        if (termQuery != null) {
            combineTermQuery (termQuery, step.getNodeTest().getType());
        }
    }

    private ParseableQuery createTermQuery(PathStep context, String value) {
        NodeTest nodeTest = context.getNodeTest();
        QName nodeName = nodeTest.getQName();
        if (nodeName == null || "*".equals(nodeName.getPrefix()) || "*".equals(nodeName.getLocalPart())) {
            return makeTextQuery(value, indexConfig);
        } else if (nodeTest.getType() == ValueType.ELEMENT) {
            return makeElementValueQuery(nodeName, value, indexConfig);
        } else if (nodeTest.getType() == ValueType.ATTRIBUTE) {
            return makeAttributeValueQuery(nodeName, value, indexConfig);
        }
        return null;
    }
    
    private void combineTermQuery (ParseableQuery termQuery, ValueType termType) {
        XPathQuery tq = XPathQuery.getQuery(termQuery, null, MINIMAL, termType, indexConfig, null);
        XPathQuery q = pop();
        XPathQuery combined;
        if (q.getBooleanQuery() instanceof TermPQuery) {
            // a single term query must be for the term covered by the termQuery we just created,
            // so it would be redundant: skip it.
            combined = tq;
        } else {
            combined = combineQueries(tq, Occur.MUST, q, q.getResultType());
        }
        combined.setPathQuery (q.getPathQuery());
        push(combined);
    }

    public static NodeTextQuery makeElementValueQuery(QName qname, String value, IndexConfiguration config) {
        return new NodeTextQuery(new Term(config.getElementTextFieldName(), value),
                qname.getEncodedName());
    }

    public static ParseableQuery makeAttributeValueQuery(QName qname, String value, IndexConfiguration config) {
        return new NodeTextQuery(new Term(config.getAttributeTextFieldName(), value),
                qname.getEncodedName());
    }

    public static ParseableQuery makeTextQuery(String value, IndexConfiguration config) {
        return new NodeTextQuery(new Term(config.getTextFieldName(), value));
    }

    @Override
    public AbstractExpression visit(LiteralExpression literal) {
        if (DEBUG) {
            debug("visit", literal);
        }
        push(XPathQuery.MATCH_ALL.setFact(IGNORABLE, true));
        return literal;
    }

    @Override
    public AbstractExpression visit(Variable variable) {
        if (DEBUG) {
            debug("visit", variable);
        }
        QName qName = variable.getQName();
        VarBinding varBinding = varBindings.get(qName);
        if (varBinding != null) {
            XPathQuery q = varBinding.getQuery();
            push(q);
            AbstractExpression value = varBinding.getExpr();
            variable.setValue(value);
            variable.setBindingContext(varBinding.getContext());
        } else {
            // this happens when the variables represent function arguments
            push(XPathQuery.MATCH_ALL.setFact(IGNORABLE, true));
        }
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
        // not minimal or singular unless we are selecting the first or last item from a sequence; 
        if (start == FunCall.LastExpression ||
                start.equals(LiteralExpression.ONE) && length.equals(LiteralExpression.ONE)) {
            return subsequence;
        }
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
        // Any (/) in the expression will have been replaced with a search, 
        // unless this expression is inside a user-defined function.
        AbstractExpression root = sequence.getRoot();
		if (root == null || root.getType() != Type.FUNCTION_CALL || LiteralExpression.ONE.equals(start)) {
        	return subsequence;
        }
        FunCall search = (FunCall) root;
        AbstractExpression[] args = search.getSubs();
        if (args.length >= 3) {
        	// there is already a start arg provided
        	return subsequence;
        }
        boolean isSingular;
        if (args.length < 1) {
        	isSingular = true; // this must be a user-supplied search call
        } else if (search instanceof SearchCall) {
        	isSingular = ((SearchCall) search).getQuery().isFact(SINGULAR);
        } else {
        	isSingular = false;
        }
        if (isSingular) {
        	AbstractExpression[] newArgs = new AbstractExpression[3];
        	int i = 0;
        	while (i < args.length) {
        		newArgs[i] = args[i];
        		++i;
        	}
        	while (i < 2) {
        		newArgs[i++] = LiteralExpression.EMPTY;
        	}
        	newArgs[i] = start;
        	search.setArguments(newArgs);
        	if (length == null || length.equals(LiteralExpression.EMPTY)) {
        		return search;
        	}
        	subsequence.setStartExpr(LiteralExpression.ONE);
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
        for (XPathQuery rangeq : rangeQueries) {
            query = combineQueries (query, Occur.MUST, rangeq, query.getResultType());
        }
        rangeQueries.clear();
        if (functionName.equals(FunCall.LUX_SEARCH)) {
            // searchCall.setFnCollection (!optimizeForOrderedResults);
            return new SearchCall(query, indexConfig);
        }
        FunCall fn = new FunCall(functionName, query.getResultType(), query.toXmlNode(indexConfig.getDefaultFieldName(), indexConfig));
        if (query.isFact(BOOLEAN_FALSE)) {
        	return new FunCall(FunCall.FN_NOT, ValueType.BOOLEAN, fn);
        } else {
        	return fn;
        }
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
        push(XPathQuery.MATCH_ALL);
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
            XPathQuery returnq = pop(); // get top two queries
            XPathQuery clauseq = pop();
            if (clause instanceof LetClause) {
                // the let query will have been marked as ignorable, but in this
                // context it is not.
            	clauseq.setFact(IGNORABLE, false);
                // merge into let query (leave the top query alone - don't
                // combine let-constraints with it)
                XPathQuery q = combineQueries(clauseq, Occur.MUST, returnq, clauseq.getResultType());
                clause.setSequence(optimizeExpression(seq, q));
                push(returnq); // restore accumulating return query to top of stack
            } else {
            	XPathQuery q = combineQueries (clauseq, Occur.MUST, returnq, returnq.getResultType());
            	//q.setBaseQuery(combineBaseQueries(returnq, clauseq));
            	push (q);
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
        VarBinding binding = varBindings.remove(varName);
        if (binding != null && binding.getShadowedBinding() != null) {
            // time to come out from the shadows, binding!
            varBindings.put(varName,  binding.getShadowedBinding());
        }
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
            SortKey sortKey = sortKeys.get(i);
            AbstractExpression key = sortKey.getKey();
            if (q.getSortFields() == null) {
            	// TODO: analyze the expression, matching against xpath indexes 
                // once we find an unindexed sort field, stop adding sort
                // indexes to the query
                foundUnindexedSort = true;
            } else if (!foundUnindexedSort) {
            	// previous analysis determined this order by clause should be optimized
                if (key instanceof FunCall) {
                    // field-values() with one argument depends on context 
                    FunCall keyFun = (FunCall) key;
                    if (keyFun.getName().equals(FunCall.LUX_KEY) || keyFun.getName().equals(FunCall.LUX_FIELD_VALUES)) { 
                    	if (keyFun.getSubs().length < 2) {
                    		throw new LuxException(
                    				"lux:key($key) depends on the context where there is no context defined");
                    	}
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
            XPathQuery query = XPathQuery.getQuery(MATCH_ALL.getBooleanQuery(), null, MATCH_ALL.getFacts(),
                    MATCH_ALL.getResultType(), indexConfig, sortFields.toArray(new SortField[sortFields.size()]));
            push(query);
        }
        return orderByClause;
    }

    @Override
    public ForClause visit(ForClause forClause) {
        visitVariableBinding(forClause);
        return forClause;
    }

    @Override
    public WhereClause visit(WhereClause whereClause) {
        // Do not use the where clause expression to filter the enclosing 
        // FLWOR!  Our assumption is that Saxon will already have converted any
        // optimizable where clauses into XPath predicate expressions
        pop();
        push (MATCH_ALL);
        return whereClause;
    }

    @Override
    public LetClause visit(LetClause letClause) {
        visitVariableBinding(letClause);
        // Mark let clause queries as ignorable so that expressions that depend
        // on the let variable don't require the variable to be non-empty by
        // default
        // peek().setFact(IGNORABLE, true);
        // It seems we no longer need this.  Some upstream logic is now cleverer?
        // TODO: Can we do away with the IGNORABLE flag?  This what its genesis, but something
        // else probably relies on it now...
        return letClause;
    }

    private void visitVariableBinding(VariableBindingClause clause) {
        XPathQuery q = peek();
        q.setSortFields(null);
        setBoundExpression(clause, q);
    }

    private void setUnboundVariable (Variable var) {
    	QName name = var.getQName();
        VarBinding currentBinding = varBindings.get(name);
        VarBinding newBinding = new VarBinding (var, null, MATCH_ALL, null, currentBinding);
        varBindings.put(name, newBinding);
    }
    
    private void setBoundExpression(VariableBindingClause clause, XPathQuery q) {
        // remember the variable binding for use in optimizations
    	Variable var = clause.getVariable();
    	QName name = var.getQName();
        VarBinding currentBinding = varBindings.get(name);
        VarBinding newBinding = new VarBinding (var, clause.getSequence(), q, clause, currentBinding);
        varBindings.put(name, newBinding);
    }

    public AbstractExpression getBoundExpression(QName name) {
        VarBinding binding = varBindings.get(name);
        while (binding != null && binding.getExpr() instanceof Variable) {
            // variable bound to another variable?
            binding = varBindings.get(((Variable) binding.getExpr()).getQName());
        }
        return binding == null ? null : binding.getExpr();
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
