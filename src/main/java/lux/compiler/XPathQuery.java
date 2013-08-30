package lux.compiler;

import java.util.ArrayList;
import java.util.Arrays;

import lux.index.IndexConfiguration;
import lux.query.BooleanPQuery;
import lux.query.BooleanPQuery.Clause;
import lux.query.MatchAllPQuery;
import lux.query.ParseableQuery;
import lux.query.SpanBooleanPQuery;
import lux.query.SpanMatchAll;
import lux.query.SpanNearPQuery;
import lux.xml.ValueType;
import lux.xpath.AbstractExpression;

import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.SortField;

/**
 * Wraps a Lucene Query, with advice as to how to process its results as XPath.
 * For now, simply distinguishes the two cases: whether the results are in fact
 * supposed to be the results of the original XPath evaluation, or if further
 * evaluation is needed.
 */
/*
 * We could also tell: whether the query will return the correct document set;
 * it's possible that we may sometimes retrieve documents that don't match.
 * We're not allowed to miss a document, though. Some evaluators that return the
 * correct doc set still may need additional evaluation though if the results
 * are not to be documents.
 * 
 * We go to great lengths to maintain return type info, yet it is only used in one place
 * that has any other purpose than maintaining this info:
 * PathOptimizer.optimizeFunCall checks if the return type is document when determining 
 * whether to replace count() with lux:count()
 * 
 */
public class XPathQuery {

    // a Lucene query corresponding to an XQuery expression
    private final ParseableQuery pquery;

    // a Lucene query corresponding to the base expression of an XQuery expression:
    // this ignores any constraints deriving from predicates and is used for combining
    // path queries
    private ParseableQuery pathQuery;

    private ValueType valueType;
    private final boolean immutable;
    
    /** bitmask holding facts proven about the query; generally these facts enable different
     * optimizations.  In the comments, we refer to the "result type" of the query meaning the
     * result type of the xpath expression that the query was generated from.
     */
    private long facts;

    /**
     * A Lucene sort order to be applied to the query.  This will have been computed from an XQuery order by expression.
     */
    private SortField[] sortFields;
    
    public SortField[] getSortFields() {
        return sortFields;
    }

    public void setSortFields(SortField[] sortFields) {
        this.sortFields = sortFields;
        if (sortFields != null) {
            setFact (IGNORABLE, false); // prevent the fields from getting dropped
        }
    }

    /**
     * A query is exact iff its xpath expression returns exactly one value per document, and the
     * generated lucene query returns exactly those documents satisfying the xpath expression.
     * EXACT <=> MINIMAL and SINGULAR, and we never use EXACT explicitly
     */
    public static final int EXACT=0x00000001;
    
    /**
     * A query is minimal if it returns all, and only, those documents satisfying the xpath expression.
     * Exact queries are all minimal.
     */
    public static final int MINIMAL=0x00000002;

    /**
     * An expression is singular if it returns a single result for every matching document.
     * An XPathQuery is singular if it was generated from a singular expression, and therefore
     * its expression returns the same number of results as the lucene query.
     * 
     */
    public static final int SINGULAR=0x00000004;

    /**
     * A query is boolean_false if its result type is boolean, and the existence of a single query result indicates a 'false()' value
     */
    public static final int BOOLEAN_FALSE=0x00000010;
    
    /**
     * If a query a is ignorable, then combine(a,b) = b unless b is also ignorable,
     * in which case combine(a,b) = a|b
     */
    public static final int IGNORABLE=0x00000020;
    
    /** queries that match all documents (have no filter) are empty. */
    public static final int EMPTY=0x00000040;
    
    /** queries whose path query constraint is already incorporated into the main query */
    public static final int PATH_COMBINED=0x00000080;
    
    public final static XPathQuery MATCH_ALL = new XPathQuery(MatchAllPQuery.getInstance(), MatchAllPQuery.getInstance(), MINIMAL|SINGULAR|EMPTY, ValueType.DOCUMENT, true);
    
    private final static XPathQuery PATH_MATCH_ALL = new XPathQuery(SpanMatchAll.getInstance(), SpanMatchAll.getInstance(), MINIMAL|SINGULAR|EMPTY, ValueType.DOCUMENT, true);
    
