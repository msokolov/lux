package lux.index;

import static javax.xml.stream.XMLStreamConstants.*;

import java.util.ArrayList;
import java.util.Arrays;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamReader;

import lux.index.field.QNameTextField;

/**
 * Accumulates text for each element and each attribute.
 */
public class QNameTextMapper extends XmlPathMapper {

    private int depth = -1;
    private StringBuilder[] stack;
    private ArrayList<String> names;
    private ArrayList<String> values;
    
    public QNameTextMapper () {
        stack = new StringBuilder[8];
        names = new ArrayList<String>();
        values = new ArrayList<String>();
        for (int i = 0; i < stack.length; i++) {
            stack[i] = new StringBuilder();
        }
    }
    
    public ArrayList<String> getValues() {
        return values;
    }

    public ArrayList<String> getNames() {
        return names;
    }
    
    public void reset () {
        super.reset();
        depth = -1;
        names.clear();
        values.clear();
    }
    
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
                QName attQName = getEventAttQName (reader, i);
                String name = '@' + encodeQName(attQName);
                names.add(name);
                // surround value by terminal markers
                buf.append(QNameTextField.RECORD_START).append (reader.getAttributeValue(i)).append(QNameTextField.RECORD_END);
                values.add(buf.toString());
                buf.setLength(0);
            }
            buf.append(QNameTextField.RECORD_START);
            break;
            
        case END_ELEMENT:
            super.handleEvent(reader, eventType);
            buf = popStackFrame();
            buf.append(QNameTextField.RECORD_END);
            names.add(encodeQName(currentQName));
            values.add(buf.toString());
            break;
            
        case CDATA:
        case SPACE:
        case CHARACTERS:
            stack[depth].append(reader.getTextCharacters(), reader.getTextStart(), reader.getTextLength());
            super.handleEvent(reader, eventType);
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
