package lux.saxon;

import java.io.StringReader;
import java.math.BigDecimal;
import java.math.BigInteger;

import lux.api.Evaluator;
import lux.saxon.Saxon.Dialect;
import lux.solr.XmlSearchComponent;
import lux.xml.XmlBuilder;
import net.sf.saxon.s9api.XdmAtomicValue;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmNodeKind;
import net.sf.saxon.value.BooleanValue;
import net.sf.saxon.value.DoubleValue;
import net.sf.saxon.value.FloatValue;
import net.sf.saxon.value.QNameValue;

import org.apache.solr.common.util.NamedList;

public class SaxonSearchComponent extends XmlSearchComponent {

    @Override
    public Evaluator createEvaluator() {
        return new Saxon(null, null, Dialect.XQUERY_1);
    }

    @Override
    public Object buildDocument(String xml, XmlBuilder builder) {
        return builder.build(new StringReader (xml));
    }

    @Override
    public void addResult(NamedList<Object> xpathResults, Object item) {
        addResult (xpathResults, (XdmItem) item);
    }
    
    private void addResult(NamedList<Object> xpathResults, XdmItem item) {
        if (item.isAtomicValue()) {
            XdmAtomicValue xdmValue = (XdmAtomicValue) item;
            Object value = xdmValue.getUnderlyingValue();
            if (value instanceof String) {
                xpathResults.add ("xs:string", xdmValue.toString());
            } else if (value instanceof BigInteger) {
                xpathResults.add ("xs:int", xdmValue.toString());
            } else if (value instanceof DoubleValue) {
                xpathResults.add ("xs:double", xdmValue.toString());
            } else if (value instanceof FloatValue) {
                xpathResults.add ("xs:float", xdmValue.toString());
            } else if (value instanceof BooleanValue) {
                xpathResults.add ("xs:boolean", xdmValue.toString());
            } else if (value instanceof BigDecimal) {
                xpathResults.add ("xs:decimal", xdmValue.toString());
            } else if (value instanceof QNameValue) {
                xpathResults.add ("xs:QName", xdmValue.toString());
            } else  {
                // no way to distinguish xs:anyURI, xs:untypedAtomic or um something else
                xpathResults.add ("xs:string", xdmValue.toString());
            } 
        } else {
            XdmNode node = (XdmNode) item;
            XdmNodeKind nodeKind = node.getNodeKind();
            xpathResults.add(nodeKind.toString().toLowerCase(), node.toString());
        }
    }
    
}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
