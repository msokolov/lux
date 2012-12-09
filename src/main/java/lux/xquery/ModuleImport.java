package lux.xquery;

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
        // TODO: quote double quotes in namespace and systemId
        if (prefix != null) {
            buf.append ("namespace ").append (prefix).append('=');
        }
        buf.append ('"').append(nsURI).append('"');
        if (systemId != null) {
            buf.append (" at ").append('"').append(systemId).append('"');
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
