package lux;

import java.io.Reader;

import javax.xml.transform.stream.StreamSource;

import lux.exception.LuxException;
import net.sf.saxon.s9api.DocumentBuilder;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmNode;

public class DocBuilder {
    private final DocIDNumberAllocator docIDNumberAllocator;
    private final DocumentBuilder documentBuilder;

    DocBuilder (DocIDNumberAllocator docIDNumberAllocator, DocumentBuilder documentBuilder) {
        documentBuilder.setDTDValidation(false);
        this.documentBuilder = documentBuilder;
        this.docIDNumberAllocator = docIDNumberAllocator;
    }
    
    public void setNextDocID (int docID) {
        docIDNumberAllocator.setNextDocID(docID);            
    }
    
    public XdmNode build(Reader reader, String uri) {
        try {
            return documentBuilder.build(new StreamSource (reader, uri));
        } catch (SaxonApiException e) {
           throw new LuxException(e);
        }
    }
}