    /**
     * @param query a Lucene query
     * @param pathQuery a Lucene query representing a constraint that the context item's path exists
     * @param resultFacts a bitmask with interesting facts about this query
     * @param valueType the type of results returned by the xpath expression, as specifically as 
     * @param immutable whether this query may be changed - set true for some internal statics like MATCH_ALL
     * can be determined.
     */
    protected XPathQuery(ParseableQuery query, ParseableQuery pathQuery, long resultFacts, ValueType valueType, boolean immutable) {
        this.pquery = query;
        this.pathQuery = pathQuery;
        this.facts = resultFacts;
        setType (valueType);
        this.immutable = immutable;
    }
    
    protected XPathQuery(ParseableQuery query, long resultFacts, ValueType valueType, boolean immutable) {
        this (query, null, resultFacts, valueType, immutable);
    }
    
    protected XPathQuery(ParseableQuery query, long resultFacts, ValueType valueType) {
        this (query, resultFacts, valueType, false);
    }
    
    /** 
     * @param query the query on which the result is based
     * @param pathQuery 
     * @param resultFacts the facts to use in the new query
     * @param valueType the result type of the new query
     * @param indexConfig the indexer configuration; controls which type of match-all query may be returned
     * @param sortFields the sort fields to record in the query
     * @return a new query (or an immutable query) based on an existing query with some modifications.
     */
    public static XPathQuery getQuery (ParseableQuery query, ParseableQuery pathQuery, long resultFacts, ValueType valueType, IndexConfiguration indexConfig, SortField[] sortFields) {
        XPathQuery q;
        q = new XPathQuery (query, resultFacts, valueType);
        q.setPathQuery(pathQuery);
        q.setSortFields(sortFields);
        return q;
    }
    
    /* decides whether to create a pathQuery or a main query based on the type of the termQuery argument. */
    public static XPathQuery getQuery(ParseableQuery termQuery, long facts,
            ValueType type, IndexConfiguration indexConfig, SortField[] sortFields) {
        if (termQuery.isSpanCompatible()) {
            return getQuery (null, termQuery, facts, type, indexConfig, sortFields);
        }
        return getQuery (termQuery, null, facts, type, indexConfig, sortFields);
    }

    public static XPathQuery getMatchAllQuery (IndexConfiguration indexConfig) {
        if (indexConfig.isOption(IndexConfiguration.INDEX_PATHS)) {
            return PATH_MATCH_ALL;
        }
        return MATCH_ALL;
    }
    
    /**
     * @return whether it is known that the query will return the minimal set of
     *         documents containing the required result value. If false, some
     *         documents may be returned that will eventually need to be
     *         discarded if they don't match the xpath.
     */
    public boolean isMinimal() {
        return (facts & MINIMAL) != 0;
    }

    public ValueType getResultType() {
        return valueType;
    }

    /**
     * Combines this query with another.
     * @param occur the occurrence specifier for this query
     * @param precursor the other query
     * @param precursorOccur the occurrence specifier for the precursor query
     * @param type the return type of the combined query
     * @param config the index configuration
     * @return the combined query
     */
    public XPathQuery combineBooleanQueries(Occur occur, XPathQuery precursor, Occur precursorOccur, ValueType type, IndexConfiguration config) {
        XPathQuery result = combineIgnorableQueries(occur, precursor);
        if (result != null) {
            return result;
        }
        long resultFacts = combineQueryFacts (this, precursor);
        ParseableQuery combined = combineBoolean (this.pquery, occur, precursor.pquery, precursorOccur);
        SortField[] combinedSorts = combineSortFields(precursor);
        XPathQuery q = getQuery(combined, null, resultFacts, type, config, combinedSorts);
        q.setPathQuery(pathQuery);
        return q;
    }

    private XPathQuery combineIgnorableQueries(Occur occur, XPathQuery precursor) {
        if (occur == Occur.MUST && isFact(IGNORABLE) != precursor.isFact(IGNORABLE)) {
            if (isFact(IGNORABLE)) {
                if (isEmpty() && isMinimal()) {
                    return precursor;
                } else {
                    return precursor.setFact(MINIMAL, false); // we are losing some information by ignoring this query
                }
            }
            else {
                if (precursor.isEmpty() && precursor.isMinimal()) {
                    return this;
                } else {
                    return setFact (MINIMAL, false);  // we are losing some information by ignoring the precursor query
                }
            }
        }
        return null;
    }

