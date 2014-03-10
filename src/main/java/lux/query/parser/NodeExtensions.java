package lux.query.parser;

import org.apache.lucene.queryparser.ext.Extensions;

class NodeExtensions extends Extensions {
    
    NodeExtensions (NodeParserExtension parser) {
        super ('<'); // set extension field delimiter
        add ("node", parser);
        add ("", parser);
    }

    /**
     * reverses the default order so that extension string comes first, followed by the extended field name.
     * Therefore ext::term indicates the default extended field, and :field:term yields an empty extension string.
     */
    @Override
    public Pair<String,String> splitExtensionField(String defaultField, String field) {
        int indexOf = field.indexOf(getExtensionFieldDelimiter());
        if (indexOf < 0)
          return new Pair<String,String>(field, null);
        final String extensionKey = field.substring(0, indexOf);
        final String indexField = indexOf >= field.length()-1 ? null : field.substring(indexOf + 1);
        return new Pair<String,String>(indexField, extensionKey);
    }
}