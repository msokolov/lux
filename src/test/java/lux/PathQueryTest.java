package lux;

import lux.index.XmlIndexer;

public class PathQueryTest extends BasicQueryTest {

    @Override
        public String getQueryString(Q q) {
        switch (q) {
        case ATTR: return "\"@attr\"";
        case SCENE: return "\"SCENE\"";
        case ACT: return "\"ACT\"";
        case ACT_CONTENT:return "\"ACT\" AND lux_node_ACT:\"luxsor content luxeor\"";
        case ACT1: return "w({},\"ACT\")";
        case ACT_CONTENT1: return "w({},\"ACT\") AND lux_node_ACT:\"luxsor content luxeor\"";
        case ACT2: return "2w({},\"ACT\")";
        case ACT_SCENE_CONTENT: return "w(\"ACT\",\"SCENE\") AND lux_node_SCENE:\"luxsor content luxeor\"";
        case ACT_SCENE: return "w(\"ACT\",\"SCENE\")";
        case ACT_SCENE_CONTENT1: return "w(w({},\"ACT\"),\"SCENE\") AND lux_node_SCENE:\"luxsor content luxeor\"";
        case ACT_SCENE1: return "w(w({},\"ACT\"),\"SCENE\")";
        case ACT_SCENE2: return "99w(w({},\"ACT\"),\"SCENE\")";
        case ACT_SCENE3: return "99w(\"ACT\",\"SCENE\")";
        case ACT_SCENE_SPEECH: return "w(\"SPEECH\",\"TITLE\") OR w(\"SCENE\",\"TITLE\") OR w(\"ACT\",\"TITLE\")";
        case ACT_SCENE_ID_123: return "w(w({},\"ACT\"),w(\"SCENE\",\"@id\")) AND lux_node_@id:\"luxsor 123 luxeor\"";
        case SCENE_ACT: return "w(\"SCENE\",\"ACT\")";
        case ACT_OR_SCENE: return "\"SCENE\" OR \"ACT\"";
        case ACT_AND_SCENE: return "\"SCENE\" AND \"ACT\"";
        case ACT_ID_123: return "w(w({},\"ACT\"),\"@id\") AND lux_node_@id:\"luxsor 123 luxeor\"";
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
        // XmlIndexer.INDEX_QNAMES | XmlIndexer.STORE_XML|XmlIndexer.BUILD_JDOM|
        return new XmlIndexer(XmlIndexer.INDEX_PATHS | XmlIndexer.INDEX_FULLTEXT);
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
