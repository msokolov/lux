package lux.api;

import lux.xpath.AbstractExpression;
import lux.xquery.XQuery;

/*
 * Represents an XPath or XQuery expression, or an XQuery main module.
 */
public interface Expression {
    
    public AbstractExpression getXPath();
    
    public XQuery getXQuery();
    
}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
