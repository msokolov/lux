package lux.xquery;

import lux.xpath.AbstractExpression;
import lux.xpath.ExpressionVisitor;
import lux.xpath.Namespace;
import lux.xpath.QName;

public class ElementConstructor extends AbstractExpression {

    private final QName qname;
    private final Namespace[] namespaces;
    private final AbstractExpression content;
    
    
    public ElementConstructor(QName qname, Namespace[] namespaces, AbstractExpression content) {
        super(Type.Element);
        this.qname = qname;
        this.content = content;
        this.namespaces = namespaces;
    }

    public AbstractExpression accept(ExpressionVisitor visitor) {
        return visitor.visit(this);
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder ();
        buf.append ("element ");
        buf.append (qname.toString());
        buf.append (" { ");
        if (namespaces != null && namespaces.length > 0) {
            appendNamespace(namespaces[0], buf);
            for (int i = 1; i < namespaces.length; i++) {
                buf.append (", ");
                appendNamespace(namespaces[i], buf);
            }
        }
        if (content != null) {
            if (namespaces != null && namespaces.length > 0) {
                buf.append (", ");
            }
            buf.append (content.toString());
        }
        buf.append (" }");
        return buf.toString();
    }
    
    private void appendNamespace (Namespace ns, StringBuilder buf) {
        buf.append ("attribute xmlns");
        if (!ns.getPrefix().isEmpty()) {
            buf.append (':');
            buf.append (ns.getPrefix());
        }
        buf.append (" { \"");
        buf.append (ns.getNamespace());
        buf.append ("\" }");        
    }

}
