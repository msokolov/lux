package lux;

import lux.index.XmlIndexer;

public class QNameTextQueryTest extends BasicQueryTest {

    @Override
    public XmlIndexer getIndexer() {
        return new XmlIndexer(XmlIndexer.INDEX_QNAMES | XmlIndexer.INDEX_FULLTEXT);
    }
    
    @Override
    public String getQueryString(Q q) {
        switch (q) {
        case ACT_CONTENT:
        case ACT_CONTENT1:
            return "+lux_elt_name:\"ACT\" +lux_node_ACT:\"content\"";
        case ACT_SCENE_CONTENT:
        case ACT_SCENE_CONTENT1:
            return "+(+lux_elt_name:\"SCENE\" +lux_elt_name:\"ACT\") +lux_node_SCENE:\"content\"";
        case ACT_ID_123:
            return "+(+lux_att_name:\"id\" +lux_elt_name:\"ACT\") +lux_node_@id:\"123\"";
        case ACT_SCENE_ID_123:
            return "+(+(+lux_att_name:\"id\" +lux_elt_name:\"SCENE\") +lux_elt_name:\"ACT\") +lux_node_@id:\"123\"";
        
        default:
            return super.getQueryString(q);
        }
    }
    
    public String getQueryXml (Q q) {
        switch (q) {
        case ACT_CONTENT:
        case ACT_CONTENT1:
            return "<BooleanQuery><Clause occurs=\"must\"><TermsQuery>ACT</TermsQuery></Clause><Clause occurs=\"must\"><QNameTextQuery fieldName=\"lux_node\" qName=\"ACT\">content</QNameTextQuery></Clause></BooleanQuery>";
        case ACT_SCENE_CONTENT:
        case ACT_SCENE_CONTENT1:
            return "<BooleanQuery>" +
                    "<Clause occurs=\"must\"><BooleanQuery>" +
                      "<Clause occurs=\"must\"><TermsQuery>SCENE</TermsQuery></Clause>" + 
            		  "<Clause occurs=\"must\"><TermsQuery>ACT</TermsQuery></Clause>" +
                      "</BooleanQuery></Clause>" +
                    "<Clause occurs=\"must\"><QNameTextQuery fieldName=\"lux_node\" qName=\"SCENE\">content</QNameTextQuery></Clause>" +
            		"</BooleanQuery>";
    
        case ACT_ID_123:
            return "<BooleanQuery><Clause occurs=\"must\">" +
            		 "<BooleanQuery>" +
            		  "<Clause occurs=\"must\"><TermsQuery fieldName=\"lux_att_name\">id</TermsQuery></Clause>" +
            		  "<Clause occurs=\"must\"><TermsQuery>ACT</TermsQuery></Clause>" +
            		 "</BooleanQuery></Clause>" +
            		 "<Clause occurs=\"must\"><QNameTextQuery fieldName=\"lux_node\" qName=\"@id\">123</QNameTextQuery></Clause>" +
            		"</BooleanQuery>";
        case ACT_SCENE_ID_123:
            return "<BooleanQuery>" +
            		 "<Clause occurs=\"must\">" +
            		  "<BooleanQuery>" +
            		   "<Clause occurs=\"must\">" +
            		    "<BooleanQuery>" +
            		     "<Clause occurs=\"must\"><TermsQuery fieldName=\"lux_att_name\">id</TermsQuery></Clause>" +
            		     "<Clause occurs=\"must\"><TermsQuery>SCENE</TermsQuery></Clause>" +
            		     "</BooleanQuery>" +
            		    "</Clause>" +
            		    "<Clause occurs=\"must\"><TermsQuery>ACT</TermsQuery></Clause>" +
            		   "</BooleanQuery>" +
            		  "</Clause>" +
            		  "<Clause occurs=\"must\"><QNameTextQuery fieldName=\"lux_node\" qName=\"@id\">123</QNameTextQuery></Clause>" +
            		"</BooleanQuery>";

        default:
            return super.getQueryXml(q);    
        }
    }
    
}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