    private SortField[] combineSortFields(XPathQuery precursor) {
        if (sortFields != null) {
            if (precursor.sortFields != null) {
            	ArrayList<SortField> combined = new ArrayList<SortField>(Arrays.asList(sortFields));
            	SortField prevSort = combined.get(combined.size()-1);
            	for (SortField sortField : precursor.sortFields) {
            		if (! sortField.equals(prevSort)) {
            			combined.add(sortField);
            		}
            	}
                return combined.toArray(new SortField[combined.size()]);
            } else {
                return sortFields;
            }
        } else if (precursor.sortFields != null) {
            return precursor.sortFields;
        } else {
            return null;
        }
    }

    /**
     * Combines this query with another, separated by the given distance.  Generates Lucene SpanQuerys, and
     * the constituent queries must be span queries as well.
     * @param precursor the other query
     * @param occur the boolean operator used to combine
     * @param isPredicate whether the enclosing expression is a predicate (or a path expression)
     * @param type the return type of the combined query
     * @param distance the distance between the queries
     * @param config the index configuration
     * @return the combined query
     */
    public XPathQuery combineSpanQueries(XPathQuery precursor, Occur occur, boolean isPredicate, ValueType type, int distance, IndexConfiguration config) {
        XPathQuery result = combineIgnorableQueries(occur, precursor);
        if (result != null) {
            return result;
        }
        long resultFacts = combineQueryFacts (this, precursor);
        ParseableQuery combined, pathCombined; 
        if (this.pathQuery == null) {
            pathCombined = precursor.pathQuery;
            if (precursor.isFact(PATH_COMBINED)) {
                resultFacts |= PATH_COMBINED;
            }
        } else if (precursor.pathQuery == null) {
            pathCombined = this.pathQuery;
            if (isFact(PATH_COMBINED)) {
                resultFacts |= PATH_COMBINED;
            }
        } else {
            pathCombined = combineSpans (this.pathQuery, occur, precursor.pathQuery, distance);
            // we're adding something new to the path query, so it isn't known to be subsumed by the main query
            resultFacts &= ~PATH_COMBINED;
        }
        combined = combineBoolean(pquery, occur, precursor.pquery, occur);
        if (isPredicate) {
            // if this is a predicate, use existing pathQuery without combining, and add the combined pathQuery
            // to the main query
            combined = combineBoolean (combined, occur, pathCombined, occur);
            pathCombined = pathQuery;
            resultFacts |= PATH_COMBINED; // indicate that the path query has been incorporated into the main query, so it is redundant
        }
        SortField[] combinedSorts = combineSortFields(precursor);
        XPathQuery q = new XPathQuery(combined, resultFacts, type);
        q.setPathQuery(pathCombined);
        q.setSortFields(combinedSorts);
        return q;
    }

    private static long combineQueryFacts (XPathQuery a, XPathQuery b) {
        if (b.isEmpty() && b.isMinimal()) {
            return a.facts; 
        }
        else if (a.isEmpty() && a.isMinimal()) {
            return b.facts;
        }
        else {
            return combineFacts(a.facts, b.facts) ;
        }
    }

    private static ParseableQuery combineBoolean (ParseableQuery a, Occur aOccur, ParseableQuery b, Occur bOccur) {
        if (a == null || a.equals(b)) {
            return b;
        }
        if (b == null) {
            return a;
        }
        if (a.isMatchAll()) {
            if (bOccur != Occur.MUST_NOT) {
                return b;
            }
        }
        if (b.isMatchAll()) {
            if (aOccur != Occur.MUST_NOT) {
                return a;
            }
        }
        return new BooleanPQuery(new BooleanPQuery.Clause(a, aOccur), new BooleanPQuery.Clause(b,  bOccur));
    }
    
    private static ParseableQuery combineSpans (ParseableQuery a, Occur occur, ParseableQuery b, int distance) {

        // don't create a span query for //foo; a single term is enough
        // distance < 0 means no distance could be computed
        if (a.isMatchAll() && occur != Occur.MUST_NOT && (distance > 90 || distance < 0)) {
            if (occur == Occur.SHOULD) {
                return a;
            }
            return b;
        }
        if (b.isMatchAll()) {
            if (occur == Occur.SHOULD) {
                return b;
            }
            return a;
        }
        if (distance >= 0) {
            // there is a specific distance (path steps separate by /*/, say)
            return combineFiniteSpan(a, occur, b, distance);
        }
        // distance = -1
        if (a.equals(b)) {
            return a;
        }
        return new SpanBooleanPQuery(occur, a, b);
    }

