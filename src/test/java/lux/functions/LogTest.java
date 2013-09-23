package lux.functions;

import static org.junit.Assert.*;

import java.io.File;
import java.io.StringWriter;

import lux.Evaluator;
import lux.XdmResultSet;
import net.sf.saxon.s9api.XdmEmptySequence;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.SimpleLayout;
import org.apache.log4j.WriterAppender;
import org.junit.Before;
import org.junit.Test;

public class LogTest {
	
	private Evaluator eval;
	
	private StringWriter buf;
	
	@Before public void init () {
		eval = new Evaluator();
		Logger logger = Logger.getLogger("lux.functions");
		buf = new StringWriter();
		WriterAppender appender = new WriterAppender();
		appender.setWriter(buf);
		appender.setLayout(new SimpleLayout());
		logger.addAppender(appender);
		logger.setLevel(Level.INFO);
	}
	
	@Test
	public void testLogFunction () {
		XdmResultSet result = eval.evaluate("(lux:log('info'), lux:log('info2', 'info')," +
				"lux:log('debug', 'debug'), lux:log('error', 'error'), lux:log('warn', 'warn')," +
				"lux:log(('fatal', 'error'), 'fatal'), lux:log('trace', 'trace'))");
		assertEquals (XdmEmptySequence.getInstance(), result.getXdmValue());
		if (File.separatorChar == '\\') {
		    // ie Windows
	        assertEquals ("INFO - info\r\nINFO - info2\r\nERROR - error\r\nWARN - warn\r\nERROR - fatalerror\r\n", buf.getBuffer().toString());
		} else {
	        assertEquals ("INFO - info\nINFO - info2\nERROR - error\nWARN - warn\nERROR - fatalerror\n", buf.getBuffer().toString());
		}		
		result = eval.evaluate("lux:log('bogus','bobo')");
		assertTrue (! result.getErrors().isEmpty());
	}

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
