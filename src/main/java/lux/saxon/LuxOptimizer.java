package lux.saxon;

/**
 Optimizer that rewrites some path expressions as lux:search() calls.
 */

import java.util.HashMap;

import lux.XPathQuery;
import lux.api.ValueType;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.parser.Optimizer;
import net.sf.saxon.expr.parser.Token;
import net.sf.saxon.expr.sort.IntComplementSet;
import net.sf.saxon.expr.sort.IntIterator;
import net.sf.saxon.expr.sort.IntSet;
import net.sf.saxon.expr.sort.IntUniversalSet;
import net.sf.saxon.functions.*;
import net.sf.saxon.om.Axis;
import net.sf.saxon.pattern.NodeTest;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.Type;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.TermQuery;

/**
 *
 */
public class LuxOptimizer extends Optimizer {

    private String attrQNameField = "lux_att_name_ms";
    private String elementQNameField = "lux_elt_name_ms";
    
    private static final XPathQuery MATCH_ALL_QUERY = XPathQuery.MATCH_ALL;
    
    public LuxOptimizer (Config config) {
        super(config);
    }
    
    public XPathQuery queryFor (SaxonExpr expr) {
        XPathQuery query = queryFor (expr.getXPathExecutable().getUnderlyingExpression().getInternalExpression());
        if (query.isImmutable()) {
            query = new XPathQuery (expr, query.getQuery(), query.getFacts(), query.getResultType());
        } else {
            query.setExpression(expr);
        }
        return query;
    }
    
    /**
     * @param expr an XPath 2.0 expression
     * @return a wrapper around a Lucene Query that excludes documents that can't possibly satisfy the expression, or null
     * if the expression is an expression that doesn't depend on any documents, like a literal.
     *
     * TODO: refactor the control mechanism and implement a brigade of optimizers, each with knowledge of some index
     * they can rewrite the expression tree, modify the lucene query, and set facts.
     * 
     * Note this method will only be called with one of the known expression types when that expression
     * is the outermost expression, ie at the top of the syntax tree.
     */
    public XPathQuery queryFor (Expression expr) {
        if (expr instanceof AxisExpression) {
            return queryFor ((AxisExpression) expr);
        }
        if (expr instanceof SlashExpression) {
            return queryFor ((SlashExpression) expr);
        }
        if (expr instanceof FilterExpression) {
            return queryFor ((FilterExpression) expr);
        }
        if (expr instanceof FunctionCall) {
            // If this is a not() expression, change the "sign" of the Occur in the query - we only want to retrieve
            // documents that contradict the not expression
            XPathQuery q = queryFor ((FunctionCall) expr);
            if (expr instanceof NotFn) {
                ((BooleanQuery)q.getQuery()).getClauses()[0].setOccur(Occur.MUST);
            }
            return q;
        }
        if (expr instanceof UnaryExpression) {
            return queryFor ((UnaryExpression) expr);
        }
        if (expr instanceof VennExpression) {
            return queryFor ((VennExpression) expr);
        }
        if (expr instanceof GeneralComparison) {
            return queryFor ((GeneralComparison) expr);
        }
        if (expr instanceof ArithmeticExpression) {
            return queryFor ((ArithmeticExpression) expr);
        }
        if (expr instanceof ValueComparison) {
            return queryFor ((ValueComparison) expr);
        }
        if (expr instanceof BinaryExpression) {
            return queryFor ((BinaryExpression) expr);
        }
        if (expr instanceof Literal) {
            return XPathQuery.MATCH_NONE;
        }
        if (expr instanceof RootExpression) {
            return XPathQuery.MATCH_ALL; // new XPathQuery (null, new MatchAllDocsQuery(), XPathQuery.MINIMAL, ValueType.DOCUMENT);
        }
        if (expr instanceof ContextItemExpression) {
            return XPathQuery.MATCH_ALL;
        }
        // other BinaryExpressions ?
        // ParentNodeExpression
        // RootExpression
        // NegateExpression!
        // ContextItemExpression
        // TODO Auto-generated method stub
        return XPathQuery.UNINDEXED;
    }
    
