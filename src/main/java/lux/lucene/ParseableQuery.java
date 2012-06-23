package lux.lucene;

//  FIXME: come up with a better naming convention for these queries that identifies
// them as all of piece
// TODO augment methods returning strings with methods accepting stringbuilders
public abstract class ParseableQuery {
    /**
     * @param field the prevailing field in the query's surrounding context.  
     * @return a serialized XML encoding of the query,
     * in a format suitable for parsing by the Lucene XML Query Parser
     */
    public abstract String toXml(String field);
    
    /**
     * @param field the prevailing field in the query's surrounding context.
     * @return a serialized encoding of the query,
     * in a format suitable for parsing by the "standard" Lucene Query Parser
     */
    public abstract String toString(String field);
    
    @Override
    public String toString() {
        return toString (null);
    }
}
