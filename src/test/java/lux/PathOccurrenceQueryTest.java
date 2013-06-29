package lux;

import lux.index.IndexConfiguration;
import lux.index.XmlIndexer;


public class PathOccurrenceQueryTest extends BasicQueryTest {
    
    @Override
    public String getQueryXml (Q q) {
        switch (q) {
        case ATTR: return "<RegexpQuery fieldName=\"lux_path\">@attr(\\/.*)?</RegexpQuery>";
        case SCENE: return "<RegexpQuery fieldName=\"lux_path\">SCENE(\\/.*)?</RegexpQuery>";
        case ACT: return "<RegexpQuery fieldName=\"lux_path\">ACT(\\/.*)?</RegexpQuery>";
        case LINE: return "<RegexpQuery fieldName=\"lux_path\">LINE</RegexpQuery>";
        case ACT_CONTENT:
            return "<BooleanQuery><Clause occurs=\"must\">" +
                    "<QNameTextQuery fieldName=\"lux_elt_text\" qName=\"ACT\">content</QNameTextQuery>" +
            		"</Clause><Clause occurs=\"must\">" +
                    "<RegexpQuery fieldName=\"lux_path\">ACT(\\/.*)?</RegexpQuery>" +
            		"</Clause></BooleanQuery>";
        case ACT1:
            return "<RegexpQuery fieldName=\"lux_path\">ACT</RegexpQuery>";
        case ACT_CONTENT1:
            return "<BooleanQuery><Clause occurs=\"must\">" +
            "<QNameTextQuery fieldName=\"lux_elt_text\" qName=\"ACT\">content</QNameTextQuery>" +
            "</Clause><Clause occurs=\"must\">" +
            "<RegexpQuery fieldName=\"lux_path\">ACT</RegexpQuery>" +
            "</Clause></BooleanQuery>";
        case ACT2:
            return "<RegexpQuery fieldName=\"lux_path\">ACT(\\/.*)?</RegexpQuery>";
        case ACT_SCENE_CONTENT:
            return "<BooleanQuery><Clause occurs=\"must\">" +
                    "<QNameTextQuery fieldName=\"lux_elt_text\" qName=\"SCENE\">content</QNameTextQuery>" +
                    "</Clause><Clause occurs=\"must\">" +
                    "<RegexpQuery fieldName=\"lux_path\">SCENE\\/ACT(\\/.*)?</RegexpQuery>" +
            		"</Clause></BooleanQuery>";
        case ACT_SCENE:
            return "<RegexpQuery fieldName=\"lux_path\">SCENE\\/ACT(\\/.*)?</RegexpQuery>";
            
        case ACT_SCENE_CONTENT1:
            return "<BooleanQuery><Clause occurs=\"must\">" +
                "<QNameTextQuery fieldName=\"lux_elt_text\" qName=\"SCENE\">content</QNameTextQuery>" +
              "</Clause><Clause occurs=\"must\">" +
              "<RegexpQuery fieldName=\"lux_path\">SCENE\\/ACT</RegexpQuery>" +
              "</Clause></BooleanQuery>";
            
        case ACT_SCENE1:
            return "<RegexpQuery fieldName=\"lux_path\">SCENE\\/ACT</RegexpQuery>";

        case ACT_SCENE2:
            return "<RegexpQuery fieldName=\"lux_path\">SCENE\\/.*ACT</RegexpQuery>";

        case ACT_SCENE3:
            return "<RegexpQuery fieldName=\"lux_path\">SCENE\\/.*ACT(\\/.*)?</RegexpQuery>";
            
        case ACT_SCENE_SPEECH:
            return "<BooleanQuery><Clause occurs=\"should\">" +
            "<RegexpQuery fieldName=\"lux_path\">TITLE\\/ACT(\\/.*)?</RegexpQuery>" +
            "</Clause><Clause occurs=\"should\">" +
            "<RegexpQuery fieldName=\"lux_path\">TITLE\\/SCENE(\\/.*)?</RegexpQuery>" +
            "</Clause><Clause occurs=\"should\">" +
            "<RegexpQuery fieldName=\"lux_path\">TITLE\\/SPEECH(\\/.*)?</RegexpQuery>" +
            "</Clause></BooleanQuery>";
            
        case ACT_SCENE_SPEECH_AND:
            return "<BooleanQuery><Clause occurs=\"must\">" + 
            "<RegexpQuery fieldName=\"lux_path\">TITLE\\/ACT(\\/.*)?</RegexpQuery>" +
            "</Clause>" +
            "<Clause occurs=\"must\">" +
            "<RegexpQuery fieldName=\"lux_path\">TITLE\\/SCENE(\\/.*)?</RegexpQuery>" +
            "</Clause>" +
            "<Clause occurs=\"must\">" +
            "<RegexpQuery fieldName=\"lux_path\">TITLE\\/SPEECH(\\/.*)?</RegexpQuery>" +
            "</Clause></BooleanQuery>";
            
        case ACT_SCENE_ID_123:
            return "<BooleanQuery><Clause occurs=\"must\">" +
            "<QNameTextQuery fieldName=\"lux_att_text\" qName=\"id\">123</QNameTextQuery>" +
            "</Clause><Clause occurs=\"must\">" +
            "<RegexpQuery fieldName=\"lux_path\">@id\\/SCENE\\/ACT</RegexpQuery>" +
            "</Clause></BooleanQuery>";
        case SCENE_ACT:
            return null; // untested?
        case ACT_OR_SCENE:
            return  "<BooleanQuery><Clause occurs=\"should\">" +
            		  "<RegexpQuery fieldName=\"lux_path\">ACT(\\/.*)?</RegexpQuery>" +
                      "</Clause><Clause occurs=\"should\">" +
            		  "<RegexpQuery fieldName=\"lux_path\">SCENE(\\/.*)?</RegexpQuery>" +
                      "</Clause></BooleanQuery>";
        case ACT_AND_SCENE:
            return "<BooleanQuery><Clause occurs=\"must\">" +
            "<RegexpQuery fieldName=\"lux_path\">ACT(\\/.*)?</RegexpQuery>" +
            "</Clause><Clause occurs=\"must\">" +
            "<RegexpQuery fieldName=\"lux_path\">SCENE(\\/.*)?</RegexpQuery>" +
            "</Clause></BooleanQuery>";
        case ACT_ID_123:
            return "<BooleanQuery><Clause occurs=\"must\">" +
                "<QNameTextQuery fieldName=\"lux_att_text\" qName=\"id\">123</QNameTextQuery>" +
                "</Clause>" +
            		"<Clause occurs=\"must\">" +
                    "<RegexpQuery fieldName=\"lux_path\">@id\\/ACT</RegexpQuery>" +
                  "</Clause>" +
            		"</BooleanQuery>";
        case ACT_ID:
            return "<RegexpQuery fieldName=\"lux_path\">@id\\/ACT(\\/.*)?</RegexpQuery>";

        case MATCH_ALL_Q: return "<MatchAllDocsQuery/>";
        case AND: return "<RegexpQuery fieldName=\"lux_path\">AND(\\/.*)?</RegexpQuery>";
        case TITLE: return "<RegexpQuery fieldName=\"lux_path\">TITLE(\\/.*)?</RegexpQuery>";
        case LUX_FOO: return "<RegexpQuery fieldName=\"lux_path\">foo\\&#x7B;http\\:\\/\\/luxdb.net\\&#x7D;(\\/.*)?</RegexpQuery>";
        case PLAY_ACT_OR_PERSONAE_TITLE:
            return "<RegexpQuery fieldName=\"lux_path\">TITLE\\/(ACT|PERSONAE)\\/PLAY</RegexpQuery>";
        case SCENE_3:
            return "<BooleanQuery>" + 
            "<Clause occurs=\"must\">" +
            "<RegexpQuery fieldName=\"lux_path\">TITLE\\/SCENE</RegexpQuery>" +
            "</Clause>" +
            "<Clause occurs=\"must\">" +
            "<RegexpQuery fieldName=\"lux_path\">SPEECH\\/SCENE</RegexpQuery>" +
            "</Clause>" +
            "<Clause occurs=\"must\">" +
            "<RegexpQuery fieldName=\"lux_path\">STAGEDIR\\/SCENE</RegexpQuery>" +
            "</Clause>" +
            "</BooleanQuery>";
        case SCENE_4:
            return "<BooleanQuery>" + 
            "<Clause occurs=\"must\">" +
            "<RegexpQuery fieldName=\"lux_path\">TITLE\\/SCENE</RegexpQuery>" +
            "</Clause>" +
            "<Clause occurs=\"must\">" +
            "<RegexpQuery fieldName=\"lux_path\">SPEECH\\/SCENE</RegexpQuery>" +
            "</Clause>" +
            "<Clause occurs=\"must\">" +
            "<RegexpQuery fieldName=\"lux_path\">STAGEDIR\\/SCENE</RegexpQuery>" +
            "</Clause>" +
            "<Clause occurs=\"must\">" +
            "<RegexpQuery fieldName=\"lux_path\">MISC\\/SCENE</RegexpQuery>" +
            "</Clause>" +
            "</BooleanQuery>";
        
        default:
            throw new UnsupportedOperationException("unregistered query enum: " + q);
        }
    }

    @Override
    public XmlIndexer getIndexer() {
        return new XmlIndexer(IndexConfiguration.INDEX_PATHS | IndexConfiguration.INDEX_FULLTEXT | IndexConfiguration.INDEX_EACH_PATH);
    }
    
    @Override
    public boolean hasPathIndexes() {
        return true;
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
