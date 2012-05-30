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
import lux.saxon.xquery.InstanceOf;
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
import lux.xquery.CommentConstructor;
import lux.xquery.ComputedElementConstructor;
import lux.xquery.Conditional;
import lux.xquery.DocumentConstructor;
import lux.xquery.ElementConstructor;
import lux.xquery.FLWOR;
import lux.xquery.FLWORClause;
import lux.xquery.ForClause;
import lux.xquery.FunctionDefinition;
import lux.xquery.LetClause;
import lux.xquery.OrderByClause;
import lux.xquery.ProcessingInstructionConstructor;
import lux.xquery.Satisfies;
import lux.xquery.Satisfies.Quantifier;
import lux.xquery.SortKey;
import lux.xquery.TextConstructor;
import lux.xquery.Variable;
import lux.xquery.VariableDefinition;
import lux.xquery.WhereClause;
import lux.xquery.XQuery;
import net.sf.saxon.expr.AtomicSequenceConverter;
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
import net.sf.saxon.expr.InstanceOfExpression;
import net.sf.saxon.expr.IntegerRangeTest;
import net.sf.saxon.expr.LastItemExpression;
import net.sf.saxon.expr.LetExpression;
import net.sf.saxon.expr.Literal;
import net.sf.saxon.expr.NegateExpression;
import net.sf.saxon.expr.ParentNodeExpression;
import net.sf.saxon.expr.QuantifiedExpression;
import net.sf.saxon.expr.RootExpression;
import net.sf.saxon.expr.SlashExpression;
import net.sf.saxon.expr.StaticProperty;
import net.sf.saxon.expr.TailExpression;
import net.sf.saxon.expr.UnaryExpression;
import net.sf.saxon.expr.VariableReference;
import net.sf.saxon.expr.flwor.Clause;
import net.sf.saxon.expr.flwor.FLWORExpression;
import net.sf.saxon.expr.flwor.LocalVariableBinding;
import net.sf.saxon.expr.instruct.Block;
import net.sf.saxon.expr.instruct.Choose;
import net.sf.saxon.expr.instruct.Comment;
import net.sf.saxon.expr.instruct.ComputedAttribute;
import net.sf.saxon.expr.instruct.ComputedElement;
import net.sf.saxon.expr.instruct.CopyOf;
import net.sf.saxon.expr.instruct.DocumentInstr;
import net.sf.saxon.expr.instruct.FixedAttribute;
import net.sf.saxon.expr.instruct.FixedElement;
import net.sf.saxon.expr.instruct.GlobalVariable;
import net.sf.saxon.expr.instruct.ProcessingInstruction;
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
import net.sf.saxon.om.NamespaceResolver;
import net.sf.saxon.om.NodeName;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.om.StandardNames;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.pattern.CombinedNodeTest;
import net.sf.saxon.pattern.DocumentNodeTest;
import net.sf.saxon.pattern.LocalNameTest;
import net.sf.saxon.pattern.NamespaceTest;
import net.sf.saxon.pattern.NodeTest;
import net.sf.saxon.query.GlobalVariableDefinition;
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
import net.sf.saxon.value.BigIntegerValue;
import net.sf.saxon.value.CalendarValue;
import net.sf.saxon.value.Cardinality;
import net.sf.saxon.value.Value;

import org.apache.commons.lang.StringUtils;

/**
 * Translates Saxon XPath 2.0 Expressions into Lux AbstractExpressions.
 */
public class SaxonTranslator {
    
    public static final String CODEPOINT_COLLATION = "http://www.w3.org/2005/xpath-functions/collation/codepoint";
    private Config config;
    private HashMap<String,String> namespaceDeclarations;
    private HashMap<String,ExprClass> dispatcher;
    private QueryModule queryModule;
    
    public SaxonTranslator (Config config) {
        this.config = config;
        namespaceDeclarations = new HashMap<String, String>();
        createDispatcher ();
    }
    
    private void createDispatcher() {
        dispatcher = new HashMap<String, ExprClass>();
        for (ExprClass eclass : ExprClass.values()) {
            dispatcher.put (eclass.toString(), eclass);            
        }
    }

    /** Converts from a Saxon to a lux xquery representation.
     * @param expr a Saxon representation of an XQuery module
     * @return a lux representation of an equivalent XQuery module
     */

