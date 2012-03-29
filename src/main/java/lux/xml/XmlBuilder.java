package lux.xml;

import java.io.Reader;

public abstract class XmlBuilder extends XmlReader {

    public abstract Object build(Reader reader);
    
}
