package lux.api;

/**
 * An interface that provides iteration of results and a total count.  Serves as a bridge
 * between java collection apis and saxon's XdmValue api.
 */
public interface ResultSet<T> extends Iterable<T> {
    int size();
    
    Exception getException ();
}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
