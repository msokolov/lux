package lux.exception;

public class ResourceExhaustedException extends LuxException {

    public ResourceExhaustedException(String msg) {
        super(msg);
    }

    public ResourceExhaustedException(String msg, Throwable t) {
        super(msg, t);
    }

    public ResourceExhaustedException(Throwable t) {
        super(t);
    }

}
