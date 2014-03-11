package lux.functions;

import lux.search.SearchService;
import lux.xpath.FunCall;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.BooleanValue;
import net.sf.saxon.value.SequenceType;

/**
 * <code>function lux:exists($query as item()) as xs:integer</code>
 *<p>
 * This function tests whether a search has any results.  It is faster and uses less memory 
 * than calling fn:exists() on the search results themselves because it does not need to load
 * any result documents in memory.  See {@link Search} for an explanation of the supported
 * $query formats.
 * </p>
 */
public class Exists extends SearchBase {
    
    public Exists() { }

    @Override
    public StructuredQName getFunctionQName() {
        return new StructuredQName("lux", FunCall.LUX_NAMESPACE, "exists");
    }

    @Override
    public SequenceType getResultType(SequenceType[] suppliedArgumentTypes) {
        return SequenceType.SINGLE_BOOLEAN;
    }
    
    @Override
    public boolean trustResultType () {
        return true;
    }
    
    @Override
    public Sequence iterate(SearchService searchService, Item query, String[] sortCriteria, int start) throws XPathException {
        boolean exists = searchService.exists(query);
        if (exists) {
            ++ searchService.getEvaluator().getQueryStats().docCount;
        }
        return BooleanValue.get(exists);
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
