/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */

package lux;

import org.apache.lucene.document.FieldSelector;
import org.apache.lucene.document.FieldSelectorResult;

public final class SingleFieldSelector implements FieldSelector {

    private final String fieldName;

    public SingleFieldSelector (String fieldName) {
        this.fieldName = fieldName;
    }

    public FieldSelectorResult accept(String fieldName) {
        return this.fieldName.equals(fieldName) ? FieldSelectorResult.LOAD : FieldSelectorResult.NO_LOAD;
    }

}

