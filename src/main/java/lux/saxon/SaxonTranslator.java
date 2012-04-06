package lux.saxon;

/**
 Optimizer that rewrites some path expressions as lux:search() calls.
 */

import lux.api.ValueType;
import lux.xpath.AbstractExpression;
import lux.xpath.BinaryOperation;
import lux.xpath.Dot;
import lux.xpath.FunCall;
import lux.xpath.PathExpression;
import lux.xpath.PathStep;
import lux.xpath.Predicate;
import lux.xpath.QName;
import lux.xpath.Root;
import lux.xpath.Sequence;
import net.sf.saxon.expr.AxisExpression;
import net.sf.saxon.expr.BinaryExpression;
import net.sf.saxon.expr.ContextItemExpression;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.FilterExpression;
import net.sf.saxon.expr.FunctionCall;
import net.sf.saxon.expr.Literal;
import net.sf.saxon.expr.RootExpression;
import net.sf.saxon.expr.SlashExpression;
import net.sf.saxon.expr.UnaryExpression;
import net.sf.saxon.expr.instruct.Block;
import net.sf.saxon.expr.parser.Token;
import net.sf.saxon.expr.sort.IntComplementSet;
import net.sf.saxon.expr.sort.IntIterator;
import net.sf.saxon.expr.sort.IntSet;
import net.sf.saxon.expr.sort.IntUniversalSet;
import net.sf.saxon.om.Axis;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.pattern.NodeTest;
import net.sf.saxon.type.Type;

/**
 *
 */
public class SaxonTranslator {
    
    private Config config;
    
    public SaxonTranslator (Config config) {
        this.config = config;
    }
    
    /** Converts from a Saxon to a lux abstract expression tree.
     * @param expr a Saxon representation of an XPath 2.0 expression
     * @return a lux representation of an equivalent XPath 2.0 expression
     */
    public AbstractExpression exprFor (Expression expr) {
        if (expr instanceof AxisExpression) {
            return exprFor ((AxisExpression) expr);
        }
        if (expr instanceof SlashExpression) {
            return exprFor ((SlashExpression) expr);
        }
        if (expr instanceof FilterExpression) {
            return exprFor ((FilterExpression) expr);
        }
        if (expr instanceof FunctionCall) {
            return exprFor ((FunctionCall) expr);
        }
        if (expr instanceof UnaryExpression) {
            return exprFor ((UnaryExpression) expr);
        }
        if (expr instanceof BinaryExpression) {
            return exprFor ((BinaryExpression) expr);
        }
        if (expr instanceof Literal) {
            return exprFor ((Literal) expr);
        }
        if (expr instanceof RootExpression) {
            return new Root();
        }
        if (expr instanceof ContextItemExpression) {
            return new Dot();
        }
        if (expr instanceof Block) {
            return exprFor ((Block) expr);
        }
        throw new IllegalArgumentException("unhandled expression type: " + expr.toString());
    }
    
    private Predicate exprFor (FilterExpression expr) {
        AbstractExpression filtered = exprFor (expr.getControllingExpression());
        AbstractExpression filter = exprFor (expr.getFilter());
        return new Predicate(filtered, filter);
    }
    
    private AbstractExpression exprFor (SlashExpression expr) {
        AbstractExpression lq = exprFor (expr.getControllingExpression());
        AbstractExpression rq = exprFor (expr.getControlledExpression());
        return new PathExpression(lq, rq);
    }
    
    private FunCall exprFor (FunctionCall funcall) {
        QName qname = qnameFor (funcall.getFunctionName());
        return new FunCall (qname, exprFor (funcall.getArguments()));
    }
    
    private QName qnameFor(StructuredQName name) {
        return new QName (name.getNamespaceBinding().getURI(), name.getLocalPart(), name.getNamespaceBinding().getPrefix());
    }

    private Sequence exprFor (Expression[] exprs) {
        AbstractExpression[] aex = new AbstractExpression [exprs.length];
        for (int i = 0 ; i < exprs.length; i++) {
            aex[i] = exprFor (exprs[i]);
        }
        return new Sequence (aex);
    }
        
