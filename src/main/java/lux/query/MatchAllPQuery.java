package lux.query;

public final class MatchAllPQuery extends ParseableQuery {

    private static MatchAllPQuery instance = new MatchAllPQuery();
    
    public static MatchAllPQuery getInstance() {
        return instance;
    }
    
    public String toXmlString(String field) {
        return "<MatchAllDocsQuery />";
    }
    
    public String toString(String field) {
        return "*:*";
    }

}
