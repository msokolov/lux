package lux.index;

import static javax.xml.stream.XMLStreamConstants.CDATA;
import static javax.xml.stream.XMLStreamConstants.CHARACTERS;
import static javax.xml.stream.XMLStreamConstants.END_ELEMENT;
import static javax.xml.stream.XMLStreamConstants.ENTITY_REFERENCE;
import static javax.xml.stream.XMLStreamConstants.SPACE;
import static javax.xml.stream.XMLStreamConstants.START_DOCUMENT;
import static javax.xml.stream.XMLStreamConstants.START_ELEMENT;

import java.util.ArrayList;
import java.util.Arrays;

import javax.xml.stream.XMLStreamReader;

/**
 * Accumulates text for each element and each attribute.
 */
public class QNameTextMapper extends XmlPathMapper {

    private int depth = -1;
    private StringBuilder[] stack;
    private ArrayList<CharSequence> names;
    private ArrayList<CharSequence> values;
    private MutableString charBuffer = new MutableString();
    
    public QNameTextMapper () {
        stack = new StringBuilder[8];
        names = new ArrayList<CharSequence>();
        values = new ArrayList<CharSequence>();
        for (int i = 0; i < stack.length; i++) {
            stack[i] = new StringBuilder();
        }
    }
    
    public ArrayList<CharSequence> getValues() {
        return values;
    }

    public ArrayList<CharSequence> getNames() {
        return names;
    }
    
    @Override
    public void reset () {
        super.reset();
        depth = -1;
        names.clear();
        values.clear();
    }
    
    @Override
    public void handleEvent(XMLStreamReader reader, int eventType) {
        StringBuilder buf;
        switch (eventType) {
        case START_DOCUMENT:
            super.handleEvent(reader, eventType);
            break;
            
        case START_ELEMENT:
            super.handleEvent(reader, eventType);
            buf = pushStackFrame();
            for (int i = 0; i < reader.getAttributeCount(); i++) {
                getEventAttQName (charBuffer, reader, i);
                String name = '@' + charBuffer.toString();
                names.add(name);
                // surround value by terminal markers
                // buf.append(QNameTextField.RECORD_START).
                // buf.append (reader.getAttributeValue(i));
                //buf.append(QNameTextField.RECORD_END);
                // values.add(buf.toString());
                values.add(reader.getAttributeValue (i));
                //buf.setLength(0);
            }
            //buf.append(QNameTextField.RECORD_START);
            break;
            
        case END_ELEMENT:
            super.handleEvent(reader, eventType);
            buf = popStackFrame();
            //buf.append(QNameTextField.RECORD_END);
            names.add(getCurrentQName());
            values.add(buf.toString());
            break;
            
        case CDATA:
        case SPACE:
        case CHARACTERS:
            stack[depth].append(reader.getTextCharacters(), reader.getTextStart(), reader.getTextLength());
            super.handleEvent(reader, eventType);
            break;
            
        case ENTITY_REFERENCE:
            stack[depth].append(reader.getText());
            break;
            
        default:
            super.handleEvent(reader, eventType);
            break;
        }
    }
    
    private StringBuilder popStackFrame () {
        return stack[depth--];
    }
    
    private StringBuilder pushStackFrame () {
        if (depth++ >= stack.length) {
            growStack();
        } else {
            stack[depth].setLength(0);
        }
        return stack[depth];
    }
    
    private void growStack () {
        stack = Arrays.copyOf(stack, stack.length + 8);
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
