package lux;

import lux.index.IndexConfiguration;
import lux.index.XmlIndexer;


public class QNameTextQueryTest extends BasicQueryTest {

    @Override
    public XmlIndexer getIndexer() {
        return new XmlIndexer(IndexConfiguration.INDEX_QNAMES | IndexConfiguration.INDEX_FULLTEXT);
    }
    
    public String getQueryXml (Q q) {
        switch (q) {
        case ACT_CONTENT:
        case ACT_CONTENT1:
            return "<BooleanQuery><Clause occurs=\"must\"><TermsQuery fieldName=\"lux_elt_name\">ACT</TermsQuery></Clause><Clause occurs=\"must\"><QNameTextQuery fieldName=\"lux_elt_text\" qName=\"ACT\">content</QNameTextQuery></Clause></BooleanQuery>";
        case ACT_SCENE_CONTENT:
        case ACT_SCENE_CONTENT1:
            return "<BooleanQuery>" +
                    "<Clause occurs=\"must\"><BooleanQuery>" +
                      "<Clause occurs=\"must\"><TermsQuery fieldName=\"lux_elt_name\">SCENE</TermsQuery></Clause>" + 
            		  "<Clause occurs=\"must\"><TermsQuery fieldName=\"lux_elt_name\">ACT</TermsQuery></Clause>" +
                      "</BooleanQuery></Clause>" +
                    "<Clause occurs=\"must\"><QNameTextQuery fieldName=\"lux_elt_text\" qName=\"SCENE\">content</QNameTextQuery></Clause>" +
            		"</BooleanQuery>";
    
        case ACT_ID_123:
            return "<BooleanQuery><Clause occurs=\"must\">" +
            		 "<BooleanQuery>" +
            		  "<Clause occurs=\"must\"><TermsQuery fieldName=\"lux_att_name\">id</TermsQuery></Clause>" +
            		  "<Clause occurs=\"must\"><TermsQuery fieldName=\"lux_elt_name\">ACT</TermsQuery></Clause>" +
            		 "</BooleanQuery></Clause>" +
            		 "<Clause occurs=\"must\"><QNameTextQuery fieldName=\"lux_att_text\" qName=\"id\">123</QNameTextQuery></Clause>" +
            		"</BooleanQuery>";
        case ACT_SCENE_ID_123:
            return "<BooleanQuery>" +
            		 "<Clause occurs=\"must\">" +
            		  "<BooleanQuery>" +
            		   "<Clause occurs=\"must\">" +
            		    "<BooleanQuery>" +
            		     "<Clause occurs=\"must\"><TermsQuery fieldName=\"lux_att_name\">id</TermsQuery></Clause>" +
            		     "<Clause occurs=\"must\"><TermsQuery fieldName=\"lux_elt_name\">SCENE</TermsQuery></Clause>" +
            		     "</BooleanQuery>" +
            		    "</Clause>" +
            		    "<Clause occurs=\"must\"><TermsQuery fieldName=\"lux_elt_name\">ACT</TermsQuery></Clause>" +
            		   "</BooleanQuery>" +
            		  "</Clause>" +
            		  "<Clause occurs=\"must\"><QNameTextQuery fieldName=\"lux_att_text\" qName=\"id\">123</QNameTextQuery></Clause>" +
            		"</BooleanQuery>";

        default:
            return super.getQueryXml(q);    
        }
    }
    
}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
