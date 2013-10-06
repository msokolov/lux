package lux.index.field;

import java.util.Collections;
import java.util.Set;

import lux.index.XmlIndexer;

import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.LongField;

/** A 64-bit generated field that we try to make globally unique; used for sorting
 * results into XQuery "document order"
 *
 * This definition exists only for the purpose of defining and declaring the field
 * the value is never to be retrieved from the XmlIndexer
 *
 */
public class IDField extends FieldDefinition {
    
    private static final IDField instance = new IDField();
    
    public static IDField getInstance() {
        return instance;
    }
    
    protected IDField () {
        super ("lux_docid", null, Store.YES, Type.LONG, false);
    }
    
    /**
     * @return an empty set. This field's value is always internally generated.
     */
    @Override
    public Set<LongField> getFieldValues(XmlIndexer indexer) {
        return Collections.emptySet();
    }
    
    @Override
    public boolean isSingleValued() {
        return true;
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
