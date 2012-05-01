package lux.xpath;

import lux.api.ValueType;


public class BinaryOperation extends AbstractExpression {
    
    private final Operator operator;
    
    public enum Operator {
        // boolean operators
        AND("and", ValueType.BOOLEAN), OR("or", ValueType.BOOLEAN), 
        // set operators
        INTERSECT("intersect", ValueType.VALUE), EXCEPT("except", ValueType.VALUE), UNION("|", ValueType.VALUE), 
        // arithmetic operators
        ADD("+", ValueType.ATOMIC), SUB("-", ValueType.ATOMIC), MUL("*", ValueType.ATOMIC), DIV("div", ValueType.ATOMIC), IDIV("idiv", ValueType.ATOMIC), MOD("mod", ValueType.ATOMIC),
        // general comparisons
        EQUALS("=", ValueType.BOOLEAN), NE("!=", ValueType.BOOLEAN), LT("<", ValueType.BOOLEAN), GT(">", ValueType.BOOLEAN), LE("<=", ValueType.BOOLEAN), GE(">=", ValueType.BOOLEAN), 
        // atomic comparisons
        AEQ("eq", ValueType.BOOLEAN), ANE("ne", ValueType.BOOLEAN), ALT("lt", ValueType.BOOLEAN), ALE("le", ValueType.BOOLEAN), AGT("gt", ValueType.BOOLEAN), AGE("ge", ValueType.BOOLEAN),
        // node operators
        IS("is", ValueType.BOOLEAN), BEFORE("<<", ValueType.BOOLEAN), AFTER(">>", ValueType.BOOLEAN);
        
        private String token;
        private ValueType resultType;
        
        Operator (String token, ValueType resultType) {
            this.token = token;
            this.resultType = resultType;
        }
        
        public String toString () {
            return token;
        }
        
        public ValueType getResultType () {
            return resultType;
        }
    };
    
    public BinaryOperation (AbstractExpression op1, Operator operator, AbstractExpression op2) {
        super (Type.Binary);
        subs = new AbstractExpression[] { op1, op2 };
        this.operator = operator;
    }
    
    public String toString () {
        return '(' + subs[0].toString() + ' ' + operator.toString() + ' ' + subs[1].toString() + ')';
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
}