    public XQuery queryFor(XQueryExecutable xquery) {
        XQueryExpression saxonQuery = xquery.getUnderlyingCompiledQuery();
        queryModule = saxonQuery.getStaticContext();
        //StructuredQName[] extVars = saxonQuery.getExternalVariableNames();
        // Namespace declarations are accumulated while walking the expression trees:
        namespaceDeclarations.clear();
        FunctionDefinition[] functionDefinitions = getFunctionDefinitions(queryModule);
        AbstractExpression body = exprFor (saxonQuery.getExpression());
        String defaultCollation = queryModule.getDefaultCollationName();
        if (defaultCollation.equals (CODEPOINT_COLLATION)) {
            defaultCollation = null;
        }
        return new XQuery(
                queryModule.getDefaultElementNamespace(),
                queryModule.getDefaultFunctionNamespace(),
                defaultCollation,
                getNamespaceDeclarations(queryModule), 
                getVariableDefinitions(queryModule), 
                functionDefinitions, 
                body,
                queryModule.isPreserveNamespaces() ? true : null,
                queryModule.isInheritNamespaces() ? null : false);
    }
    
    public XQuery queryFor(AbstractExpression ex) {
        return new XQuery(ex);
    }

    private Namespace[] getNamespaceDeclarations(QueryModule queryModule) {
        // String defElementNS = queryModule.getDefaultElementNamespace();
        // String defFunctionNS = queryModule.getDefaultFunctionNamespace();
        // We'd like to get our hands on queryModule.explicitPrologNamespaces :(
        // Without it we have to resort to some scanning ugliness in search of 
        // all namespaces.
        Namespace[] decls = new Namespace[namespaceDeclarations.size()];
        int i = 0;
        for (Map.Entry<String, String> entry : namespaceDeclarations.entrySet()) {
            decls[i++] = new Namespace (entry.getKey(), entry.getValue());
        }
        return decls;
    }
    
    private VariableDefinition[] getVariableDefinitions(QueryModule module) {
        ArrayList<VariableDefinition> defs = new ArrayList<VariableDefinition>();
        @SuppressWarnings("unchecked")
        Iterator<GlobalVariableDefinition> decls = module.getModuleVariables();
        while (decls.hasNext()) {
            GlobalVariableDefinition decl = decls.next();
            GlobalVariable global = decl.getCompiledVariable();
            AbstractExpression var = global != null ? exprFor(global) : new Variable(qnameFor(decl.getVariableQName()));
            VariableDefinition def = new VariableDefinition(var, exprFor(decl.getValueExpression()));
            defs.add(def);
        }
        return defs.toArray(new VariableDefinition[0]);
    }
    
