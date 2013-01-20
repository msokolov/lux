package lux.xpath;

import lux.xquery.AttributeConstructor;
import lux.xquery.CastableExpression;
import lux.xquery.CommentConstructor;
import lux.xquery.ComputedElementConstructor;
import lux.xquery.Conditional;
import lux.xquery.DocumentConstructor;
import lux.xquery.ElementConstructor;
import lux.xquery.FLWOR;
import lux.xquery.ForClause;
import lux.xquery.FunctionDefinition;
import lux.xquery.InstanceOf;
import lux.xquery.Let;
import lux.xquery.LetClause;
import lux.xquery.OrderByClause;
import lux.xquery.ProcessingInstructionConstructor;
import lux.xquery.Satisfies;
import lux.xquery.TextConstructor;
import lux.xquery.TreatAs;
import lux.xquery.Variable;
import lux.xquery.WhereClause;

public abstract class ExpressionVisitor {
    
    private boolean reverse = false;
    /**
     * @return true if the visit is done; this allows visits to terminate early
     */
    public boolean isDone () {
        return false;
    }

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

    public abstract AbstractExpression visit (AttributeConstructor attributeConstructor);
    public abstract AbstractExpression visit (BinaryOperation op);
    public abstract AbstractExpression visit (CastableExpression castable);
    public abstract AbstractExpression visit (CommentConstructor commentConstructor);
    public abstract AbstractExpression visit (ComputedElementConstructor computedElementConstructor);
    public abstract AbstractExpression visit (Conditional conditional);
    public abstract AbstractExpression visit (DocumentConstructor documentConstructor);
    public abstract AbstractExpression visit (Dot dot);
    public abstract AbstractExpression visit (ElementConstructor elementConstructor);
    public abstract AbstractExpression visit (FLWOR flwor);
    public abstract ForClause visit (ForClause forClause);
    public abstract AbstractExpression visit (FunCall func);
    public abstract AbstractExpression visit(FunctionDefinition func);
    public abstract AbstractExpression visit (InstanceOf instanceOf);
    public abstract AbstractExpression visit (Let let);
    public abstract LetClause visit (LetClause letClause);
    public abstract AbstractExpression visit (LiteralExpression literal);
    public abstract OrderByClause visit (OrderByClause orderByClause);
    public abstract AbstractExpression visit (PathExpression path);
    public abstract AbstractExpression visit (PathStep step);
    public abstract AbstractExpression visit (Predicate predicate);
    public abstract AbstractExpression visit (ProcessingInstructionConstructor processingInstructionConstructor);
    public abstract AbstractExpression visit (Root root);
    public abstract AbstractExpression visit (Satisfies satisfies);
    public abstract AbstractExpression visit (Sequence sequence);
    public abstract AbstractExpression visit (SetOperation setOperation);
    public abstract AbstractExpression visit (Subsequence subsequence);
    public abstract AbstractExpression visit (TextConstructor textConstructor);
    public abstract AbstractExpression visit (TreatAs treat);
    public abstract AbstractExpression visit (UnaryMinus predicate);
    public abstract AbstractExpression visit (Variable variable);
    public abstract WhereClause visit (WhereClause whereClause);

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
