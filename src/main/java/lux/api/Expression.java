package lux.api;

import lux.XPathQuery;

/*
 * Represents an XPath or XQuery expression to be evaluated.
 */
public interface Expression {

    XPathQuery getXPathQuery();
}
