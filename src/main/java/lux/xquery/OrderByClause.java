package lux.xquery;

import java.util.ArrayList;
import java.util.Collections;

import lux.xpath.AbstractExpression;
import lux.xpath.ExpressionVisitor;
import lux.xpath.LiteralExpression;

public class OrderByClause extends FLWORClause {

    private final ArrayList<SortKey> sortKeys;
    
    public OrderByClause(SortKey[] sortKeys) {
        this.sortKeys = new ArrayList<SortKey>();
        Collections.addAll(this.sortKeys, sortKeys);
    }
    
    @Override
    public AbstractExpression getSequence() {
        return LiteralExpression.EMPTY;
    }
    
    @Override
    public void setSequence (AbstractExpression seq) {
    }

    /**
     * @return a *mutable* list of the sort keys.
     */
    public ArrayList<SortKey> getSortKeys () {
        return sortKeys;
    }

    @Override
    public void toString(StringBuilder buf) {
        if (sortKeys.isEmpty()) {
            return;
        }
        buf.append ("order by ");
        sortKeys.get(0).toString(buf);
        for (int i = 1; i < sortKeys.size(); i++) {
            buf.append(", ");
            sortKeys.get(i).toString(buf);
        }
    }

    @Override
    public OrderByClause accept(ExpressionVisitor visitor) {
        for (int i = 0; i < sortKeys.size(); i++) {
            SortKey sk = sortKeys.get(i);
            AbstractExpression key = sk.getKey();
            AbstractExpression key2 = key.accept(visitor);
            if (key != key2) {
                sortKeys.set(i, new SortKey(key2, sk.getOrder(), sk.getCollation(), sk.isEmptyLeast()));
            }
        }
        return visitor.visit(this);
    }
}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
