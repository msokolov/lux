package lux.functions;

import lux.search.SearchService;
import lux.xpath.FunCall;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.Int64Value;
import net.sf.saxon.value.SequenceType;

/**
 * <code>function lux:count($query as item()) as xs:integer</code>
 * <p>
 * This function counts the number of results of a search.  It is faster and uses less memory 
 * than calling fn:count() on the search results themselves because it does not need to load
 * the result documents in memory.  See {@link Search} for an explanation of the supported
 * $query formats.
 * </p>
 */
public class Count extends SearchBase {
    
    public Count() { }
    
    @Override
    public StructuredQName getFunctionQName() {
        return new StructuredQName("lux", FunCall.LUX_NAMESPACE, "count");
    }

    @Override
    public SequenceType getResultType(SequenceType[] suppliedArgumentTypes) {
        return SequenceType.SINGLE_INTEGER;
    }
    
    @Override
    public boolean trustResultType () {
        return true;
    }
    
    /**
     * Count the search results
     *
     * @param searchService 
     * @param queryArg the query to execute
     * @return an iterator with the results of executing the query and applying the
     * expression to its result.
     * @throws XPathException
     */
    @Override
    public Sequence iterate(final SearchService searchService, final Item queryArg, final String[] sortCriteria, final int start) throws XPathException {        
        long count = searchService.count(queryArg);
        searchService.getEvaluator().getQueryStats().docCount += count;
        return new Int64Value(count);
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
