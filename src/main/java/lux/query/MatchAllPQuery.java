package lux.query;

import lux.xpath.QName;
import lux.xquery.ElementConstructor;

public final class MatchAllPQuery extends ParseableQuery {

    private static final ElementConstructor INSTANCE_ELEMENT_CONSTRUCTOR = new ElementConstructor(new QName("MatchAllDocsQuery"));
    private static final MatchAllPQuery INSTANCE = new MatchAllPQuery();
    
    public static MatchAllPQuery getInstance() {
        return INSTANCE;
    }
    
    public String toXmlString(String field) {
        return "<MatchAllDocsQuery />";
    }
    
    public String toString(String field) {
        return "*:*";
    }

    @Override
    public ElementConstructor toXmlNode(String field) {
        return INSTANCE_ELEMENT_CONSTRUCTOR;
    }

}
