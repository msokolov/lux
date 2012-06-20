package lux;

import lux.index.XmlIndexer;

public class QNameTextQueryTest extends QNameQueryTest {

    @Override
    public XmlIndexer getIndexer() {
        return new XmlIndexer(XmlIndexer.INDEX_QNAMES | XmlIndexer.INDEX_FULLTEXT);
    }
    
    @Override
    public String getQueryString(Q q) {
        switch (q) {
        case ACT_CONTENT:
        case ACT_CONTENT1:
            return "+lux_elt_name:\"ACT\" +lux_node_ACT:\"luxsor content luxeor\"";
        case ACT_SCENE_CONTENT:
        case ACT_SCENE_CONTENT1:
            return "+(+lux_elt_name:\"SCENE\" +lux_elt_name:\"ACT\") +lux_node_SCENE:\"luxsor content luxeor\"";
        case ACT_ID_123:
            return "+(+lux_att_name:\"id\" +lux_elt_name:\"ACT\") +lux_node_@id:\"luxsor 123 luxeor\"";
        case ACT_SCENE_ID_123:
            return "+(+(+lux_att_name:\"id\" +lux_elt_name:\"SCENE\") +lux_elt_name:\"ACT\") +lux_node_@id:\"luxsor 123 luxeor\"";
        
        default:
            return super.getQueryString(q);
        }
    }
    
}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
