package lux.xquery;

import lux.xml.QName;
import lux.xpath.AbstractExpression;
import lux.xpath.ExpressionVisitor;
import lux.xpath.LiteralExpression;
import lux.xpath.Namespace;

public class ElementConstructor extends AbstractExpression {

    private final QName name;
    private final Namespace[] namespaces;
    private final AttributeConstructor[] attributes;
    
    /**
     * Make an element constructor - this models literal element constructors, not computed element
     * constructors.
     * @param qname the name of the element
     * @param namespaces this element's namespace declarations
     * @param content the content of the element
     * @param attributes the element's literal attributes - these AttributeConstructors 
     * may only have LiteralExpressions for their names and values
     */
    public ElementConstructor(QName qname, Namespace[] namespaces, AbstractExpression content, AttributeConstructor ... attributes) {
        super(Type.ELEMENT);
        this.name = qname;
        this.subs = new AbstractExpression[] { content };
        this.namespaces = namespaces;
        this.attributes = attributes;
    }
    
    public ElementConstructor (QName qname, AbstractExpression content, AttributeConstructor ... attributes) {
        this (qname, null, content, attributes);
    }

    public ElementConstructor (QName qname, AttributeConstructor ... attributes) {
        super(Type.ELEMENT);
        this.name = qname;
        this.subs = new AbstractExpression[0];
        this.namespaces = null;
        this.attributes = attributes;
    }

    public AbstractExpression accept(ExpressionVisitor visitor) {
        acceptSubs(visitor);
        return visitor.visit(this);
    }

    @Override
    public void toString(StringBuilder buf) {
        buf.append ('<');
        name.toString(buf);
        boolean hasNsDecl = namespaces != null && namespaces.length > 0;
        if (hasNsDecl) {
            buf.append (' ');
            appendNamespace(namespaces[0], buf);
            for (int i = 1; i < namespaces.length; i++) {
                buf.append (' ');
                appendNamespace(namespaces[i], buf);
            }
        }
        if (attributes != null && attributes.length > 0) {
            if (attributes[0] != null) {
                buf.append (' ');
                appendAttribute(attributes[0], buf);
            }
            for (int i = 1; i < attributes.length; i++) {
                if (attributes[i] != null) {
                    buf.append (' ');
                    appendAttribute(attributes[i], buf);
                }
            }
        }
        AbstractExpression content = getContent();
        if (content == null) {
            buf.append (" />");
        } else {
            buf.append ('>');
            switch (content.getType()) {
            case ELEMENT: 
                content.toString(buf);
                break;
            case LITERAL:
                if (content != LiteralExpression.EMPTY) {
                    LiteralExpression.escapeText(((LiteralExpression)content).getValue().toString(), buf);
                }
                break;
            case SEQUENCE:
            {
                boolean allElements = true;
                for (AbstractExpression kid : content.getSubs()) {
                    if (kid.getType() != Type.ELEMENT) {
                        allElements = false;
                        break;
                    }
                }
                if (allElements) {
                    for (AbstractExpression kid : content.getSubs()) {
                        kid.toString(buf);
                    }
                    break;
                }
            }
            default:
                buf.append ('{');
                content.toString(buf);
                buf.append ('}');
            }
            buf.append("</");
            name.toString(buf);
            buf.append ('>');
        }
    }
    
    private AbstractExpression getContent() {
        return subs.length > 0 ? subs[0] : null;
    }
    
    private void appendNamespace (Namespace ns, StringBuilder buf) {
        buf.append ("xmlns");
        if (!ns.getPrefix().isEmpty()) {
            buf.append (':');
            buf.append (ns.getPrefix());
        }
        buf.append ("=\"");
        buf.append (ns.getNamespace());
        buf.append ('"');
    }

    private void appendAttribute (AttributeConstructor attr, StringBuilder buf) {
        buf.append (((LiteralExpression)attr.getName()).getValue().toString());
        buf.append ('=');
        LiteralExpression.quoteString(((LiteralExpression)attr.getContent()).getValue().toString(), buf);
    }

    @Override
    public int getPrecedence () {
        return 0;
    }
}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
