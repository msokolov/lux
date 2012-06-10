package lux.xqts;

import javax.xml.transform.ErrorListener;
import javax.xml.transform.TransformerException;

/**
 * Provides control over whether error messages from Saxon are to be printed to System.err or not
 * when installed using {@link Configuration#setErrorListener}.
 *
 */
class ErrorIgnorer implements ErrorListener {
    
    private boolean showErrors = false;
    
    public void warning(TransformerException exception) throws TransformerException {
        if (showErrors) {
            System.err.println (exception.getMessageAndLocation());
        }
    }

    public void setShowErrors(boolean b) {
        this.showErrors = b;    
    }

    public void error(TransformerException exception) throws TransformerException {
        if (showErrors) {
            System.err.println (exception.getMessageAndLocation());
        }
    }

    public void fatalError(TransformerException exception) throws TransformerException {
        if (showErrors) {
            System.err.println (exception.getMessageAndLocation());
        }
    }
    
}


/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
