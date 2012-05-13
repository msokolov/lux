package lux;

import lux.index.XmlIndexer;

public class QNameQueryTest extends BasicQueryTest {

    @Override
    public void populateQueryStrings() {
        queryStrings.put (Q.ATTR, "lux_att_name:\"attr\"");
        queryStrings.put (Q.SCENE, "lux_elt_name:\"SCENE\"");
        queryStrings.put (Q.ACT_SCENE, "+lux_elt_name:\"SCENE\" +lux_elt_name:\"ACT\"");
        queryStrings.put (Q.ACT_SCENE1, "+lux_elt_name:\"SCENE\" +lux_elt_name:\"ACT\"");
        queryStrings.put (Q.ACT_SCENE2, "+lux_elt_name:\"SCENE\" +lux_elt_name:\"ACT\"");
        queryStrings.put (Q.ACT_SCENE3, "+lux_elt_name:\"SCENE\" +lux_elt_name:\"ACT\"");
        queryStrings.put (Q.SCENE_ACT, "+lux_elt_name:\"ACT\" +lux_elt_name:\"SCENE\"");
        queryStrings.put (Q.ACT_OR_SCENE, "lux_elt_name:\"SCENE\" lux_elt_name:\"ACT\"");
        queryStrings.put (Q.ACT_AND_SCENE, "+lux_elt_name:\"SCENE\" +lux_elt_name:\"ACT\"");
        queryStrings.put(Q.ACT_SCENE_SPEECH, 
                         "(+lux_elt_name:\"TITLE\" +lux_elt_name:\"SPEECH\")" +
                         " ((+lux_elt_name:\"TITLE\" +lux_elt_name:\"SCENE\")" +
                         " (+lux_elt_name:\"TITLE\" +lux_elt_name:\"ACT\"))");
        queryStrings.put (Q.ACT, "lux_elt_name:\"ACT\"");
        queryStrings.put (Q.ACT2, "lux_elt_name:\"ACT\"");
        queryStrings.put (Q.ACT1, "lux_elt_name:\"ACT\"");
        queryStrings.put (Q.ACT_ID, "+lux_att_name:\"id\" +lux_elt_name:\"ACT\"");
        queryStrings.put (Q.PLAY_ACT_OR_PERSONAE_TITLE, "+lux_elt_name:\"TITLE\" +(+(lux_elt_name:\"PERSONAE\" lux_elt_name:\"ACT\") +lux_elt_name:\"PLAY\")");
        queryStrings.put (Q.MATCH_ALL, "*:*");
        queryStrings.put (Q.AND, "lux_elt_name:\"AND\"");
    }

    @Override
    public XmlIndexer getIndexer() {
        return new XmlIndexer(XmlIndexer.INDEX_QNAMES);
    }

}
