package lux.query;

import java.util.ArrayList;

import lux.index.IndexConfiguration;
import lux.xml.QName;
import lux.xpath.AbstractExpression;
import lux.xpath.LiteralExpression;
import lux.xpath.Sequence;
import lux.xquery.AttributeConstructor;
import lux.xquery.ElementConstructor;

import org.apache.lucene.search.BooleanClause.Occur;

public class BooleanPQuery extends ParseableQuery {

    public static final QName BOOLEAN_QUERY_QNAME = new QName("BooleanQuery");
    public static final QName CLAUSE_QNAME = new QName("Clause");
    public static final LiteralExpression OCCURS_ATT_NAME = new LiteralExpression("occurs");
    public static final AttributeConstructor MUST_OCCUR_ATT = new AttributeConstructor (OCCURS_ATT_NAME, new LiteralExpression ("must"));
    public static final AttributeConstructor SHOULD_OCCUR_ATT = new AttributeConstructor (OCCURS_ATT_NAME, new LiteralExpression ("should"));
    public static final AttributeConstructor MUST_NOT_OCCUR_ATT = new AttributeConstructor (OCCURS_ATT_NAME, new LiteralExpression ("mustNot"));
    private Clause clauses[];
    
    public BooleanPQuery (Clause ... clauses) {
    	setClauses (clauses);
    }
    
    public BooleanPQuery (Occur occur, ParseableQuery ... queries) {
        Clause[] cl= new Clause[queries.length];
        int i = 0;
        for (ParseableQuery query : queries) {
            cl[i++] = new Clause(query, occur);
        }
        setClauses (cl);
    }    
    
    private void setClauses (Clause[] clauses) {
    	if (clauses.length == 0) {
    		this.clauses = clauses;
    		return;
    	}
        Occur oc = clauses[0].getOccur();
        // We assume all the clauses have the same occur 
        // otherwise possibly merge the clauses if some of them are BooleanPQuery
        ArrayList<Clause> cl = new ArrayList<Clause> ();
        RangePQuery rangeQuery = null;
        for (Clause clause : clauses) {
            ParseableQuery query = clause.getQuery();
            if (query instanceof BooleanPQuery) {
                BooleanPQuery bq = (BooleanPQuery) query;
                if (bq.getOccur() == oc) {
                    // same occur; let's merge
                    for (Clause subclause : bq.getClauses()) {
                        cl.add (subclause);
                    }
                    continue;
                }
            } else if (query instanceof RangePQuery) {
            	RangePQuery rquery = (RangePQuery) query;
				if (rangeQuery == null) {
            		rangeQuery = rquery;
            	} else {
            		if (rangeQuery.intersect (rquery)) {
            			continue;
            		} else {
            			// assume similar fields are adjacent?
            			rangeQuery = rquery;
            		}
            	}
            } else if (query instanceof MatchAllPQuery && oc == Occur.MUST) {
                continue;
            }
            // no merging possible
            cl.add (clause);
        }
        this.clauses = cl.toArray(new Clause[cl.size()]);
    }
    
    public Occur getOccur () {
        return clauses.length > 0 ? clauses[0].occur : Occur.SHOULD;
    }
    
    public Clause[] getClauses() {
        return clauses;
    }

    @Override
    public ElementConstructor toXmlNode(String field, IndexConfiguration config) {
        if (clauses.length == 1 && clauses[0].occur == Occur.MUST) {
            return clauses[0].getQuery().toXmlNode(field, config);
        }
        AbstractExpression[] clauseExprs = new AbstractExpression[clauses.length];
        int i = 0;
        for (Clause clause : clauses) {
            AbstractExpression q = clause.getQuery().toXmlNode(field, config);
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
        return new ElementConstructor(BOOLEAN_QUERY_QNAME, new Sequence(clauseExprs));
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

    // derived from BooleanQuery.toString()
    @Override
    public String toQueryString(String field, IndexConfiguration config) {
        StringBuilder buf = new StringBuilder();
        for (int i = 0 ; i < clauses.length; i++) {
          Clause c = clauses[i];
          if (c.occur == Occur.MUST_NOT) {
            buf.append("-");
          }
          else if (c.occur == Occur.MUST) {
            buf.append("+");
          }
          ParseableQuery subq = c.getQuery();
          if (subq != null) {
            if (subq instanceof BooleanPQuery) {
              buf.append('(').append(subq.toQueryString(field, config)).append(')');
            } else {
              buf.append(subq.toQueryString(field, config));
            }
          }
          if (i < clauses.length-1) {
            buf.append(' ');
          }
        }
        return buf.toString();
    }
    
    @Override
    public boolean equals(ParseableQuery other) {
        if (! (other instanceof BooleanPQuery)) {
            return false;
        }
        BooleanPQuery oq = (BooleanPQuery) other;
        if (clauses.length != oq.clauses.length) {
            return false;
        }
        for (int i = 0; i < clauses.length; i++) {
            if (! (clauses[i].occur == oq.clauses[i].occur &&
                    clauses[i].query.equals(oq.clauses[i].query))) {
                return false;
            }
        }
        return true;
    }

}
