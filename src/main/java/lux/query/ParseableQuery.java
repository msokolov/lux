package lux.query;

import lux.xquery.ElementConstructor;


/**
 * ParseableQueries represent Lucene Queries in an abstract form that can be rendered as an XML tree
 * for later parsing. This is used by Lux to embed generated queries within an XQuery expression.
 */
public abstract class ParseableQuery {

    /** 
     * @param field the prevailing field in the query's surrounding context.
     * @param config a Saxon configuration holding the namepool used to construct the xml objects
     * @return an xml object representation of the query, in a format suitable for parsing by one the Lucene XML Query Parser
     */
    public abstract ElementConstructor toXmlNode(String field);

}
