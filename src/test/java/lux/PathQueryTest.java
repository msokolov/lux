package lux;

import lux.index.XmlIndexer;

public class PathQueryTest extends BasicQueryTest {
    
    @Override
    public String getQueryXml (Q q) {
        switch (q) {
        case ATTR: return "<SpanTerm fieldName=\"lux_path\">@attr</SpanTerm>";
        case SCENE: return "<SpanTerm fieldName=\"lux_path\">SCENE</SpanTerm>";
        case ACT: return "<SpanTerm fieldName=\"lux_path\">ACT</SpanTerm>";
        case ACT_CONTENT:
            return "<BooleanQuery><Clause occurs=\"must\">" +
            		"<SpanTerm fieldName=\"lux_path\">ACT</SpanTerm>" +
            		"</Clause><Clause occurs=\"must\">" +
            		"<QNameTextQuery fieldName=\"lux_elt_text\" qName=\"ACT\">content</QNameTextQuery>" +
            		"</Clause></BooleanQuery>";
        case ACT1:
            return "<SpanNear inOrder=\"true\" slop=\"0\">" +
            		"<SpanTerm fieldName=\"lux_path\">&#x7B;&#x7D;</SpanTerm>" +
            		"<SpanTerm fieldName=\"lux_path\">ACT</SpanTerm>" +
            		"</SpanNear>";
        case ACT_CONTENT1:
            return "<BooleanQuery><Clause occurs=\"must\">" +
            "<SpanNear inOrder=\"true\" slop=\"0\"><SpanTerm fieldName=\"lux_path\">&#x7B;&#x7D;</SpanTerm><SpanTerm fieldName=\"lux_path\">ACT</SpanTerm></SpanNear>" +
            "</Clause><Clause occurs=\"must\">" +
            "<QNameTextQuery fieldName=\"lux_elt_text\" qName=\"ACT\">content</QNameTextQuery>" +
            "</Clause></BooleanQuery>";
        case ACT2:
            return "<SpanNear inOrder=\"true\" slop=\"1\">" +
            		"<SpanTerm fieldName=\"lux_path\">&#x7B;&#x7D;</SpanTerm>" +
            		"<SpanTerm fieldName=\"lux_path\">ACT</SpanTerm>" +
            		"</SpanNear>";
        case ACT_SCENE_CONTENT:
            return "<BooleanQuery><Clause occurs=\"must\">" +
            		"<SpanNear inOrder=\"true\" slop=\"0\">" +
            		  "<SpanTerm fieldName=\"lux_path\">ACT</SpanTerm>" +
            		  "<SpanTerm fieldName=\"lux_path\">SCENE</SpanTerm>" +
            		"</SpanNear></Clause><Clause occurs=\"must\">" +
            		"<QNameTextQuery fieldName=\"lux_elt_text\" qName=\"SCENE\">content</QNameTextQuery>" +
            		"</Clause></BooleanQuery>";
        case ACT_SCENE:
            return "<SpanNear inOrder=\"true\" slop=\"0\">" +
            "<SpanTerm fieldName=\"lux_path\">ACT</SpanTerm>" +
            "<SpanTerm fieldName=\"lux_path\">SCENE</SpanTerm>" +
            "</SpanNear>";
            
        case ACT_SCENE_CONTENT1:
            return "<BooleanQuery><Clause occurs=\"must\">" +
                "<SpanNear inOrder=\"true\" slop=\"0\">" +
                    "<SpanTerm fieldName=\"lux_path\">&#x7B;&#x7D;</SpanTerm>" +
                    "<SpanTerm fieldName=\"lux_path\">ACT</SpanTerm>" +
                    "<SpanTerm fieldName=\"lux_path\">SCENE</SpanTerm>" +
                "</SpanNear>" +
                    "</Clause><Clause occurs=\"must\">" +
                    "<QNameTextQuery fieldName=\"lux_elt_text\" qName=\"SCENE\">content</QNameTextQuery>" +
                    "</Clause></BooleanQuery>";
            
        case ACT_SCENE1:
            return "<SpanNear inOrder=\"true\" slop=\"0\">" +
            		  "<SpanTerm fieldName=\"lux_path\">&#x7B;&#x7D;</SpanTerm>" +
            		  "<SpanTerm fieldName=\"lux_path\">ACT</SpanTerm>" +
            		  "<SpanTerm fieldName=\"lux_path\">SCENE</SpanTerm>" +
            		"</SpanNear>";
        case ACT_SCENE2:
            return "<SpanNear inOrder=\"true\" slop=\"98\">" +
            "<SpanNear inOrder=\"true\" slop=\"0\">" +
            "<SpanTerm fieldName=\"lux_path\">&#x7B;&#x7D;</SpanTerm>" +
            "<SpanTerm fieldName=\"lux_path\">ACT</SpanTerm>" +
            "</SpanNear>" +
            "<SpanTerm fieldName=\"lux_path\">SCENE</SpanTerm>" +
            "</SpanNear>";
        case ACT_SCENE3:
            return "<SpanNear inOrder=\"true\" slop=\"98\">" +
            "<SpanTerm fieldName=\"lux_path\">ACT</SpanTerm>" +
            "<SpanTerm fieldName=\"lux_path\">SCENE</SpanTerm>" +
            "</SpanNear>";
        case ACT_SCENE_SPEECH:
            return "<SpanOr>" +
            		  "<SpanNear inOrder=\"true\" slop=\"0\">" +
            		    "<SpanTerm fieldName=\"lux_path\">SPEECH</SpanTerm><SpanTerm fieldName=\"lux_path\">TITLE</SpanTerm>" +
            		  "</SpanNear>" +
            		  "<SpanOr>" +
            		    "<SpanNear inOrder=\"true\" slop=\"0\">" +
            		      "<SpanTerm fieldName=\"lux_path\">SCENE</SpanTerm><SpanTerm fieldName=\"lux_path\">TITLE</SpanTerm>" +
            		    "</SpanNear>" +
            		    "<SpanNear inOrder=\"true\" slop=\"0\">" +
            		      "<SpanTerm fieldName=\"lux_path\">ACT</SpanTerm>" +
            		      "<SpanTerm fieldName=\"lux_path\">TITLE</SpanTerm>" +
            		    "</SpanNear>" +
            		  "</SpanOr>" +
            		"</SpanOr>";
        case ACT_SCENE_ID_123:
            return "<BooleanQuery><Clause occurs=\"must\">" +
            "<SpanNear inOrder=\"true\" slop=\"0\">" +
              "<SpanTerm fieldName=\"lux_path\">&#x7B;&#x7D;</SpanTerm>" +
              "<SpanTerm fieldName=\"lux_path\">ACT</SpanTerm>" +
              "<SpanTerm fieldName=\"lux_path\">SCENE</SpanTerm>" +
              "<SpanTerm fieldName=\"lux_path\">@id</SpanTerm>" +
            "</SpanNear></Clause>" +
            "<Clause occurs=\"must\"><QNameTextQuery fieldName=\"lux_att_text\" qName=\"id\">123</QNameTextQuery></Clause>" +
            "</BooleanQuery>";
        case SCENE_ACT:
            return "w(\"SCENE\",\"ACT\")";
        case ACT_OR_SCENE:
            return "<SpanOr>" +
            		  "<SpanTerm fieldName=\"lux_path\">SCENE</SpanTerm>" +
            		  "<SpanTerm fieldName=\"lux_path\">ACT</SpanTerm>" +
            		"</SpanOr>";
        case ACT_AND_SCENE:
            return "<BooleanQuery>" +
            "<Clause occurs=\"must\"><SpanTerm fieldName=\"lux_path\">SCENE</SpanTerm></Clause>" +
            "<Clause occurs=\"must\"><SpanTerm fieldName=\"lux_path\">ACT</SpanTerm></Clause>" +
            "</BooleanQuery>";
        case ACT_ID_123:
            return "<BooleanQuery><Clause occurs=\"must\">" +
            		"<SpanNear inOrder=\"true\" slop=\"0\">" +
            		  "<SpanTerm fieldName=\"lux_path\">&#x7B;&#x7D;</SpanTerm>" +
            		  "<SpanTerm fieldName=\"lux_path\">ACT</SpanTerm>" +
            		  "<SpanTerm fieldName=\"lux_path\">@id</SpanTerm>" +
            		"</SpanNear></Clause>" +
            		"<Clause occurs=\"must\"><QNameTextQuery fieldName=\"lux_att_text\" qName=\"id\">123</QNameTextQuery></Clause>" +
            		"</BooleanQuery>";
        case ACT_ID:
            return "<SpanNear inOrder=\"true\" slop=\"0\">" +
            "<SpanTerm fieldName=\"lux_path\">ACT</SpanTerm>" +
            "<SpanTerm fieldName=\"lux_path\">@id</SpanTerm>" +
            "</SpanNear>";
        case MATCH_ALL: return "<SpanTerm fieldName=\"lux_path\">&#x7B;&#x7D;</SpanTerm>";
        case AND: return "<SpanTerm fieldName=\"lux_path\">AND</SpanTerm>";
        case LUX_FOO: return "<SpanTerm fieldName=\"lux_path\">foo&#x7B;lux&#x7D;</SpanTerm>";
        case PLAY_ACT_OR_PERSONAE_TITLE:
            return "<SpanNear inOrder=\"true\" slop=\"0\">" +
                "<SpanTerm fieldName=\"lux_path\">&#x7B;&#x7D;</SpanTerm>" +
                "<SpanTerm fieldName=\"lux_path\">PLAY</SpanTerm>" +
                "<SpanOr>" +
                    "<SpanTerm fieldName=\"lux_path\">PERSONAE</SpanTerm>" +
                    "<SpanTerm fieldName=\"lux_path\">ACT</SpanTerm>" +
                "</SpanOr>" +
                "<SpanTerm fieldName=\"lux_path\">TITLE</SpanTerm>" +
            "</SpanNear>";
        default:
            throw new UnsupportedOperationException("unregistered query enum: " + q);
        }
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
