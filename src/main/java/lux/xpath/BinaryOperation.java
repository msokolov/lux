package lux.xpath;


public class BinaryOperation extends AbstractExpression {
    
    private final AbstractExpression operand1;
    private final AbstractExpression operand2;
    private final Operator operator;
    
    public enum Operator {
        // boolean operators
        AND("and"), OR("or"), 
        // set operators
        INTERSECT("intersect"), EXCEPT("except"), UNION("|"), 
        // arithmetic operators
        ADD("+"), SUB("-"), MUL("*"), DIV("div"), IDIV("idiv"), MOD("mod"),
        // general comparisons
        EQ("="), NE("!="), LT("<"), GT(">"), LE("<="), GE(">="), 
        // atomic comparisons
        AEQ("eq"), ANE("ne"), ALT("lt"), ALE("le"), AGT("gt"), AGE("ge"),
        // node operators
        IS("is"), BEFORE("<<"), AFTER(">>");
        
        private String token;
        
        Operator (String token) {
            this.token = token;
        }
        
        public String toString () {
            return token;
        }
    };
    
    public BinaryOperation (AbstractExpression op1, Operator operator, AbstractExpression op2) {
        super (Type.Binary);
        this.operand1 = op1;
        this.operand2 = op2;
        this.operator = operator;
    }
    
    public String toString () {
        return '(' + operand1.toString() + ' ' + operator.toString() + ' ' + operand2.toString() + ')';
    }
    
    public AbstractExpression getOperand1() {
        return operand1;
    }

    public AbstractExpression getOperand2() {
        return operand2;
    }
    
    public Operator getOperator () {
        return operator;
    }

}
