/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */

package lux.gen;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Random;

import lux.api.LuxException;
import lux.saxon.Saxon;

import net.sf.saxon.expr.*;
import net.sf.saxon.expr.parser.Token;
//import net.sf.saxon.functions.*;
import net.sf.saxon.om.Axis;
import net.sf.saxon.om.NamePool;
import net.sf.saxon.pattern.NameTest;
import net.sf.saxon.pattern.NodeKindTest;
import net.sf.saxon.pattern.NodeTest;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.Value;

/*
 * An XPath/XQuery expression generator

 * XPath-only for now; leave out any casts
 */
public class ExprGen {

    private final Random random;
    private final String[] terms;
    private final String[] tags;
    private final Saxon saxon;
    // limit on the number of levels in the generated expression trees
    private final int depth;
    // limit on the number of different terms and tags used to generate expressions
    private final int breadth;

    static ExprTemplate [] templates  = new ExprTemplate [] {
        new ExprTemplate (AndExpression.class, 1, ArgType.EXPR, ArgType.EXPR),
        new ExprTemplate (ArithmeticExpression.class, 1, ArgType.EXPR, ArgType.ints (new int[] { Token.PLUS, Token.MINUS, Token.MULT, Token.DIV, Token.IDIV, Token.MOD}), ArgType.EXPR),
        new ExprTemplate (Atomizer.class, 1, ArgType.EXPR),
        // new ExprTemplate (AxisExpression.class, 1, ArgType.INT.max(12), ArgType.NODE_TEST),
        // new ExprTemplate (CompareToIntegerConstant.class, 1, ArgType.EXPR, ArgType.ints(new int[] { Token.FEQ, Token.FNE, Token.FGE, Token.FGT, Token.FLE, Token.FLT }), new ArgType (ValueType.LONG, 100)),
        new ExprTemplate (ContextItemExpression.class, 1),
        new ExprTemplate (FilterExpression.class, 1, ArgType.EXPR, ArgType.expr (3)),
        new ExprTemplate (FilterExpression.class, 1, ArgType.EXPR, ArgType.EXPR),
        new ExprTemplate (GeneralComparison.class, 1, ArgType.EXPR, ArgType.ints (new int[] {Token.EQUALS, Token.NE, Token.LT, Token.GT, Token.LE, Token.GE }), ArgType.EXPR),
        new ExprTemplate (IdentityComparison.class, 1, ArgType.NODE, ArgType.ints (new int[] { Token.IS, Token.PRECEDES, Token.FOLLOWS }), ArgType.NODE),
        //new ExprTemplate (IntegerRangeTest.class, 1, ArgType.EXPR, ArgType.expr (3), ArgType.expr (100)),
        new ExprTemplate (LastItemExpression.class, 1, ArgType.EXPR),
        // we should have some node literals
        // and an empty literal
        //new ExprTemplate (StringLiteral.class, 1, ArgType.STRING_VALUE),
        //new ExprTemplate (Literal.class, 1, new ArgType (ValueType.INT_VALUE, 10)),
        new ExprTemplate (ParentNodeExpression.class, 1),
        new ExprTemplate (RangeExpression.class, 1, ArgType.expr (3), ArgType.ints (new int[] { Token.TO }), ArgType.expr (10)),
        new ExprTemplate (RootExpression.class, 1),
        new ExprTemplate (SlashExpression.class, 1, ArgType.EXPR, ArgType.EXPR),
        new ExprTemplate (OrExpression.class, 1, ArgType.EXPR, ArgType.EXPR),
        new ExprTemplate (ValueComparison.class, 1, ArgType.EXPR,ArgType.ints (new int[] { Token.FEQ, Token.FNE, Token.FGE, Token.FGT, Token.FLE, Token.FLT }), ArgType.EXPR),
        new ExprTemplate (VennExpression.class, 1, ArgType.EXPR, ArgType.ints (new int[] { Token.UNION, Token.INTERSECT, Token.EXCEPT }), ArgType.EXPR)
        
        // FUNCTIONS!!
    };
        
    static int weightedTemplates[];
    static {
        int totalWeight = 0;
        for (ExprTemplate t : templates) {
            totalWeight += t.weight;
        }
        weightedTemplates = new int [totalWeight];
        int k = 0;
        for (int i = 0; i < templates.length; i++) {
            for (int j = 0; j < templates[i].weight; j++) {
                weightedTemplates[k++] = i;
            }
        }
    }
    
    public ExprGen (String[] terms, String[] tags, Random random, int depth, int breadth) {
        this (terms, tags, random, new Saxon(), depth, breadth);
    }
    
    public ExprGen (String[] terms, String[] tags) {
        this (terms, tags, new Random(), 5, 5);
    }

    public ExprGen(String[] terms, String[] tags, Random random, Saxon saxon, int depth, int breadth) {
        this.terms = terms;
        this.tags = tags;
        this.random = random;
        this.depth = depth;
        this.breadth = breadth;
        this.saxon = saxon;
    }

    public Expression next () {
        return next(depth);
    }
    
