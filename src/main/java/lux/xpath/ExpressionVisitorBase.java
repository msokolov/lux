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

public abstract class ExpressionVisitorBase extends ExpressionVisitor {
    
    /**
     * This method is called by every visit() method in this class. 
     * Subclasses may extend this convenience method in order to provide a default behavior for all
     * expressions for which they don't provide an explicit visit() override.
     * @param expr an expression to visit
     * @return the expression
     */
    protected AbstractExpression visitDefault (AbstractExpression expr) {
        return expr;
    }
    
    protected AbstractExpression visitSubs (AbstractExpression expr) {
        for (int i = 0; i < expr.getSubs().length; i++) {
            AbstractExpression sub = expr.getSubs()[i].accept(this);
            if (sub != expr.getSubs()[i]) {
                expr.getSubs()[i] = sub;
            }
        }
        return expr;
    }

    @Override
    public AbstractExpression visit(AttributeConstructor attributeConstructor) {
        return visitDefault (attributeConstructor);
    }

    @Override
    public AbstractExpression visit(BinaryOperation op) {
        return visitDefault (op);
    }
    
    @Override
    public AbstractExpression visit(CastableExpression cast) {
        return visitDefault(cast);
    }
    

    @Override
    public AbstractExpression visit(CommentConstructor comment) {
        return visitDefault (comment);
    }
    
    @Override
    public AbstractExpression visit(ComputedElementConstructor element) {
        return visitDefault (element);
    }

    @Override
    public AbstractExpression visit(Conditional cond) {
        return visitDefault (cond);
    }
    
    @Override
    public AbstractExpression visit(DocumentConstructor documentConstructor) {
        return visitDefault (documentConstructor);
    }

    @Override
    public AbstractExpression visit(Dot dot) {
        return visitDefault (dot);
    }

    @Override
    public AbstractExpression visit(ElementConstructor elementConstructor) {
        return visitDefault (elementConstructor);
    }

    @Override
    public AbstractExpression visit(FLWOR flwor) {
        return visitDefault (flwor);
    }

    @Override
    public ForClause visit (ForClause forClause) {
        return forClause;
    }

    @Override
    public AbstractExpression visit(FunCall func) {
        return visitDefault (func);
    }
    
    @Override
    public AbstractExpression visit(FunctionDefinition func) {
        return visitDefault (func);
    }

    @Override
    public AbstractExpression visit(InstanceOf expr) {
        return visitDefault (expr);
    }

    @Override
    public AbstractExpression visit(Let let) {
        return visitDefault (let);
    }

    @Override
    public LetClause visit (LetClause letClause) {
        return letClause;
    }

    @Override
    public AbstractExpression visit(LiteralExpression literal) {
        return visitDefault (literal);
    }

    @Override
    public OrderByClause visit (OrderByClause orderByClause) {
        return orderByClause;
    }

    @Override
    public AbstractExpression visit(PathExpression path) {
        return visitDefault (path);
    }
    
    @Override
    public AbstractExpression visit(PathStep step) {
        return visitDefault (step);
    }

    @Override
    public AbstractExpression visit(Predicate predicate) {
        return visitDefault (predicate);
    }
    
    @Override
    public AbstractExpression visit(ProcessingInstructionConstructor pi) {
        return visitDefault (pi);
    }

    @Override
    public AbstractExpression visit(Root root) {
        return visitDefault (root);
    }
    
    @Override
    public AbstractExpression visit(Satisfies satisfies) {
        return visitDefault (satisfies);
    }

    @Override
    public AbstractExpression visit(Sequence seq) {
        return visitDefault (seq);
    }
    
    @Override
    public AbstractExpression visit(SetOperation setop) {
        return visitDefault (setop);
    }
    
    @Override
    public AbstractExpression visit(Subsequence subseq) {
        return visitDefault (subseq);
    }
    
    @Override
    public AbstractExpression visit(TreatAs treat) {
        return visitDefault(treat);
    }
    
    @Override
    public AbstractExpression visit(TextConstructor textConstructor) {
        return visitDefault (textConstructor);
    }
    
    @Override
    public AbstractExpression visit(UnaryMinus unaryMinus) {
        return visitDefault (unaryMinus);
    }

    @Override
    public AbstractExpression visit(Variable var) {
        return visitDefault (var);
    }

    @Override
    public WhereClause visit (WhereClause whereClause) {
        return whereClause;
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