    private AbstractExpression exprFor (AxisExpression expr) {
        PathStep.Axis axis;
        switch (expr.getAxis()) {
        case Axis.ANCESTOR: axis = PathStep.Axis.Ancestor; break;
        case Axis.PARENT: axis = PathStep.Axis.Parent; break;
        case Axis.DESCENDANT: axis = PathStep.Axis.Descendant; break;
        case Axis.PRECEDING: axis = PathStep.Axis.Preceding; break;
        case Axis.FOLLOWING: axis = PathStep.Axis.Following; break;
        case Axis.SELF: axis = PathStep.Axis.Self; break;
        case Axis.PRECEDING_SIBLING: axis = PathStep.Axis.PrecedingSibling; break;
        case Axis.FOLLOWING_SIBLING: axis = PathStep.Axis.FollowingSibling; break;
        case Axis.ANCESTOR_OR_SELF: axis = PathStep.Axis.AncestorSelf; break;
        case Axis.DESCENDANT_OR_SELF: axis = PathStep.Axis.DescendantSelf; break;
        case Axis.ATTRIBUTE: axis = PathStep.Axis.Attribute; break;
        case Axis.CHILD: axis = PathStep.Axis.Child; break;
        default: throw new IllegalArgumentException("Unsupported axis in expression: " + expr.toString());
        }
        NodeTest nodeTest = expr.getNodeTest();
        if (nodeTest == null) {
            return new PathStep (axis, new lux.xpath.NodeTest(ValueType.NODE));
        }
        int nameCode = nodeTest.getFingerprint();
        ValueType nodeType;
        switch (nodeTest.getPrimitiveType()) {
        case Type.NODE: nodeType = ValueType.NODE; break;
        case Type.ELEMENT: nodeType = ValueType.ELEMENT; break;
        case Type.TEXT: nodeType = ValueType.TEXT; break;
        case Type.ATTRIBUTE: nodeType = ValueType.ATTRIBUTE; break;
        case Type.DOCUMENT: nodeType = ValueType.DOCUMENT; break;
        case Type.PROCESSING_INSTRUCTION: nodeType = ValueType.PROCESSING_INSTRUCTION; break;
        case Type.COMMENT: nodeType = ValueType.COMMENT; break;
        default: throw new IllegalArgumentException("Unsupported node type in expression: " + expr.toString());
        }
        if (nameCode >= 0) { // matches a single node name 
            StructuredQName sqname = config.getNamePool().getStructuredQName(nameCode);
            QName name = new QName (sqname.getURI(), sqname.getLocalPart(), sqname.getPrefix());
            return new PathStep (axis, new lux.xpath.NodeTest (nodeType, name));
        } else { // matches multiple node names
            IntSet nameCodes = nodeTest.getRequiredNodeNames();
            if (nameCodes == IntUniversalSet.getInstance()) {
                return new PathStep (axis, new lux.xpath.NodeTest (nodeType));                
            } else if (nameCodes instanceof IntComplementSet) {
                // FIXME: implement as a set operation
                throw new IllegalArgumentException("Unsupported expression: " + expr.toString());
            }
            IntIterator nameCodesIter = nameCodes.iterator();
            while (nameCodesIter.hasNext()) {
                // TODO: implement how exactly?? is this something like node()[self::x or self::y]?
                // StructuredQName sqname = config.getNamePool().getStructuredQName(nameCodesIter.next());
            }
            throw new IllegalArgumentException("Unsupported expression: " + expr.toString());
        }        
    }
    
    /*
    private AbstractExpression exprFor (VennExpression expr) {
        int op = expr.getOperator() ;
        Expression [] operands = expr.getOperands();
    }
    
    private AbstractExpression exprFor (ArithmeticExpression expr) {
        // TODO: indexing for value comparisons
        AbstractExpression query = exprFor (expr.getArguments(), Occur.SHOULD);
        return query;
    }
    */
    
    // covers ArithmeticExpression, BooleanExpression, GeneralComparison, ValueComparison,
    // IdentityComparison, RangeExpression
    // TODO: specialize
    private AbstractExpression exprFor (BinaryExpression expr) {
        Expression [] operands = expr.getOperands();
        BinaryOperation.Operator op;
        switch (expr.getOperator()) {
        case Token.AND: op = BinaryOperation.Operator.AND; break;
        case Token.OR: op = BinaryOperation.Operator.OR; break;
        case Token.INTERSECT: op = BinaryOperation.Operator.INTERSECT; break;
        case Token.EXCEPT: op = BinaryOperation.Operator.EXCEPT; break;
        case Token.UNION: op = BinaryOperation.Operator.UNION; break;
        case Token.PLUS: op = BinaryOperation.Operator.ADD; break;
        case Token.MINUS: op = BinaryOperation.Operator.SUB; break;
        case Token.MULT: op = BinaryOperation.Operator.MUL; break;
        case Token.DIV: op = BinaryOperation.Operator.DIV; break;
        case Token.IDIV: op = BinaryOperation.Operator.IDIV; break;
        case Token.MOD: op = BinaryOperation.Operator.MOD; break;
        case Token.EQUALS: op = BinaryOperation.Operator.EQ; break;
        case Token.NE: op = BinaryOperation.Operator.NE; break;
        case Token.LT: op = BinaryOperation.Operator.LT; break;
        case Token.GT: op = BinaryOperation.Operator.GT; break;
        case Token.LE: op = BinaryOperation.Operator.LE; break;
        case Token.GE: op = BinaryOperation.Operator.GE; break;
        case Token.FEQ: op = BinaryOperation.Operator.AEQ; break;
        case Token.FNE: op = BinaryOperation.Operator.ANE; break;
        case Token.FLT: op = BinaryOperation.Operator.ALT; break;
        case Token.FLE: op = BinaryOperation.Operator.ALE; break;
        case Token.FGT: op = BinaryOperation.Operator.AGT; break;
        case Token.FGE: op = BinaryOperation.Operator.AGE; break;
        case Token.IS: op = BinaryOperation.Operator.IS; break;
        case Token.PRECEDES: op = BinaryOperation.Operator.BEFORE; break;
        case Token.FOLLOWS: op = BinaryOperation.Operator.AFTER; break;
        default: throw new IllegalArgumentException("Unsupported operator in expression: " + expr.toString());
        }
        return new BinaryOperation (exprFor(operands[0]), op, exprFor(operands[1]));
    }
    
    // UnaryExpression includes First/LastItemFilters 
    // also unary -/+ ?
    private AbstractExpression exprFor (UnaryExpression expr) {
        throw new IllegalArgumentException("Unsupported operator in expression: " + expr.toString());
        // return exprFor (expr.getBaseExpression());
    }

}