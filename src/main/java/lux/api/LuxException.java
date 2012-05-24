/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */

package lux.api;

public class LuxException extends RuntimeException {
    
    public LuxException (Throwable t) {
        super (t);
    }
    
    public LuxException (String msg, Throwable t) {
        super (msg, t);
    }

    public LuxException(String msg) {
        super (msg);
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
