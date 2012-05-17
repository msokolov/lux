/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */

package lux.functions;

import java.io.IOException;

import lux.XPathQuery;
import lux.index.XmlField;
import lux.index.XmlIndexer;
import lux.saxon.ResultIterator;
import lux.saxon.Saxon;
import lux.xpath.FunCall;
import net.sf.saxon.expr.StaticProperty;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.lib.ExtensionFunctionCall;
import net.sf.saxon.lib.ExtensionFunctionDefinition;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.pattern.NodeKindTest;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.IntegerValue;
import net.sf.saxon.value.SequenceType;

import org.apache.lucene.analysis.WhitespaceAnalyzer;
import org.apache.lucene.queryParser.surround.parser.ParseException;
import org.apache.lucene.queryParser.surround.parser.QueryParser;
import org.apache.lucene.queryParser.surround.query.BasicQueryFactory;
import org.apache.lucene.queryParser.surround.query.SrndQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.util.Version;

/**
 * Executes a Lucene search query and returns documents.
 * 
 */
public class LuxSearch extends ExtensionFunctionDefinition {
    protected final Saxon saxon;
    
    public LuxSearch (Saxon saxon) {
        this.saxon = saxon;
    }
    
    private XPathQuery makeXPathQuery(String queryString, long facts) throws ParseException, org.apache.lucene.queryParser.ParseException {
        Query query;
        XmlIndexer indexer = saxon.getContext().getIndexer();
        if (indexer.isOption(XmlIndexer.INDEX_PATHS)) {
            SrndQuery q = getSurroundQueryParser().parse2(queryString);
            query = q.makeLuceneQueryFieldNoBoost(XmlField.PATH.getName(), getQueryFactory());
        } else {
            query = getQueryParser().parse(queryString);
        }
        return XPathQuery.getQuery(query, facts, indexer.getOptions());
    }
    
    private BasicQueryFactory queryFactory;
    protected BasicQueryFactory getQueryFactory () {
        if (queryFactory == null) {
            queryFactory = new BasicQueryFactory();
        }
        return queryFactory;
    }
    
    private QueryParser surroundQueryParser;
    protected QueryParser getSurroundQueryParser () {
        if (surroundQueryParser == null) {
            surroundQueryParser = new QueryParser ();//Version.LUCENE_34, null, new WhitespaceAnalyzer(Version.LUCENE_34));
        }
        return surroundQueryParser;
    }
    
    private org.apache.lucene.queryParser.QueryParser queryParser;
    private org.apache.lucene.queryParser.QueryParser getQueryParser () {
        if (queryParser == null) {
            queryParser = new org.apache.lucene.queryParser.QueryParser (Version.LUCENE_34, null, new WhitespaceAnalyzer(Version.LUCENE_34));
        }
        return queryParser;
    }
    
    @Override
    public int getMinimumNumberOfArguments() {
        return 1;
    }
    
    @Override
    public int getMaximumNumberOfArguments() {
        return 2;
    }
    
    @Override
    public SequenceType[] getArgumentTypes() {
        return new SequenceType[] { 
                SequenceType.SINGLE_STRING,
                SequenceType.OPTIONAL_INTEGER
                };
    }
    
    @Override
    public StructuredQName getFunctionQName() {
        return new StructuredQName("lux", FunCall.LUX_NAMESPACE, "search");
    }

    @Override
    public SequenceType getResultType(SequenceType[] suppliedArgumentTypes) {
        return SequenceType.makeSequenceType(NodeKindTest.DOCUMENT, StaticProperty.ALLOWS_ZERO_OR_MORE);
    }

    @Override
    public ExtensionFunctionCall makeCallExpression() {
        return new LuxSearchCall ();
    }
    

    /**
     * Iterate over the search results
     *
     * @param query the query to execute
     * @return an iterator with the results of executing the query and applying the
     * expression to its result.
     * @throws XPathException
     */        
    @SuppressWarnings("rawtypes")
    public SequenceIterator<Item> iterate(final XPathQuery query) throws XPathException {        
        try {
            return new ResultIterator (saxon, query);
        } catch (IOException e) {
            throw new XPathException (e);
        }
    }
    
    class LuxSearchCall extends ExtensionFunctionCall {
        
        private XPathQuery query;
        
        @SuppressWarnings("rawtypes") @Override
        public SequenceIterator<? extends Item> call(SequenceIterator[] arguments, XPathContext context) throws XPathException {
            if (arguments.length == 0 || arguments.length > 2) {
                throw new XPathException ("wrong number of arguments");
            }
            Item arg = arguments[0].next();        
            if (arg == null) {
                throw new XPathException ("search arg is null");
            }
            String queryString = arg.getStringValue();
            long facts=0;
            if (arguments.length >= 2) {
                IntegerValue num  = (IntegerValue) arguments[1].next();
                facts = num.longValue();
            }
            try {
                query = makeXPathQuery(queryString, facts);
            } catch (ParseException e) {
                throw new XPathException ("Failed to parse surround query " + queryString, e);
            } catch (org.apache.lucene.queryParser.ParseException e) {
                throw new XPathException ("Failed to parse lucene query " + queryString, e);
            }
            System.out.println ("executing xpath query: " + query);
            return iterate (query);
        }
        
    }
}
