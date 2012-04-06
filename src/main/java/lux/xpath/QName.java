package lux.xpath;

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

}