    private void addNamespaceDeclaration (QName qname) {
        String prefix = qname.getPrefix();
        String namespaceURI = qname.getNamespaceURI();
        if (! namespaceDeclarations.containsKey(prefix)) {
            // We only want the outermost binding of each prefix; these
            // are used to generate the namespace declarations in the query preamble
            namespaceDeclarations.put(prefix, namespaceURI);
        }
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
    
    public AbstractExpression exprFor (AtomicSequenceConverter atomizer) {
        ItemType type = atomizer.getItemType(config.getTypeHierarchy());
        if (!type.isAtomicType()) {
            throw new LuxException ("AtomicSequenceConverter converting to non-atomic type: " + type);
        }
        AtomicType atomicType = (AtomicType) type;
        Variable var = new Variable(new QName("x"));
        
        return new FLWOR(castExprFor(var, atomicType), 
                new ForClause (var, null, exprFor(atomizer.getBaseExpression())));
        // this often works, doesn't provide the type conversion:
        // return exprFor (atomizer.getBaseExpression());
        /*
        // this produces expressions like: fn:string(data($x))
        // buf if $x is a sequence, that's not allowed
        Expression base = atomizer.getBaseExpression();
        ItemType type = atomizer.getItemType(config.getTypeHierarchy());
        if (!type.isAtomicType()) {
            throw new LuxException ("AtomicSequenceConverter converting to non-atomic type: " + type);
        }
        AtomicType atomicType = (AtomicType) type;
        ArrayList<AbstractExpression> abstracted = new ArrayList<AbstractExpression>();
        if (base instanceof Block) {
            Expression[] children = ((Block)base).getChildren();
            for (int i = 0; i < children.length; i++) {
                addCastExprFor (abstracted, children[i], atomicType);
            }
        } else {
            addCastExprFor(abstracted, base, atomicType);
        }
        return new Sequence(abstracted.toArray(new AbstractExpression[0]));
        */
    }

    /* return a QName suitable for use as a constructor of the given type */
    private QName qnameFor (AtomicType type) {
        if (type.isBuiltInType()) {
            return qnameFor (((BuiltInAtomicType)type).getQualifiedName());
        }
        return qnameForNameCode(type.getNameCode());
    }

    public AbstractExpression exprFor (Atomizer atomizer) {
        Expression base = atomizer.getBaseExpression();
        return new FunCall (FunCall.FN_DATA, ValueType.ATOMIC, exprFor (base));
    }
    
    public AbstractExpression exprFor (AxisExpression expr) {
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
        String prefix = sqname.getPrefix();
        String namespace = sqname.getURI();
        if (StringUtils.isBlank(prefix) && StringUtils.isNotBlank(namespace)) {
            prefix = getPrefixForNamespace(namespace);
        }
        QName name = new QName (namespace, sqname.getLocalPart(), prefix);
        return name;
    }
    
    public AbstractExpression exprFor (PathStep.Axis axis, NodeTest nodeTest) {
        if (nodeTest != null && nodeTest instanceof CombinedNodeTest) {
            return exprFor (axis, (CombinedNodeTest) nodeTest);
        }        
        return new PathStep (axis, nodeTestFor (nodeTest));
    }

    private lux.xpath.NodeTest nodeTestFor (NodeTest nodeTest) {
        if (nodeTest == null) {
            return new lux.xpath.NodeTest(ValueType.NODE);
        }
        if (nodeTest instanceof DocumentNodeTest) {
            return nodeTestFor ((DocumentNodeTest) nodeTest);
        }
        int nameCode = nodeTest.getFingerprint();
        ValueType nodeType = valueTypeForItemType(nodeTest);
        if (nameCode >= 0) { // matches a single node name 
            return new lux.xpath.NodeTest (nodeType, qnameForNameCode(nameCode));
        } else { // matches multiple node names
            if (nodeTest instanceof LocalNameTest) {
                return new lux.xpath.NodeTest (nodeType, new QName(null, ((LocalNameTest)nodeTest).getLocalName(), "*"));
            }
            if (nodeTest instanceof NamespaceTest) {
                NamespaceTest namespaceTest = (NamespaceTest) nodeTest;
                String namespace = namespaceTest.getNamespaceURI();
                String prefix = getPrefixForNamespace (namespace);
                QName qname = new QName(namespace, "*", prefix);
                addNamespaceDeclaration(qname);
                return new lux.xpath.NodeTest (nodeType, qname);
            }
            IntSet nameCodes = nodeTest.getRequiredNodeNames();
            if (nameCodes == IntUniversalSet.getInstance()) {
                return new lux.xpath.NodeTest (nodeType);
            } 
            throw new IllegalArgumentException("Unsupported node test: " + nodeTest.toString());
        }
    }

    private String getPrefixForNamespace(String namespace) {
        String prefix = config.getNamePool().suggestPrefixForURI(namespace);
        if (prefix == null) {
            NamespaceResolver resolver = queryModule.getNamespaceResolver();
            Iterator<String> prefixes = resolver.iteratePrefixes();
            while (prefixes.hasNext()) {
                String p = prefixes.next();
                if (namespace.equals(resolver.getURIForPrefix(p, true))) {
                    prefix = p;
                    break;
                }
            }
        }
        if (prefix != null) {
            namespaceDeclarations.put(prefix, namespace);
        } else {
            for (Map.Entry<String, String> ns : namespaceDeclarations.entrySet()) {
                if (ns.getValue().equals(namespace))
                    return ns.getKey();
            }
        }
        return prefix;
    }

    // covers ArithmeticExpression, BooleanExpression, GeneralComparison, ValueComparison,
    // IdentityComparison, RangeExpression
    public AbstractExpression exprFor (BinaryExpression expr) {
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
        case Token.TO: return BinaryOperation.Operator.TO;
        case Token.NEGATE: return BinaryOperation.Operator.SUB;
        default: throw new IllegalArgumentException("Unsupported operator: " + op);
        }
    }

    public AbstractExpression exprFor (Block expr) {
        return exprFor (expr.getChildren());
    }

    public AbstractExpression exprFor (CastExpression expr) {
        Expression base = expr.getBaseExpression();
        AtomicType type = expr.getTargetType();
        return castExprFor(exprFor(base), type);
    }
    
