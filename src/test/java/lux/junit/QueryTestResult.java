package lux.junit;

import java.util.List;

import lux.xml.ValueType;

import org.apache.commons.lang.StringUtils;

import net.sf.saxon.s9api.XdmNode;

class QueryTestResult {

    // if true, an error result is expected
    final boolean isError;

    // if present, an error result is expected, and the error text is expected to match
    final String errorText;

    // if present, the compiled optimized query text is expected to match
    final String queryText;

    // The lucene queries expected to be contained in the compiled query
    final List<XdmNode> searchQueries;

    final ValueType resultType;

    final String orderBy;

    // TODO: add search test result attributes (or a separate class?)

    QueryTestResult (boolean isError, String errorText, String queryText, List<XdmNode> queryNode,
                     String resultType, String orderBy) {
        this.isError=isError;
        this.errorText=StringUtils.isEmpty(errorText) ? null : errorText;
        this.queryText=queryText;
        this.searchQueries=queryNode;
        this.resultType= StringUtils.isEmpty(resultType) ? null : ValueType.valueOf(resultType);
        this.orderBy=StringUtils.isEmpty(orderBy) ? null : orderBy;
    }

}
