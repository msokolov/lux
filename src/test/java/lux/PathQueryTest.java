package lux;

import lux.index.XmlIndexer;

public class PathQueryTest extends BasicQueryTest {

    @Override
        public String getQueryString(Q q) {
        switch (q) {
        case ATTR: return "\"@attr\"";
        case SCENE: return "\"SCENE\"";
        case ACT: return "\"ACT\"";
        case ACT1: return "w({},\"ACT\")";
        case ACT2: return "2w({},\"ACT\")";
        case ACT_SCENE: return "w(\"ACT\",\"SCENE\")";
        case ACT_SCENE1: return "w(w({},\"ACT\"),\"SCENE\")";
        case ACT_SCENE2: return "99w(w({},\"ACT\"),\"SCENE\")";
        case ACT_SCENE3: return "99w(\"ACT\",\"SCENE\")";
        case ACT_SCENE_SPEECH: return "w(\"SPEECH\",\"TITLE\") OR w(\"SCENE\",\"TITLE\") OR w(\"ACT\",\"TITLE\")";
        case SCENE_ACT: return "w(\"SCENE\",\"ACT\")";
        case ACT_OR_SCENE: return "\"SCENE\" OR \"ACT\"";
        case ACT_AND_SCENE: return "\"SCENE\" AND \"ACT\"";
        case ACT_ID: return "w(\"ACT\",\"@id\")";
        case MATCH_ALL: return "{}";
        case PLAY_ACT_OR_PERSONAE_TITLE: return "w(w(w({},\"PLAY\"),\"PERSONAE\" OR \"ACT\"),\"TITLE\")";
        case AND: return "\"AND\"";
        case LUX_FOO: return "\"foo{lux}\"";
        default: throw new UnsupportedOperationException("unregistered query enum: " + q);
        }
        // TODO: see if there is any merit in collapsing queries like this?
        //queryStrings.put(Q.PLAY_ACT_OR_PERSONAE_TITLE, "w({},\"PLAY\",(\"ACT\" OR \"PERSONAE\"),\"TITLE\")");
    }

    @Override
    public XmlIndexer getIndexer() {
        return new XmlIndexer(XmlIndexer.INDEX_PATHS);
    }

}