    private AbstractExpression castExprFor (AbstractExpression ae, AtomicType type) {
        if (type.isAbstract()) {
            return ae;
        }
        return new FunCall (qnameFor(type), valueTypeForItemType(type), ae);        
    }

    private Sequence exprFor (Expression[] exprs) {
        AbstractExpression[] aex = new AbstractExpression [exprs.length];
        for (int i = 0 ; i < exprs.length; i++) {
            aex[i] = exprFor (exprs[i]);
        }
        return new Sequence (aex);
    }
        
    public AbstractExpression exprFor (Choose choose) {
        // convert a list of condition/action pairs (a la XSLT) to a chain
        // of if-then-else conditions a-la XQuery
        Expression[] conds = choose.getConditions();
        Expression[] actions = choose.getActions();
        int l = conds.length;
        if (actions.length != conds.length) {
            throw new LuxException ("Choose must have the same number of actions as conditions");            
        }
        if (l < 2) {
            return new Conditional (exprFor (conds[0]), exprFor(actions[0]), new LiteralExpression());
        }
        l -= 2;
        AbstractExpression tail = new Conditional(exprFor (conds[l]), exprFor (actions[l]), exprFor(actions[l+1]));
        while (l-- > 0) {
            tail = new Conditional (exprFor (conds[l]), exprFor(actions[l]), tail);
        }
        return tail;
    }

    public AbstractExpression exprFor (Comment expr) {
        return new CommentConstructor(exprFor(((Comment)expr).getContentExpression()));
    }
    
    public AbstractExpression exprFor (ComputedAttribute expr) {
        return new AttributeConstructor(exprFor(expr.getNameExpression()), 
                exprFor(expr.getContentExpression()));
    }
    
    public AbstractExpression exprFor (ComputedElement expr) {
        return new ComputedElementConstructor(exprFor (expr.getNameExpression()), 
                exprFor (expr.getContentExpression()));
    }

    public AbstractExpression exprFor (ContextItemExpression expr) {
        return new Dot();
    }

    private AbstractExpression exprFor(CopyOf expr) {
        return exprFor (expr.getSelectExpression());
    }
    
    public AbstractExpression exprFor (DocumentInstr documentInstr) {
        return new DocumentConstructor (exprFor (documentInstr.getContentExpression()));
    }
    
    public AbstractExpression exprFor (FilterExpression expr) {
        AbstractExpression filtered = exprFor (expr.getControllingExpression());
        AbstractExpression filter = exprFor (expr.getFilter());
        return new Predicate(filtered, filter);
    }

    public AbstractExpression exprFor (FirstItemExpression expr) {
        return new Subsequence (exprFor (expr.getBaseExpression()), LiteralExpression.ONE, LiteralExpression.ONE);
    }

    public AbstractExpression exprFor (FixedAttribute attribute) {
        NodeName name = attribute.getAttributeName();
        QName qname = qnameFor (name.getStructuredQName());
        AttributeConstructor att = new AttributeConstructor(
                new LiteralExpression(qname.toString(), ValueType.STRING), 
                exprFor (attribute.getContentExpression()));
        return att;
    }
    
    public AbstractExpression exprFor (FixedElement element) {
        NodeName name = element.getElementName();
        QName qname = qnameFor (name.getStructuredQName());
        AbstractExpression content = exprFor (element.getContentExpression());
        Namespace [] namespaces = namespacesFor (element.getActiveNamespaces());
        ElementConstructor elcon = new ElementConstructor(qname, namespaces, content);
        return elcon;
    }

    public AbstractExpression exprFor (ForExpression forExpr) {
        StructuredQName var = forExpr.getVariableQName();
        Expression seq = forExpr.getSequence();
        Expression returns = forExpr.getAction();
        return new FLWOR(exprFor(returns), new ForClause (new Variable(qnameFor(var)), null, exprFor(seq)));
    }

