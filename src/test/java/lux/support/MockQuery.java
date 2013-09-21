package lux.support;

import lux.xml.ValueType;
import lux.xpath.AbstractExpression;

public class MockQuery {
    private final AbstractExpression queryNode;
    private final ValueType resultType;
    
    public MockQuery (AbstractExpression queryNode, ValueType valueType) {
        this.resultType = valueType;
        this.queryNode= queryNode;
    }

    public AbstractExpression getQuery() {
        return queryNode;
    }
    
    public ValueType getResultType () {
    	return resultType;
    }
    
}