/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */

package lux.xpath;

import lux.ExpressionVisitor;
import lux.api.ValueType;


public class BinaryOperation extends AbstractExpression {
    
    private final Operator operator;
    
    public enum Operator {
        // boolean operators
        AND("and", ValueType.BOOLEAN, 5), OR("or", ValueType.BOOLEAN, 4), 
        // set operators
            INTERSECT("intersect", ValueType.VALUE, 11), EXCEPT("except", ValueType.VALUE, 11), UNION("|", ValueType.VALUE, 10), 
        // arithmetic operators
            ADD("+", ValueType.ATOMIC, 8), SUB("-", ValueType.ATOMIC, 8), MUL("*", ValueType.ATOMIC, 9), DIV("div", ValueType.ATOMIC, 9), IDIV("idiv", ValueType.ATOMIC, 9), MOD("mod", ValueType.ATOMIC, 9),
        // general comparisons
            EQUALS("=", ValueType.BOOLEAN, 6), NE("!=", ValueType.BOOLEAN, 6), LT("<", ValueType.BOOLEAN, 6), GT(">", ValueType.BOOLEAN, 6), LE("<=", ValueType.BOOLEAN, 6), GE(">=", ValueType.BOOLEAN, 6), 
        // atomic comparisons
            AEQ("eq", ValueType.BOOLEAN, 6), ANE("ne", ValueType.BOOLEAN, 6), ALT("lt", ValueType.BOOLEAN, 6), ALE("le", ValueType.BOOLEAN, 6), AGT("gt", ValueType.BOOLEAN, 6), AGE("ge", ValueType.BOOLEAN, 6),
        // node operators
            IS("is", ValueType.BOOLEAN, 6), BEFORE("<<", ValueType.BOOLEAN, 6), AFTER(">>", ValueType.BOOLEAN, 6), TO("to", ValueType.ATOMIC, 7);
        
        private String token;
        private ValueType resultType;
        private int precedence;
        
        Operator (String token, ValueType resultType, int precedence) {
            this.token = token;
            this.resultType = resultType;
            this.precedence = precedence;
        }
        
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
        subs = new AbstractExpression[] { op1, op2 };
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
}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
