/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */

package lux.xpath;

import static org.junit.Assert.assertEquals;
import lux.api.ValueType;
import lux.xpath.BinaryOperation.Operator;
import lux.xpath.PathStep.Axis;
import lux.xquery.ElementConstructor;
import lux.xquery.Let;

import org.junit.Test;

public class TestSerialization {

    private static final QName FOO_QNAME = new QName("foo");

    @Test public void testNodeTestToString () {
        QName foo = FOO_QNAME;
        QName foobar = new QName ("bar", "bar", "foo");
        QName star = new QName ("*");
        QName starstar = new QName ("*", "*", "*");
        QName starfoo = new QName (null, "foo", "*");
        QName foostar = new QName ("#ns", "*", "foo");

        assertEquals ("node()", new NodeTest (ValueType.NODE, null).toString());
        assertEquals ("node()", new NodeTest (ValueType.NODE, foo).toString());
        assertEquals ("node()", new NodeTest (ValueType.NODE, star).toString());
        assertEquals ("node()", new NodeTest (ValueType.NODE, foobar).toString());
        assertEquals ("node()", new NodeTest (ValueType.NODE, foostar).toString());
        assertEquals ("node()", new NodeTest (ValueType.NODE, starfoo).toString());

        assertEquals ("text()", new NodeTest (ValueType.TEXT, null).toString());
        assertEquals ("text()", new NodeTest (ValueType.TEXT, star).toString());
        assertEquals ("text()", new NodeTest (ValueType.TEXT, foo).toString());
        assertEquals ("text()", new NodeTest (ValueType.TEXT, foobar).toString());
        assertEquals ("text()", new NodeTest (ValueType.TEXT, foostar).toString());
        assertEquals ("text()", new NodeTest (ValueType.TEXT, starfoo).toString());

        assertEquals ("element()", new NodeTest (ValueType.ELEMENT, null).toString());
        assertEquals ("*", new NodeTest (ValueType.ELEMENT, star).toString());
        assertEquals ("*:*", new NodeTest (ValueType.ELEMENT, starstar).toString());
        assertEquals ("foo", new NodeTest (ValueType.ELEMENT, foo).toString());
        assertEquals ("foo:bar", new NodeTest (ValueType.ELEMENT, foobar).toString());
        assertEquals ("foo:*", new NodeTest (ValueType.ELEMENT, foostar).toString());
        assertEquals ("*:foo", new NodeTest (ValueType.ELEMENT, starfoo).toString());

        assertEquals ("attribute()", new NodeTest (ValueType.ATTRIBUTE, null).toString());
        assertEquals ("foo", new NodeTest (ValueType.ATTRIBUTE, foo).toString());
        assertEquals ("foo:bar", new NodeTest (ValueType.ATTRIBUTE, foobar).toString());
        assertEquals ("foo:*", new NodeTest (ValueType.ATTRIBUTE, foostar).toString());
        assertEquals ("*:foo", new NodeTest (ValueType.ATTRIBUTE, starfoo).toString());

        assertEquals ("document-node()", new NodeTest (ValueType.DOCUMENT, null).toString());
        assertEquals ("document-node(element(foo))", new NodeTest (ValueType.DOCUMENT, foo).toString());
        assertEquals ("document-node(element(foo:bar))", new NodeTest (ValueType.DOCUMENT, foobar).toString());
        assertEquals ("document-node(element(foo:*))", new NodeTest (ValueType.DOCUMENT, foostar).toString());
        assertEquals ("document-node(element(*:foo))", new NodeTest (ValueType.DOCUMENT, starfoo).toString());

        assertEquals ("comment()", new NodeTest (ValueType.COMMENT, null).toString());
        assertEquals ("comment()", new NodeTest (ValueType.COMMENT, foo).toString());
        assertEquals ("comment()", new NodeTest (ValueType.COMMENT, foobar).toString());
        assertEquals ("comment()", new NodeTest (ValueType.COMMENT, foostar).toString());
        assertEquals ("comment()", new NodeTest (ValueType.COMMENT, starfoo).toString());

        assertEquals ("processing-instruction()", new NodeTest (ValueType.PROCESSING_INSTRUCTION, null).toString());
        assertEquals ("processing-instruction(foo)", new NodeTest (ValueType.PROCESSING_INSTRUCTION, foo).toString());
        assertEquals ("processing-instruction(bar)", new NodeTest (ValueType.PROCESSING_INSTRUCTION, foobar).toString());
        assertEquals ("processing-instruction(*)", new NodeTest (ValueType.PROCESSING_INSTRUCTION, foostar).toString());
        assertEquals ("processing-instruction(foo)", new NodeTest (ValueType.PROCESSING_INSTRUCTION, starfoo).toString());
    }
    
