/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */

package lux.saxon;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import lux.api.LuxException;
import lux.api.ValueType;
import lux.xpath.AbstractExpression;
import lux.xpath.BinaryOperation;
import lux.xpath.BinaryOperation.Operator;
import lux.xpath.Dot;
import lux.xpath.FunCall;
import lux.xpath.LiteralExpression;
import lux.xpath.Namespace;
import lux.xpath.PathExpression;
import lux.xpath.PathStep;
import lux.xpath.Predicate;
import lux.xpath.QName;
import lux.xpath.Root;
import lux.xpath.Sequence;
import lux.xpath.Subsequence;
import lux.xpath.UnaryMinus;
import lux.xquery.AttributeConstructor;
import lux.xquery.Conditional;
import lux.xquery.ElementConstructor;
import lux.xquery.FLWOR;
import lux.xquery.FLWORClause;
import lux.xquery.ForClause;
import lux.xquery.FunctionDefinition;
import lux.xquery.LetClause;
import lux.xquery.OrderByClause;
import lux.xquery.SortKey;
import lux.xquery.TextConstructor;
import lux.xquery.Variable;
import lux.xquery.WhereClause;
import lux.xquery.XQuery;
import net.sf.saxon.expr.Atomizer;
import net.sf.saxon.expr.AxisExpression;
import net.sf.saxon.expr.BinaryExpression;
import net.sf.saxon.expr.CastExpression;
import net.sf.saxon.expr.CompareToIntegerConstant;
import net.sf.saxon.expr.ContextItemExpression;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.FilterExpression;
import net.sf.saxon.expr.FirstItemExpression;
import net.sf.saxon.expr.ForExpression;
import net.sf.saxon.expr.FunctionCall;
import net.sf.saxon.expr.LastItemExpression;
import net.sf.saxon.expr.LetExpression;
import net.sf.saxon.expr.Literal;
import net.sf.saxon.expr.LocalVariableReference;
import net.sf.saxon.expr.NegateExpression;
import net.sf.saxon.expr.ParentNodeExpression;
import net.sf.saxon.expr.RootExpression;
import net.sf.saxon.expr.SlashExpression;
import net.sf.saxon.expr.StaticProperty;
import net.sf.saxon.expr.TailExpression;
import net.sf.saxon.expr.UnaryExpression;
import net.sf.saxon.expr.flwor.Clause;
import net.sf.saxon.expr.flwor.FLWORExpression;
import net.sf.saxon.expr.flwor.LocalVariableBinding;
import net.sf.saxon.expr.instruct.Block;
import net.sf.saxon.expr.instruct.Choose;
import net.sf.saxon.expr.instruct.FixedAttribute;
import net.sf.saxon.expr.instruct.FixedElement;
import net.sf.saxon.expr.instruct.UserFunctionParameter;
import net.sf.saxon.expr.instruct.ValueOf;
import net.sf.saxon.expr.parser.Token;
import net.sf.saxon.expr.sort.IntSet;
import net.sf.saxon.expr.sort.IntUniversalSet;
import net.sf.saxon.expr.sort.SortKeyDefinition;
import net.sf.saxon.functions.StandardFunction;
import net.sf.saxon.functions.StandardFunction.Entry;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.Axis;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.NamespaceBinding;
import net.sf.saxon.om.NodeName;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.pattern.CombinedNodeTest;
import net.sf.saxon.pattern.DocumentNodeTest;
import net.sf.saxon.pattern.NodeTest;
import net.sf.saxon.query.QueryModule;
import net.sf.saxon.query.XQueryExpression;
import net.sf.saxon.query.XQueryFunction;
import net.sf.saxon.s9api.XQueryExecutable;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.AtomicType;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.Cardinality;
import net.sf.saxon.value.Value;

/**
 * Translates Saxon XPath 2.0 Expressions into Lux AbstractExpressions.
 */
public class SaxonTranslator {
    
    private Config config;
    private HashMap<String,String> namespaceDeclarations;
    
    public SaxonTranslator (Config config) {
        this.config = config;
        namespaceDeclarations = new HashMap<String, String>();
    }
    
    /** Converts from a Saxon to a lux xquery representation.
     * @param expr a Saxon representation of an XQuery module
     * @return a lux representation of an equivalent XQuery module
     */

