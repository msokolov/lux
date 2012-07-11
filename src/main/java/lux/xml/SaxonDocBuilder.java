package lux.xml;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import lux.api.LuxException;
import lux.index.analysis.Offsets;
import net.sf.saxon.s9api.BuildingStreamWriterImpl;
import net.sf.saxon.s9api.DocumentBuilder;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmNode;

/*
 * TODO: merge w/Saxon$SaxonBuilder
 */
public class SaxonDocBuilder implements StAXHandler {
    
    private final Offsets offsets;
    private final DocumentBuilder builder;
    private BuildingStreamWriterImpl writer;

    private int lastTextLocation;

    private boolean fixupCRLF = false;
    
    /**
     * creates its own Saxon processor and DocumentBuilder
     * @throws SaxonApiException if there is an error instantiating the Saxon services.
     */
    public SaxonDocBuilder () throws SaxonApiException {
        this (new Processor (false).newDocumentBuilder());
    }
    
    public SaxonDocBuilder(Offsets offsets) throws SaxonApiException {
        this (new Processor (false).newDocumentBuilder(), offsets);
    }

    public SaxonDocBuilder(DocumentBuilder builder) throws SaxonApiException {
        this (builder, null);
    }

    /**
     * @param builder a DocumentBuilder supplied from an external Saxon processor.
     * @param offsets an Offsets object that will accumulate offset information about a 
     * document as it is parsed.  Note that the Offsets is reused for subsequent parses.
     * Its contents will be overwritten when a document is read.  Passing null (the default)
     * disables the accumulation of offset information, which will save a small amount of time 
     * and space.
     * @throws SaxonApiException if there is an error creating an XMLStreamWriter
     */
    public SaxonDocBuilder(DocumentBuilder builder, Offsets offsets) throws SaxonApiException {
        this.builder = builder;
        writer = builder.newBuildingStreamWriter();
        this.offsets = offsets;
    }
    
    public XdmNode getDocument() throws SaxonApiException {
        return writer.getDocumentNode();
    }

    public void handleEvent(XMLStreamReader reader, int eventType) throws XMLStreamException {
        
        // System.out.println ("offset=" + reader.getLocation().getCharacterOffset() + " for event " + eventType + " at line " + reader.getLocation().getLineNumber() + ", column " + reader.getLocation().getColumnNumber());
        
        switch (eventType) {

        case XMLStreamConstants.START_DOCUMENT:
            lastTextLocation = -1;
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
            if (offsets != null) {
                recordOffsets(reader);
            }
            break;

        case XMLStreamConstants.END_ELEMENT:
            writer.writeEndElement();
            if (offsets != null) {
                recordOffsets(reader);
            }
            break;

        case XMLStreamConstants.COMMENT:
            writer.writeComment(reader.getText());
            if (offsets != null) {
                recordOffsets(reader);
            }
            break;

        case XMLStreamConstants.PROCESSING_INSTRUCTION:
            if (reader.getPIData() == null) {
                writer.writeProcessingInstruction(reader.getPITarget());
            } else {
                writer.writeProcessingInstruction(reader.getPITarget(), reader.getPIData());
            }
            if (offsets != null) {
                recordOffsets(reader);
            }
            break;

        case XMLStreamConstants.DTD:
            writer.writeDTD(reader.getText());
            break;
            
        case XMLStreamConstants.CDATA:
            writer.writeCharacters(reader.getTextCharacters(), reader.getTextStart(), reader.getTextLength());
            if (offsets != null) {
                recordOffsets(reader, 
                        reader.getLocation().getCharacterOffset() + "<!CDATA[[".length(), 
                        reader.getTextLength()
                        );
                recordOffsets(reader, lastTextLocation, "]]>".length());
            }
            break;
        
        case XMLStreamConstants.SPACE:
        case XMLStreamConstants.CHARACTERS:
            int textLength = reader.getTextLength();
            writer.writeCharacters(reader.getTextCharacters(), reader.getTextStart(), textLength);
            if (fixupCRLF) {
                if (reader.getTextCharacters()[reader.getTextStart()] == '\n') {
                    recordOffsets(reader, reader.getLocation().getCharacterOffset() + 1, textLength);
                } else {
                    recordOffsets(reader, reader.getLocation().getCharacterOffset(), textLength);                    
                }
                offsetCRLF(reader.getLocation().getCharacterOffset(), reader.getTextCharacters(), reader.getTextStart(), textLength);
            } else {
                if (offsets != null) {
                    recordOffsets(reader, reader.getLocation().getCharacterOffset(), textLength);                    
                }
            }
            break;

        case XMLStreamConstants.ENTITY_REFERENCE:
            String text = reader.getText();
            writer.writeCharacters(text);
            if (offsets != null) {
                recordOffsets(reader, reader.getLocation().getCharacterOffset(), text.length());
            }
            break;
            
        case XMLStreamConstants.ENTITY_DECLARATION:
        case XMLStreamConstants.NOTATION_DECLARATION:
        default:
            throw new RuntimeException("Unrecognized XMLStream event type: " + reader.getEventType());
        }

    }
    
    // generate character offsets wherever there is a line feed (\n == 10)
    // since we're told it was a CRLF (\r\n = 13, 10) in the original text
    // XML parser are *required* to perform this "normalization"
    private void offsetCRLF(int location, char[] cbuf, int off, int size) {
        for (int i = off + 1; i < off + size; i++) {
            if (cbuf[i] == '\n') {
                offsets.addDelta(location + off - i, (short) 1);
            }
        }
    }

    // Keep track of the location at the end of the last text event, and use that to infer the presence of
    // a length-changing entity reference.  The lastTextLocation is reset to -1 in start
    // element events so that the first text event in an element will have its position
    // stored absolutely.  For each subsequent text-like event within the same text node
    // (which will occur because entity references are reported as separate events),
    // compute a delta based on the difference of the end offset of the last text event
    // and the start offset of this text event.  Store the delta for use by an offset-correcting
    // CharStream.    
    private void recordOffsets(XMLStreamReader reader, int location, int textLength) throws XMLStreamException {    
        if (lastTextLocation < 0) {
            offsets.addOffset (location);
        } else {
            offsets.addDelta (location, (short) (location - lastTextLocation));      
        }
        lastTextLocation = location + textLength;
    }
    
    private void recordOffsets(XMLStreamReader reader) throws XMLStreamException {
        int location = reader.getLocation().getCharacterOffset();
        if (lastTextLocation >= 0 && location > lastTextLocation) {
            offsets.addDelta (location, (short) (location - lastTextLocation)); 
        }
        lastTextLocation = -1;
    }
    
    /**
     * @return the offsets accumulated for the parsed document.  This object is only valid
     * after a document has been parsed, and in any case may be null if it setOffsets(null) was
     * called.
     */
    public Offsets getOffsets() {
        return offsets;
    }
    
    public void reset() {
        try {
            writer = builder.newBuildingStreamWriter();
        } catch (SaxonApiException e) {
            // unlikely to happen since this already succeeded once, and we can't throw a checked exception here
            throw new LuxException(e);
        }
        if (offsets != null) {
            offsets.reset();
        }
    }

    public boolean isFixupCRLF() {
        return fixupCRLF;
    }

    public void setFixupCRLF(boolean fixupCRLF) {
        this.fixupCRLF = fixupCRLF;
    }

}
