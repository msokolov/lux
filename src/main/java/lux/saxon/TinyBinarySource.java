package lux.saxon;

import javax.xml.transform.Source;

public class TinyBinarySource implements Source {

    
    private String systemId;
    
    public void setSystemId(String systemId) {
        this.systemId = systemId;
    }

    public String getSystemId() {
        return systemId;
    }

}
