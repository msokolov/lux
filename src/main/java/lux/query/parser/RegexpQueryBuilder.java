package lux.query.parser;

import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.xml.ParserException;
import org.apache.lucene.queryparser.xml.QueryBuilder;
import org.apache.lucene.search.RegexpQuery;
import org.w3c.dom.Element;

public class RegexpQueryBuilder implements QueryBuilder {

    @Override
    public RegexpQuery getQuery(Element e) throws ParserException {
        return new RegexpQuery(new Term(e.getAttribute("fieldName"), e.getTextContent()));
    }

}
