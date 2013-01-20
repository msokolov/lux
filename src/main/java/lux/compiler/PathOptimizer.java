package lux.compiler;

import static lux.index.IndexConfiguration.INDEX_FULLTEXT;
import static lux.index.IndexConfiguration.INDEX_PATHS;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;

import lux.Compiler.SearchStrategy;
import lux.SearchResultIterator;
import lux.exception.LuxException;
import lux.index.FieldName;
import lux.index.IndexConfiguration;
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
import lux.xquery.WhereClause;
import lux.xquery.XQuery;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.SortField;

/**
 * Prepares an XPath expression tree for indexed execution against a
 * Lux data store.
 * 
 * This class is part of the Lux internal API and is not intended to be called by consumers of the API.
 *
 * The general strategy here is to consider each expression in isolation,
 * determining whether it imposes any restriction on its context, and then
 * to compose such constraints into queries, to be executed by a searcher, with
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
    private final IndexConfiguration indexConfig;
    private final XPathQuery MATCH_ALL;
    
    private final String attrQNameField;
    private final String elementQNameField;
    private boolean optimizeForOrderedResults;
    private int nextVariableNumber = 0;
    
    public PathOptimizer(IndexConfiguration indexConfig) {
        queryStack = new ArrayList<XPathQuery>();
        MATCH_ALL = XPathQuery.getMatchAllQuery(indexConfig);
        this.indexConfig = indexConfig;
        attrQNameField = indexConfig.getFieldName(FieldName.ATT_QNAME);
        elementQNameField = indexConfig.getFieldName(FieldName.ELT_QNAME);
        optimizeForOrderedResults = true;
    }    

    /**
     * Prepares an XQuery module for indexed execution against a
     * Lux data store.  See {@link #optimize(AbstractExpression)}.
     * 
     * @param query the query to optimize
     * @return the optimized query
     */
    public XQuery optimize(XQuery query) {
        queryStack.clear();
        push (MATCH_ALL);
        AbstractExpression main = query.getBody();
        // Don't attempt to optimize if no indexes are available, or if the query has no body
        if (main != null && indexConfig.isIndexingEnabled()) {
            main = optimize (main);
            return new XQuery(query.getDefaultElementNamespace(), query.getDefaultFunctionNamespace(), query.getDefaultCollation(), 
                    query.getModuleImports(), query.getNamespaceDeclarations(), query.getVariableDefinitions(),
                    optimizeFunctionDefinitions (query.getFunctionDefinitions()),
                    main, query.getBaseURI(), query.isPreserveNamespaces(), query.isInheritNamespaces(), query.isEmptyLeast());
        }
        return query;
    }
    
    // note: modifies its argument
    private FunctionDefinition[] optimizeFunctionDefinitions(FunctionDefinition[] functionDefinitions) {
        for (int i = 0; i < functionDefinitions.length; i++) {
            FunctionDefinition function = functionDefinitions[i];
            AbstractExpression body = optimize (function.getBody());
            function = new FunctionDefinition (function.getName(), function.getReturnType(), (Variable[]) function.getSubs(), body);
            functionDefinitions[i] = function;
        }
        return functionDefinitions;
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
        // visit the expression tree, optimizing any absolute sub-expressions
        expr = expr.accept (this);
        // optimize the top level expression
        return optimizeExpression (expr, 0);
    }
    
    /**
     * @param expr the expression to optimize
     * @param j the query stack depth at which expr's query is to be found
     * @param facts additional facts to apply to the query
     */
    private AbstractExpression optimizeExpression (AbstractExpression expr, int j) {
        if (expr instanceof SearchCall) {
            // TODO: when handling count(), exists(), etc: merging facts loses info about their optimizations
            // append any additional constraints from where clauses
            // and ordering criteria from order by clauses
            // to an existing search call
            return mergeSearchCall ((SearchCall) expr, j);
        }
        if (! expr.isAbsolute()) {
            return expr;
        }
        FunCall search = getSearchCall(j);
        if (search.getReturnType().equals(ValueType.DOCUMENT)) {
            // Avoid the need to sort the results of this expression so that it can be 
            // embedded in a subsequence or similar and evaluated lazily.
            AbstractExpression tail = expr.getTail();
            if (tail != null) {
                return new Predicate (search, tail);
            }
        }
        if (optimizeForOrderedResults) {
            return expr.replaceRoot (search);
        } else {
            // We can't assert that lux:search is document-ordered since saxon PE/EE doesn't allow it;
            // the next best thing is to produce an expression that is not expected to be 
            // *in* document order.  In some cases where the context requires ordering (like intersect)
            // this leads to suboptimal evaluation (ie retrieving all of one or both of the sequences)
            Variable var = new Variable (new QName("_lx" + nextVariableNumber++));
            expr = expr.replaceRoot(var);
            // for $var in lux:search(...) return $var op $expr
            // avoids the need for document-ordering (b/c we are *already* document-ordered)
            return new FLWOR (expr, new ForClause(var, null, search));
        }
    }

    /**
     * Each absolute subexpression S is joined with a call to lux:search(), 
     * effectively replacing it with search(QS)/S, where QS is the query derived 
     * corresponding to S.  In addition, if S returns documents, the foregoing expression
     * is wrapped by root(); root(search(QS)/S)
     * 
     * @param expr an expression
     */
    protected void optimizeSubExpressions(AbstractExpression expr) {
        AbstractExpression[] subs = expr.getSubs();
        for (int i = 0; i < subs.length; i ++) {
            subs[i] = optimizeExpression (subs[i], subs.length - i - 1);
        }
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
            query = XPathQuery.getQuery(query.getParseableQuery(), query.getFacts(), type, indexConfig, query.getSortFields());
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

    @Override
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
        predicate.setFilter (optimizeExpression (predicate.getFilter(), 0));
        XPathQuery filterQuery = pop();
        XPathQuery baseQuery = pop();
        
        XPathQuery query = combineAdjacentQueries(predicate.getBase(), predicate.getFilter(), baseQuery, filterQuery, ResultOrientation.LEFT);
        // This is a counting expr if its base expr is
        query.setFact(XPathQuery.COUNTING, baseQuery.isFact(XPathQuery.COUNTING));
        push (query);
        optimizeComparison(predicate);
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
        if (indexConfig.isOption(INDEX_PATHS)) {
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
            query = lq.combineSpanQueries(rq, Occur.MUST, resultType, rSlop + lSlop);
        } else {
            query = combineQueries (lq, Occur.MUST, rq, resultType);
        }
        return query;
    }
    
    @Override
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
            // FIXME: This depends on the indexes available; with path indexes, this should be true
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
            query = XPathQuery.getQuery(MATCH_ALL.getParseableQuery(), getQuery().getFacts(), type, indexConfig, getQuery().getSortFields());
        } else {
            ParseableQuery termQuery = nodeNameTermQuery(step.getAxis(), name);
            query = XPathQuery.getQuery(termQuery, isMinimal ? XPathQuery.MINIMAL : 0, step.getNodeTest().getType(), indexConfig, getQuery().getSortFields());
        }
       push(query);
       return step;
    }
    
    /**
     * If a function F is emptiness-preserving, in other words F(a,b,c...)
     * is empty ( =()) if *any* of its arguments are empty, and is
     * non-empty if *all* of its arguments are non-empty, then its
     * argument's queries can be combined with Occur.MUST.  At first I thought that was the way
     * most functions work, but it's not: string(()) = '', not ()
     *
     * Otherwise, no optimization is possible in the general case, which we indicate by 
     * combining with Occur.SHOULD.
     *
     * count(), exists() and not()
     *  (and maybe max(), min(), and avg()?) are optimized as special cases.
     *  
     * @param funcall the function call expression to optimize
     * @return the same function call expression, after having possibly optimized its arguments
     */

    @Override
    public AbstractExpression visit(FunCall funcall) {
        QName name = funcall.getName();
        // Try special function optimizations, like count(), exists(), etc.
        AbstractExpression luxfunc = optimizeFunCall (funcall);
        if (luxfunc != funcall) {
            return luxfunc;
        }
        // see if the function args can be converted to searches.
        optimizeSubExpressions(funcall);
        Occur occur;
        String namespaceURI = name.getNamespaceURI();
        if (! (namespaceURI.equals (FunCall.FN_NAMESPACE) ||
                namespaceURI.equals(FunCall.XS_NAMESPACE) ||
                namespaceURI.equals(FunCall.LUX_NAMESPACE))) {
            // We know nothing about this function; it's best 
            // not to attempt any optimization, so we will throw away any filters coming from the
            // function arguments
            occur = Occur.SHOULD;
        }
        // a built-in XPath 2 function
        else if (isomorphs.contains(name.getLocalPart())) {
            occur = Occur.MUST;
            // what does it mean if occur is null here??
        } else {
            // for functions in fn: and xs: namespaces not listed below, we assume that they 
            // are *not* optimizable
            occur = Occur.SHOULD;
        }
        AbstractExpression[] args = funcall.getSubs();
        if (occur == Occur.SHOULD) {
            for (int i = args.length; i > 0; --i) {
                pop();
            }
            push (XPathQuery.getQuery(MATCH_ALL.getParseableQuery(), 0, ValueType.VALUE, indexConfig, null));
        } else {
            combineTopQueries(args.length, occur, funcall.getReturnType());
        }
        if (name.equals(FunCall.LUX_FIELD_VALUES)) {
            if (args.length > 0) {
                AbstractExpression arg = args[0];
                if (arg.getType() == Type.LITERAL) {
                    // save away the field name as a possible sort key
                    getQuery().setSortFields(new SortField[] {new SortField (((LiteralExpression)arg).getValue().toString(), SortField.STRING)});
                }
            }
        }
        return funcall;
    }

    // These functions are emptiness-preserving - if their argument is empty, then their
    // result is also empty
    protected static HashSet<String> isomorphs = new HashSet<String>();

    static {
        isomorphs.add("data");
        isomorphs.add("exists");
        isomorphs.add("root");
        isomorphs.add("collection");
        isomorphs.add("doc");
        isomorphs.add("uri-collection");
        isomorphs.add("unparsed-text");
    };
    
    /**
     * Possibly convert this function call to a lux: function.  If we do that, all the arguments' queries
     * will be removed from the stack.  Otherwise leave them there for the caller.
     * @param funcall a function call to be optimized
     * @return an optimized function, or the original function call
     */
    
    private AbstractExpression optimizeFunCall (FunCall funcall) {
        AbstractExpression[] subs = funcall.getSubs();
        QName fname = funcall.getName();
        
        // If this function's single argument is a call to lux:search, get its query.  We may remove the call
        // to lux:search if this function can perform the search itself without retrieving documents
        AbstractExpression searchArg = null;
        if (subs.length == 1 && subs[0].getType() == Type.FUNCTION_CALL && ((FunCall)subs[0]).getName().equals(FunCall.LUX_SEARCH)) {
            if (fname.equals(FunCall.FN_COUNT) || fname.equals(FunCall.FN_EXISTS) || fname.equals(FunCall.FN_EMPTY)) {
                searchArg = subs[0].getSubs()[0];
            }
        } else if (fname.equals(FunCall.LUX_SEARCH) && !(funcall instanceof SearchCall)) {
            if (subs.length == 1) {
                return new SearchCall (subs[0]);
            } else {
                return funcall;
            }
        }
        else if (subs.length == 1 && !subs[0].isAbsolute()) {
            return funcall;
        }
        if (fname.equals(FunCall.FN_COLLECTION) && subs.length == 0) {
            // Optimize when no arguments to collection()
            return new Root();
        } 
        XPathQuery query = pop();
        // can only use these function optimizations when we are sure that its argument expression
        // is properly indexed - MINIMAL here guarantees that every document matching the query will 
        // produce a non-empty result in the function's argument
        if (query.isMinimal()) {
            int functionFacts = 0;
            ValueType returnType = null;
            QName qname = null;
            if (fname.equals(FunCall.FN_COUNT) && query.getResultType().is(ValueType.DOCUMENT)) {
                functionFacts = XPathQuery.COUNTING;
                returnType = ValueType.INT;
                qname = FunCall.LUX_COUNT;
            } 
            else if (fname.equals(FunCall.FN_EXISTS)) {
                functionFacts = XPathQuery.BOOLEAN_TRUE;
                returnType = ValueType.BOOLEAN;
                qname = FunCall.LUX_EXISTS;
            }
            else if (fname.equals(FunCall.FN_EMPTY)) {
                functionFacts = XPathQuery.BOOLEAN_FALSE;
                returnType = ValueType.BOOLEAN_FALSE;
                qname = FunCall.LUX_EXISTS;
            }
            else if (fname.equals(FunCall.FN_CONTAINS)) {
                // also see optimizeComparison
                if (! subs[0].isAbsolute()) {
                    // don't query if the sequence arg isn't absolute??
                    // if the arg is relative, presumably the contains is in a predicate somewhere
                    // and may have been optimized there?
                    push (query);
                    return funcall;
                }
                functionFacts = XPathQuery.BOOLEAN_TRUE;
                returnType = ValueType.BOOLEAN;
                qname = FunCall.LUX_SEARCH;
            }
            if (qname != null) {
                // We will insert a searching function. apply no restrictions to the enclosing scope:
                push (MATCH_ALL);
                if (searchArg != null) {
                    // create a searching function call using the argument to the enclosed lux:search call
                    return new FunCall (qname, returnType, searchArg, new LiteralExpression (XPathQuery.MINIMAL));
                }
                if (query.isImmutable()) {
                    // combine functionFacts with query
                    if (functionFacts != 0) {
                        query = XPathQuery.getQuery(query.getParseableQuery(), query.getFacts() | functionFacts, returnType, indexConfig, query.getSortFields());
                    }
                } else {
                    query.setType(returnType);
                    query.setFact(functionFacts, true);
                }
                return createSearchCall(qname, query);
            }
        }
        push (query);
        return funcall;
    }
    
    

    private XPathQuery combineQueries(XPathQuery rq, Occur occur, XPathQuery lq, ValueType resultType) {
        XPathQuery query;
        if (indexConfig.isOption(INDEX_PATHS)) {
            query = lq.combineSpanQueries(rq, occur, resultType, -1);
        } else {
            query = lq.combineBooleanQueries(occur, rq, occur, resultType, indexConfig);
        }
        return query;
    }
    
    private ParseableQuery nodeNameTermQuery(Axis axis, QName name) {
        String nodeName = name.getEncodedName();
        if (indexConfig.isOption (INDEX_PATHS)) {
            if (axis == Axis.Attribute) {
                nodeName = '@' + nodeName;
            }
            Term term = new Term (indexConfig.getFieldName(FieldName.PATH), nodeName);
            return new SpanTermPQuery (term);
        } else {
            String fieldName = (axis == Axis.Attribute) ? attrQNameField : elementQNameField;
            Term term = new Term (fieldName, nodeName);
            return new TermPQuery (term);
        }
    }
    
    @Override
    public AbstractExpression visit(Dot dot) {
        push(MATCH_ALL);
        return dot;
    }

    @Override
    public AbstractExpression visit(BinaryOperation op) {
        optimizeSubExpressions(op);
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
            type = ValueType.BOOLEAN;
            break;

        case ADD: case SUB: case DIV: case MUL: case IDIV: case MOD:
            type = ValueType.ATOMIC;
            // Casting an empty sequence to an operand of an operator expecting atomic values raises an error
            // and to be properly error-preserving we use SHOULD here
            // occur = Occur.MUST
            break;
    
        case AEQ: case EQUALS:
        case ANE: case ALT: case ALE: case AGT: case AGE:
        case NE: case LT: case GT: case LE: case GE:
            type = ValueType.BOOLEAN;
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

    private void optimizeComparison(Predicate predicate) {
        if (!indexConfig.isOption(INDEX_FULLTEXT)) {
            return;
        }
        LiteralExpression value = null;
        AbstractExpression path = null;
        AbstractExpression filter = predicate.getFilter();
        if (filter.getType() == Type.BINARY_OPERATION) {
            BinaryOperation op = (BinaryOperation) predicate.getFilter();
            if (!(op.getOperator() == Operator.EQUALS || op.getOperator() == Operator.AEQ)) {
                return;
            }
        } 
        else if (filter.getType() == Type.FUNCTION_CALL) {
            if (! ((FunCall)filter).getName().equals(FunCall.FN_CONTAINS)) {
                return;
            }
        }
        else {
            return;
        }
        if (filter.getSubs()[0].getType() == Type.LITERAL) {
            value = (LiteralExpression) filter.getSubs()[0];
            path = filter.getSubs()[1];
        }
        else if (filter.getSubs()[1].getType() == Type.LITERAL) {
            value = (LiteralExpression) filter.getSubs()[1];
            path = filter.getSubs()[0];
        }
        else {
            return;
        }
        AbstractExpression last = path.getLastContextStep();
        if (last.getType() != Type.PATH_STEP) {
            // get the context from the base of the predicate if the filter doesn't
            // contain any path steps
            last = predicate.getBase().getLastContextStep();
        }            
        if (last.getType() == Type.PATH_STEP) {
            String v = value.getValue().toString();
            if (filter.getType() == Type.FUNCTION_CALL) {
                if (v.matches("\\w+")) {
                    // when optimizing contains(), we have to do a wildcard query;
                    // we can only do this if the term contains only word characters
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
        }
        else if (nodeTest.getType() == ValueType.ELEMENT) {
            termQuery = makeElementValueQuery(nodeName, value, indexConfig);
        } 
        else if (nodeTest.getType() == ValueType.ATTRIBUTE) {
            termQuery = makeAttributeValueQuery(nodeName, value, indexConfig);
        }
        if (termQuery != null) {
            XPathQuery query = XPathQuery.getQuery(termQuery, XPathQuery.MINIMAL, nodeTest.getType(), indexConfig, null);
            XPathQuery baseQuery = pop();
            query = combineQueries (query, Occur.MUST, baseQuery, baseQuery.getResultType());
            push(query);
        }
    }

    public static NodeTextQuery makeElementValueQuery (QName qname, String value, IndexConfiguration config) {
        return new NodeTextQuery(new Term(config.getFieldName(IndexConfiguration.ELEMENT_TEXT), value), qname.getEncodedName());
    }

    public static ParseableQuery makeAttributeValueQuery (QName qname, String value, IndexConfiguration config) {
        return new NodeTextQuery(new Term(config.getFieldName(IndexConfiguration.ATTRIBUTE_TEXT), value), qname.getEncodedName());
    }

    public static ParseableQuery makeTextQuery (String value, IndexConfiguration config) {
        return new NodeTextQuery(new Term(config.getFieldName(IndexConfiguration.XML_TEXT), value));
    }

    @Override
    public AbstractExpression visit(LiteralExpression literal) {
        push (MATCH_ALL);
        return literal;
    }
    
    @Override
    public AbstractExpression visit(Variable variable) {
        push (MATCH_ALL);
        return variable;
    }

    @Override
    public AbstractExpression visit(Subsequence subsequence) {
        optimizeSubExpressions (subsequence);
        AbstractExpression start = subsequence.getStartExpr();
        AbstractExpression length = subsequence.getLengthExpr();
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

    // merge any sorting criteria from the query at position i on the stack
    // with the search call.  TODO: also merge any query with the existing
    // query
    private SearchCall mergeSearchCall (SearchCall search, int i) {
        int j = queryStack.size() - i - 1;
        XPathQuery query = queryStack.get(j);
        search.combineQuery(query, indexConfig);
        return search;
    }
    
    private FunCall getSearchCall (int i) {
        int j = queryStack.size() - i - 1;
        XPathQuery query = queryStack.get(j);
        queryStack.set (j, MATCH_ALL);
        return createSearchCall(FunCall.LUX_SEARCH, query);
    }
    
    private FunCall createSearchCall (QName functionName, XPathQuery query) {
        if (functionName.equals(FunCall.LUX_SEARCH)) {
            // searchCall.setFnCollection (!optimizeForOrderedResults);
            return new SearchCall(query, indexConfig);
        }
        return new FunCall (functionName, query.getResultType(),
                query.toXmlNode(indexConfig.getDefaultFieldName()),
                new LiteralExpression (query.getFacts()));
    }
    
    @Override
    public AbstractExpression visit(Sequence sequence) {
        optimizeSubExpressions(sequence);
        combineTopQueries (sequence.getSubs().length, Occur.SHOULD);
        return sequence;
    }
    
    /*
     * Unoptimized expressions should call this to ensure the proper state of the query stack
     */
    private void popChildQueries (AbstractExpression expr) {
        for (int i = 0; i < expr.getSubs().length; i++) {
            pop();
        }
        push (MATCH_ALL);
    }
    
    @Override
    public AbstractExpression visitDefault (AbstractExpression expr) {
        popChildQueries (expr);
        return expr;
    }
    
    @Override
    public AbstractExpression visit(FLWOR flwor) {
        // combine any constraint from the return clause with the constraint from the clauses
        flwor.getSubs()[0] = optimizeExpression (flwor.getReturnExpression(), 0);
        getQuery().setSortFields(null); // ignore any ordering expressions found in the return clause
        int length = flwor.getClauses().length;
        // iterate in reverse order so we can unwind the query stack from its "top"
        // which corresponds to the "bottom" of the expression tree.
        for (int i = length - 1; i >= 0; i--) {
            // TODO: is MUST correct here??? What if we have an irrelevant let clause?
            // for $foo in /foo let $bar := /bar return $foo??
            combineTopQueries(2, Occur.MUST);
            FLWORClause clause = flwor.getClauses()[i];
            AbstractExpression seq = clause.getSequence();
            clause.setSequence(optimizeExpression(seq, 0));
        }
        return flwor;
    }

    @Override
    public OrderByClause visit (OrderByClause orderByClause) {
        LinkedList<SortField> sortFields  = new LinkedList<SortField>();
        ArrayList<SortKey> sortKeys = orderByClause.getSortKeys();
        boolean foundUnindexedSort = false;
        int stackOffset = queryStack.size() - sortKeys.size();
        for (int i = 0; i < sortKeys.size(); i++) {
            // pop the queries off the stack that correspond to each sort key in this order by clause.
            // Accumulate a list of contiguous indexed order keys.  Merge them with the
            // query on the stack as an ordering criterion (not as a filter)
            
            // Pull queries from middle of stack since they get pushed in reverse order
            XPathQuery q = queryStack.remove(stackOffset);
            if (q.getSortFields() == null) {
                // once we find an unindexed sort field, stop adding sort indexes to the query
                foundUnindexedSort = true;
            } else if (! foundUnindexedSort) {
                SortKey sortKey = sortKeys.get(i);
                AbstractExpression key = sortKey.getKey();
                if (key instanceof FunCall) {
                    // special case - it would be nice if Saxon figured this out when compiling
                    FunCall keyFun = (FunCall)key;
                    if (keyFun.getName().equals(FunCall.LUX_FIELD_VALUES) &&
                        keyFun.getSubs().length < 2) {
                        throw new LuxException("lux:field-values($key) depends on the context where there is no context defined");
                    }
                }
                String order = ((LiteralExpression) sortKey.getOrder()).getValue().toString();
                SortField sortField = q.getSortFields()[0];
                if (! sortKey.isEmptyLeast()) {
                    // empty greatest
                    sortField = new SortField (sortField.getField(), SearchResultIterator.MISSING_LAST, order.toString().equals("descending"));
                }
                else if (order.toString().equals("descending")) {
                    // reverse sort order
                    sortField = new SortField (sortField.getField(), sortField.getType(), true);
                }
                // add at the beginning: fields pop off the stack in reverse order
                sortFields.add(sortField);
                sortKeys.remove(i);
                -- i; // don't advance: we shrank the list
            }
        }
        if (sortFields.isEmpty()) {
            push (MATCH_ALL);
        } else {
            XPathQuery query = XPathQuery.getQuery(MATCH_ALL.getParseableQuery(), MATCH_ALL.getFacts(), MATCH_ALL.getResultType(), indexConfig, sortFields.toArray(new SortField[sortFields.size()]));
            push (query);
        }
        return orderByClause;
    }
    
    @Override
    public ForClause visit (ForClause forClause) {
        getQuery().setSortFields(null);
        return forClause;
    }

    @Override
    public WhereClause visit (WhereClause whereClause) {
        getQuery().setSortFields(null);
        return whereClause;
    }
    
    @Override
    public LetClause visit (LetClause letClause) {
        getQuery().setSortFields(null);
        return letClause;
    }

    /**
     * @return whether to generate code that relies on our ability to assert that lux:search() returns
     * results in "document order" - ie ordered by document ID.
     */
    public boolean isOptimizedForOrderedResults() {
        return optimizeForOrderedResults;
    }

    public void setSearchStrategy (SearchStrategy searchStrategy ) {
        this.optimizeForOrderedResults = (searchStrategy == SearchStrategy.LUX_SEARCH);
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
