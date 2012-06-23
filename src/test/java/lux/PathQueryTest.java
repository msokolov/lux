package lux;

import lux.index.XmlIndexer;

public class PathQueryTest extends BasicQueryTest {

    @Override
        public String getQueryString(Q q) {
        switch (q) {
        case ATTR: return "\"@attr\"";
        case SCENE: return "\"SCENE\"";
        case ACT: return "\"ACT\"";
        case ACT_CONTENT:return "\"ACT\" AND lux_node_ACT:\"content\"";
        case ACT1: return "w({},\"ACT\")";
        case ACT_CONTENT1: return "w({},\"ACT\") AND lux_node_ACT:\"content\"";
        case ACT2: return "2w({},\"ACT\")";
        case ACT_SCENE_CONTENT: return "w(\"ACT\",\"SCENE\") AND lux_node_SCENE:\"content\"";
        case ACT_SCENE: return "w(\"ACT\",\"SCENE\")";
        case ACT_SCENE_CONTENT1: return "w(w({},\"ACT\"),\"SCENE\") AND lux_node_SCENE:\"content\"";
        case ACT_SCENE1: return "w(w({},\"ACT\"),\"SCENE\")";
        case ACT_SCENE2: return "99w(w({},\"ACT\"),\"SCENE\")";
        case ACT_SCENE3: return "99w(\"ACT\",\"SCENE\")";
        case ACT_SCENE_SPEECH: return "w(\"SPEECH\",\"TITLE\") OR w(\"SCENE\",\"TITLE\") OR w(\"ACT\",\"TITLE\")";
        case ACT_SCENE_ID_123: return "w(w({},\"ACT\"),w(\"SCENE\",\"@id\")) AND lux_node_@id:\"123\"";
        case SCENE_ACT: return "w(\"SCENE\",\"ACT\")";
        case ACT_OR_SCENE: return "\"SCENE\" OR \"ACT\"";
        case ACT_AND_SCENE: return "\"SCENE\" AND \"ACT\"";
        case ACT_ID_123: return "w(w({},\"ACT\"),\"@id\") AND lux_node_@id:\"123\"";
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
    public String getQueryXml (Q q) {
        switch (q) {
        case ATTR: return "<SpanTerm fieldName=\"lux_path\">@attr</SpanTerm>";
        case SCENE: return "<SpanTerm fieldName=\"lux_path\">SCENE</SpanTerm>";
        case ACT: return "<SpanTerm fieldName=\"lux_path\">ACT</SpanTerm>";
        case ACT_CONTENT:
            return "<BooleanQuery><Clause occurs=\"must\">" +
            		"<SpanTerm fieldName=\"lux_path\">ACT</SpanTerm>" +
            		"</Clause><Clause occurs=\"must\">" +
            		"<TermsQuery fieldName=\"lux_node_ACT\">content</TermsQuery>" +
            		"</Clause></BooleanQuery>";
        case ACT1:
            return "<SpanNear inOrder=\"true\" slop=\"0\">" +
            		"<SpanTerm fieldName=\"lux_path\">{}</SpanTerm>" +
            		"<SpanTerm fieldName=\"lux_path\">ACT</SpanTerm>" +
            		"</SpanNear>";
        case ACT_CONTENT1:
            return "<BooleanQuery><Clause occurs=\"must\">" +
            "<SpanNear inOrder=\"true\" slop=\"0\"><SpanTerm fieldName=\"lux_path\">{}</SpanTerm><SpanTerm fieldName=\"lux_path\">ACT</SpanTerm></SpanNear>" +
            "</Clause><Clause occurs=\"must\">" +
            "<TermsQuery fieldName=\"lux_node_ACT\">content</TermsQuery>" +
            "</Clause></BooleanQuery>";
        case ACT2:
            return "<SpanNear inOrder=\"true\" slop=\"1\">" +
            		"<SpanTerm fieldName=\"lux_path\">{}</SpanTerm>" +
            		"<SpanTerm fieldName=\"lux_path\">ACT</SpanTerm>" +
            		"</SpanNear>";
        case ACT_SCENE_CONTENT:
            return "<BooleanQuery><Clause occurs=\"must\">" +
            		"<SpanNear inOrder=\"true\" slop=\"0\">" +
            		  "<SpanTerm fieldName=\"lux_path\">ACT</SpanTerm>" +
            		  "<SpanTerm fieldName=\"lux_path\">SCENE</SpanTerm>" +
            		"</SpanNear></Clause><Clause occurs=\"must\">" +
            		"<TermsQuery fieldName=\"lux_node_SCENE\">content</TermsQuery>" +
            		"</Clause></BooleanQuery>";
        case ACT_SCENE:
            return "<SpanNear inOrder=\"true\" slop=\"0\">" +
            "<SpanTerm fieldName=\"lux_path\">ACT</SpanTerm>" +
            "<SpanTerm fieldName=\"lux_path\">SCENE</SpanTerm>" +
            "</SpanNear>";
        case ACT_SCENE_CONTENT1:

            return "<BooleanQuery><Clause occurs=\"must\">" +
                "<SpanNear inOrder=\"true\" slop=\"0\">" +
                    "<SpanNear inOrder=\"true\" slop=\"0\"><SpanTerm fieldName=\"lux_path\">{}</SpanTerm><SpanTerm fieldName=\"lux_path\">ACT</SpanTerm></SpanNear>" +
                    "<SpanTerm fieldName=\"lux_path\">SCENE</SpanTerm>" +
                "</SpanNear>" +
                    "</Clause><Clause occurs=\"must\">" +
                    "<TermsQuery fieldName=\"lux_node_SCENE\">content</TermsQuery>" +
                    "</Clause></BooleanQuery>";
            
        case ACT_SCENE1:
            return "<SpanNear inOrder=\"true\" slop=\"0\">" +
            		"<SpanNear inOrder=\"true\" slop=\"0\">" +
            		"<SpanTerm fieldName=\"lux_path\">{}</SpanTerm>" +
            		"<SpanTerm fieldName=\"lux_path\">ACT</SpanTerm>" +
            		"</SpanNear>" +
            		"<SpanTerm fieldName=\"lux_path\">SCENE</SpanTerm>" +
            		"</SpanNear>";
        case ACT_SCENE2:
            return "<SpanNear inOrder=\"true\" slop=\"98\">" +
            "<SpanNear inOrder=\"true\" slop=\"0\">" +
            "<SpanTerm fieldName=\"lux_path\">{}</SpanTerm>" +
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
              "<SpanNear inOrder=\"true\" slop=\"0\"><SpanTerm fieldName=\"lux_path\">{}</SpanTerm><SpanTerm fieldName=\"lux_path\">ACT</SpanTerm></SpanNear>" +
              "<SpanNear inOrder=\"true\" slop=\"0\"><SpanTerm fieldName=\"lux_path\">SCENE</SpanTerm><SpanTerm fieldName=\"lux_path\">@id</SpanTerm></SpanNear>" +
            "</SpanNear></Clause>" +
            "<Clause occurs=\"must\"><TermsQuery fieldName=\"lux_node_@id\">123</TermsQuery></Clause>" +
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
            		  "<SpanNear inOrder=\"true\" slop=\"0\"><SpanTerm fieldName=\"lux_path\">{}</SpanTerm><SpanTerm fieldName=\"lux_path\">ACT</SpanTerm></SpanNear>" +
            		  "<SpanTerm fieldName=\"lux_path\">@id</SpanTerm>" +
            		"</SpanNear></Clause>" +
            		"<Clause occurs=\"must\"><TermsQuery fieldName=\"lux_node_@id\">123</TermsQuery></Clause>" +
            		"</BooleanQuery>";
        case ACT_ID:
            return "<SpanNear inOrder=\"true\" slop=\"0\">" +
            "<SpanTerm fieldName=\"lux_path\">ACT</SpanTerm>" +
            "<SpanTerm fieldName=\"lux_path\">@id</SpanTerm>" +
            "</SpanNear>";
        case MATCH_ALL: return "<SpanTerm fieldName=\"lux_path\">{}</SpanTerm>";
        case AND: return "<SpanTerm fieldName=\"lux_path\">AND</SpanTerm>";
        case LUX_FOO: return "<SpanTerm fieldName=\"lux_path\">foo{lux}</SpanTerm>";
        case PLAY_ACT_OR_PERSONAE_TITLE:
            return "<SpanNear inOrder=\"true\" slop=\"0\">" +
            		"<SpanNear inOrder=\"true\" slop=\"0\">" +
            		  "<SpanNear inOrder=\"true\" slop=\"0\">" +
            		    "<SpanTerm fieldName=\"lux_path\">{}</SpanTerm><SpanTerm fieldName=\"lux_path\">PLAY</SpanTerm>" +
            		  "</SpanNear>" +
            		  "<SpanOr><SpanTerm fieldName=\"lux_path\">PERSONAE</SpanTerm><SpanTerm fieldName=\"lux_path\">ACT</SpanTerm></SpanOr>" +
            		"</SpanNear>" +
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
