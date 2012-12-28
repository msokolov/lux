package lux.xml;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import lux.exception.LuxException;
import net.sf.saxon.s9api.BuildingStreamWriterImpl;
import net.sf.saxon.s9api.DocumentBuilder;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmNode;

/*
 * Builds Saxon documents from a stream of pushed StAX events by writing them to an XmlStreamWriter.
 */
public class SaxonDocBuilder implements StAXHandler {
    
    private final DocumentBuilder builder;
    private BuildingStreamWriterImpl writer;
    
    /**
     * @param processor the Saxon processor
     * @throws SaxonApiException if there is an error creating an XMLStreamWriter
     */
    public SaxonDocBuilder (Processor processor) throws SaxonApiException {
        builder = processor.newDocumentBuilder();
        writer = builder.newBuildingStreamWriter();
    }

    public XdmNode getDocument() throws SaxonApiException {
        return writer.getDocumentNode();
    }

    @Override
    public void handleEvent(XMLStreamReader reader, int eventType) throws XMLStreamException {
        
        // System.out.println ("offset=" + reader.getLocation().getCharacterOffset() + " for event " + eventType + " at line " + reader.getLocation().getLineNumber() + ", column " + reader.getLocation().getColumnNumber());
        
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
            for (int i = 0; i < reader.getNamespaceCount(); i++) {
                writer.writeNamespace(reader.getNamespacePrefix(i), reader.getNamespaceURI(i));
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
            
        case XMLStreamConstants.CDATA:
            writer.writeCharacters(reader.getTextCharacters(), reader.getTextStart(), reader.getTextLength());
            break;
        
        case XMLStreamConstants.SPACE:
        case XMLStreamConstants.CHARACTERS:
            int textLength = reader.getTextLength();
            writer.writeCharacters(reader.getTextCharacters(), reader.getTextStart(), textLength);
            break;

        case XMLStreamConstants.ENTITY_REFERENCE:
            String text = reader.getText();
            writer.writeCharacters(text);
            break;
            
        case XMLStreamConstants.ENTITY_DECLARATION:
        case XMLStreamConstants.NOTATION_DECLARATION:
        default:
            throw new RuntimeException("Unrecognized XMLStream event type: " + reader.getEventType());
        }

    }
    
    @Override
    public void reset() {
        try {
            writer = builder.newBuildingStreamWriter();
        } catch (SaxonApiException e) {
            // unlikely to happen since this already succeeded once, and we can't throw a checked exception here
            throw new LuxException(e);
        }
    }
    
    /**
     * @return the offsets accumulated for the parsed document.  This class always returns null; to receive
     * offsets, use {@link OffsetDocBuilder}.
     */
    public Offsets getOffsets() {
        return null;
    }

}
