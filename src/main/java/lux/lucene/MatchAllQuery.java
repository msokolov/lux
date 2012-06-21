package lux.lucene;

public final class MatchAllQuery extends ParseableQuery {

    private static MatchAllQuery instance = new MatchAllQuery();
    
    public static MatchAllQuery getInstance() {
        return instance;
    }
    
    public String toXml(String field) {
        return "<MatchAllDocsQuery />";
    }
    
    public String toString(String field) {
        return "*:*";
    }

}
