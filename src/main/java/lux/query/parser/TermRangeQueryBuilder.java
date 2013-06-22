package lux.query.parser;

import org.apache.lucene.queryparser.xml.DOMUtils;
import org.apache.lucene.queryparser.xml.ParserException;
import org.apache.lucene.queryparser.xml.QueryBuilder;
import org.apache.lucene.search.TermRangeQuery;
import org.w3c.dom.Element;

public class TermRangeQueryBuilder implements QueryBuilder {

    @Override
    public TermRangeQuery getQuery(Element e) throws ParserException {
        String fieldName = DOMUtils.getAttributeWithInheritance(e, "fieldName");
        String lowerTerm = e.hasAttribute ("lowerTerm") ? e.getAttribute("lowerTerm") : null;
        String upperTerm = e.hasAttribute("upperTerm") ? e.getAttribute("upperTerm") : null;
        boolean includeLower = DOMUtils.getAttribute(e, "includeLower", true);
        boolean includeUpper = DOMUtils.getAttribute(e, "includeUpper", true);
        return TermRangeQuery.newStringRange(fieldName, lowerTerm, upperTerm, includeLower, includeUpper);
    }

}
