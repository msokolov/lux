package lux.xquery;

import lux.xpath.AbstractExpression;
import lux.xpath.Visitable;


public abstract class FLWORClause implements Visitable {
    
    public abstract AbstractExpression getSequence ();
    
    public abstract void setSequence (AbstractExpression seq);
    
    public abstract void toString (StringBuilder buf);

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
