package lux.index.analysis;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import net.sf.saxon.s9api.QName;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.util.TokenFilterFactory;

public class QNameFilterFactory extends TokenFilterFactory {
    
    private static final String VISIBILITY = "visibility";
    private final HashMap<String,ElementVisibility> elVis;
    private final ElementVisibility defVis;
    
    public QNameFilterFactory (Map<String,String> args) {
        super (args);
        if (args.containsKey(VISIBILITY)) {
            defVis = ElementVisibility.valueOf(args.get(VISIBILITY).toUpperCase());
        } else {
            defVis = ElementVisibility.OPAQUE;
        }
        elVis = new HashMap<String, ElementVisibility>();
        setElementVisibility (ElementVisibility.CONTAINER, args);
        setElementVisibility (ElementVisibility.OPAQUE, args);
        setElementVisibility (ElementVisibility.TRANSPARENT, args);
        setElementVisibility (ElementVisibility.HIDDEN, args);
        /*
        for (Map.Entry<String,String> entry : args.entrySet()) {
            ElementVisibility vis;
            try {
                vis = ElementVisibility.valueOf(entry.getKey().toUpperCase());
            } catch (IllegalArgumentException e) {
                continue;
            }
            setElementVisibility(vis, args);
        }
        */
    }
    
    private void setElementVisibility (ElementVisibility vis, Map<String, String> args) {
        String qnameList = args.get(vis.name().toLowerCase());
        if (qnameList != null) {
            for (String name : qnameList.split(",")) {
                QName.fromClarkName(name); // expect the name in Clark notation: {namespace}local-name
                elVis.put(name, vis);
            }
        }
    }

    @Override
    public TokenStream create(TokenStream input) {
        return new QNameTokenFilter (input, defVis, elVis);
    }
    
    public ElementVisibility getDefaultVisibility () {
        return defVis;
    }
    
    public Map<String,ElementVisibility> getElementVisibility () {
        return Collections.unmodifiableMap(elVis);
    }

}
