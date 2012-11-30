package lux.query;

import lux.index.IndexConfiguration;
import lux.xml.QName;
import lux.xpath.AbstractExpression;
import lux.xpath.LiteralExpression;
import lux.xpath.Sequence;
import lux.xquery.AttributeConstructor;
import lux.xquery.ElementConstructor;

import org.apache.lucene.search.BooleanClause.Occur;

public class BooleanPQuery extends ParseableQuery {

    private static final QName CLAUSE_QNAME = new QName("Clause");
    private static final LiteralExpression OCCURS_ATT_NAME = new LiteralExpression("occurs");
    private static final AttributeConstructor MUST_OCCUR_ATT = new AttributeConstructor (OCCURS_ATT_NAME, new LiteralExpression ("must"));
    private static final AttributeConstructor SHOULD_OCCUR_ATT = new AttributeConstructor (OCCURS_ATT_NAME, new LiteralExpression ("should"));
    private static final AttributeConstructor MUST_NOT_OCCUR_ATT = new AttributeConstructor (OCCURS_ATT_NAME, new LiteralExpression ("mustNot"));
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
    
    public Occur getOccur () {
        return clauses[0].occur;
    }
    
    public Clause[] getClauses() {
        return clauses;
    }

    @Override
    public ElementConstructor toXmlNode(String field) {
        if (clauses.length == 1 && clauses[0].occur == Occur.MUST) {
            return clauses[0].getQuery().toXmlNode(field);
        }
        AbstractExpression[] clauseExprs = new AbstractExpression[clauses.length];
        int i = 0;
        for (Clause clause : clauses) {
            AbstractExpression q = clause.getQuery().toXmlNode(field);
            AttributeConstructor occurAtt = null;
            if (clause.occur == Occur.MUST) {
                occurAtt = MUST_OCCUR_ATT;                
            }
            else if (clause.occur == Occur.SHOULD) {
                occurAtt = SHOULD_OCCUR_ATT;                 
            }
            else if (clause.occur == Occur.MUST_NOT) {
                occurAtt = MUST_NOT_OCCUR_ATT;
            }
            clauseExprs[i++] = new ElementConstructor(CLAUSE_QNAME, q, occurAtt);
        }
        return new ElementConstructor(new QName("BooleanQuery"), new Sequence(clauseExprs));
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

    @Override
    public String toSurroundString(String field, IndexConfiguration config) {
        // TODO Auto-generated method stub
        return null;
    }

}
