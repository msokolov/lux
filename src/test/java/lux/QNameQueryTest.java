/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */

package lux;

import lux.index.XmlIndexer;

public class QNameQueryTest extends BasicQueryTest {

    @Override
    public String getQueryString(Q q) {
        switch (q) {
        case ATTR: return "lux_att_name:\"attr\"";
        case SCENE: return "lux_elt_name:\"SCENE\"";
        case ACT_SCENE: return "+lux_elt_name:\"SCENE\" +lux_elt_name:\"ACT\"";
        case ACT_SCENE1: return "+lux_elt_name:\"SCENE\" +lux_elt_name:\"ACT\"";
        case ACT_SCENE2: return "+lux_elt_name:\"SCENE\" +lux_elt_name:\"ACT\"";
        case ACT_SCENE3: return "+lux_elt_name:\"SCENE\" +lux_elt_name:\"ACT\"";
        case SCENE_ACT: return "+lux_elt_name:\"ACT\" +lux_elt_name:\"SCENE\"";
        case ACT_OR_SCENE: return "lux_elt_name:\"SCENE\" lux_elt_name:\"ACT\"";
        case ACT_AND_SCENE: return "+lux_elt_name:\"SCENE\" +lux_elt_name:\"ACT\"";
        case ACT_SCENE_SPEECH: return
            "(+lux_elt_name:\"TITLE\" +lux_elt_name:\"SPEECH\")" +
            " ((+lux_elt_name:\"TITLE\" +lux_elt_name:\"SCENE\")" +
            " (+lux_elt_name:\"TITLE\" +lux_elt_name:\"ACT\"))";
        case ACT: return "lux_elt_name:\"ACT\"";
        case ACT2: return "lux_elt_name:\"ACT\"";
        case ACT1: return "lux_elt_name:\"ACT\"";
        case ACT_ID: return "+lux_att_name:\"id\" +lux_elt_name:\"ACT\"";
        case PLAY_ACT_OR_PERSONAE_TITLE: return "+lux_elt_name:\"TITLE\" +(+(lux_elt_name:\"PERSONAE\" lux_elt_name:\"ACT\") +lux_elt_name:\"PLAY\")";
        case MATCH_ALL: return "*:*";
        case AND: return "lux_elt_name:\"AND\"";
        case LUX_FOO: return "lux_elt_name:\"foo{lux}\"";
        default: throw new UnsupportedOperationException("unregistered query enum: " + q);
        }
    }

    @Override
    public XmlIndexer getIndexer() {
        return new XmlIndexer(XmlIndexer.INDEX_QNAMES);
    }

}
