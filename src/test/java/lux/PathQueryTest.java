package lux;

import lux.index.XmlIndexer;

public class PathQueryTest extends BasicQueryTest {

    @Override
    public void populateQueryStrings() {
        queryStrings.put(Q.ATTR, "\"@attr\"");
        queryStrings.put(Q.SCENE, "\"SCENE\"");
        queryStrings.put(Q.ACT, "\"ACT\"");
        queryStrings.put(Q.ACT1, "w({},\"ACT\")");
        queryStrings.put(Q.ACT2, "2w({},\"ACT\")");
        queryStrings.put(Q.ACT_SCENE, "w(\"ACT\",\"SCENE\")");
        queryStrings.put(Q.ACT_SCENE1, "w(w({},\"ACT\"),\"SCENE\")");
        queryStrings.put(Q.ACT_SCENE2, "99w(w({},\"ACT\"),\"SCENE\")");
        queryStrings.put(Q.ACT_SCENE3, "99w(\"ACT\",\"SCENE\")");
        queryStrings.put(Q.ACT_SCENE_SPEECH, "w(\"SPEECH\",\"TITLE\") OR w(\"SCENE\",\"TITLE\") OR w(\"ACT\",\"TITLE\")");
        queryStrings.put(Q.SCENE_ACT, "w(\"SCENE\",\"ACT\")");
        queryStrings.put(Q.ACT_OR_SCENE, "\"SCENE\" OR \"ACT\"");
        queryStrings.put(Q.ACT_AND_SCENE, "\"SCENE\" AND \"ACT\"");
        queryStrings.put (Q.ACT_ID, "w(\"ACT\",\"@id\")");
        queryStrings.put(Q.MATCH_ALL, "{}");
        queryStrings.put(Q.AND, "\"AND\"");        
    }

    @Override
    public XmlIndexer getIndexer() {
        return new XmlIndexer(XmlIndexer.INDEX_PATHS);
    }

}
