package lux.xml;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import lux.api.LuxException;
import net.sf.saxon.s9api.BuildingStreamWriterImpl;
import net.sf.saxon.s9api.DocumentBuilder;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmNode;

/*
 * TODO: merge w/Saxon$SaxonBuilder
 * TODO: accumulate array of text node character offsets for use by QNameTextTokenStream
 */
public class SaxonBuilder implements StAXHandler {
    
    private DocumentBuilder builder;
    private BuildingStreamWriterImpl writer;
    
    /**
     * creates its own Saxon processor and DocumentBuilder
     * @throws SaxonApiException if there is an error instantiating the Saxon services.
     */
    public SaxonBuilder () throws SaxonApiException {
        this (new Processor (false).newDocumentBuilder());
    }

    /**
     * uses a DocumentBuilder supplied from an external Saxon processor.
     * @throws SaxonApiException if there is an error creating an XMLStreamWriter
     */
    public SaxonBuilder(DocumentBuilder builder) throws SaxonApiException {
        this.builder = builder;
        writer = builder.newBuildingStreamWriter();
    }
    
    public XdmNode getDocument() throws SaxonApiException {
        return writer.getDocumentNode();
    }

    public void handleEvent(XMLStreamReader reader, int eventType) throws XMLStreamException {
        switch (eventType) {

        case XMLStreamConstants.START_DOCUMENT:
            writer.writeStartDocument(reader.getEncoding(), reader.getVersion());
            break;

        case XMLStreamConstants.END_DOCUMENT:
            writer.writeEndDocument();
            break;

        case XMLStreamConstants.START_ELEMENT:
            String nsuri = reader.getNamespaceURI();
            if (nsuri == null) {
                writer.writeStartElement(reader.getLocalName());
            } else {
                String prefix = reader.getPrefix();
                if (prefix == null) {
                    writer.writeStartElement(nsuri, reader.getLocalName());                    
                } else {
                    writer.writeStartElement(prefix, reader.getLocalName(), nsuri);
                }
            }
            for (int i = 0; i < reader.getAttributeCount(); i++) {
                nsuri = reader.getAttributeNamespace(i);
                if (nsuri == null) {
                    writer.writeAttribute(reader.getAttributeLocalName(i), reader.getAttributeValue(i));
                } else {
                    String prefix = reader.getAttributePrefix(i);
                    if (prefix == null) {
                        writer.writeAttribute(nsuri, reader.getAttributeLocalName(i), reader.getAttributeValue(i));
                    } else {
                        writer.writeAttribute(prefix, nsuri, reader.getAttributeLocalName(i), reader.getAttributeValue(i));
                    }                    
                }
            }
            break;

        case XMLStreamConstants.END_ELEMENT:
            writer.writeEndElement();
            break;

        case XMLStreamConstants.CDATA:
        case XMLStreamConstants.SPACE:
            // fall through

        case XMLStreamConstants.CHARACTERS:
            // TODO: preserve character offsets somehow????
            // reader.getLocation().getCharacterOffset();
            writer.writeCharacters(reader.getTextCharacters(), reader.getTextStart(), reader.getTextLength());
            break;

        case XMLStreamConstants.COMMENT:
            writer.writeComment(reader.getText());
            break;

        case XMLStreamConstants.PROCESSING_INSTRUCTION:
            if (reader.getPIData() == null) {
                writer.writeProcessingInstruction(reader.getPITarget());
            } else {
                writer.writeProcessingInstruction(reader.getPITarget(), reader.getPIData());
            }
            break;

        case XMLStreamConstants.DTD:
            writer.writeDTD(reader.getText());
            break;
            
        case XMLStreamConstants.ENTITY_DECLARATION:
        case XMLStreamConstants.NOTATION_DECLARATION:
        case XMLStreamConstants.ENTITY_REFERENCE:
        default:
            throw new RuntimeException("Unrecognized XMLStream event type: " + reader.getEventType());
        }
    }

    public void reset() {
        try {
            writer = builder.newBuildingStreamWriter();
        } catch (SaxonApiException e) {
            // unlikely to happen since this already succeeded once, and we can't throw a checked exception here
            throw new LuxException(e);
        }
    }

}
