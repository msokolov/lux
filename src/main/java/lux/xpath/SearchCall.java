package lux.xpath;

import java.util.ArrayList;

import lux.compiler.XPathQuery;
import lux.index.IndexConfiguration;
import lux.query.BooleanPQuery;
import lux.xml.ValueType;
import lux.xquery.ElementConstructor;

import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.SortField;

/**
 * A special search function call; this holds a query that is used to accumulate constraints
 * while optimizing.  The arguments are generated from the supplied query
 */
public class SearchCall extends FunCall {

    private AbstractExpression queryArg;
    private XPathQuery query; // for facts and sortFields only
    private boolean fnCollection;
    
    public SearchCall(XPathQuery query, IndexConfiguration config) {
        this (query.getParseableQuery().toXmlNode(config.getDefaultFieldName()), query.getFacts(), query.getResultType(), query.getSortFields());
    }

    public SearchCall(AbstractExpression abstractExpression, ValueType returnType, SortField[] sortFields) {
        this (abstractExpression, XPathQuery.MINIMAL, returnType, sortFields);
    }
    
    public SearchCall(AbstractExpression queryArg, long facts, ValueType resultType, SortField[] sortFields) {
        super(FunCall.LUX_SEARCH, resultType);
        this.queryArg = queryArg;
        fnCollection = false;
        query = XPathQuery.getQuery(null, facts, resultType, null, sortFields);
        generateArguments();
    }


    public void combineQuery(XPathQuery query, IndexConfiguration config) {
        ElementConstructor additional = query.getParseableQuery().toXmlNode(config.getDefaultFieldName());
        if (! additional.getName().getLocalPart().equals("MatchAllDocsQuery")) {
            if (queryArg.getType() == Type.ELEMENT) {
                ElementConstructor addClause = new ElementConstructor(BooleanPQuery.CLAUSE_QNAME, additional, BooleanPQuery.MUST_OCCUR_ATT);
                ElementConstructor thisClause = new ElementConstructor(BooleanPQuery.CLAUSE_QNAME, queryArg, BooleanPQuery.MUST_OCCUR_ATT);
                ElementConstructor combined = new ElementConstructor(BooleanPQuery.BOOLEAN_QUERY_QNAME, new Sequence (thisClause, addClause));
                queryArg = combined;
            }
        }
        // TODO: combine optimizer constraints with user-defined (string) queries 
        this.query = this.query.combineBooleanQueries(Occur.MUST, query, Occur.MUST, this.query.getResultType(), config);
        generateArguments();
    }
    
    private void generateArguments () {
        ArrayList<AbstractExpression> args = new ArrayList<AbstractExpression>();
        args.add (queryArg);
        args.add (new LiteralExpression(query.getFacts()));
        SortField[] sortFields = query.getSortFields();
        if (sortFields != null) {
            args.add(new LiteralExpression (createSortString(sortFields)));
        }
        subs = args.toArray(new AbstractExpression[args.size()]);
    }

    /**
     * create an string describing sort options to be passed as an argument search
     * @return
     */
    private String createSortString (SortField[] sort) {
        StringBuilder buf = new StringBuilder();
        if (sort != null) {
            for (SortField sortField : sort) {
                buf.append (sortField.getField());
                if (sortField.getReverse()) {
                    buf.append (" descending");
                }
                buf.append (",");
            }
            if (buf.length() > 0) {
                buf.setLength(buf.length() - 1);
            }
        }
        return buf.toString();
    }
    
    /*
    @Override
    public void toString (StringBuilder buf)  {
        if (fnCollection) {
            // FIXME: URL (or URI?) escaping here:
            FunCall collection = new FunCall (FunCall.FN_COLLECTION, query.getResultType(), 
                new LiteralExpression ("lux:" + 
                        query.toQueryString(defaultField, indexConfig) +
                        "?sort=" + createSortString(query.getSortFields())));
            collection.toString(buf);
        } else {
            super.toString(buf);
        }
    }
    */

    /**
     * 
     * @return whether this function call will be represented by fn:collection("lux:" + query)
     */
    public boolean isFnCollection() {
        return fnCollection;
    }

    public void setFnCollection(boolean isFnCollection) {
        this.fnCollection = isFnCollection;
    }

}
