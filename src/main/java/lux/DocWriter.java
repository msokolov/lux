package lux;

import net.sf.saxon.om.NodeInfo;

public interface DocWriter {
    
    void write (NodeInfo node, String uri);
    
    void delete (String uri);
    
    void commit ();

}
