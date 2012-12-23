package lux;

import java.util.ArrayList;

import javax.xml.transform.ErrorListener;
import javax.xml.transform.TransformerException;

/**
 * Captures errors produced by Saxon during compilation and evaluation of queries.
 * Maintains a list of TransformerExceptions.
 */
public class TransformErrorListener implements ErrorListener {
    
    public TransformErrorListener () {
        errors = new ArrayList<TransformerException>();
    }
    
    private ArrayList<TransformerException> errors;

    public ArrayList<TransformerException> getErrors() {
        return errors;
    }
    
    public void clear () {
        errors.clear();
    }

    @Override
    public void error(TransformerException exception) throws TransformerException {
        errors.add(exception);
    }

    @Override
    public void fatalError(TransformerException exception) throws TransformerException {
        while (exception != exception.getCause() && exception.getCause() != null
                && exception.getCause() instanceof TransformerException) {
            exception = (TransformerException) exception.getCause();
        }
        errors.add(exception);
    }

    @Override
    public void warning(TransformerException exception) throws TransformerException {
        errors.add(exception);
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
