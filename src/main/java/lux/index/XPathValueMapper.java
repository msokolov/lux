package lux.index;

import static javax.xml.stream.XMLStreamConstants.*;

import java.util.ArrayList;
import java.util.Arrays;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamReader;

/**
 * Accumulates index keys composed of XML paths (including only child steps) and their string values.
 * String values are represented by 8-character hashes of their first N characters.  If the value fits in 
 * 8 characters, it is padded with nulls (unicode 0).  The hash algorithm is analogous to that used by 
 * java.lang.String, but arithmetic is done with shorts rather than ints, and we keep more of them so that
 * the likelihood of collisions is very small.
 */
public class XPathValueMapper extends XmlPathMapper {
    
    private static final int HASH_SIZE = 8;
    int depth;
    int[] valueOffsets = new int[16];
    char[][] values = new char[16][HASH_SIZE];
    char[] attValue = new char[HASH_SIZE];
    private ArrayList<String> pathValues = new ArrayList<String>();
    
    public ArrayList<String> getPathValues() {
        return pathValues;
    }

    public void handleEvent(XMLStreamReader reader, int eventType) {
        switch (eventType) {
        case START_DOCUMENT:
            depth = -1;
            super.handleEvent(reader, eventType);
            break;
            
        case START_ELEMENT:
            super.handleEvent(reader, eventType);
            ++depth;
            if (depth >= values.length) {
                growValues();
            }
            valueOffsets[depth] = 0;
            Arrays.fill(values[depth], '\u0000');
            {
                // append to the currentPath buffer
                int l = currentPath.length();
                for (int i = 0; i < reader.getAttributeCount(); i++) {
                    QName attQName = getEventAttQName (reader, i);
                    currentPath.append(" @").append(encodeQName(attQName)).append('|');
                    hashString (reader.getAttributeValue(i).toCharArray(), attValue);
                    currentPath.append(attValue);
                    pathValues.add(currentPath.toString());
                    // rewind currentPath
                    currentPath.setLength(l);
                }
            }
            break;
            
        case END_ELEMENT:
        {
            int l = currentPath.length();
            currentPath.append('|').append(values[depth]);
            pathValues.add(currentPath.toString());
            --depth;
            currentPath.setLength(l);
            super.handleEvent(reader, eventType);
        }
            break;
            
        case CDATA:
        case SPACE:
        case CHARACTERS:
            hashText (reader.getTextCharacters(), reader.getTextStart(), reader.getTextLength());
            super.handleEvent(reader, eventType);
            break;
            
        default:
            super.handleEvent(reader, eventType);
            break;
        }
    }
    
    private char[] hashString(char[] value, char[] buf) {
        Arrays.fill(buf, '\u0000');
        for (int i = 0; i < value.length && i < HASH_SIZE; i++) {
            buf[i % HASH_SIZE] = value[i];
        }
        for (int i = HASH_SIZE; i < value.length; i++) {
            buf[i % HASH_SIZE] = (char)(buf[i % HASH_SIZE] * 15 + value[i]);       
        }
        return buf;
    }

    private void hashText(final char[] textCharacters, final int textStart, final int textLength) {
        for (int j = 0; j <= depth; j++) {
            int k = valueOffsets[j];
            for (int i = textStart; i < textLength + textStart; i++) {
                values[j][k] = (char)(values[j][k] * 15 + textCharacters[i]);
                if (k == HASH_SIZE - 1) {
                    k = 0;
                } else {
                    k = (k + 1);
                }
            }
            valueOffsets[j] = k;
        }
    }

    private void growValues () {
        values = Arrays.copyOf(values, values.length + 16);
        for (int i = 0; i < 16; i++) {
            values[i + values.length] = new char[HASH_SIZE];
        }
        valueOffsets = Arrays.copyOf(valueOffsets, valueOffsets.length + 16);
    }
}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
