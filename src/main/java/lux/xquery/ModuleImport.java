package lux.xquery;

import lux.xpath.LiteralExpression;

public class ModuleImport {
    private final String prefix;
    private final String nsURI;
    private final String systemId;
    
    public ModuleImport (String prefix, String nsURI, String systemId) {
        this.prefix = prefix;
        this.nsURI = nsURI;
        this.systemId = systemId;
    }
    
    public void toString (StringBuilder buf) {
        buf.append("import module ");
        if (prefix != null) {
            buf.append ("namespace ").append (prefix).append('=');
        }
        LiteralExpression.quoteString(nsURI, buf);
        if (systemId != null) {
            buf.append (" at ");
            LiteralExpression.quoteString(systemId, buf);
        }
        buf.append(";\n");
    }

    public String getPrefix() {
        return prefix;
    }

    public String getNsURI() {
        return nsURI;
    }

    public String getSystemId() {
        return systemId;
    }

}
