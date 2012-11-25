package lux.query.parser;

import lux.index.FieldName;
import lux.index.IndexConfiguration;

import org.apache.commons.lang.StringUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.queryParser.ext.ExtendableQueryParser;
import org.apache.lucene.queryParser.ext.ExtensionQuery;
import org.apache.lucene.queryParser.ext.Extensions;
import org.apache.lucene.queryParser.ext.ParserExtension;

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
public class LuxQueryParser extends ExtendableQueryParser {
    
    public LuxQueryParser(IndexConfiguration config) {
        super(IndexConfiguration.LUCENE_VERSION, 
                config.getFieldName(FieldName.XML_TEXT), 
                config.getField(FieldName.ELEMENT_TEXT).getAnalyzer(), 
                new NodeExtensions (new NodeParser(
                        config.getFieldName(FieldName.XML_TEXT),
                        config.getFieldName(FieldName.ELEMENT_TEXT),
                        config.getFieldName(FieldName.ATTRIBUTE_TEXT),
                        config.getField(FieldName.ELEMENT_TEXT).getAnalyzer())));
    }
    
    static class NodeParser extends ParserExtension {
        
        private final String textFieldName;
        private final String elementTextFieldName;
        private final String attributeTextFieldName;
        QNameQueryBuilder queryBuilder;
        
        NodeParser (String textFieldName, String elementTextFieldName, String attributeTextFieldName, Analyzer a) {
            queryBuilder = new QNameQueryBuilder(a);
            this.textFieldName = textFieldName;
            this.elementTextFieldName = elementTextFieldName;
            this.attributeTextFieldName = attributeTextFieldName;
        }

        @Override
        public org.apache.lucene.search.Query parse(ExtensionQuery query) throws ParseException {
            String field = query.getField();
            String term = query.getRawQueryString();
            // create either a term query or a phrase query (or a span?)
            if (StringUtils.isEmpty(field)) {
                return queryBuilder.parseQueryTerm(textFieldName, field, term, 1.0f);
            } else if (field.charAt(0) == '@') {
                return queryBuilder.parseQueryTerm(attributeTextFieldName, field.substring(1), term, 1.0f);
            } else {
                return queryBuilder.parseQueryTerm(elementTextFieldName, field, term, 1.0f);
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
            final String indexField = indexOf >= field.length()-1 ? null : field.substring(indexOf + 1);
            return new Pair<String,String>(indexField, extensionKey);
        }
    }

}
