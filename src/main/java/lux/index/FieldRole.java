package lux.index;

/**
 * Certain fields are known to the indexer and optimizer: these are identified by their FieldRole. 
  */
public  enum FieldRole {
    
    URI("lux_uri"),
    ID("lux_docid"),
    XML_STORE("lux_xml"),
    ELT_QNAME("lux_elt_name"),
    ATT_QNAME("lux_att_name"),
    PATH("lux_path"),
    PATH_VALUE("lux_path_value"),
    QNAME_VALUE("lux_qname_value"),
    ELEMENT_TEXT("lux_elt_text"),
    ATTRIBUTE_TEXT("lux_att_text"),
    XML_TEXT("lux_text");

    // sort keys only
    public static final String LUX_DOCID = "lux:docid";
    public static final String LUX_SCORE = "lux:score";
    
    private String luceneFieldName;
    
    FieldRole (String luceneFieldName) {
        this.luceneFieldName = luceneFieldName;
    }
    
    public String getFieldName () {
        return luceneFieldName;
    }
    
}


/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
