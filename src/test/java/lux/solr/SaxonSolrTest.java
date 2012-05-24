package lux.solr;

public class SaxonSolrTest extends LuxSolrTest {
    public String getXPathEngine () {
        return "saxon";
    }
    
    public String getSolrSearchPath () {
        return "/xpaths";
    }
}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
