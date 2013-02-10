package lux.exception;

import javax.xml.transform.TransformerException;

public class NotFoundException extends TransformerException {

    private final String uri;
    
    public NotFoundException(String uri) {
        super("document not found: " + uri);
        this.uri = uri;
    }
    
    public String getUri() {
        return uri;
    }

}