    public XQuery queryFor(XQueryExecutable xquery) {
        XQueryExpression saxonQuery = xquery.getUnderlyingCompiledQuery();
        QueryModule queryModule = saxonQuery.getStaticContext();
        //StructuredQName[] extVars = saxonQuery.getExternalVariableNames();
        // Namespace declarations are accumulated while walking the expression trees:
        namespaceDeclarations.clear();
        FunctionDefinition[] defs = getFunctionDefinitions(queryModule);
        //Namespace[] namespaceDeclarations = getNamespaceDeclarations (queryModule);
        AbstractExpression body = exprFor (saxonQuery.getExpression());
        return new XQuery(getNamespaceDeclarations(queryModule), defs, body);
    }
    
    public XQuery queryFor(AbstractExpression ex) {
        return new XQuery(getNamespaceDeclarations(null), null, ex);
    }

    private Namespace[] getNamespaceDeclarations(QueryModule queryModule) {
        // String defElementNS = queryModule.getDefaultElementNamespace();
        // String defFunctionNS = queryModule.getDefaultFunctionNamespace();
        // We'd like to get our hands on queryModule.explicitPrologNamespaces :(
        // I think possibly we will have to resort to walking the module in search of 
        // all namespaces??
        Namespace[] decls = new Namespace[namespaceDeclarations.size()];
        int i = 0;
        for (Map.Entry<String, String> entry : namespaceDeclarations.entrySet()) {
            decls[i++] = new Namespace (entry.getKey(), entry.getValue());
        }
        return decls;
    }
    
    private void addNamespaceDeclaration (QName qname) {
        String prefix = qname.getPrefix();
        String namespaceURI = qname.getNamespaceURI();
        namespaceDeclarations.put(prefix, namespaceURI);
    }

    private FunctionDefinition[] getFunctionDefinitions(QueryModule queryModule) {
        ArrayList<FunctionDefinition> functionDefinitions = new ArrayList<FunctionDefinition>();
        Iterator<XQueryFunction> functions = queryModule.getLocalFunctionLibrary().getFunctionDefinitions();
        while (functions.hasNext()) {
            XQueryFunction function = functions.next();
            UserFunctionParameter[] params = function.getParameterDefinitions();
            Variable[] args = new Variable[params.length];
            for (int i = 0; i < params.length; i++) {
                QName argname = qnameFor (params[i].getVariableQName());
                addNamespaceDeclaration(argname);
                args[i] = new Variable (argname);
            }
            QName fname = qnameFor(function.getFunctionName());
            addNamespaceDeclaration(fname);
            FunctionDefinition fdef = new FunctionDefinition(fname, 
                    valueTypeForItemType(function.getResultType().getPrimaryType()), 
                    args, exprFor (function.getBody()));  
            functionDefinitions.add (fdef);
        }
        FunctionDefinition[] defs = functionDefinitions.toArray(new FunctionDefinition[0]);
        return defs;
    }
    
