package lux.junit;

import static org.junit.Assert.*;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import lux.Compiler;
import lux.Evaluator;
import lux.exception.LuxException;
import lux.xml.ValueType;
import lux.xpath.AbstractExpression;
import lux.xpath.ExpressionVisitorBase;
import lux.xpath.FunCall;
import lux.xpath.LiteralExpression;
import lux.xquery.XQuery;
import net.sf.saxon.expr.sort.CodepointCollator;
import net.sf.saxon.expr.sort.GenericAtomicComparer;
import net.sf.saxon.functions.DeepEqual;
import net.sf.saxon.s9api.Axis;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.SingletonIterator;

import org.apache.commons.lang.StringUtils;
import org.apache.lucene.queryparser.classic.ParseException;

class QueryTestCase {
    
    private final String name;
    private final String query;
    private final QueryTestResult expectedResult;

    QueryTestCase (String name, String query, QueryTestResult expected) {
        this.name = name;
        this.query = query;
        this.expectedResult = expected;
    }

    public void evaluate (Evaluator eval) {
        Compiler compiler = eval.getCompiler();
        try {
        	compiler.compile(query);
        } catch (LuxException e) {
        	if (! expectedResult.isError) {
        		throw e;
        	}
        	if (! StringUtils.isEmpty(expectedResult.errorText)) {
        		assertEquals (expectedResult.errorText, e.getMessage());
        	}
        	return;
        }
    	if (expectedResult.isError) {
    		fail ("expected exception not thrown");
    	}
        XQuery optimizedQuery = compiler.getLastOptimized();
        AbstractExpression ex = optimizedQuery.getBody();
        String expectedOptimized = expectedResult.queryText;
        if (!StringUtils.isEmpty(expectedOptimized)) {
            assertEquals (expectedOptimized, optimizedQuery.toString());
        }
        SearchExtractor extractor = new SearchExtractor();
        ex.accept(extractor);
        List<XdmNode> queries = expectedResult.searchQueries;
        if (queries != null) {
        	assertEquals ("wrong number of queries for " + query, queries.size(), extractor.queries.size());
        	for (int i = 0; i < queries.size(); i++) {
        		AbstractExpression xmlQuery = extractor.queries.get(i).getQuery();
        		if (xmlQuery instanceof LiteralExpression) {
        			continue;
        			// we don't have a way to "unparse" these
        		}
        		XdmNode queryNode = (XdmNode) eval.build(new StringReader(xmlQuery.toString()), "query").axisIterator(Axis.CHILD).next();
        		// These are node trees, but let's use string comparison to display something meaningful to the user
        		try {
					if (!compareNodes (eval, queries.get(i), queryNode)) {
						assertEquals (queries.get(i).toString(), queryNode.toString().trim());
					}
				} catch (XPathException e) {
					fail (e.getMessage());
				}
        	}
        	if (queries.size() > 0) {
        		if (expectedResult.resultType != null) {
        			assertSame (expectedResult.resultType, extractor.queries.get(0).getResultType());
        		}
        	}
        }

        // sort keys
        if (expectedResult.orderBy != null) {
            String[] sortFields = expectedResult.orderBy.split(",");
            SortExtractor sortExtractor = new SortExtractor();
            ex.accept(sortExtractor);
            assertEquals ("incorrect number of sort fields:", sortFields.length, sortExtractor.sorts.size());
            for (int i = 0; i < sortFields.length; i++) {
                assertEquals (sortFields[i], sortExtractor.sorts.get(i));
            }
        }
    }

    protected boolean compareNodes (Evaluator eval, XdmNode node1, XdmNode node2) throws XPathException {
        return DeepEqual.deepEquals
            (SingletonIterator.makeIterator(node1.getUnderlyingNode()),
             SingletonIterator.makeIterator(node2.getUnderlyingNode()),
             new GenericAtomicComparer
             (CodepointCollator.getInstance(),
              eval.getCompiler().getProcessor().getUnderlyingConfiguration().getConversionContext()),
             eval.getCompiler().getProcessor().getUnderlyingConfiguration(),
             DeepEqual.INCLUDE_PREFIXES |
             DeepEqual.EXCLUDE_WHITESPACE_TEXT_NODES |
             DeepEqual.COMPARE_STRING_VALUES
             );
    }

    public String getName() {
		return name;
	}

	static class MockQuery {
        private final AbstractExpression queryNode;
        private final ValueType resultType;
        
        MockQuery (AbstractExpression queryNode, ValueType valueType) {
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

    static class SearchExtractor extends ExpressionVisitorBase {
        ArrayList<MockQuery> queries = new ArrayList<MockQuery>();
        
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
    
    static class SortExtractor extends ExpressionVisitorBase {
        ArrayList<String> sorts = new ArrayList<String>();
        
        @Override
        public FunCall visit (FunCall funcall) {
            if (funcall.getName().equals (FunCall.LUX_SEARCH)) {
                if (funcall.getSubs().length >= 2) {
                    AbstractExpression sortArg = funcall.getSubs()[1];
                    String s = ((LiteralExpression)sortArg).getValue().toString();
                    sorts.add(s);
                }
            }
            return funcall;
        }
        
    }
}
