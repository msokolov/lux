package lux.api;

public class LuxException extends RuntimeException {
    
    public LuxException (Throwable t) {
        super (t);
    }
    
    public LuxException (String msg, Throwable t) {
        super (msg, t);
    }

    public LuxException(String msg) {
        super (msg);
    }

}
