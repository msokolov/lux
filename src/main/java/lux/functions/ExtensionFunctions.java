package lux.functions;

import lux.xpath.FunCall;
import net.sf.saxon.s9api.ExtensionFunction;
import net.sf.saxon.s9api.ItemType;
import net.sf.saxon.s9api.OccurrenceIndicator;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.SequenceType;
import net.sf.saxon.s9api.XdmEmptySequence;
import net.sf.saxon.s9api.XdmSequenceIterator;
import net.sf.saxon.s9api.XdmValue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>This class registers a number of ExtensionFunctions, including:</p>
 * 
 * <code>lux:log($message as xs:string+, $level as xs:string?) as empty-sequence()</code>
 * <p>Writes a log message formed by joining the argument sequence
 * with single spaces.  The log level is INFO by default: valid values for $level
 * are TRACE, DEBUG, INFO, WARN, ERROR, FATAL: these correspond to the slf4j log levels.
 * The level argument is case-insensitive.</p>
 *
 */
public class ExtensionFunctions {
    
    public static void registerFunctions (Processor processor) {
        processor.registerExtensionFunction(createLogFunction());
    }

    private enum LogLevel { TRACE, DEBUG, INFO, WARN, ERROR, FATAL };
    
    private static ExtensionFunction createLogFunction() {
        
        return new Function (
                new QName ("lux", FunCall.LUX_NAMESPACE, "log"),
                SequenceType.makeSequenceType(ItemType.ANY_ITEM, OccurrenceIndicator.ZERO_OR_ONE),
                new SequenceType[] { 
                    SequenceType.makeSequenceType(ItemType.ANY_ITEM, OccurrenceIndicator.ONE_OR_MORE),
                    SequenceType.makeSequenceType(ItemType.STRING, OccurrenceIndicator.ZERO_OR_ONE)    
                })
        {
            
            @Override
            public XdmValue call(XdmValue[] arguments) throws SaxonApiException {
                Logger logger = LoggerFactory.getLogger("lux.functions");
                LogLevel level;
                if (arguments.length > 1) {
                    String lvl = arguments[1].itemAt(0).getStringValue();
                    try {
                        level = LogLevel.valueOf(lvl.toUpperCase());
                    } catch (IllegalArgumentException e) {
                        throw new SaxonApiException("Undefined log level: " + lvl);
                    }
                } else {
                    level = LogLevel.INFO;
                }
                if (! isLogEnabled(logger, level)) {
                    return XdmEmptySequence.getInstance();
                }
                XdmSequenceIterator tokens = arguments[0].iterator();
                StringBuilder message = new StringBuilder();
                if (tokens.hasNext()) {
                    message.append(tokens.next().getStringValue());
                }
                while (tokens.hasNext()) {
                    message.append(' ').append(tokens.next().getStringValue());
                }
                log (logger, level, message.toString());
                return XdmEmptySequence.getInstance();
            }
            
            private void log (Logger logger, LogLevel level, String message) {
                switch (level) {
                case TRACE: logger.trace(message); break;
                case DEBUG: logger.debug(message); break;
                case INFO: logger.info(message); break;
                case WARN: logger.warn(message); break;
                case ERROR: logger.error(message); break;
                default:
                case FATAL: logger.error(message); // slf4j doesn't seem to support fatal?
                }
            }
            
            private boolean isLogEnabled (Logger logger, LogLevel level) {
                switch (level) {
                case TRACE: return logger.isTraceEnabled();
                case DEBUG: return logger.isDebugEnabled();
                case INFO: return logger.isInfoEnabled();
                case WARN: return logger.isWarnEnabled();
                case ERROR: return logger.isErrorEnabled();
                case FATAL: 
                default: return true;
                }
            }
        };
    }
}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
