package lux.query;

import lux.xquery.ElementConstructor;


// TODO augment methods returning strings with methods accepting stringbuilders
/**
 * ParseableQueries represent Lucene Queries in an abstract form that can be serialized as Strings 
 * or rendered as an XML tree.  This is used by Lux to embed generated queries within an XQuery expression.
 */
public abstract class ParseableQuery {
    /**
     * @param field the prevailing field in the query's surrounding context.  
     * @return a serialized XML encoding of the query,
     * in a format suitable for parsing by the Lucene XML Query Parser
     */
    public abstract String toXmlString(String field);

    /** Produces the same xml representation of the query as {@link #toXmlString(String)}, but in a more 
     * efficient way than parsing the string.
     * @param field the prevailing field in the query's surrounding context.
     * @param config a Saxon configuration holding the namepool used to construct the xml objects
     * @return an xml object representation of the query,
     * in a format suitable for parsing by one the Lucene XML Query Parser
     */
    public abstract ElementConstructor toXmlNode(String field);
    
    /**
     * @param field the prevailing field in the query's surrounding context.
     * @return a serialized encoding of the query,
     * in a format suitable for parsing by one of the Lucene Query Parsers:
     * either the "standard" parser, or, if the query is a SpanQuery, the "surround" parser.
     */
    public abstract String toString(String field);
    
    @Override
    public String toString() {
        return toString (null);
    }
}