    public AbstractExpression exprFor (FunctionCall funcall) {
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
    
    public AbstractExpression exprFor (GlobalVariable var) {
        // TODO: encode the declared type
        return new Variable (qnameFor (var.getVariableQName()));
    }
    
    public AbstractExpression exprFor (IntegerRangeTest rangeTest) {
        return new BinaryOperation
            (new BinaryOperation
             (exprFor (rangeTest.getValueExpression()), Operator.ALE, 
              exprFor (rangeTest.getMaxValueExpression())),
             Operator.AND,
             new BinaryOperation
             (exprFor (rangeTest.getValueExpression()), Operator.AGE, 
              exprFor (rangeTest.getMinValueExpression())));
    }
    
    private AbstractExpression exprFor (InstanceOfExpression expr) {
        ItemType type = expr.getRequiredItemType();
        String typeExpr;
        if (type.isPlainType()) {
            typeExpr = type.toString();
        } else if (type instanceof NodeTest) {
            typeExpr = nodeTestFor((NodeTest)type).toString();
        } else {
            throw new LuxException ("Unsupported node test in instance-of expression: " + expr.toString());
        }
        return new InstanceOf(typeExpr, exprFor (expr.getBaseExpression()));
    }

    private ValueType valueTypeForItemType(ItemType itemType) {
        if (itemType.isAtomicType()) {
            switch (itemType.getPrimitiveType()) {
            case StandardNames.XS_STRING:
            case StandardNames.XS_ANY_URI:
                return ValueType.STRING;
            case StandardNames.XS_DATE:
                return ValueType.DATE;
            case StandardNames.XS_DATE_TIME:
                return ValueType.DATE_TIME;
            case StandardNames.XS_G_YEAR:
                return ValueType.YEAR;
            case StandardNames.XS_G_YEAR_MONTH:
                return ValueType.YEAR_MONTH;
            case StandardNames.XS_G_DAY:
                return ValueType.DAY;
            case StandardNames.XS_G_MONTH_DAY:
                return ValueType.MONTH_DAY;
            case StandardNames.XS_INT:
            case StandardNames.XS_INTEGER:
                return ValueType.INT;
            case StandardNames.XS_DOUBLE:
                return ValueType.DOUBLE;
            case StandardNames.XS_FLOAT:
                return ValueType.FLOAT;
            case StandardNames.XS_DECIMAL:
                return ValueType.DECIMAL;
            case StandardNames.XS_BOOLEAN:
                return ValueType.BOOLEAN;
            case StandardNames.XS_TIME:
                return ValueType.TIME;
            case StandardNames.XS_HEX_BINARY:
                return ValueType.HEX_BINARY;
            case StandardNames.XS_BASE64_BINARY:
                return ValueType.BASE64_BINARY;
            case StandardNames.XS_UNTYPED_ATOMIC:
                return ValueType.ATOMIC;
            default:
                return ValueType.VALUE;
            }
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

    public AbstractExpression exprFor (LastItemExpression expr) {
        return new Subsequence (exprFor (expr.getBaseExpression()), FunCall.LastExpression, LiteralExpression.ONE);
    }

    public AbstractExpression exprFor (LetExpression let) {
        // TODO: get rid of Let and use a FLWOR
        StructuredQName var = let.getVariableQName();
        Expression seq = let.getSequence();
        Expression returns = let.getAction();
        return new FLWOR(exprFor(returns), new LetClause (new Variable(qnameFor(var)), exprFor(seq)));
    }
    
    public AbstractExpression exprFor (Literal literal) {
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
                        items.add (exprFor ((AtomicValue) member));
                    } else {
                        throw new LuxException ("unsupported node in a literal sequence: " + literal.toString());
                    }                    
                }
                return new Sequence (items.toArray(new LiteralExpression[0]));
            }
            return exprFor ((AtomicValue) value);
        } catch (XPathException e) {
            throw new LuxException (e);
        }        
    }

    public LiteralExpression exprFor (AtomicValue value) {
        ValueType type = valueTypeForItemType(value.getPrimitiveType());
        if (value instanceof CalendarValue) {
            //return new LiteralExpression(((CalendarValue)value).getCalendar(), type);
            return new LiteralExpression(value.getStringValue(), type);
        }
        if (value instanceof BigIntegerValue) {
            return new LiteralExpression (((BigIntegerValue)value).getStringValue(), type);
        }
        try {
            Object oval = Value.convertToJava(value.asItem());
            return new LiteralExpression(oval, type);
        } catch (XPathException e) {
            throw new LuxException (e);
        }
    }
    public AbstractExpression exprFor (NegateExpression expr) {
        return new UnaryMinus(exprFor (expr.getBaseExpression()));
    }

    public AbstractExpression exprFor (ParentNodeExpression expr) {
        return new PathStep(PathStep.Axis.Parent, new lux.xpath.NodeTest(ValueType.NODE));
    }

    public AbstractExpression exprFor (ProcessingInstruction pi) {
        return new ProcessingInstructionConstructor(exprFor(pi.getNameExpression()), exprFor(pi.getContentExpression()));
    }

    public AbstractExpression exprFor (QuantifiedExpression expr) {
        Satisfies.Quantifier quantifier = expr.getOperator() == Token.SOME ? Quantifier.SOME : Quantifier.EVERY;
        return new Satisfies(quantifier, new Variable(qnameFor(expr.getVariableQName())), exprFor(expr.getSequence()), exprFor (expr.getAction()));
    }
    
    public AbstractExpression exprFor (RootExpression expr) {
        return new Root();
    }

    public AbstractExpression exprFor (SlashExpression expr) {
        AbstractExpression lq = exprFor (expr.getControllingExpression());
        AbstractExpression rq = exprFor (expr.getControlledExpression());
        return new PathExpression(lq, rq);
    }
    
    public AbstractExpression exprFor (TailExpression expr) {
        return new Subsequence (exprFor (expr.getBaseExpression()), new LiteralExpression(expr.getStart()));
    }
    
    public AbstractExpression exprFor (UnaryExpression expr) {
        // int cardinality = checker.getRequiredCardinality();
        // TODO: implement?  can we merge this with an Atomizer?
        // CardinalityChecker; DocumentSorter
        return exprFor (expr.getBaseExpression());
    }
    
    public AbstractExpression exprFor (ValueOf valueOf) {
        return new TextConstructor(exprFor (valueOf.getContentExpression()));
    }
    
    public AbstractExpression exprFor (VariableReference var) {
        StructuredQName varName;
        if (var.getBinding() != null) {
            varName = var.getBinding().getVariableQName();
        } else {
            // total HACK, but Saxon provides no other public method to retrieve the constant value
            try {
                Object o = var.optimize(null, null);
                if (o instanceof Literal) {
                    return exprFor ((Literal) o);
                }
            } catch (XPathException e) {
                throw new LuxException ("Unsupported variable reference: " + var);
            } 
            throw new LuxException ("Unsupported variable reference: " + var);
        }
        return new Variable (qnameFor (varName));
        // TODO If generating XPath, inline all variable references generated by Saxon:
        // LetExpression binding = (LetExpression) var.getBinding();
        // return exprFor (binding.getSequence());
    }
    
    private static final StructuredQName itemAtQName = new StructuredQName("", NamespaceConstant.SAXON, "item-at");
    
    private QName qnameFor(StructuredQName name) {
        QName qname = new QName (name.getNamespaceBinding().getURI(), name.getLocalPart(), name.getNamespaceBinding().getPrefix());
        if (!(qname.getPrefix().equals ("fn") && qname.getNamespaceURI().equals(FunCall.FN_NAMESPACE) ||
                qname.getPrefix().equals("local") && qname.getNamespaceURI().equals(FunCall.LOCAL_NAMESPACE) ||
                qname.getPrefix().equals("xs") && qname.getNamespaceURI().equals(FunCall.XS_NAMESPACE))) {
            addNamespaceDeclaration(qname);
        }
        return qname;
    }

    public AbstractExpression exprFor (CompareToIntegerConstant comp) {
        Operator op = operatorFor (comp.getComparisonOperator());
        long num = comp.getComparand();
        return new BinaryOperation  (exprFor (comp.getOperand()), op, new LiteralExpression (num));
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
    
    public AbstractExpression exprFor (FLWORExpression flwor) {  
        List<Clause> saxonClauses = flwor.getClauseList();
        int i = 0;
        while (i < saxonClauses.size() && saxonClauses.get(i).getClauseKey() == Clause.WHERE) {
            // Saxon optimizes constant where clauses to the left of the expression where they
            // are no longer syntactically valid as xquery
            ++i;
        }
        FLWORClause clauses[];
        int k = 0;
        if (i < saxonClauses.size()) {
            clauses = new FLWORClause[saxonClauses.size()];
            // get the first non-where clause
            clauses[k++] = clauseFor (saxonClauses.get(i));
        } else {
            // Generate a dummy let clause??
            clauses = new FLWORClause[saxonClauses.size() + 1];
            clauses[k++] = new LetClause(new Variable (new QName("x")), LiteralExpression.ONE);
        }
        if (i > 0) { 
            // append any of the preamble where clauses
            for (int j = 0; j < i; j++) {
                clauses[k++] = clauseFor (saxonClauses.get(j));                    
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
    
    public AbstractExpression exprFor (Expression expr) {
        if (expr == null) {
            return null;
        }
        ExprClass exprClass = null;        
        for (Class<?> cls = expr.getClass(); exprClass == null && cls != Object.class; cls = cls.getSuperclass()) {
            exprClass = dispatcher.get (cls.getSimpleName());
        }
        if (exprClass == null) {
            throw new UnsupportedOperationException ("unhandled expression type: " + expr.getClass().getSimpleName() + " in " + expr.toString());
        }
        switch (exprClass) {
        case AtomicSequenceConverter:
            return exprFor ((AtomicSequenceConverter)expr);
        case Atomizer:
            return exprFor ((Atomizer) expr);
        case AxisExpression:
            return exprFor ((AxisExpression) expr);
        case BinaryExpression:
            return exprFor ((BinaryExpression) expr);
        case Block:
            return exprFor ((Block) expr);
        case CastExpression:
            return exprFor ((CastExpression) expr);
        case Choose:
            return exprFor ((Choose) expr);
        case Comment:
            return exprFor ((Comment) expr);
        case CompareToIntegerConstant:
            return exprFor ((CompareToIntegerConstant) expr);
        case ComputedAttribute:
            return exprFor ((ComputedAttribute) expr);
        case ComputedElement:
            return exprFor ((ComputedElement) expr);
        case ContextItemExpression:
            return exprFor ((ContextItemExpression) expr);
        case CopyOf:
            return exprFor ((CopyOf) expr);
        case DocumentInstr:
            return exprFor ((DocumentInstr) expr);            
        case FilterExpression:
            return exprFor ((FilterExpression) expr);
        case FirstItemExpression:
            return exprFor ((FirstItemExpression) expr);
        case FixedAttribute:
            return exprFor ((FixedAttribute) expr);
        case FixedElement:
            return exprFor ((FixedElement) expr);
        case FLWORExpression:
            return exprFor ((FLWORExpression) expr);
        case ForExpression:
            return exprFor ((ForExpression) expr);
        case FunctionCall:
            return exprFor ((FunctionCall) expr);
        case InstanceOfExpression:
            return exprFor ((InstanceOfExpression) expr);
        case IntegerRangeTest:
            return exprFor ((IntegerRangeTest) expr);
        case LastItemExpression:
            return exprFor ((LastItemExpression) expr);
        case LetExpression:
            return exprFor ((LetExpression) expr);
        case Literal:
            return exprFor ((Literal) expr);
        case NegateExpression:
            return exprFor ((NegateExpression) expr);
        case ParentNodeExpression:
            return exprFor ((ParentNodeExpression) expr);
        case ProcessingInstruction:
            return exprFor ((ProcessingInstruction) expr);
        case QuantifiedExpression:
            return exprFor ((QuantifiedExpression) expr);
        case RootExpression:
            return exprFor ((RootExpression) expr);
        case SlashExpression:
            return exprFor ((SlashExpression) expr);
        case TailExpression:
            return exprFor ((TailExpression) expr);
        case UnaryExpression:
            return exprFor ((UnaryExpression) expr);
        case ValueOf:
            return exprFor ((ValueOf) expr);
        //case LocalVariableReference:
        case VariableReference:
            return exprFor ((VariableReference) expr);
        default:
            throw new UnsupportedOperationException("unhandled expression type: " + expr.getClass().getSimpleName() + " in " + expr.toString());
        }
    }
    
    private enum ExprClass {
        AtomicSequenceConverter,
        Atomizer,
        AxisExpression,
        BinaryExpression,
        CastExpression,
        CompareToIntegerConstant,
        ComputedAttribute,
        ComputedElement,
        ContextItemExpression,
        CopyOf,
        Expression,
        FilterExpression,
        FirstItemExpression,
        ForExpression,
        FunctionCall,
        InstanceOfExpression,
        IntegerRangeTest,
        LastItemExpression,
        LetExpression,
        Literal,
        NegateExpression,
        ProcessingInstruction,
        ParentNodeExpression,
        QuantifiedExpression,
        RootExpression,
        SlashExpression,
        TailExpression,
        UnaryExpression,
        VariableReference,
        FLWORExpression,
        Block,
        Choose,
        Comment,
        DocumentInstr,
        FixedAttribute,
        FixedElement,
        GlobalVariable,
        ValueOf
    }

}/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