    Expression next (int maxdepth) {
        int i = random.nextInt(weightedTemplates.length);
        ExprTemplate template = templates[weightedTemplates[i]];
        try {
            return createRandomExpression(template, maxdepth-1);
        } catch (Exception e) {
            throw new RuntimeException (e);
        }
    }

    public Expression genRandomStringLiteral() {
        String s = terms[random.nextInt(terms.length)];
        return new StringLiteral (s);
    }

    public AxisExpression[] genAxisExpressions(byte axisMask) {
        final int axisCount = countBits (axisMask);
        AxisExpression [] axes = new AxisExpression [axisCount * (breadth + 2)];
        int n = 0;
        for (byte axis = Axis.ANCESTOR; axis <= Axis.SELF; axis ++) {
            if (((1<<axis) & axisMask) == 0)
                continue;
            for (int j = 0; j < breadth; j++) {
                String tag = tags[random.nextInt(tags.length)];
                NodeTest nodeTest = new NoNamespaceNameTest(Type.ELEMENT, tag, saxon.getConfig().getNamePool());
                axes[n++] = new AxisExpression(axis, nodeTest);
            }
            axes[n++] = new AxisExpression (axis, NodeKindTest.ELEMENT);
            axes[n++] = new AxisExpression (axis, NodeKindTest.TEXT);
        }
        return axes;
    }
    
    private int countBits (byte bits) {
        int n = 0;
        while (bits > 0) {
            if ((bits & 1) == 1)
                ++n;
            bits >>=1 ;
        }
        return n;
    }

