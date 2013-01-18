package lux.exception;

public class LuxHttpException extends LuxException {

    private final int code;
    
    public LuxHttpException(int code, String msg) {
        super(msg);
        this.code = code;
    }
    
    public int getCode() {
        return code;
    }

}
