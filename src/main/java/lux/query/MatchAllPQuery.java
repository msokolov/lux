package lux.query;

import lux.index.IndexConfiguration;
import lux.xml.QName;
import lux.xquery.ElementConstructor;

public final class MatchAllPQuery extends ParseableQuery {

    public static final ElementConstructor INSTANCE_ELEMENT_CONSTRUCTOR = new ElementConstructor(new QName("MatchAllDocsQuery"));
    private static final MatchAllPQuery INSTANCE = new MatchAllPQuery();
    
    public static MatchAllPQuery getInstance() {
        return INSTANCE;
    }

    @Override
    public ElementConstructor toXmlNode(String field, IndexConfiguration config) {
        return INSTANCE_ELEMENT_CONSTRUCTOR;
    }

    @Override
    public String toQueryString(String field, IndexConfiguration config) {
        return "*:*";
    }
    
    @Override
    public boolean isSpanCompatible () {
        return true;
    }
    
    @Override
    public boolean isMatchAll () {
        return true;
    }

    @Override
    public boolean equals(ParseableQuery other) {
        return other == this;
    }

}