    private static ParseableQuery combineFiniteSpan(ParseableQuery a, Occur occur, ParseableQuery b, int distance) {
        if (occur != Occur.MUST) {
            throw new IllegalArgumentException ("unsupported boolean combination for span query: " + occur);
        }
        assert (! (a instanceof SpanBooleanPQuery && b instanceof SpanBooleanPQuery));
        if ((a instanceof SpanBooleanPQuery && ((SpanBooleanPQuery) a).getOccur() == Occur.MUST) ||
                (b instanceof SpanBooleanPQuery && ((SpanBooleanPQuery) b).getOccur() == Occur.MUST)) {
            return combineBooleanWithSpan(a, b, distance);
        }
        if (a == MatchAllPQuery.getInstance()) {
            return new SpanNearPQuery(distance, true, SpanMatchAll.getInstance(), b);
        }
        return new SpanNearPQuery(distance, true, a, b);
    }
    
    private static ParseableQuery combineBooleanWithSpan(ParseableQuery a, ParseableQuery b, int distance) {
        // ((A NEAR B) AND C) NEAR D => ((A NEAR B) AND (C NEAR D))
        // but what about
        // A NEAR (B AND C) => (A NEAR B) AND (B NEAR C)
        SpanBooleanPQuery bq = (SpanBooleanPQuery) ((a instanceof SpanBooleanPQuery) ? a : b);
        Clause[] bclauses = bq.getClauses();
        Clause[] clauses = new Clause [bclauses.length];
        for (int i = 0; i < clauses.length; i++) {
            Clause clause = bclauses[i];
            ParseableQuery query = clause.getQuery();
            if (! query.isSpanCompatible()) {
                clauses[i] = clause;
                continue;
            }
            if (bq == a) {
                clauses[i] = new Clause (new SpanNearPQuery (distance, true, query, b), clause.getOccur());
            } else {
                clauses[i] = new Clause (new SpanNearPQuery (distance, true, a, query), clause.getOccur());
            }
        }
        return new SpanBooleanPQuery (clauses);
    }
    
    private static final long combineFacts (long facts2, long facts3) {
        return facts2 & facts3;
    }

    public boolean isEmpty() {
        return isFact(EMPTY);
    }
    
    @Override
    public String toString () {
        StringBuilder buf = new StringBuilder ();
        buf.append(pquery == null ? "" : pquery.toString());
        if (pathQuery != null) {
            buf.append('{').append(pathQuery.toString()).append('}');
        }
        return buf.toString();
    }

  public XPathQuery setFact(int fact, boolean t) {
      XPathQuery query = this;
      if (immutable) {
          query = new XPathQuery (this.pquery, facts, valueType);
      }
      if (t) {
          query.facts |= fact;
      } else {
          query.facts &= (~fact);
      }
      return query;
  }
  
  public final boolean isFact (int fact) {
      return (facts & fact) == fact;
  }

  public long getFacts() {
      return facts;
  }

  public XPathQuery setType(ValueType type) {
	  XPathQuery query;
      if (type == null) {
          type = ValueType.VALUE;
      }
	  if (immutable) {
          query = new XPathQuery (pquery, facts, type);
      } else {
    	  query = this;
      }
      query.valueType = type;
      query.facts &= ~BOOLEAN_FALSE;
      if (query.valueType == ValueType.BOOLEAN) {
          query.facts &= ~SINGULAR;
      }
      else if (query.valueType == ValueType.DOCUMENT) {
          query.facts |= SINGULAR;
      }
      return query;
      // no other type info is stored in facts since it's not needed by search()
  }

  public boolean isImmutable() {
      return immutable;
  }

  public AbstractExpression toXmlNode(String defaultField, IndexConfiguration config) {
      return getFullQuery().toXmlNode(defaultField, config);
  }
  
  public ParseableQuery getBooleanQuery() {
      return pquery;
  }

  /**
   * A query generated by a predicate expression.  Predicates store their base query,
   * rather than their predicated filter query, as the base for path combinations, and
   * also set the filter query to add in as an additional filter.
   * @return the filter query.
   */
  public ParseableQuery getPathQuery() {
      return pathQuery;
  }

  public void setPathQuery(ParseableQuery baseQuery) {
      this.pathQuery = baseQuery;
  }
  
  public ParseableQuery getFullQuery () {
      ParseableQuery q = getBooleanQuery();
      if (pathQuery != null && !isFact(PATH_COMBINED)) {
          // add in the path constraint in baseQuery
          if (q == null || q.isMatchAll()) {
              q = pathQuery;
          } else {
              q = new BooleanPQuery (Occur.MUST, q, pathQuery);
          }
      }
      return q;
  }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