    /** Converts from a Saxon to a lux abstract expression tree.
     * @param expr a Saxon representation of an XQuery expression
     * @return a lux representation of an equivalent XQuery expression
     */
    public AbstractExpression exprFor (Expression expr) {
        if (expr == null) {
            return null;
        }
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
        if (expr instanceof FirstItemExpression) {
            return exprFor ((FirstItemExpression) expr);
        }
        if (expr instanceof LastItemExpression) {
            return exprFor ((LastItemExpression) expr);
        }
        if (expr instanceof CastExpression) {
            return exprFor ((CastExpression) expr);
        }
        if (expr instanceof Atomizer) {
            return exprFor ((Atomizer) expr);
        }
        if (expr instanceof NegateExpression) {
            return exprFor ((NegateExpression) expr);
        }
        if (expr instanceof BinaryExpression) {
            return exprFor ((BinaryExpression) expr);
        }
        if (expr instanceof TailExpression) {
            return exprFor ((TailExpression) expr);
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
        if (expr instanceof ParentNodeExpression) {
            return new PathStep(PathStep.Axis.Parent, new lux.xpath.NodeTest(ValueType.NODE));
        }
        if (expr instanceof Block) {
            return exprFor (((Block) expr).getChildren());
        }
        if (expr instanceof CompareToIntegerConstant) {
            return exprFor ((CompareToIntegerConstant) expr);
        }
        if (expr instanceof LetExpression) {
            return exprFor ((LetExpression) expr);
        }
        if (expr instanceof ForExpression) {
            return exprFor ((ForExpression) expr);
        }
        if (expr instanceof LocalVariableReference) {
            return exprFor ((LocalVariableReference) expr);
        }
        if (expr instanceof FixedElement) {
            return exprFor ((FixedElement) expr);
        }
        if (expr instanceof FixedAttribute) {
            return exprFor ((FixedAttribute) expr);
        }
        if (expr instanceof ValueOf) {
            return exprFor ((ValueOf) expr);
        }
        if (expr instanceof FLWORExpression) {
            return exprFor ((FLWORExpression) expr);
        }
        if (expr instanceof Choose) {
            return exprFor ((Choose) expr);
        }
        if (expr instanceof UnaryExpression) {
            return exprFor ((UnaryExpression) expr);
        }
        throw new IllegalArgumentException("unhandled expression type: " + expr.getClass().getSimpleName() + " in " + expr.toString());
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
    
    private AbstractExpression exprFor (TailExpression expr) {
        return new Subsequence (exprFor (expr.getBaseExpression()), new LiteralExpression(expr.getStart()));
    }
    
    private AbstractExpression exprFor (LetExpression let) {
        // TODO: get rid of Let and use a FLWOR
        StructuredQName var = let.getVariableQName();
        Expression seq = let.getSequence();
        Expression returns = let.getAction();
        return new FLWOR(exprFor(returns), new LetClause (new Variable(qnameFor(var)), exprFor(seq)));
    }
    
    private AbstractExpression exprFor (ForExpression forExpr) {
        StructuredQName var = forExpr.getVariableQName();
        Expression seq = forExpr.getSequence();
        Expression returns = forExpr.getAction();
        return new FLWOR(exprFor(returns), new ForClause (new Variable(qnameFor(var)), null, exprFor(seq)));
    }

    private AbstractExpression exprFor (LocalVariableReference var) {
        return new Variable (qnameFor (var.getBinding().getVariableQName()));
        // in XQuery this could be bound to a for expression, or another variable, etc.
        // In XPath, inline all variable references:
        //LetExpression binding = (LetExpression) var.getBinding();
        //return exprFor (binding.getSequence());
    }
    
    private static final StructuredQName itemAtQName = new StructuredQName("", NamespaceConstant.SAXON, "item-at");
    
    private AbstractExpression exprFor (FunctionCall funcall) {
        if (funcall.getFunctionName().equals(itemAtQName)) {
            return new Subsequence(exprFor (funcall.getArguments()[0]), exprFor(funcall.getArguments()[1]), LiteralExpression.ONE);
        }
        if (functionEqualsBuiltin(funcall, "reverse")) {
            // Saxon wraps a call to reverse() around reverse axis expressions; its axis expression
            // always returns items in axis (reverse) order, unlike an xpath axis expression, whose results
            // are returned in different order depending on the context
            Expression arg = funcall.getArguments()[0];
            if ((arg.getSpecialProperties() & StaticProperty.REVERSE_DOCUMENT_ORDER) != 0 ||
                    (! Cardinality.allowsMany(arg.getCardinality()))) {
                // if (arg instanceof AxisExpression && (!Axis.isForwards[((AxisExpression) arg).getAxis()])) {
                // wrap in a sequence so as to preserve document order using an expression that will serialize
                // to an appropriate xpath syntax 
                return new Sequence (exprFor (arg));
            }        
        }
        if (funcall.getFunctionName().equals(FunCall.FN_SUBSEQUENCE)) {
            if (funcall.getNumberOfArguments() == 2) {
                return new Subsequence (exprFor(funcall.getArguments()[0]), exprFor(funcall.getArguments()[1]));
            } else {
                if (funcall.getNumberOfArguments() != 3) {
                    throw new LuxException ("call to subsequence has " + funcall.getNumberOfArguments() + " arguments?");                    
                }
                return new Subsequence (exprFor(funcall.getArguments()[0]), exprFor(funcall.getArguments()[1]), exprFor(funcall.getArguments()[2]));
            }
        }
        Expression[] args = funcall.getArguments();
        AbstractExpression[] aargs = new AbstractExpression[args.length];
        for (int i = 0; i < args.length; i++) {
            aargs[i] = exprFor (args[i]);
        }
        Entry entry = StandardFunction.getFunction(funcall.getFunctionName().getDisplayName(), aargs.length);
        ValueType returnType = entry != null ? valueTypeForItemType (entry.itemType) : ValueType.VALUE;
        if (functionEqualsBuiltin(funcall, "root")) 
        {
            // root() may return an element when executed in the context of a fragment
            // However for the purposes of our optimizer, we want to know if it is returning
            // documents.  We only optimize absolute expressions, and this inference is correct in those cases.
            returnType = ValueType.DOCUMENT;
        }
        return new FunCall (qnameFor (funcall.getFunctionName()), returnType, aargs);
    }
    
    private boolean functionEqualsBuiltin (FunctionCall funcall, String builtinFunction) {
        return funcall.getFunctionName().getDisplayName().equals (builtinFunction);
    }
    
    private ValueType valueTypeForItemType(ItemType itemType) {
        if (itemType.isAtomicType()) {
            return ValueType.ATOMIC;
        }
        if (itemType instanceof NodeTest) {
            NodeTest nodeTest = (NodeTest) itemType;
            switch (nodeTest.getPrimitiveType()) {
            case Type.NODE: return ValueType.NODE;
            case Type.ELEMENT: return ValueType.ELEMENT;
            case Type.TEXT: return ValueType.TEXT;
            case Type.ATTRIBUTE: return ValueType.ATTRIBUTE;
            case Type.DOCUMENT: return ValueType.DOCUMENT;
            case Type.PROCESSING_INSTRUCTION: return ValueType.PROCESSING_INSTRUCTION;
            case Type.COMMENT: return ValueType.COMMENT;
            }
        }
        // could be a function type? or namespace()?
        return ValueType.VALUE;
    }

    private AbstractExpression exprFor (Literal literal) {
        // This could be a sequence!!
        Value<?> value = literal.getValue();
        try {
            int len = value.getLength();
            if (len == 0) {
                return new LiteralExpression();
            }
            if (len > 1) {
                ArrayList<LiteralExpression> items = new ArrayList<LiteralExpression>();
                SequenceIterator<?> iter = value.iterate();
                Item<?> member;
                while ((member = iter.next()) != null) {
                    if (member instanceof AtomicValue) {
                        items.add(new LiteralExpression (Value.convertToJava(member)));
                    } else {
                        throw new LuxException ("unsupported node in a literal sequence: " + literal.toString());
                    }                    
                }
                return new Sequence (items.toArray(new LiteralExpression[0]));
            }
            // FIXME: precision loss here for BigInteger values
            return new LiteralExpression(Value.convertToJava(value.asItem()));
        } catch (XPathException e) {
            throw new LuxException (e);
        }        
    }
    
    private QName qnameFor(StructuredQName name) {
        QName qname = new QName (name.getNamespaceBinding().getURI(), name.getLocalPart(), name.getNamespaceBinding().getPrefix());
        if (!(qname.getPrefix().equals ("fn") && qname.getNamespaceURI().equals(FunCall.FN_NAMESPACE) ||
                qname.getPrefix().equals("local") && qname.getNamespaceURI().equals(FunCall.LOCAL_NAMESPACE) ||
                qname.getPrefix().equals("xs") && qname.getNamespaceURI().equals(FunCall.XS_NAMESPACE))) {
            addNamespaceDeclaration(qname);
        }
        return qname;
    }
    
    /* return a QName suitable for use as a constructor of the given type */
    private QName qnameFor (AtomicType type) {
        if (type.isBuiltInType()) {
            return qnameFor (((BuiltInAtomicType)type).getQualifiedName());
        }
        return qnameForNameCode(type.getNameCode());
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
        AbstractExpression ae = exprFor (axis, expr.getNodeTest());
        /*
        if (!Axis.isForwards[expr.getAxis()]) {
            ae = new FunCall (new QName("reverse"), ae);
        }
        */
        return ae;
    }
    
    private BinaryOperation exprFor (PathStep.Axis axis, CombinedNodeTest nodeTest) {
        CombinedNodeTest combinedNodeTest = (CombinedNodeTest) nodeTest;
        NodeTest[] tests = combinedNodeTest.getComponentNodeTests();
        BinaryOperation.Operator op = operatorFor(combinedNodeTest.getOperator());            
        return new BinaryOperation (exprFor(axis, tests[0]), op, exprFor(axis, tests[1]));
    }
    
    private lux.xpath.NodeTest nodeTestFor (DocumentNodeTest nodeTest) {
        NodeTest elementTest = nodeTest.getElementTest();
        int nameCode = elementTest.getFingerprint();
        return new lux.xpath.NodeTest (ValueType.DOCUMENT, qnameForNameCode(nameCode));
    }

    private QName qnameForNameCode(int nameCode) {
        StructuredQName sqname = config.getNamePool().getStructuredQName(nameCode);
        QName name = new QName (sqname.getURI(), sqname.getLocalPart(), sqname.getPrefix());
        return name;
    }
    
    private AbstractExpression exprFor (PathStep.Axis axis, NodeTest nodeTest) {
        if (nodeTest == null) {
            return new PathStep (axis, new lux.xpath.NodeTest(ValueType.NODE));
        }
        if (nodeTest instanceof DocumentNodeTest) {
            return new PathStep (axis, nodeTestFor ((DocumentNodeTest) nodeTest));
        }
        if (nodeTest instanceof CombinedNodeTest) {
            return exprFor (axis, (CombinedNodeTest) nodeTest);
        }
        int nameCode = nodeTest.getFingerprint();
        ValueType nodeType = valueTypeForItemType(nodeTest);
        if (nameCode >= 0) { // matches a single node name 
            return new PathStep (axis, new lux.xpath.NodeTest (nodeType, qnameForNameCode(nameCode)));
        } else { // matches multiple node names
            IntSet nameCodes = nodeTest.getRequiredNodeNames();
            if (nameCodes == IntUniversalSet.getInstance()) {
                return new PathStep (axis, new lux.xpath.NodeTest (nodeType));
            } 
            throw new IllegalArgumentException("Unsupported node test: " + nodeTest.toString());
            // I think we are already handling this case as "CombinedNodeTest" above?
            /*
            else if (nameCodes instanceof IntComplementSet) {
                // I think we are already handling this case as "CombinedNodeTest" above?
                throw new IllegalArgumentException("Unsupported node test: " + nodeTest.toString());
            } else if (nameCodes instanceof IntEmptySet) {
                throw new IllegalArgumentException("Unsupported node test: " + nodeTest.toString());
            }
            IntIterator nameCodesIter = nameCodes.iterator();
            ArrayList<AbstractExpression> tests = new ArrayList<AbstractExpression>();
            while (nameCodesIter.hasNext()) {
                tests.add(new PathStep (axis, new lux.xpath.NodeTest(ValueType.ELEMENT, qnameForNameCode(nameCodesIter.next()))));
            }
            return new SetOperation (Operator.UNION, tests.toArray(new AbstractExpression[0]));
            */
        }    
    }
    
    // covers ArithmeticExpression, BooleanExpression, GeneralComparison, ValueComparison,
    // IdentityComparison, RangeExpression
    private AbstractExpression exprFor (BinaryExpression expr) {
        Expression [] operands = expr.getOperands();
        BinaryOperation.Operator op = operatorFor(expr.getOperator());
        return new BinaryOperation (exprFor(operands[0]), op, exprFor(operands[1]));
    }

    private BinaryOperation.Operator operatorFor(int op) {
        switch (op) {
        case Token.AND: return BinaryOperation.Operator.AND;
        case Token.OR: return BinaryOperation.Operator.OR;
        case Token.INTERSECT: return BinaryOperation.Operator.INTERSECT;
        case Token.EXCEPT: return BinaryOperation.Operator.EXCEPT;
        case Token.UNION: return BinaryOperation.Operator.UNION;
        case Token.PLUS: return BinaryOperation.Operator.ADD;
        case Token.MINUS: return BinaryOperation.Operator.SUB;
        case Token.MULT: return BinaryOperation.Operator.MUL;
        case Token.DIV: return BinaryOperation.Operator.DIV;
        case Token.IDIV: return BinaryOperation.Operator.IDIV;
        case Token.MOD: return BinaryOperation.Operator.MOD;
        case Token.EQUALS: return BinaryOperation.Operator.EQUALS;
        case Token.NE: return BinaryOperation.Operator.NE;
        case Token.LT: return BinaryOperation.Operator.LT;
        case Token.GT: return BinaryOperation.Operator.GT;
        case Token.LE: return BinaryOperation.Operator.LE;
        case Token.GE: return BinaryOperation.Operator.GE;
        case Token.FEQ: return BinaryOperation.Operator.AEQ;
        case Token.FNE: return BinaryOperation.Operator.ANE;
        case Token.FLT: return BinaryOperation.Operator.ALT;
        case Token.FLE: return BinaryOperation.Operator.ALE;
        case Token.FGT: return BinaryOperation.Operator.AGT;
        case Token.FGE: return BinaryOperation.Operator.AGE;
        case Token.IS: return BinaryOperation.Operator.IS;
        case Token.PRECEDES: return BinaryOperation.Operator.BEFORE;
        case Token.FOLLOWS: return BinaryOperation.Operator.AFTER;
        default: throw new IllegalArgumentException("Unsupported operator: " + op);
        }
    }

    private AbstractExpression exprFor (FirstItemExpression expr) {
        return new Subsequence (exprFor (expr.getBaseExpression()), LiteralExpression.ONE, LiteralExpression.ONE);
    }

    private AbstractExpression exprFor (LastItemExpression expr) {
        return new Subsequence (exprFor (expr.getBaseExpression()), FunCall.LastExpression, LiteralExpression.ONE);
    }

    private AbstractExpression exprFor (NegateExpression expr) {
        return new UnaryMinus(exprFor (expr.getBaseExpression()));
    }

    private AbstractExpression exprFor (CastExpression expr) {
        Expression base = expr.getBaseExpression();
        AtomicType type = expr.getTargetType();
        return new FunCall (qnameFor(type), valueTypeForItemType(type), exprFor(base));
    }
    
    private AbstractExpression exprFor (Atomizer atomizer) {
        Expression base = atomizer.getBaseExpression();
        return new FunCall (FunCall.FN_DATA, ValueType.ATOMIC, exprFor (base));
    }
    
    private AbstractExpression exprFor (UnaryExpression expr) {
        // int cardinality = checker.getRequiredCardinality();
        // TODO: implement?  can we merge this with an Atomizer?
        // CardinalityChecker; DocumentSorter
        return exprFor (expr.getBaseExpression());
    }
    
    private AbstractExpression exprFor (CompareToIntegerConstant comp) {
        Operator op = operatorFor (comp.getComparisonOperator());
        long num = comp.getComparand();
        return new BinaryOperation  (exprFor (comp.getOperand()), op, new LiteralExpression (num));
    }

    /*
     * XQuery expressions
     */
   
    private AbstractExpression exprFor (FixedElement element) {
        NodeName name = element.getElementName();
        QName qname = qnameFor (name.getStructuredQName());
        AbstractExpression content = exprFor (element.getContentExpression());
        Namespace [] namespaces = namespacesFor (element.getActiveNamespaces());
        ElementConstructor elcon = new ElementConstructor(qname, namespaces, content);
        return elcon;
    }

    private Namespace[] namespacesFor(NamespaceBinding[] activeNamespaces) {
        if (activeNamespaces == null)
            return null;
        Namespace[] namespaces = new Namespace[activeNamespaces.length];
        int i = 0;
        for (NamespaceBinding binding : activeNamespaces) {
            namespaces[i++] = new Namespace (binding.getPrefix(), binding.getURI());
        }
        return namespaces;
    }

    private AbstractExpression exprFor (FixedAttribute attribute) {
        NodeName name = attribute.getAttributeName();
        QName qname = qnameFor (name.getStructuredQName());
        AttributeConstructor att = new AttributeConstructor(qname, exprFor (attribute.getContentExpression()));
        return att;
    }
    
    private AbstractExpression exprFor (ValueOf valueOf) {
        return new TextConstructor(exprFor (valueOf.getContentExpression()));
    }
    
    private AbstractExpression exprFor (FLWORExpression flwor) {  
        List<Clause> saxonClauses = flwor.getClauseList();
        int i = 0;
        while (saxonClauses.get(i).getClauseKey() == Clause.WHERE) {
            // Saxon optimizes constant where clauses to the left of the expression where they
            // are no longer syntactically valid as xquery
            ++i;
        }
        FLWORClause clauses[] = new FLWORClause[saxonClauses.size()];
        int k = 1;
        if (i < saxonClauses.size()) {
            // get the first non-where clause
            clauses[0] = clauseFor (saxonClauses.get(i));
            if (i > 0) { 
                // append any of the preamble where clauses
                for (int j = 0; j < i; j++) {
                    clauses[k++] = clauseFor (saxonClauses.get(j));                    
                }
            }
        }
        // and the rest of the clauses...
        for (int j = i + 1; j < saxonClauses.size(); j++) {
            clauses[k++] = clauseFor (saxonClauses.get(j));                            
        }
        return new FLWOR(exprFor (flwor.getReturnClause()), clauses);
    }
    
    private FLWORClause clauseFor (Clause clause) {
        switch (clause.getClauseKey()) {
        case Clause.LET: return clauseFor ((net.sf.saxon.expr.flwor.LetClause) clause);
        case Clause.FOR: return clauseFor ((net.sf.saxon.expr.flwor.ForClause) clause);
        case Clause.WHERE: return clauseFor ((net.sf.saxon.expr.flwor.WhereClause) clause);
        case Clause.ORDERBYCLAUSE: return clauseFor ((net.sf.saxon.expr.flwor.OrderByClause) clause);
        default: throw new LuxException ("Unsupported FLWOR clause " + clause.getClass().getSimpleName());
        }
    }
    
    private FLWORClause clauseFor (net.sf.saxon.expr.flwor.ForClause clause) {        
        AbstractExpression seq = exprFor (clause.getSequence());
        Variable var = new Variable (qnameFor(clause.getRangeVariable().getVariableQName()));        
        LocalVariableBinding positionVariable = clause.getPositionVariable();
        Variable pos;
        if (positionVariable != null) {
            pos = new Variable (qnameFor(positionVariable.getVariableQName()));
        } else  {
            pos = null;
        }            
        return new ForClause(var, pos, seq);
    }

    private FLWORClause clauseFor (net.sf.saxon.expr.flwor.LetClause clause) {        
        AbstractExpression seq = exprFor (clause.getSequence());
        Variable var = new Variable (qnameFor(clause.getRangeVariable().getVariableQName()));
        return new LetClause(var, seq);
    }

    private FLWORClause clauseFor (net.sf.saxon.expr.flwor.WhereClause clause) {
        return new WhereClause(exprFor (clause.getPredicate()));
    }
    
    private FLWORClause clauseFor (net.sf.saxon.expr.flwor.OrderByClause clause) {
        SortKeyDefinition[] sortKeyDefs = clause.getSortKeyDefinitions();
        SortKey[] sortKeys = new SortKey[sortKeyDefs.length];
        for (int i = 0; i < sortKeyDefs.length; i++) {
            sortKeys[i] = sortKeyFor (sortKeyDefs[i]);
        }
        return new OrderByClause(sortKeys);
    }
    
    private SortKey sortKeyFor (SortKeyDefinition sortKeyDef) {
        return new SortKey (exprFor (sortKeyDef.getSortKey()), 
                (LiteralExpression) exprFor (sortKeyDef.getOrder()),
                exprFor (sortKeyDef.getCollationNameExpression()), 
                sortKeyDef.getEmptyLeast());
    }
    
    private AbstractExpression exprFor (Choose choose) {
        // convert a list of condition/action pairs (a la XSLT) to a chain of if-then-else conditions
        // a-la XQuery
        Expression[] conds = choose.getConditions();
        Expression[] actions = choose.getActions();
        int l = conds.length;
        if (l < 2) {
            throw new LuxException ("Choose must have at least two conditions");
        }
        if (actions.length != conds.length) {
            throw new LuxException ("Choose must have the same number of actions as conditions");            
        }
        l -= 2;
        AbstractExpression tail = new Conditional(exprFor (conds[l]), exprFor (actions[l]), exprFor(actions[l+1]));
        while (--l > 0) {
            tail = new Conditional (exprFor (conds[l]), exprFor(actions[l]), tail);
        }
        return tail;
    }

}