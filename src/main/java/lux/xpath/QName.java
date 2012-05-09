package lux.xpath;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

public class QName extends javax.xml.namespace.QName {

    public QName(String localPart) {
        super(localPart);
    }
    
    public QName(String namespace, String localName) {
        super (namespace, localName);
    }
    
    public QName(String namespace, String localName, String prefix) {
        super (namespace, localName, prefix);
    }

    @Override public String toString () {
        if (getPrefix ().equals ("")) {
            return getLocalPart();
        }
        return getPrefix() + ':' + getLocalPart();
    }

    /**
     * The Clark name is named after James Clark. It has the namespace surrounded by {},
     * followed by the local name.  In our case we omit the {} when the namespace is empty.
     * Not sure if that's Clark standard?
     * @return the Clark name
     */
    public String getClarkName () {
      if (getNamespaceURI().isEmpty())
          return getLocalPart();
      StringBuilder buf = new StringBuilder ();
      buf.append ('{').append(getNamespaceURI()).append('}').append(getLocalPart());
      return buf.toString();
    }
    
    /**
     * @return the name encoded in a suitable form for indexing.  The namespace uri, if any,
     * is URL-encoded, wrapped in {} and appended to the local-name.
     */
    public String getEncodedName() {
        if (getNamespaceURI().isEmpty())
            return getLocalPart();
        StringBuilder buf = new StringBuilder ();
        try {
            buf.append(getLocalPart()).append ('{').append(URLEncoder.encode(getNamespaceURI(), "utf-8")).append("}");
        } catch (UnsupportedEncodingException e) { }
        return buf.toString();
    }

}
