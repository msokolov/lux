package lux.xpath;

public class Namespace {
    private final String prefix;
    private final String namespace;
    
    public Namespace (String prefix, String namespace) {
        this.prefix = prefix;
        this.namespace = namespace;
    }
    
    public String getPrefix () {
        return prefix;
    }
    
    public String getNamespace () {
        return namespace;
    }
}
