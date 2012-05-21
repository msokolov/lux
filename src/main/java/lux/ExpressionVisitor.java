/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */

package lux;

import lux.xpath.AbstractExpression;
import lux.xpath.BinaryOperation;
import lux.xpath.Dot;
import lux.xpath.FunCall;
import lux.xpath.LiteralExpression;
import lux.xpath.PathExpression;
import lux.xpath.PathStep;
import lux.xpath.Predicate;
import lux.xpath.Root;
import lux.xpath.Sequence;
import lux.xpath.SetOperation;
import lux.xpath.Subsequence;
import lux.xpath.UnaryMinus;
import lux.xquery.AttributeConstructor;
import lux.xquery.Conditional;
import lux.xquery.ElementConstructor;
import lux.xquery.FLWOR;
import lux.xquery.Let;
import lux.xquery.TextConstructor;
import lux.xquery.Variable;

public abstract class ExpressionVisitor {
    private boolean reverse = false;
    /**
     * @return whether the sub-expressions should be visited in reverse (right-to-left)
     * order.
     */
    public boolean isReverse () {
        return reverse;
    }

    public void setReverse (boolean reverse) {
        this.reverse = reverse;
    }
    
    /**
     * @return true if the visit is done; this allows visits to terminate early
     */
    public boolean isDone () {
        return false;
    }

    public abstract AbstractExpression visit (PathStep step);
    public abstract AbstractExpression visit (PathExpression path);
    public abstract AbstractExpression visit (Root root);
    public abstract AbstractExpression visit (Dot dot);
    public abstract AbstractExpression visit (BinaryOperation op);
    public abstract AbstractExpression visit (FunCall func);
    public abstract AbstractExpression visit (LiteralExpression literal);
    public abstract AbstractExpression visit (Predicate predicate);
    public abstract AbstractExpression visit (Sequence predicate);
    public abstract AbstractExpression visit (Subsequence predicate);
    public abstract AbstractExpression visit (SetOperation predicate);
    public abstract AbstractExpression visit (UnaryMinus predicate);
    public abstract AbstractExpression visit (Let let);
    public abstract AbstractExpression visit (Variable variable);
    public abstract AbstractExpression visit (ElementConstructor elementConstructor);
    public abstract AbstractExpression visit (AttributeConstructor attributeConstructor);
    public abstract AbstractExpression visit (TextConstructor textConstructor);
    public abstract AbstractExpression visit (FLWOR flwor);
    public abstract AbstractExpression visit (Conditional conditional);
}
