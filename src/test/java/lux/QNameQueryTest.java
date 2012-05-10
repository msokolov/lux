package lux;

import lux.index.XmlIndexer;

public class QNameQueryTest extends BasicQueryTest {

    @Override
    public void populateQueryStrings() {
        queryStrings.put (Q.ATTR, "lux_att_name:attr");
        queryStrings.put (Q.BAR, "lux_elt_name:bar");
        queryStrings.put (Q.FOO_BAR, "+lux_elt_name:bar +lux_elt_name:foo");
        queryStrings.put (Q.FOO_BAR1, "+lux_elt_name:bar +lux_elt_name:foo");
        queryStrings.put (Q.FOO_BAR2, "+lux_elt_name:bar +lux_elt_name:foo");
        queryStrings.put (Q.FOO_BAR3, "+lux_elt_name:bar +lux_elt_name:foo");
        queryStrings.put (Q.BAR_FOO, "+lux_elt_name:foo +lux_elt_name:bar");
        queryStrings.put (Q.FOO_OR_BAR, "lux_elt_name:bar lux_elt_name:foo");
        queryStrings.put (Q.FOO_AND_BAR, "+lux_elt_name:bar +lux_elt_name:foo");
        queryStrings.put(Q.FOO_BAR_BAZ, 
                         "(+lux_elt_name:title +lux_elt_name:baz)" +
                         " ((+lux_elt_name:title +lux_elt_name:bar)" +
                         " (+lux_elt_name:title +lux_elt_name:foo))");
        queryStrings.put (Q.FOO, "lux_elt_name:foo");
        queryStrings.put (Q.FOO2, "lux_elt_name:foo");
        queryStrings.put (Q.FOO1, "lux_elt_name:foo");
        queryStrings.put (Q.FOO_ID, "+lux_att_name:id +lux_elt_name:foo");
        queryStrings.put (Q.MATCH_ALL, "*:*");
    }

    @Override
    public XmlIndexer getIndexer() {
        return new XmlIndexer(XmlIndexer.INDEX_QNAMES);
    }

}
