package lux.xpath;


public class BinaryOperation extends AbstractExpression {
    
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

    public void accept(Visitor<AbstractExpression> visitor) {
        visitor.visit(this);
    }
}
