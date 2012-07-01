package lux.query.parser;

import java.io.IOException;
import java.io.StringReader;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.xmlparser.DOMUtils;
import org.apache.lucene.xmlparser.ParserException;
import org.apache.lucene.xmlparser.QueryBuilder;
import org.w3c.dom.Element;

public class QNameQueryBuilder implements QueryBuilder {

    private Analyzer analyzer;
    
    public QNameQueryBuilder(Analyzer analyzer) {
        this.analyzer = analyzer;
    }

    public Query getQuery(Element e) throws ParserException {
        String fieldName=DOMUtils.getAttributeWithInheritanceOrFail(e,"fieldName");
        String qName=DOMUtils.getAttributeWithInheritance(e,"qName");
        StringBuilder termText = new StringBuilder();
        if (qName != null) {
            termText.append(qName).append(':');
        }
        int prefixLength = termText.length();
        // TODO: see if we are embedded in a SpanQuery of any sort.  If so,
        // generate a SpanNearQuery here, not a PhraseQuery
        String text=DOMUtils.getNonBlankTextOrFail(e);        
        PhraseQuery pq=new PhraseQuery();
        Term term = null;
        try {
            TokenStream ts = analyzer.reusableTokenStream(fieldName, new StringReader(text));
            CharTermAttribute termAtt = ts.addAttribute(CharTermAttribute.class);
            ts.reset();
            if (ts.incrementToken()) {
                termText.append(termAtt.buffer(), 0, termAtt.length());
                term = new Term(fieldName, termText.toString());
                pq.add(term);
                while (ts.incrementToken()) {
                    termText.setLength(prefixLength);
                    termText.append(termAtt.buffer(), 0, termAtt.length());
                    // create from previous to save fieldName.intern overhead
                    term = term.createTerm(termText.toString()); 
                    pq.add(term);
                }
            }
            ts.end();
            ts.close();
        } 
        catch (IOException ioe) { }
        Query q;
        if (pq.getTerms().length > 1) {
            q = pq;
        } else {
            q = new TermQuery (term);
        }
        q.setBoost(DOMUtils.getAttribute (e, "boost", 1.0f));
        return q;
    }

}
