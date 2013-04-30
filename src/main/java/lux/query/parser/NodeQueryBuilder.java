package lux.query.parser;

import java.io.IOException;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;

import lux.index.analysis.WildcardAnalyzer;

import org.apache.commons.lang.StringUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.WildcardQuery;
import org.apache.lucene.xmlparser.DOMUtils;
import org.apache.lucene.xmlparser.ParserException;
import org.apache.lucene.xmlparser.QueryBuilder;
import org.w3c.dom.Element;

public class NodeQueryBuilder implements QueryBuilder {

    private final Map<String,String> nsMap;
    private final Analyzer analyzer;
    private final Analyzer wildcardAnalyzer;
    private final boolean namespaceAware;
    
    public NodeQueryBuilder(Analyzer analyzer, boolean namespaceAware) {
        this.analyzer = analyzer;
        this.wildcardAnalyzer = new WildcardAnalyzer();
        nsMap = new HashMap<String, String>();
        this.namespaceAware = namespaceAware;
    }

    @Override
    public Query getQuery(Element e) throws ParserException {
        String fieldName=DOMUtils.getAttributeWithInheritanceOrFail(e,"fieldName");
        String qName=DOMUtils.getAttributeWithInheritance(e,"qName");
        String text=DOMUtils.getNonBlankTextOrFail(e);
        float boost = DOMUtils.getAttribute (e, "boost", 1.0f);
        return parseQueryTerm(fieldName, qName, text, boost);
    }
    
    void bindNamespacePrefix (String prefix, String namespaceURI) {
        if (StringUtils.isEmpty(namespaceURI)) {
            nsMap.remove(prefix);
        } else {
            nsMap.put(prefix, namespaceURI);
        }
    }
    
    void clearNamespaces () {
        nsMap.clear();
    }
    
    Query parseQueryTerm(final String fieldName, final String qName, final String text, final float boost) throws ParserException {
        StringBuilder termText = new StringBuilder();
        boolean isWild = false;
        if (StringUtils.isNotEmpty(qName)) {
            if (qName.matches("[^{:]+:.*")) {
                String[] parts = qName.split(":", 2);
                String prefix = parts[0];
                String name = parts[1];
                if ("*".equals(prefix)) {
                    termText.append(name).append("*:");
                    isWild = true;
                } else {
                    String namespaceURI = nsMap.get(prefix);
                    if (namespaceURI == null) {
                        if (namespaceAware) {
                            throw new ParserException ("unbound namespace prefix '" + prefix + "'");
                        }
                        termText.append(qName).append(':');
                    } else {
                        termText.append(name).append('{').append(namespaceURI).append("}:");
                    }
                }
            } 
            else {
                termText.append(qName).append(':');
            }
        }
        int prefixLength = termText.length();

        Analyzer termAnalyzer;
        if ((text.indexOf('*') >= 0 || text.indexOf('?') >= 0)) {
            if (text.matches(".*\\s.*")) {
                // warning? we just ignore the "wildcards" if this is a phrase query
                termAnalyzer = analyzer;
            } else {
                isWild = true;
                termAnalyzer = wildcardAnalyzer;
            }
        } else {
            termAnalyzer = analyzer;
        }
        // We assume that we are never embedded in a SpanQuery of any sort, which is valid
        // because the query syntax doesn't provide any means of specifying Spans.
        PhraseQuery pq=new PhraseQuery();
        Term term = null;
        try {
            TokenStream ts = termAnalyzer.tokenStream(fieldName, new StringReader(text));
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
                    term = new Term (fieldName, termText.toString()); 
                    pq.add(term);
                }
            }
            ts.end();
            ts.close();
        } 
        catch (IOException ioe) { }
        Query q;
        if (pq.getTerms().length > 1) {
            if (isWild) {
                throw new ParserException("wildcarded namespace prefix cannot be combined with a multi-word phrase");
            }
            q = pq;
        } else {
            if (term == null) {
                // if the analyzer threw all the text away, or it was empty to begin with
                q = new WildcardQuery (new Term(fieldName, termText.toString() + "*"));
            }
            else if (isWild) {
                q = new WildcardQuery(term);
            } else {
                q = new TermQuery (term);
            }
        }
        q.setBoost(boost);
        return q;
    }

}
