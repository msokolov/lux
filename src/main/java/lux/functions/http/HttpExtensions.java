package lux.functions.http;

import lux.exception.LuxHttpException;
import lux.functions.Function;
import net.sf.saxon.s9api.ExtensionFunction;
import net.sf.saxon.s9api.ItemType;
import net.sf.saxon.s9api.OccurrenceIndicator;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.SequenceType;
import net.sf.saxon.s9api.XdmValue;
import net.sf.saxon.value.IntegerValue;

/**
 
 * EXPERIMENTAL - not yet working
 * 
 * <code>http:sendRedirect($location as xs:string) as xs:string</code>
 * <p>Causes the container to send a 302 HTTP response with Location header as given. 
 * Does not return.  Declared as returning string so that it isn't optimized
 * out of existence.</p>

 * <code>http:sendError($status as xs:integer, $message as xs:string) as xs:string</code>
 * <p>Causes the container to send send an HTTP response with the given status code,
 * with the message as the body. Does not return.  Declared as returning string so that it isn't optimized
 * out of existence.</p>
 * 
 */
public class HttpExtensions {
    
    public static final String LUX_HTTP_NS = "http://luxproject.net/http";
    
    public static void registerFunctions (Processor processor) {
        processor.registerExtensionFunction(fnSendRedirect());
        processor.registerExtensionFunction(fnSendError());
    }
    
    private static ExtensionFunction fnSendRedirect () {
        return new Function (
                new QName ("http", LUX_HTTP_NS, "sendRedirect"),
                SequenceType.makeSequenceType(ItemType.STRING, OccurrenceIndicator.ONE),
                new SequenceType[] { SequenceType.makeSequenceType(ItemType.STRING, OccurrenceIndicator.ONE)})
        {
            @Override
            public XdmValue call(XdmValue[] arguments) throws LuxHttpException {
                String location = arguments[0].itemAt(0).getStringValue();
                throw new LuxHttpException (302, location);
            }
        };
    }

    private static ExtensionFunction fnSendError () {
        return new Function (
                new QName ("http", LUX_HTTP_NS, "sendError"),
                SequenceType.makeSequenceType(ItemType.STRING, OccurrenceIndicator.ONE),
                new SequenceType[] { 
                    SequenceType.makeSequenceType(ItemType.INTEGER, OccurrenceIndicator.ONE),
                    SequenceType.makeSequenceType(ItemType.STRING, OccurrenceIndicator.ONE)})
        {
            @Override
            public XdmValue call(XdmValue[] arguments) throws SaxonApiException {
                int code = ((IntegerValue)arguments[0].itemAt(0).getUnderlyingValue()).asBigInteger().intValue();
                String message = arguments[1].itemAt(0).getStringValue();
                throw new LuxHttpException (code, message);
            }
        };
    }
    
}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
