package lux.query.parser;

import lux.index.FieldName;
import lux.index.IndexConfiguration;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.queryParser.ext.ExtendableQueryParser;
import org.apache.lucene.queryParser.ext.Extensions;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.spans.SpanNearQuery;
import org.apache.lucene.search.spans.SpanOrQuery;
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.search.spans.SpanTermQuery;
import org.apache.lucene.util.Version;

/**
 * A Lucene query parser extension that supports query terms of the form:
 * 
 * <blockquote><code>[node]<[nodeName]:[term]</code></blockquote>
 * 
 * <p>In which nodeName is either empty, an unqualified element name, a prefixed element name
 * (ie a QName), or a QName prefixed with "@", indicating an attribute. nodeName is optional:
 * if it is not present, a full text query of the entire document is indicated.  The "node"
 * prefix is also optional. Concrete examples:
 * </p>
 * 
 * <pre>
 *  node<:"Alas, poor Yorick"
 *  node<title:Hamlet
 *  node<@id:s12340
 *  node<@xml:id:x2345
 *  node<math:equation:3.14159
 *  
 *  or, equivalently:
 *  
 *  <:"Alas, poor Yorick"
 *  <title:Hamlet
 *  <@id:s12340
 *  <@xml:id:x2345
 *  <math:equation:3.14159
 * </pre>
 * 
 * <p>TODO: supply a facility for looking up namespace prefixes (such as <code>math</code> in the last example).</p>
 *
 * AND
 * 
 * Boolean queries containing a marker term: lux_within:{slop} or lux_near:{slop} will be
 * replaced by a SpanNearQuery, if the term is required, or a SpanOrQuery, otherwise.  Note
 * that all BooleanQueries nested inside a marked query must also themselves be marked (as a Span).
 */
public class LuxQueryParser extends ExtendableQueryParser {
    
    public LuxQueryParser(Version matchVersion, String f, Analyzer a, Extensions ext) {
        super(matchVersion, f, a, ext);
    }

    public static LuxQueryParser makeLuxQueryParser(IndexConfiguration config) {
        return new LuxQueryParser(IndexConfiguration.LUCENE_VERSION, 
                config.getFieldName(FieldName.XML_TEXT), 
                config.getFieldAnalyzers(), 
                new NodeExtensions (new NodeParser(
                        config.getFieldName(FieldName.XML_TEXT),
                        config.getFieldName(FieldName.ELEMENT_TEXT),
                        config.getFieldName(FieldName.ATTRIBUTE_TEXT),
                        config.getField(FieldName.ELEMENT_TEXT).getAnalyzer())));
    }
    
    @Override
    public Query parse (String queryString) throws ParseException {
        Query q = super.parse(queryString);        
        return maybeConvert (q);
    }

    private Query maybeConvert (Query q) {
        if (! (q instanceof BooleanQuery)) {
            return q;
        }
        BooleanQuery bq = (BooleanQuery) q;
        Query q1 = bq.getClauses()[0].getQuery();
        if (q1 instanceof TermQuery) {
            Term term = ((TermQuery) q1).getTerm();
            if (term.field().equals("lux_within") || term.field().equals("lux_near")) {
                return toSpanQuery (bq);
            }
        }
        // else (we didn't convert this query, but maybe some nested queries is marked as a span) :)
        convertNestedSpans (bq);
        return bq;
    }

    private void convertNestedSpans (BooleanQuery bq) {
        for (BooleanClause clause : bq.clauses()) {
            Query q = clause.getQuery();
            Query converted = maybeConvert (q);
            if (converted != q) {
                clause.setQuery (converted);
            }
        }
    }

    /**
       Converts BooleanQuery to SpanNearQuery, SpanOrQuery.
       Converts TermQuery to SpanTermQuery.
       It is an error to pass other Queries to this method.
    */
    private SpanQuery toSpanQuery(Query q) {
        if (q instanceof BooleanQuery) {
            BooleanQuery bq = (BooleanQuery) q;
            BooleanClause[] booleanClauses = bq.getClauses();
            if (booleanClauses.length == 0) {
                return new SpanOrQuery();
            }
            Query q1 = booleanClauses[0].getQuery();
            if (q1 instanceof TermQuery) {
                Term term = ((TermQuery) q1).getTerm();
                boolean inOrder;
                int slop;
                int start;
                if (term.field().equals("lux_within")) {
                    inOrder = true;
                    slop = Integer.valueOf(term.text());
                    start = 1;
                }
                else if (term.field().equals("lux_near")) {
                    inOrder = false;
                    slop = Integer.valueOf(term.text());
                    start = 1;
                }
                else {
                    inOrder = true;
                    slop = 0;
                    start = 0;
                }
                SpanQuery [] clauses = convertClauses (booleanClauses, start);
                if (clauses.length == 1) {
                    return clauses[0];
                }
                if (booleanClauses[0].isRequired()) {
                    return new SpanNearQuery(clauses, slop, inOrder);
                }
                return new SpanOrQuery(clauses);
            }
        }
        if (q instanceof TermQuery) {
            return new SpanTermQuery(((TermQuery)q).getTerm());
        }
        throw new IllegalStateException("Can't convert query <" + q + "> of type " + q.getClass().getName() + " to a SpanQuery");
    }

    private SpanQuery[] convertClauses(BooleanClause[] clauses, int start) {
        SpanQuery[] spans = new SpanQuery[clauses.length - start];
        for (int i = start; i < clauses.length; i++) {
            Query subquery = clauses[i].getQuery();
            spans[i - start] = (SpanQuery) toSpanQuery(subquery);
        }
        return spans;
    }

    public final static String escapeQParser (String s) {
        if (s.indexOf(' ') >= 0) {
            return '"' + s.replaceAll("\"", "\\\"") + '"';
        }
        return QueryParser.escape (s);
    }
}
