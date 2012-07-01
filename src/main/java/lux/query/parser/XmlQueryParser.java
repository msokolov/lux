package lux.query.parser;

import lux.index.field.XmlField;

import org.apache.lucene.xmlparser.CoreParser;

public class XmlQueryParser extends CoreParser {

    public XmlQueryParser (XmlField field) {
        super (field.getName(), field.getAnalyzer());
        queryFactory.addBuilder("QNameTextQuery", new QNameQueryBuilder(analyzer));
    }
    
}
