package lux.xml;

import org.apache.xerces.parsers.SAXParser;
import org.apache.xerces.parsers.StandardParserConfiguration;
import org.apache.xerces.xni.parser.XMLParserConfiguration;
import org.xml.sax.EntityResolver;

/**
 * An XML parser that attempts to read the entity using the systemID, and if it 
 * fails, returns an empty stream.  This has the effect of processing DTDs when
 * they are present at the systemID, and ignoring them otherwise.
 */
public class GentleXmlReader extends SAXParser {

    public GentleXmlReader () {
        this (new StandardParserConfiguration());
    }
    
    protected GentleXmlReader(XMLParserConfiguration config) {
        super(config);
        super.setEntityResolver (new GentleXmlResolver());
    }

    @Override
    public void setEntityResolver (EntityResolver resolver) {
        // System.err.println ("WHO IS SETTING MY ENTITY RESOLVER!!!");
        // DO NOTHING - Saxon tries to free memory by removing our resolver!!
    }

}