    // same code as queryFor(SlashExpression) - should we merge?
    private XPathQuery queryFor (FilterExpression expr) {
        XPathQuery filtered = queryFor (expr.getControllingExpression());
        XPathQuery filter = queryFor (expr.getFilter());
        XPathQuery combined = combineQueries (filtered, Occur.MUST, filter, Occur.MUST, filtered.getResultType());
        // This is a counting query if its filtered expr is
        combined.setFact(XPathQuery.COUNTING, filtered.isFact(XPathQuery.COUNTING));
        return combined;
    }
    
    private XPathQuery queryFor (SlashExpression expr) {
        XPathQuery lq = queryFor (expr.getControllingExpression());
        XPathQuery rq = queryFor (expr.getControlledExpression());
        return combineQueries (lq,  Occur.MUST, rq, Occur.MUST, rq.getResultType());
    }
    
    /**
     * If a function always returns empty (or true?) with an empty arglist and non-empty (or false?) with a non-empty arglist,
     * then its argument's queries are combined with Occur.MUST.  This is the default case, and such functions are not mapped
     * explicitly in fnArgParity. If the inverse is true, ie the function returns empty when its args are non-empty and vice-versa, we map 
     * it as Occur.MUST_NOT.  In other cases, like count(), which always returns something whether its arguments exist or not, 
     * the situation is more complicated, we note that as null in this map, and no query is generated for such functions.
     */
    private XPathQuery queryFor (FunctionCall funcall) {
        Occur occur;
        if (funcall instanceof SystemFunction) {
            if (fnArgParity.containsKey(funcall.getClass())) {
                occur = fnArgParity.get(funcall.getClass());
            } else {
                occur = Occur.MUST;
            }
        } else {
            // we know nothing about this function
            return XPathQuery.UNINDEXED;
        }
        XPathQuery query = queryFor (funcall.getArguments(), occur);
        if (funcall instanceof Root) {
            // Saxon identifies this as returning node() since the context node might not be a document,
            // but we assume the context node will always be a document
            query.restrictType(ValueType.DOCUMENT);
        }
        else if (funcall instanceof Count) {
            query.setFact(XPathQuery.COUNTING, true);
            query.setType(ValueType.ATOMIC);
        }
        else if (funcall instanceof NotFn || funcall instanceof Exists) {
            query.setType(ValueType.BOOLEAN);
        }
        else {
            ValueType returnType = SaxonExpr.getValueType(funcall, config);
            if (! (query.getResultType() == ValueType.DOCUMENT && query.getResultType().is(returnType))) {
                query.setType(returnType);
            }
            // else - we computed that the args return DOCUMENT, and the function is declared as returning NODE or VALUE
            // and we make the assumption that the function is returning some subset of the DOCUMENTS?  TODO: check
            // all the XPath 2.0 system functions to see if this is true.
        }
        return query;
    }

    private static HashMap<Class<? extends FunctionCall>, Occur> fnArgParity = new HashMap<Class<? extends FunctionCall>, Occur>();
    static {
        fnArgParity.put(Collection.class, null);
        fnArgParity.put(Doc.class, null);
        fnArgParity.put(Empty.class, Occur.MUST_NOT);
        fnArgParity.put(GenerateId.class, null);
        fnArgParity.put(net.sf.saxon.functions.Error.class, null);
        fnArgParity.put(NotFn.class, Occur.MUST_NOT);
        fnArgParity.put(UriCollection.class, null);
        fnArgParity.put(UnparsedText.class, null);
    };
    
