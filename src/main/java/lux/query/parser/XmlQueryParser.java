package lux.query.parser;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.queryparser.xml.CoreParser;

public class XmlQueryParser extends CoreParser {

    public XmlQueryParser (String fieldName, Analyzer analyzer) {
        super (fieldName, analyzer);
        queryFactory.addBuilder("RegexpQuery", new RegexpQueryBuilder());
        queryFactory.addBuilder("QNameTextQuery", new NodeQueryBuilder(analyzer, true));
        queryFactory.addBuilder("TermRangeQuery", new TermRangeQueryBuilder());
    }
    
}