    @Test public void testPathStepToString () {
        NodeTest foo = new NodeTest (ValueType.ELEMENT, FOO_QNAME);
        assertEquals ("self::foo", new PathStep (Axis.Self, foo).toString());
        assertEquals ("child::foo", new PathStep (Axis.Child, foo).toString());
        assertEquals ("parent::foo", new PathStep (Axis.Parent, foo).toString());
        assertEquals ("ancestor::foo", new PathStep (Axis.Ancestor, foo).toString());
        assertEquals ("ancestor-or-self::foo", new PathStep (Axis.AncestorSelf, foo).toString());
        assertEquals ("descendant::foo", new PathStep (Axis.Descendant, foo).toString());
        assertEquals ("descendant-or-self::foo", new PathStep (Axis.DescendantSelf, foo).toString());
        assertEquals ("preceding::foo", new PathStep (Axis.Preceding, foo).toString());
        assertEquals ("preceding-sibling::foo", new PathStep (Axis.PrecedingSibling, foo).toString());
        assertEquals ("following::foo", new PathStep (Axis.Following, foo).toString());
        assertEquals ("following-sibling::foo", new PathStep (Axis.FollowingSibling, foo).toString());
        
        assertEquals ("attribute::att", new PathStep (Axis.Attribute, new NodeTest (ValueType.ATTRIBUTE, new QName("att"))).toString());
        assertEquals ("attribute::foo", new PathStep (Axis.Attribute, foo).toString());
        
        NodeTest node = new NodeTest (ValueType.NODE);
        assertEquals ("self::node()", new PathStep (Axis.Self, node).toString());
        assertEquals ("ancestor::document-node()", new PathStep (Axis.Ancestor, new NodeTest (ValueType.DOCUMENT)).toString());
    }
    
    @Test public void testPredicateToString () {
        PathStep step = new PathStep (Axis.Child, new NodeTest(ValueType.ELEMENT));
        Predicate p = new Predicate (step, step);
        assertEquals ("child::element()[child::element()]", p.toString());
    }
    
    @Test public void testRootToString () {
        assertEquals ("(/)", new Root().toString());
    }
    
    @Test public void testDotToString () {
        assertEquals (".", new Dot().toString());
    }
    
    @Test public void testFunctionCallToString() {
        FunCall fun = new FunCall (FOO_QNAME, ValueType.VALUE, new LiteralExpression ("bar"));
        assertEquals ("foo(\"bar\")", fun.toString());

        FunCall fun2 = new FunCall (FOO_QNAME, ValueType.VALUE, new LiteralExpression ("bar"), new LiteralExpression("baz"));
        assertEquals ("foo(\"bar\",\"baz\")", fun2.toString());        

        FunCall fun3 = new FunCall (FunCall.FN_EXISTS, ValueType.BOOLEAN, new Sequence(LiteralExpression.ONE, LiteralExpression.ONE));
        assertEquals ("fn:exists((1,1))", fun3.toString());
    }
    
