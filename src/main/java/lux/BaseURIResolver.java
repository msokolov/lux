package lux;

import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.URIResolver;

import net.sf.saxon.s9api.XdmNode;

public abstract class BaseURIResolver implements URIResolver {

    private final URIResolver systemURIResolver;

    public BaseURIResolver (URIResolver systemURIResolver) {
        this.systemURIResolver = systemURIResolver;
    }

    /**
     * file: uri resolution is delegated to the system URI resolver.  lux: and other uris are all resolved
     * by #getDocument(String). The lux: prefix is optional, e.g: the uris "lux:/hello.xml" and "/hello.xml"
     * are equivalent.
     * @throws TransformerException if the document is not found
     */
    @Override
    public Source resolve(String href, String base) throws TransformerException {
        boolean isFile;
        String path = href;
        if (href.matches("^\\w+:.*$")) {
            isFile = href.startsWith("file:");
            if (isFile) {
                path = href.substring(5);
            } else if (href.startsWith("lux:/")) {
                path = href.substring(5);
            }
        } else {
            // relative url, look at base
            if (base != null) {
                isFile = base.startsWith("file:");
            } else {
                isFile = false;
            }
        }
        if (isFile) {
            return systemURIResolver.resolve (path, base);
        }
        path = path.replace('\\', '/');
        return getDocument(path).asSource();
    }

    public abstract XdmNode getDocument (String uri) throws TransformerException;

}
