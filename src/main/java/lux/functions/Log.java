package lux.functions;

import lux.xpath.FunCall;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.lib.ExtensionFunctionCall;
import net.sf.saxon.lib.ExtensionFunctionDefinition;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.EmptySequence;
import net.sf.saxon.value.SequenceType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <code>lux:log($message as xs:string+, $level as xs:string?) as empty-sequence()</code>
 * <p>Writes a log message formed by joining the argument sequence
 * with single spaces.  The log level is INFO by default: valid values for $level
 * are TRACE, DEBUG, INFO, WARN, ERROR, FATAL: these correspond to the slf4j log levels.
 * The level argument is case-insensitive.</p>
 *
 */
public class Log extends ExtensionFunctionDefinition {

	private enum LogLevel { TRACE, DEBUG, INFO, WARN, ERROR, FATAL }
    
	class LogCall extends ExtensionFunctionCall {

		@Override
		public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException
		{
	    	Logger logger = LoggerFactory.getLogger("lux.functions");
	    	LogLevel level;
	    	if (arguments.length > 1) {
	    		String lvl = arguments[1].head().getStringValue();
	    		try {
	    			level = LogLevel.valueOf(lvl.toUpperCase());
	    		} catch (IllegalArgumentException e) {
	    			throw new XPathException("Undefined log level: " + lvl);
	    		}
	    	} else {
	    		level = LogLevel.INFO;
	    	}
	    	if (! isLogEnabled(logger, level)) {
	    		return EmptySequence.getInstance();
	    	}
	    	Sequence tokens = arguments[0];
	    	StringBuilder message = new StringBuilder();
	    	Item item;
            SequenceIterator tokenIter = tokens.iterate();
	    	while ((item = tokenIter.next()) != null) {
	    		message.append(item.getStringValue());
	    	}
	    	log (logger, level, message.toString());
	    	return EmptySequence.getInstance();
		}

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

	@Override
	public StructuredQName getFunctionQName() {
		return new StructuredQName("lux", FunCall.LUX_NAMESPACE, "log");
	}

	@Override
	public net.sf.saxon.value.SequenceType[] getArgumentTypes() {
		return new SequenceType[] { 
                SequenceType.ANY_SEQUENCE,
                SequenceType.OPTIONAL_STRING
            };
	}

	@Override
	public net.sf.saxon.value.SequenceType getResultType(
			net.sf.saxon.value.SequenceType[] suppliedArgumentTypes) {
		return SequenceType.OPTIONAL_ITEM;
	}

	@Override
	public ExtensionFunctionCall makeCallExpression() {
		return new LogCall ();
	}

	@Override
	public int getMinimumNumberOfArguments () {
		return 1;
	}

	@Override
	public int getMaximumNumberOfArguments () {
		return 2;
	}

}
	
