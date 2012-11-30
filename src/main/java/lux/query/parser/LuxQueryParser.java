package lux.query.parser;

import lux.index.FieldName;
import lux.index.IndexConfiguration;

import org.apache.lucene.queryParser.ext.ExtendableQueryParser;

/**
 * A Lucene query parser extension that supports query terms of the form:
 * 
 * <blockquote><code>[node]<[nodeName]:[term]</code></blockquote>
 * 
 * <p>In which nodeName is either empty, an unqualified element name, a prefixed element name
 * (ie a QName), or a QName prefixed with "@", indicating an attribute. nodeName is optional:
 * if it is not present, a full text query of the entire document is indicated.  The "node"
 * prefix is also optional. Concrete examples:
 * </p>
 * 
 * <pre>
 *  node<:"Alas, poor Yorick"
 *  node<title:Hamlet
 *  node<@id:s12340
 *  node<@xml:id:x2345
 *  node<math:equation:3.14159
 *  
 *  or, equivalently:
 *  
 *  <:"Alas, poor Yorick"
 *  <title:Hamlet
 *  <@id:s12340
 *  <@xml:id:x2345
 *  <math:equation:3.14159
 * </pre>
 * 
 * <p>TODO: supply a facility for looking up namespace prefixes (such as <code>math</code> in the last example).</p>
 */
public class LuxQueryParser {
    
    public static ExtendableQueryParser makeLuxQueryParser(IndexConfiguration config) {
        return new ExtendableQueryParser(IndexConfiguration.LUCENE_VERSION, 
                config.getFieldName(FieldName.XML_TEXT), 
                config.getField(FieldName.ELEMENT_TEXT).getAnalyzer(), 
                new NodeExtensions (new NodeParser(
                        config.getFieldName(FieldName.XML_TEXT),
                        config.getFieldName(FieldName.ELEMENT_TEXT),
                        config.getFieldName(FieldName.ATTRIBUTE_TEXT),
                        config.getField(FieldName.ELEMENT_TEXT).getAnalyzer())));
    }

}
