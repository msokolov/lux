package lux.xquery;

import lux.api.ValueType;

import org.junit.Test;

public class TestSerialization {

    @Test public void testNodeTest () {
        QName foo = new QName ("foo");
        QName foobar = new QName ("bar", "foo", "foo");
        QName star = new QName ("*");
        QName starfoo = new QName (null, "*", "foo");
        QName foostar = new QName ("#ns", "foo", "*");

        assertEquals ("node()", new NodeTest (ValueType.NODE, null));
        assertEquals ("node()", new NodeTest (ValueType.NODE, foo));
        assertEquals ("node()", new NodeTest (ValueType.NODE, foobar));
        assertEquals ("node()", new NodeTest (ValueType.NODE, foostar));
        assertEquals ("node()", new NodeTest (ValueType.NODE, starfoo));

        assertEquals ("text()", new NodeTest (ValueType.TEXT, null));
        assertEquals ("text()", new NodeTest (ValueType.TEXT, foo));
        assertEquals ("text()", new NodeTest (ValueType.TEXT, foobar));
        assertEquals ("text()", new NodeTest (ValueType.TEXT, foostar));
        assertEquals ("text()", new NodeTest (ValueType.TEXT, starfoo));

        assertEquals ("element()", new NodeTest (ValueType.ELEMENT, null));
        assertEquals ("foo", new NodeTest (ValueType.ELEMENT, foo));
        assertEquals ("foo:bar", new NodeTest (ValueType.ELEMENT, foobar));
        assertEquals ("foo:*", new NodeTest (ValueType.ELEMENT, foostar));
        assertEquals ("*:foo", new NodeTest (ValueType.ELEMENT, starfoo));

        assertEquals ("attribute()", new NodeTest (ValueType.ATTRIBUTE, null));
        assertEquals ("foo", new NodeTest (ValueType.ATTRIBUTE, foo));
        assertEquals ("foo:bar", new NodeTest (ValueType.ATTRIBUTE, foobar));
        assertEquals ("foo:*", new NodeTest (ValueType.ATTRIBUTE, foostar));
        assertEquals ("*:foo", new NodeTest (ValueType.ATTRIBUTE, starfoo));

        assertEquals ("document-node()", new NodeTest (ValueType.DOCUMENT, null));
        assertEquals ("document-node(foo)", new NodeTest (ValueType.DOCUMENT, foo));
        assertEquals ("doument-node(foo:bar)", new NodeTest (ValueType.DOCUMENT, foobar));
        assertEquals ("document-node(foo:*)", new NodeTest (ValueType.DOCUMENT, foostar));
        assertEquals ("document-node(*:foo)", new NodeTest (ValueType.DOCUMENT, starfoo));

        assertEquals ("comment()", new NodeTest (ValueType.COMMENT, null));
        assertEquals ("comment()", new NodeTest (ValueType.COMMENT, foo));
        assertEquals ("comment()", new NodeTest (ValueType.COMMENT, foobar));
        assertEquals ("comment()", new NodeTest (ValueType.COMMENT, foostar));
        assertEquals ("comment()", new NodeTest (ValueType.COMMENT, starfoo));

        assertEquals ("processing-instruction()", new NodeTest (ValueType.PROCESSING_INSTRUCTION, null));
        assertEquals ("processing-instruction(foo)", new NodeTest (ValueType.PROCESSING_INSTRUCTION, foo));
        assertEquals ("processing-instruction(bar)", new NodeTest (ValueType.PROCESSING_INSTRUCTION, foobar));
        assertEquals ("processing-instruction(*)", new NodeTest (ValueType.PROCESSING_INSTRUCTION, foostar));
        assertEquals ("processing-instruction(foo)", new NodeTest (ValueType.PROCESSING_INSTRUCTION, starfoo));
    }
        
}