    @Test public void testSequenceToString() {
        Sequence seq= new Sequence(new LiteralExpression ("bar"), new LiteralExpression("baz"));
        assertEquals ("(\"bar\",\"baz\")", seq.toString());        
    }
    
    @Test public void testSubsequenceToString () {
        Subsequence subseq = new Subsequence(new Dot(), LiteralExpression.ONE);
        assertEquals ("subsequence(.,1)", subseq.toString());
        subseq = new Subsequence(new Dot(), LiteralExpression.ONE, new LiteralExpression(10));
        assertEquals ("subsequence(.,1,10)", subseq.toString());
        subseq = new Subsequence(new Dot(), FunCall.LastExpression, LiteralExpression.ONE);
        assertEquals (".[fn:last()]", subseq.toString());
        subseq = new Subsequence(new Dot(), LiteralExpression.ONE, LiteralExpression.ONE);
        assertEquals (".[1]", subseq.toString());
    }
    
    @Test public void testLetToString () {
        Let let = new Let (new QName("x"), new LiteralExpression("bar"), new LiteralExpression("foo"));
        assertEquals ("let $x := \"bar\" return \"foo\"", let.toString());
    }
    
    @Test public void testBinaryOperationToString () {
        LiteralExpression one = LiteralExpression.ONE;
        assertEquals ("1 = 1", new BinaryOperation(one, Operator.EQUALS, one).toString());
        assertEquals ("1 != 1", new BinaryOperation(one, Operator.NE, one).toString());
        assertEquals ("1 > 1", new BinaryOperation(one, Operator.GT, one).toString());
        assertEquals ("1 < 1", new BinaryOperation(one, Operator.LT, one).toString());
        assertEquals ("1 >= 1", new BinaryOperation(one, Operator.GE, one).toString());
        assertEquals ("1 <= 1", new BinaryOperation(one, Operator.LE, one).toString());

        assertEquals ("1 eq 1", new BinaryOperation(one, Operator.AEQ, one).toString());
        assertEquals ("1 ne 1", new BinaryOperation(one, Operator.ANE, one).toString());
        assertEquals ("1 gt 1", new BinaryOperation(one, Operator.AGT, one).toString());
        assertEquals ("1 lt 1", new BinaryOperation(one, Operator.ALT, one).toString());
        assertEquals ("1 ge 1", new BinaryOperation(one, Operator.AGE, one).toString());
        assertEquals ("1 le 1", new BinaryOperation(one, Operator.ALE, one).toString());
        
        LiteralExpression foo = new LiteralExpression ("foo");
        LiteralExpression two = new LiteralExpression (2);
        LiteralExpression pi = new LiteralExpression (3.14);
        assertEquals ("2 * xs:double(3.14)", new BinaryOperation(two, Operator.MUL, pi).toString());
        assertEquals ("\"foo\" = \"foo\"", new BinaryOperation(foo, Operator.EQUALS, foo).toString());           
    }
    
    @Test public void testElementToString () {
        ElementConstructor e = new ElementConstructor (FOO_QNAME);
        assertEquals ("<foo />", e.toString());
        e = new ElementConstructor (FOO_QNAME, new Namespace [] {
                new Namespace ("", "default"),
                new Namespace ("lux", "lux")
        }, new ElementConstructor (FOO_QNAME));
        assertEquals ("<foo xmlns=\"default\" xmlns:lux=\"lux\"><foo /></foo>", e.toString());   
    }
    
    @Test public void testLiteralExpressionToString () {
        assertEquals ("1", LiteralExpression.ONE.toString());
        assertEquals ("\"1\"", new LiteralExpression("1").toString());
        assertEquals ("\"&amp;\"", new LiteralExpression("&").toString());
        assertEquals ("\"&lt;\"", new LiteralExpression("<").toString());
    }
    
    // TODO: add more serialization tests
    // testAttributeToString
    // testTextToString
    // testFLWORToString
}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
