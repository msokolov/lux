package lux.query;

import lux.index.IndexConfiguration;
import lux.xquery.ElementConstructor;

/**
 * ParseableQueries represent Lucene Queries in an abstract form that can be rendered as an XML tree
 * for later parsing. This is used by Lux to embed generated queries within an XQuery expression.
 */
public abstract class ParseableQuery {

    /** 
     * @param field the prevailing field in the query's surrounding context.
     * @param config the index configuration
     * @return an xml object representation of the query, in a format suitable for parsing by one the Lucene XML Query Parser
     */
    public abstract ElementConstructor toXmlNode(String field, IndexConfiguration config);

    /** 
     * @param field the prevailing field in the query's surrounding context.
     * @param config the index configuration
     * @return a String representation of the query, in the Surround Query Parser dialect
     */
    public abstract String toQueryString(String field, IndexConfiguration config);
    
    @Override
    public String toString () {
        return toQueryString ("", IndexConfiguration.DEFAULT);
    }
    
    public boolean isSpan() {
    	return false;
    }

}
