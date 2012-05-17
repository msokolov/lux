/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */

package lux.gen;

import net.sf.saxon.expr.Expression;

import org.junit.Test;

public class TestExprGen {
    
    String[] tags = new String[] { "foo", "bar" };
    String[] terms = new String[] { "cat", "dog" };

    @Test
    public void testAllExpressions () throws Exception {
        // Make sure all of the Expressions are correctly modeled
        // by creating one of each
        ExprGen gen = new ExprGen(terms, tags);
        for (ExprGen.ExprTemplate template : ExprGen.templates) {
            Expression expr = gen.createRandomExpression (template, 0);
            System.out.println (template + ": " + expr);
        }
    }
    
    @Test
    public void testRandomExpression () throws Exception {    
        ExprGen gen = new ExprGen(terms, tags);
        for (int i = 0; i < 100; i++) {
            Expression expr = gen.next(1);
            System.out.println (expr);
        }
    }
    
    @Test
    public void testBreederInit () throws Exception {
        ExprBreeder breeder = new ExprBreeder(new ExprGen(terms, tags));
        for (Expression expr : breeder.getPrimitives()) {
            System.out.println (expr);
        }
    }

}
