package lux.lucene;

import org.apache.lucene.search.BooleanClause.Occur;

public class BooleanPQuery extends ParseableQuery {

    private Clause clauses[];
    
    public BooleanPQuery (Clause ... clauses) {
        this.clauses = clauses;
    }
    
    public BooleanPQuery (Occur occur, ParseableQuery ... queries) {
        clauses = new Clause[queries.length];
        int i = 0;
        for (ParseableQuery query : queries) {
            clauses[i++] = new Clause(query, occur);
        }
    }

    public String toXml (String field) {
        StringBuilder buf = new StringBuilder("<BooleanQuery>");
        for (Clause clause : clauses) {
            buf.append ("<Clause occurs=\"").append (clause.getOccurAttribute()).append("\">");
            buf.append (clause.getQuery().toXml(field));
            buf.append("</Clause>");
        }
        buf.append ("</BooleanQuery>");
        return buf.toString();
    }
    
    public Occur getOccur () {
        return clauses[0].occur;
    }
    
    public Clause[] getClauses() {
        return clauses;
    }
    
    public String toString(String field) {
        StringBuilder buf = new StringBuilder();
        for (Clause clause : clauses) {
            if (buf.length() > 0) {
                buf.append (' ');
            }
            Occur occur = clause.getOccur();
            if (occur == Occur.MUST_NOT) {
                buf.append ('-');
            } 
            else if (occur == Occur.MUST) {
                buf.append ('+');
            }
            boolean isTerm = clause.getQuery() instanceof LuxTermQuery;
            if (!isTerm) {
                buf.append ('(');
            }
            buf.append (clause.getQuery().toString(field));
            if (!isTerm) {
                buf.append (')');
            }
        }
        return buf.toString();
    }
    
    public static class Clause {
        private final Occur occur;
        private final ParseableQuery query;        

        public Clause (ParseableQuery query, Occur occur) {
            this.occur = occur;
            this.query = query;
        }
        
        public Occur getOccur () {
            return occur;
        }

        public String getOccurAttribute() {
            switch (occur) {
            case MUST: return "must";
            case SHOULD: return "should";
            case MUST_NOT: return "mustNot";
            default: return "";
            }
        }

        public ParseableQuery getQuery() {
            return query;
        }
    }

}
