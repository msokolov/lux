package lux;

import lux.index.XmlIndexer;

public class PathQueryTest extends BasicQueryTest {

    @Override
    public void populateQueryStrings() {
        queryStrings.put(Q.ATTR, "@attr");
        queryStrings.put(Q.BAR, "bar");
        queryStrings.put(Q.FOO, "foo");
        queryStrings.put(Q.FOO1, "w({},foo)");
        queryStrings.put(Q.FOO2, "2w({},foo)");
        queryStrings.put(Q.FOO_BAR, "bar AND foo");
        queryStrings.put(Q.FOO_BAR1, "w(w({},foo),bar)");
        queryStrings.put(Q.FOO_BAR_BAZ, "w(foo,title) OR w(bar,title) OR w(baz,title)");
        queryStrings.put(Q.BAR_FOO, "99w(bar,foo)");
        queryStrings.put(Q.FOO_OR_BAR, "bar OR foo");
        queryStrings.put (Q.FOO_ID, "w(id foo)");
        queryStrings.put(Q.MATCH_ALL, "{}");
        
    }

    @Override
    public XmlIndexer getIndexer() {
        return new XmlIndexer(XmlIndexer.INDEX_PATHS);
    }

}
