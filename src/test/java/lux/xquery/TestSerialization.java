package lux.xquery;

import static org.junit.Assert.*;

import javax.xml.namespace.QName;

import lux.api.ValueType;

import org.junit.Test;

public class TestSerialization {

    @Test public void testNodeTestToString () {
        QName foo = new QName ("foo");
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
        assertEquals ("document-node(foo)", new NodeTest (ValueType.DOCUMENT, foo).toString());
        assertEquals ("document-node(foo:bar)", new NodeTest (ValueType.DOCUMENT, foobar).toString());
        assertEquals ("document-node(foo:*)", new NodeTest (ValueType.DOCUMENT, foostar).toString());
        assertEquals ("document-node(*:foo)", new NodeTest (ValueType.DOCUMENT, starfoo).toString());

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
        
}