    Expression createRandomExpression (ExprTemplate template, int maxdepth) throws SecurityException, NoSuchMethodException, InstantiationException, IllegalAccessException, InvocationTargetException {
        Object[] args = new Object[template.args.length];
        Class<?>[] argTypes = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) {
            ArgType argType = template.args[i];
            argTypes[i] = argType.valueType.aclass;
            switch (template.args[i].valueType) {
            case EXPR:
                if (maxdepth <= 0) {
                    //args[i] = Literal.makeEmptySequence(); 
                    // TODO: randomize axis and type (allow attributes)
                    args[i] = new AxisExpression (Axis.CHILD, new NoNamespaceNameTest (Type.ELEMENT, tags[random.nextInt(tags.length)], 
                            saxon.getConfig().getNamePool()));
                } else {
                    args[i] = next(maxdepth);
                }
                break;
            case INT: case LONG: case BYTE:
                if (argType.max != null) {
                    args[i] = random.nextInt(argType.max + 1);
                } else if (argType.values != null) {
                    int j = random.nextInt(argType.values.length);
                    args[i] = argType.values[j];
                } else {
                    throw new IllegalArgumentException();
                }
                break;
            case INT_VALUE:
                args[i] = new net.sf.saxon.value.Int64Value(random.nextInt(argType.max + 1));
                break;
            case STRING:
                // unused?
                args[i] = "QName";
            case STRING_VALUE:
                args[i] = new net.sf.saxon.value.StringValue(terms[random.nextInt(terms.length)]);
                break;
            }
        }
        Constructor<? extends Expression> ctor = template.eclass.getConstructor (argTypes);
        try {
            return ctor.newInstance(args);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException ("Error constructing expression with " + ctor);             
        }
    }

    public Expression createNumberedExpression(final int nextExpr, final int argsLeft, final ArrayList<Expression> primitives) {
        ExprTemplate template = templates[nextExpr];
        Object[] args = new Object[template.args.length];
        Class<?>[] argTypes = new Class<?>[args.length];
        int argIndex = argsLeft;
        for (int i = 0; i < args.length; i++) {
            ArgType argType = template.args[i];
            argTypes[i] = argType.valueType.aclass;
            
            int valueCount=0; // the number of possible different values
            switch (argType.valueType) {
            case EXPR:
                if (argType.max != null) {
                    valueCount = breadth;
                }
                else {
                    valueCount = primitives.size();
                }
                break;
            case INT: case LONG: case BYTE:
                if (argType.values != null) {
                    valueCount = argType.values.length;
                } else {
                    valueCount = breadth;
                }
            }
            // The index of the value for this argument is held in the least-significant
            // portion of the argIndex
            int iValue = argIndex % valueCount;
            // reduce argIndex so that the index for the next argument will be in its
            // least significant portion
            argIndex /= valueCount;
            switch (argType.valueType) {
            case EXPR:
                args[i] = primitives.get(iValue);
                break;
            case INT: case LONG: case BYTE:
                if (argType.max != null) {
                    args[i] = random.nextInt(argType.max + 1);
                } else if (argType.values != null) {
                    args[i] = argType.values[iValue];
                }
                break;
            }
        }
        Constructor<? extends Expression> ctor;
        try {
            ctor = template.eclass.getConstructor (argTypes);
        } catch (NoSuchMethodException e) {
            throw new LuxException (e);
        }
        try {
            return ctor.newInstance(args);
        } catch (IllegalArgumentException e) {
            throw new LuxException ("Error constructing expression with " + ctor);
        } catch (InstantiationException e) {
            throw new LuxException ("Error constructing expression with " + ctor);
        } catch (IllegalAccessException e) {
            throw new LuxException ("Error constructing expression with " + ctor);
        } catch (InvocationTargetException e) {
            throw new LuxException ("Error constructing expression with " + ctor);
        }
    }
    
    enum ValueType {
        BYTE(byte.class), INT(int.class), LONG(long.class), INT_VALUE(Value.class), 
        STRING(Value.class), STRING_VALUE(net.sf.saxon.value.StringValue.class), 
        EXPR(Expression.class);

        Class<?> aclass;
        
        ValueType (Class<?> cls) {
            aclass = cls;
        }
        
    };
    
    static class ArgType {
        ValueType valueType;
        Integer max;
        int [] values;
                
        static final ArgType EXPR = new ArgType (ValueType.EXPR);
        static final ArgType NODE = new ArgType (ValueType.EXPR);
        static final ArgType STRING = new ArgType (ValueType.STRING);
        static final ArgType STRING_VALUE = new ArgType (ValueType.STRING_VALUE);
        static final ArgType INT = new ArgType (ValueType.INT);
        static final ArgType INT_VALUE = new ArgType (ValueType.INT_VALUE);
        static final ArgType LONG = new ArgType (ValueType.LONG);
        
        static final ArgType expr (int ... values) {
            if (values.length == 0) {
                return EXPR;
            }
            if (values.length == 1) {
                return new ArgType (ValueType.EXPR, values[0]);
            }
            return new ArgType (ValueType.EXPR, values);
        }
        
        static final ArgType ints (int ... values) {
            return new ArgType (ValueType.INT, values);
        }
        
        ArgType (ValueType valueType) {
            this.valueType = valueType;
        }
        
        ArgType (ValueType valueType, int max) {
            this.valueType = valueType;
            this.max = max;
        }
        
        ArgType (ValueType valueType, int [] values) {
            this.valueType = valueType;
            this.values = values;
        }
        
        int minValue () {
            if (values != null) return values[0];
            return 0;
        }
        
        public String toString () {
            switch (valueType) {
            case INT: return "int";
            case STRING: return "string";
            case STRING_VALUE: return "string";
            case EXPR: return (max != null || values != null) ? "int" : "expr";
            case INT_VALUE: return "int";
            case LONG: return "long";
            case BYTE: return "byte";
            }
            return "unknown";
        }
    }

    static class ExprTemplate {

        int weight;
        Class<? extends Expression> eclass;
        ArgType [] args;

        ExprTemplate (Class<? extends Expression> eclass, int weight, ArgType ... args) {
            this.eclass = eclass;
            this.weight = weight;
            this.args = args;
        }
        
        public String toString () {
            StringBuilder buf = new StringBuilder(eclass.getSimpleName());
            buf.append (": [");
            boolean first = true;
            for (ArgType arg : args) {
                if (first) { first = false; } 
                else { buf.append (", "); }
                buf.append(arg);
            }
            buf.append ("]");
            return buf.toString();
        }

        /**
         * @param size the number of child expressions
         * @param breadth the number of distinct values to generate for open-ended integer parameters
         * @return the number of distinct argument combinations given a set of possible child expressions;
         * this will be size ^ (number of expression args) * number of combinations of enumerated args (like axis, type, etc).
         */
        public int computeArgFanout(int size, int breadth) {
            int fanout = 1;
            for (ArgType arg : args) {
                switch (arg.valueType) {
                case EXPR: 
                    if (arg.max != null)
                        fanout *= breadth;
                    else
                        fanout *= size; 
                    break;
                case INT: 
                    if (arg.values != null)
                        fanout *= arg.values.length;
                    else
                        fanout *= breadth;
                    break;
                default:
                    // other cases shouldn't arise?
                    throw new IllegalArgumentException(arg.toString());
                }
            }
            return fanout;
        }
    }
    
    /**
     * Extend Saxon's NameTest in order to provide a toString that generates valid xpath.
     * Only works for elements in no namespace
     *
     */
    class NoNamespaceNameTest extends NameTest {

        public NoNamespaceNameTest(int nodeKind, String localName, NamePool namePool) {
            super(nodeKind, "", localName, namePool);
        }
        
        @Override
        public String toString(NamePool pool) {
            int fingerprint = getFingerprint();
            switch (getPrimitiveType()) {
                case Type.ELEMENT:
                    return "element(" + pool.getLocalName(fingerprint) + ")";
                case Type.ATTRIBUTE:
                    return "attribute(" + pool.getLocalName(fingerprint) + ")";
                case Type.PROCESSING_INSTRUCTION:
                    return "processing-instruction(" + pool.getDisplayName(fingerprint) + ')';
                case Type.NAMESPACE:
                    return "namespace-node(" + pool.getDisplayName(fingerprint) + ')';
            }
            return pool.getDisplayName(fingerprint);
        }
        
    }
    
    public int getTemplateCount() {
        return templates.length;
    }

    public ExprTemplate getTemplate(int i) {        
        return templates[i];
    }

    public int getBreadth() {
        return breadth;
    }

    public Saxon getSaxon() {
        return saxon;
    }

}