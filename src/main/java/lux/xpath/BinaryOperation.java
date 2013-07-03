/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */

package lux.xpath;

import lux.xml.ValueType;


public class BinaryOperation extends AbstractExpression {
    
    private static final int MLT = 1;
    private static final int MEQ = 2;
    private static final int MGT = 4;
    private static final int MAND = 8;
    private static final int MOR = 16; // OR >= (AND,OR)
    private static final int MINTERSECT = 32;
    private static final int MUNION = 64;
    private static final int MEXCEPT = 128;
    
    private final Operator operator;
    
    public enum Operator {
        
        // boolean operators
        AND("and", ValueType.BOOLEAN, 5, MAND),
        OR("or", ValueType.BOOLEAN, 4, MAND | MOR), 
        
        // set operators
        INTERSECT("intersect", ValueType.VALUE, 11, MINTERSECT),
        EXCEPT("except", ValueType.VALUE, 11, MEXCEPT),
        UNION("|", ValueType.VALUE, 10, MUNION | MEXCEPT | MINTERSECT), 

        // arithmetic operators
        ADD("+", ValueType.ATOMIC, 8),
        SUB("-", ValueType.ATOMIC, 8),
        MUL("*", ValueType.ATOMIC, 9),
        DIV("div", ValueType.ATOMIC, 9),
        IDIV("idiv", ValueType.ATOMIC, 9),
        MOD("mod", ValueType.ATOMIC, 9),

        // general comparisons
        EQUALS("=", ValueType.BOOLEAN, 6, MEQ),
        NE("!=", ValueType.BOOLEAN, 6, MGT | MLT), 
        LT("<", ValueType.BOOLEAN, 6, MLT), 
        LE("<=", ValueType.BOOLEAN, 6, MLT | MEQ), 
        GT(">", ValueType.BOOLEAN, 6, MGT),
        GE(">=", ValueType.BOOLEAN, 6, MGT | MEQ), 

        // atomic comparisons
        AEQ("eq", ValueType.BOOLEAN, 6, MEQ),
        ANE("ne", ValueType.BOOLEAN, 6, MLT | MGT),
        ALT("lt", ValueType.BOOLEAN, 6, MLT), 
        ALE("le", ValueType.BOOLEAN, 6, MLT | MEQ),
        AGT("gt", ValueType.BOOLEAN, 6, MGT),
        AGE("ge", ValueType.BOOLEAN, 6, MGT | MEQ),

        // node operators
        IS("is", ValueType.BOOLEAN, 6),
        BEFORE("<<", ValueType.BOOLEAN, 6),
        AFTER(">>", ValueType.BOOLEAN, 6),
        TO("to", ValueType.ATOMIC, 7);

        private String token;
        private ValueType resultType;
        private int precedence;
        private int rangeMask;
        
        Operator (String token, ValueType resultType, int precedence) {
            this (token, resultType, precedence, 0);
        }

        Operator (String token, ValueType resultType, int precedence, int rangeMask) {
            this.token = token;
            this.resultType = resultType;
            this.precedence = precedence;
        }
        
        @Override
        public String toString () {
            return token;
        }
        
        public ValueType getResultType () {
            return resultType;
        }
        
        public int getPrecedence() {
            return precedence;
        }
        
    };
    
    public BinaryOperation (AbstractExpression op1, Operator operator, AbstractExpression op2) {
        super (Type.BINARY_OPERATION);
        setSubs (op1, op2);
        this.operator = operator;
    }
    
    @Override
    public void toString (StringBuilder buf) {
        appendSub(buf, subs[0]);
        buf.append(' ').append(operator).append(' ');
        appendSub(buf, subs[1]);
    }

    public AbstractExpression getOperand1() {
        return subs[0];
    }

    public AbstractExpression getOperand2() {
        return subs[1];
    }
    
    public Operator getOperator () {
        return operator;
    }

    @Override
    public AbstractExpression accept(ExpressionVisitor visitor) {
        super.acceptSubs(visitor);
        return visitor.visit(this);
    }
    
    @Override
    public boolean isDocumentOrdered () {
        return operator.getResultType().isNode && super.isDocumentOrdered();
    }

    @Override
    public int getPrecedence () {
        return operator.precedence;
    }

    @Override
    protected boolean propEquals (AbstractExpression other) {
        return operator == ((BinaryOperation)other).operator;
    }

    @Override
	public boolean propGreaterEqual (AbstractExpression other) {
        Operator op2 = ((BinaryOperation)other).operator;
        // GE >= AGE, is AGE >= GE?
        return operator == op2 ||
            (op2.rangeMask != 0 && (operator.rangeMask & op2.rangeMask) == op2.rangeMask);
    }
    
    @Override
    public int equivHash () {
    	return 13 + operator.ordinal();
    }
    
    @Override
    public boolean isRestrictive () {
        return (operator == Operator.AND || operator == Operator.INTERSECT);
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
