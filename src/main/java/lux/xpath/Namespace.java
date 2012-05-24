package lux.xpath;

public class Namespace {
    private final String prefix;
    private final String namespace;
    
    public Namespace (String prefix, String namespace) {
        this.prefix = prefix;
        this.namespace = namespace;
    }
    
    public String getPrefix () {
        return prefix;
    }
    
    public String getNamespace () {
        return namespace;
    }
}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
