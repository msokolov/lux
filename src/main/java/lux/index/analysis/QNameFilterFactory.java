package lux.index.analysis;

import java.util.HashMap;
import java.util.Map;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.util.TokenFilterFactory;

public class QNameFilterFactory extends TokenFilterFactory {
    
    private static final String VISIBILITY = "visibility";
    private HashMap<String,ElementVisibility> elvis;
    private boolean defvis;
    
    public QNameFilterFactory (Map<String,String> args) {
        super (args);
        if (args.containsKey(VISIBILITY)) {
            defvis = Boolean.valueOf(args.get(VISIBILITY));
        }
        // TODO: get default visibility
        // get visibility of elements
        // treat attribute names as element names and attribute values as visibility settings
    }

    @Override
    public TokenStream create(TokenStream input) {
        return new QNameTokenFilter (input);
    }

}
