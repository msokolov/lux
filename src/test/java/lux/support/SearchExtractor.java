package lux.support;

import java.util.ArrayList;
import java.util.List;

import lux.xpath.AbstractExpression;
import lux.xpath.ExpressionVisitorBase;
import lux.xpath.FunCall;

public class SearchExtractor extends ExpressionVisitorBase {
    private ArrayList<MockQuery> queries = new ArrayList<MockQuery>();
    
    public List<MockQuery> getQueries () {
    	return queries;
    }
    
    @Override
    public FunCall visit (FunCall funcall) {
        if (funcall.getName().equals (FunCall.LUX_SEARCH)
                || funcall.getName().equals (FunCall.LUX_COUNT) 
                || funcall.getName().equals (FunCall.LUX_EXISTS)) 
        {
            AbstractExpression queryArg = funcall.getSubs()[0];
            queries.add( new MockQuery (queryArg, funcall.getReturnType()));
        }
        return funcall;
    }
    
}