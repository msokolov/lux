package lux.xml;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;

import org.apache.xerces.parsers.SAXParser;
import org.apache.xerces.parsers.StandardParserConfiguration;
import org.apache.xerces.xni.parser.XMLParserConfiguration;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * An XML parser that attempts to read the entity using the systemID, and if it 
 * fails, returns an empty stream.  This has the effect of processing DTDs when
 * they are present at the systemID, and ignoring them otherwise.
 */
public class GentleXmlReader extends SAXParser implements EntityResolver {

    public GentleXmlReader () {
        this (new StandardParserConfiguration());
    }
    
    protected GentleXmlReader(XMLParserConfiguration config) {
        super(config);
        super.setEntityResolver (this);
    }

    @Override
    public InputSource resolveEntity(String publicId, String systemId) throws SAXException {
        InputSource source;
        InputStream input = null;
        try {
            input = new URL(systemId).openStream();
        } catch (MalformedURLException e) {
        } catch (IOException e) {
        }
        if (input == null) {
            input = new ByteArrayInputStream(new byte[0]);
        }
        source = new InputSource (input);
        source.setSystemId(systemId);
        return source;
    }
    
    @Override
    public void setEntityResolver (EntityResolver resolver) {
        // System.err.println ("WHO IS SETTING MY ENTITY RESOLVER!!!");
        // DO NOTHING - Saxon tries to free memory by removing our resolver!!
    }

}
