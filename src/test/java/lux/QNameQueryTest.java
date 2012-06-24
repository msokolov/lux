package lux;

import lux.index.XmlIndexer;

public class QNameQueryTest extends BasicQueryTest {

    @Override
    public String getQueryString(Q q) {
        switch (q) {
        case ATTR: return "lux_att_name:\"attr\"";
        case SCENE: return "lux_elt_name:\"SCENE\"";
        case ACT_SCENE: 
        case ACT_SCENE1:
        case ACT_SCENE2:
        case ACT_SCENE3:
        case ACT_SCENE_CONTENT:
        case ACT_SCENE_CONTENT1:
            return "+lux_elt_name:\"SCENE\" +lux_elt_name:\"ACT\"";
        case SCENE_ACT: return "+lux_elt_name:\"ACT\" +lux_elt_name:\"SCENE\"";
        case ACT_OR_SCENE: return "lux_elt_name:\"SCENE\" lux_elt_name:\"ACT\"";
        case ACT_AND_SCENE: return "+lux_elt_name:\"SCENE\" +lux_elt_name:\"ACT\"";
        case ACT_SCENE_SPEECH: return
            "(+lux_elt_name:\"TITLE\" +lux_elt_name:\"SPEECH\")" +
            " ((+lux_elt_name:\"TITLE\" +lux_elt_name:\"SCENE\")" +
            " (+lux_elt_name:\"TITLE\" +lux_elt_name:\"ACT\"))";
        case ACT:
        case ACT1:
        case ACT2:
        case ACT_CONTENT:
        case ACT_CONTENT1:
            return "lux_elt_name:\"ACT\"";
        case ACT_ID_123:
        case ACT_ID: 
            return "+lux_att_name:\"id\" +lux_elt_name:\"ACT\"";
        case ACT_SCENE_ID_123:
            return "+(+lux_att_name:\"id\" +lux_elt_name:\"SCENE\") +lux_elt_name:\"ACT\"";
        case PLAY_ACT_OR_PERSONAE_TITLE: return "+lux_elt_name:\"TITLE\" +(+(lux_elt_name:\"PERSONAE\" lux_elt_name:\"ACT\") +lux_elt_name:\"PLAY\")";
        case MATCH_ALL: return "*:*";
        case AND: return "lux_elt_name:\"AND\"";
        case LUX_FOO: return "lux_elt_name:\"foo{lux}\"";
        default: throw new UnsupportedOperationException("unregistered query enum: " + q);
        }
    }

    @Override
    public String getQueryXml(Q q) {
        switch (q) {
        case ATTR: return "<TermsQuery fieldName=\"lux_att_name\">attr</TermsQuery>";
        case SCENE: return "<TermsQuery>SCENE</TermsQuery>";
        case ACT_SCENE: 
        case ACT_SCENE1:
        case ACT_SCENE2:
        case ACT_SCENE3:
        case ACT_SCENE_CONTENT:
        case ACT_SCENE_CONTENT1:
        case ACT_AND_SCENE: 
            return "<BooleanQuery><Clause occurs=\"must\"><TermsQuery>SCENE</TermsQuery></Clause>" + 
                "<Clause occurs=\"must\"><TermsQuery>ACT</TermsQuery></Clause></BooleanQuery>";
        case SCENE_ACT: 
            return "<BooleanQuery><Clause occurs=\"must\"><TermsQuery>ACT</TermsQuery></Clause>" + 
                "<Clause occurs=\"must\"><TermsQuery>SCENE</TermsQuery></Clause></BooleanQuery>";
        case ACT_OR_SCENE: 
            return "<BooleanQuery><Clause occurs=\"should\"><TermsQuery>SCENE</TermsQuery></Clause>" + 
                "<Clause occurs=\"should\"><TermsQuery>ACT</TermsQuery></Clause></BooleanQuery>";
        case ACT_SCENE_SPEECH:
            return 
                "<BooleanQuery><Clause occurs=\"should\">" +
                  "<BooleanQuery>" +
                    "<Clause occurs=\"must\"><TermsQuery>TITLE</TermsQuery></Clause>" + 
                    "<Clause occurs=\"must\"><TermsQuery>SPEECH</TermsQuery></Clause>" +
                   "</BooleanQuery>" +
                "</Clause><Clause occurs=\"should\">" +
                  "<BooleanQuery><Clause occurs=\"should\">" +
                    "<BooleanQuery>" +
                      "<Clause occurs=\"must\"><TermsQuery>TITLE</TermsQuery></Clause>" + 
                      "<Clause occurs=\"must\"><TermsQuery>SCENE</TermsQuery></Clause>" +
                    "</BooleanQuery>" +
                  "</Clause><Clause occurs=\"should\">" +
                  "<BooleanQuery>" +
                    "<Clause occurs=\"must\"><TermsQuery>TITLE</TermsQuery></Clause>" + 
                    "<Clause occurs=\"must\"><TermsQuery>ACT</TermsQuery></Clause>" +
                  "</BooleanQuery>" +
                  "</Clause></BooleanQuery>" +
                "</Clause></BooleanQuery>";
        case ACT:
        case ACT1:
        case ACT2:
        case ACT_CONTENT:
        case ACT_CONTENT1:
            return "<TermsQuery>ACT</TermsQuery>";
        case ACT_ID_123:
        case ACT_ID: 
            return "<BooleanQuery><Clause occurs=\"must\"><TermsQuery fieldName=\"lux_att_name\">id</TermsQuery></Clause>" + 
                "<Clause occurs=\"must\"><TermsQuery>ACT</TermsQuery></Clause></BooleanQuery>";
        case ACT_SCENE_ID_123:
            return 
                "<BooleanQuery><Clause occurs=\"must\">" +
                "<BooleanQuery><Clause occurs=\"must\"><TermsQuery fieldName=\"lux_att_name\">id</TermsQuery></Clause>" + 
                "<Clause occurs=\"must\"><TermsQuery>SCENE</TermsQuery></Clause></BooleanQuery>" +
                "</Clause>" +
                "<Clause occurs=\"must\"><TermsQuery>ACT</TermsQuery></Clause>" + 
                "</BooleanQuery>";
        case PLAY_ACT_OR_PERSONAE_TITLE: 
            return 
                "<BooleanQuery>" +
                "<Clause occurs=\"must\"><TermsQuery>TITLE</TermsQuery></Clause>" + 
                "<Clause occurs=\"must\"><BooleanQuery>" +
                  "<Clause occurs=\"must\"><BooleanQuery>" +
                    "<Clause occurs=\"should\"><TermsQuery>PERSONAE</TermsQuery></Clause>" + 
                    "<Clause occurs=\"should\"><TermsQuery>ACT</TermsQuery></Clause>" + 
                  "</BooleanQuery></Clause>" +
                  "<Clause occurs=\"must\"><TermsQuery>PLAY</TermsQuery></Clause>" + 
                "</BooleanQuery></Clause>" +
                "</BooleanQuery>";
        case MATCH_ALL: return "<MatchAllDocsQuery />";
        case AND: return "<TermsQuery>AND</TermsQuery>";
        case LUX_FOO: return "<TermsQuery>foo&#x7B;lux&#x7D;</TermsQuery>";
        default: throw new UnsupportedOperationException("unregistered query enum: " + q);
        }
    }

    @Override
    public XmlIndexer getIndexer() {
        return new XmlIndexer(XmlIndexer.INDEX_QNAMES);
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
