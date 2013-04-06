package lux.functions;

import net.sf.saxon.s9api.Processor;

public final class ExtensionFunctions {
    
    public static void registerFunctions (Processor processor) {
        processor.registerExtensionFunction(new Log());
    }
    
}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