    private XPathQuery queryFor (AxisExpression expr) {
        byte axis = expr.getAxis();
        if (axis == Axis.NAMESPACE) {
            return MATCH_ALL_QUERY;
        }
        NodeTest nodeTest = expr.getNodeTest();
        if (nodeTest == null) {
            return MATCH_ALL_QUERY;
        }
        int nameCode = nodeTest.getFingerprint();
        boolean isMinimal = (axis == Axis.DESCENDANT || axis == Axis.DESCENDANT_OR_SELF || axis == Axis.ATTRIBUTE);
        
        if (nameCode >= 0) { // matches a single node name 
            TermQuery termQuery = nodeNameTermQuery(axis, nameCode);
            return new XPathQuery (null, termQuery, isMinimal ? XPathQuery.MINIMAL : 0, ValueType.NODE);
        } else { // matches multiple node names
            IntSet nameCodes = nodeTest.getRequiredNodeNames();
            if (nameCodes == IntUniversalSet.getInstance()) {
                // if the context item type is unknown, assume that indicates this is being evaluated in the
                // external context, which we know will always be document
                ItemType contextType = expr.getContextItemType();
                ValueType type;
                if (nodeTest.getNodeKindMask() ==  1<<Type.DOCUMENT) {
                    type = ValueType.DOCUMENT;
                } else {
                    boolean documentContext = (contextType == null || contextType.getPrimitiveType() == Type.ITEM || contextType.getPrimitiveType() == Type.DOCUMENT);
                    type = (documentContext && (axis == Axis.ANCESTOR_OR_SELF || axis == Axis.DESCENDANT_OR_SELF || axis == Axis.SELF))
                            ? ValueType.DOCUMENT : ValueType.NODE;
                }
                return new XPathQuery (null, new MatchAllDocsQuery(), XPathQuery.MINIMAL, type);
            } else if (nameCodes instanceof IntComplementSet) {
                // match all names *except* some set
                return XPathQuery.UNINDEXED;
            }
            BooleanQuery bq = new BooleanQuery ();
            IntIterator nameCodesIter = nameCodes.iterator();
            while (nameCodesIter.hasNext()) {
                bq.add(nodeNameTermQuery(axis, nameCodesIter.next()), Occur.SHOULD);
            }
            return new XPathQuery (null, bq, isMinimal ? XPathQuery.MINIMAL : 0, ValueType.NODE);
        }        
    }
    
    private XPathQuery queryFor (VennExpression expr) {
        int op = expr.getOperator() ;
        Expression [] operands = expr.getOperands();
        if (op == Token.EXCEPT) {
            return combineQueries (queryFor (operands[0]), Occur.MUST, queryFor (operands[1]), Occur.MUST_NOT); 
        }
        Occur occur = op == Token.UNION ? Occur.SHOULD : Occur.MUST;               
        return queryFor (operands, occur);
    }
    
    private XPathQuery queryFor (ArithmeticExpression expr) {
        // TODO: indexing for value comparisons
        XPathQuery query = queryFor (expr.getArguments(), Occur.SHOULD);
        return query;
    }
    
    // covers ArithmeticExpression, BooleanExpression, GeneralComparison, ValueComparison,
    // IdentityComparison, RangeExpression
    // TODO: specialize
    private XPathQuery queryFor (BinaryExpression expr) {
        // TODO: indexing for value comparisons
        XPathQuery query = queryFor (expr.getArguments(), Occur.SHOULD);
        query.setFact(XPathQuery.MINIMAL, false);
        return query;
    }

    private TermQuery nodeNameTermQuery(byte axis, int nameCode) {
        String nodeName = getConfiguration().getNamePool().getClarkName(nameCode);
        String fieldName = (axis == Axis.ATTRIBUTE) ? attrQNameField : elementQNameField;
        TermQuery termQuery = new TermQuery (new Term (fieldName, nodeName));
        return termQuery;
    }
    
    // UnaryExpression includes First/LastItemFilters 
    private XPathQuery queryFor (UnaryExpression expr) {
        if (expr instanceof NegateExpression) {
            return queryFor (new Expression[] {expr.getBaseExpression()}, Occur.MUST_NOT);
        }
        return queryFor (expr.getBaseExpression());
    }
    
    private XPathQuery queryFor(Expression[] exprs, Occur occur) {
        XPathQuery query = MATCH_ALL_QUERY;
        for (Expression arg : exprs) {
            query = combineQueries(query, occur, queryFor (arg), occur);
        }
        return query;
    }
    
    private static final XPathQuery combineQueries(XPathQuery lq, Occur loccur, XPathQuery rq, Occur roccur) {
        return combineQueries(lq, loccur, rq, roccur, rq.getResultType());
    }
        
    private static final XPathQuery combineQueries(XPathQuery lq, Occur loccur, XPathQuery rq, Occur roccur, ValueType valueType) {
        return lq.combine(loccur, rq, roccur, valueType);
    }

}