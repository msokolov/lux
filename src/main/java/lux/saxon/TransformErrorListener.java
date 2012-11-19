package lux.saxon;

import java.util.ArrayList;

import javax.xml.transform.ErrorListener;
import javax.xml.transform.TransformerException;

public class TransformErrorListener implements ErrorListener {
    
    public TransformErrorListener () {
        errors = new ArrayList<TransformerException>();
    }
    
    private ArrayList<TransformerException> errors;

    public ArrayList<TransformerException> getErrors() {
        return errors;
    }
    
    public void clear () {
        errors.clear();
    }

    @Override
    public void error(TransformerException exception) throws TransformerException {
        errors.add(exception);
    }

    @Override
    public void fatalError(TransformerException exception) throws TransformerException {
        errors.add(exception);
    }

    @Override
    public void warning(TransformerException exception) throws TransformerException {
        errors.add(exception);
    }

}
