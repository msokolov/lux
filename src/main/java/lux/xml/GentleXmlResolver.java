package lux.xml;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

import javax.xml.stream.XMLResolver;

import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * This resolver treats the systemID as a file path relative to the baseURI's path,
 * and reads the entity from the file found there, if any.
 * If there is no systemID, or any error occurs while reading the file, an empty entity
 * is returned.  No exception is ever raised.
 */
public class GentleXmlResolver implements XMLResolver, EntityResolver {

    @Override
    public Object resolveEntity(String publicID, String systemID, String baseURI, String namespace) {
        if (systemID != null) {
            try {
                String path = URI.create(baseURI).resolve(systemID).getPath();
                return new FileInputStream(path);
            } catch (IOException ex) { }
        }
        return new ByteArrayInputStream(new byte[0]);
    }

    @Override
    public InputSource resolveEntity(String publicId, String systemID) throws SAXException, IOException {
        InputStream in = null;
        if (systemID != null) {
            try {
                in = new FileInputStream(systemID);
            } catch (IOException ex) { }
        }
        if (in == null) {
            in = new ByteArrayInputStream(new byte[0]);
        }
        InputSource source = new InputSource (in);
        source.setSystemId(systemID);
        return source;
    }

}
