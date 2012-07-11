package lux.query.parser;

import lux.index.field.XmlField;

import org.apache.commons.lang.StringUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.queryParser.ext.ExtendableQueryParser;
import org.apache.lucene.queryParser.ext.ExtensionQuery;
import org.apache.lucene.queryParser.ext.Extensions;
import org.apache.lucene.queryParser.ext.ParserExtension;
import org.apache.lucene.util.Version;

/**
 * A Lucene query parser extension that supports query terms of the form:
 * 
 * <blockquote><code>[node]:[nodeName]:[term]</code></blockquote>
 * 
 * <p>In which nodeName is either empty, an unqualified element name, a prefixed element name
 * (ie a QName), or a QName prefixed with "@", indicating an attribute. nodeName is optional:
 * if it is not present, a full text query of the entire document is indicated.  The "node"
 * prefix is also optional. Concrete examples:
 * </p>
 * 
 * <pre>
 *  node::"Alas, poor Yorick"
 *  node:title:Hamlet
 *  node:@id:s12340
 *  node:@xml:id:x2345
 *  node:math:equation:3.14159
 *  
 *  or, equivalently:
 *  
 *  ::"Alas, poor Yorick"
 *  :title:Hamlet
 *  :@id:s12340
 *  :@xml:id:x2345
 *  :math:equation:3.14159
 * </pre>
 * 
 * <p>TODO: supply a facility for looking up namespace prefixes (such as <code>math</code> in the last example).</p>
 */
public class LuxQueryParser extends ExtendableQueryParser {
    
    public LuxQueryParser(Version matchVersion, String f, Analyzer a) {
        super(matchVersion, f, a, new NodeExtensions (new NodeParser(a)));
    }
    
    static class NodeParser extends ParserExtension {
        
        QNameQueryBuilder queryBuilder;
        
        NodeParser (Analyzer a) {
            queryBuilder = new QNameQueryBuilder(a);            
        }

        @Override
        public org.apache.lucene.search.Query parse(ExtensionQuery query) throws ParseException {
            String field = query.getField();
            String term = query.getRawQueryString();
            // create either a term query or a phrase query (or a span?)
            if (StringUtils.isEmpty(field)) {
                return queryBuilder.parseQueryTerm(XmlField.XML_TEXT.getName(), field, term, 1.0f);
            } else if (field.charAt(0) == '@') {
                return queryBuilder.parseQueryTerm(XmlField.ATTRIBUTE_TEXT.getName(), field.substring(1), term, 1.0f);
            } else {
                return queryBuilder.parseQueryTerm(XmlField.ELEMENT_TEXT.getName(), field, term, 1.0f);
            }
        }
        
    }
    
    static class NodeExtensions extends Extensions {
        
        NodeExtensions (NodeParser parser) {
            super ('<'); // set extension field delimiter
            add ("node", parser);
            add ("", parser);            
        }
    
        /**
         * reverses the default order so that extension string comes first, followed by the extended field name.
         * Therefore ext::term indicates the default extended field, and :field:term yields an empty extension string.
         */
        public Pair<String,String> splitExtensionField(String defaultField, String field) {
            int indexOf = field.indexOf(getExtensionFieldDelimiter());
            if (indexOf < 0)
              return new Pair<String,String>(field, null);
            final String extensionKey = field.substring(0, indexOf);
            final String indexField = indexOf >= field.length()-1 ? defaultField : field.substring(indexOf + 1);
            return new Pair<String,String>(indexField, extensionKey);
        }
    }

}
