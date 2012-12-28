package lux.xml;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;

/**
 * Holds an Offsets object that will accumulate offset information about a 
 * document as it is parsed.  Note that the Offsets is reused for subsequent parses.
 * Its contents will be overwritten when a document is read.  
 */
public class OffsetDocBuilder extends SaxonDocBuilder {

    private final Offsets offsets;
    private int lastTextLocation;

    private boolean fixupCRLF = false;

    /**
     * @param processor a Saxon processor.
     * @throws SaxonApiException 
     */
    
    public OffsetDocBuilder(Processor processor) throws SaxonApiException {
        super(processor);
        offsets = new Offsets();
    }
    
    @Override
    public void reset () {
        super.reset();
        offsets.reset();
    }
    
    @Override
    public void handleEvent(XMLStreamReader reader, int eventType) throws XMLStreamException {
        
        super.handleEvent(reader, eventType);
        
        switch (eventType) {

        case XMLStreamConstants.START_DOCUMENT:
            lastTextLocation = -1;
            break;

        case XMLStreamConstants.START_ELEMENT:
        case XMLStreamConstants.END_ELEMENT:
        case XMLStreamConstants.COMMENT:
        case XMLStreamConstants.PROCESSING_INSTRUCTION:
            recordOffsets(reader);
            break;

        case XMLStreamConstants.CDATA:
            recordOffsets(reader, 
                    reader.getLocation().getCharacterOffset() + "<!CDATA[[".length(), 
                    reader.getTextLength()
                    );
            recordOffsets(reader, lastTextLocation, "]]>".length());
            break;
        
        case XMLStreamConstants.SPACE:
        case XMLStreamConstants.CHARACTERS:
            int textLength = reader.getTextLength();
            if (isFixupCRLF()) {
                if (reader.getTextCharacters()[reader.getTextStart()] == '\n') {
                    recordOffsets(reader, reader.getLocation().getCharacterOffset() + 1, textLength);
                } else {
                    recordOffsets(reader, reader.getLocation().getCharacterOffset(), textLength);                    
                }
                offsetCRLF(reader.getLocation().getCharacterOffset(), reader.getTextCharacters(), reader.getTextStart(), textLength);
            } else {
                recordOffsets(reader, reader.getLocation().getCharacterOffset(), textLength);                    
            }
            break;

        case XMLStreamConstants.ENTITY_REFERENCE:
            String text = reader.getText();
            recordOffsets(reader, reader.getLocation().getCharacterOffset(), text.length());
            break;
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
    @Override
    public Offsets getOffsets() {
        return offsets;
    }
    
    public boolean isFixupCRLF() {
        return fixupCRLF;
    }

    public void setFixupCRLF(boolean fixupCRLF) {
        this.fixupCRLF = fixupCRLF;
    }
}
