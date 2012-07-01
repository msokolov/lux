/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */

package lux.xpath;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import org.apache.commons.lang.StringUtils;

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

    public void toString (StringBuilder buf) {
        if (! getPrefix ().equals ("")) {
            buf.append (getPrefix()).append(':');
        }
        buf.append (getLocalPart());
    }    

    @Override public String toString () {
        StringBuilder buf = new StringBuilder ();
        toString (buf);
        return buf.toString();
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
        return encode (getLocalPart(), getNamespaceURI());
    }

    /**
     * @param localPart the local part of the QName to encode
     * @param namespaceURI the namespace (URI) of the QName to encode (may be null)
     * @return the QName encoded in a suitable form for indexing.  The namespace uri, if any,
     * is URL-encoded, wrapped in {} and appended to the local-name.
     */
    public static String encode (String localPart, String namespaceURI) {
        if (StringUtils.isEmpty(namespaceURI)) {
            return localPart;
        }
        StringBuilder buf = new StringBuilder ();
        try {
            buf.append(localPart).append ('{').append(URLEncoder.encode(namespaceURI, "utf-8")).append("}");
        } catch (UnsupportedEncodingException e) { }
        return buf.toString();
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
