package lux.functions;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.io.IOException;
import java.util.Collection;

import javax.xml.transform.TransformerException;

import lux.Evaluator;
import lux.XdmResultSet;

import org.apache.commons.io.IOUtils;
import org.junit.BeforeClass;

public abstract class XQueryTest {

    protected static Evaluator evaluator;

    @BeforeClass
    public static void setup() throws Exception {
        evaluator = new Evaluator();
    }

    protected void assertXQueryFile(String result, String queryFile) throws IOException {
        assertXQueryFile (result, queryFile, null);
    }

    protected void assertXQueryFile(String result, String queryFile, String firstError) throws IOException {
        String query = IOUtils.toString(TransformTest.class.getResourceAsStream(queryFile));
        assertXQuery (result, query, firstError);
    }

    protected void assertXQuery(String result, String query) {
        assertXQuery (result, query, null);
    }

    protected void assertXQuery(String result, String query, String firstError) {
        XdmResultSet results = evaluator.evaluate(query);
        if (result == null) {
            assertEquals (0, results.size());
            if (firstError != null) {
                assertEquals (firstError, results.getErrors().iterator().next().getMessage());
            } else {
                Collection<TransformerException> errors = results.getErrors();
                if (errors != null && ! errors.isEmpty()) {
                    assertFalse
                        ("got errors " + errors.iterator().next().getMessage(),
                        true);
                }
            }
            return;
        }
        assertEquals (1, results.size());
        assertEquals (result, results.iterator().next().toString());
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
