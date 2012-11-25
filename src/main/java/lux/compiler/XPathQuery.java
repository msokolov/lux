/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */

package lux.compiler;

import lux.exception.LuxException;
import lux.index.IndexConfiguration;
import lux.query.BooleanPQuery;
import lux.query.MatchAllPQuery;
import lux.query.ParseableQuery;
import lux.query.SurroundBoolean;
import lux.query.SurroundMatchAll;
import lux.query.SurroundSpanQuery;
import lux.xml.ValueType;
import net.sf.saxon.s9api.XQueryExecutable;

import org.apache.lucene.search.BooleanClause.Occur;

/**
 * Wraps a Lucene Query, with advice as to how to process its results as XPath.
 * For now, simply distinguishes the two cases: whether the results are in fact
 * supposed to be the results of the original XPath evaluation, or if further
 * evaluation is needed.
 * 
 * We could also tell: whether the query will return the correct document set;
 * it's possible that we may sometimes retrieve documents that don't match.
 * We're not allowed to miss a document, though. Some evaluators that return the
 * correct doc set still may need additional evaluation though if the results
 * are not to be documents.
 */
public class XPathQuery {

    private final ParseableQuery query;
    private XQueryExecutable expr;
    private ValueType valueType;
    private final boolean immutable;
    
    /** bitmask holding facts proven about the query; generally these facts enable different
     * optimizations.  In the comments, we refer to the "result type" of the query meaning the
     * result type of the xpath expression that the query was generated from.
     */
    protected long facts;
    
    /**
     * A query is exact iff its xpath expression returns exactly one value per document, and the
     * generated lucene query returns exactly those documents satisfying the xpath expression.
     */
    public static final int EXACT=0x00000001;
    
    /**
     * A query is minimal if it returns all, and only, those documents satisfying the xpath expression.
     * Exact queries are all minimal.
     */
    public static final int MINIMAL=0x00000002;
    
    /**
     * A query is counting if its expression returns the count of the results of the lucene query
     */
    public static final int COUNTING=0x00000004;
    
    public static final int RESULT_TYPE_FLAGS = 0x00000018;

    /**
     * A query is boolean_true if its result type is boolean, and the existence of a single query result indicates a 'true()' value
     */
    public static final int BOOLEAN_TRUE=0x00000008;

    /**
     * A query is boolean_false if its result type is boolean, and the existence of a single query result indicates a 'false()' value
     */
    public static final int BOOLEAN_FALSE=0x00000010;
    
    /**
     * A query has document results if its result type is document-node()
     */
    public static final int DOCUMENT_RESULTS=0x00000018;
    

    public final static XPathQuery MATCH_ALL = new XPathQuery(null, MatchAllPQuery.getInstance(), MINIMAL, ValueType.DOCUMENT, true);
    
    private final static XPathQuery MATCH_ALL_NODE = new XPathQuery(null, MatchAllPQuery.getInstance(), MINIMAL, ValueType.NODE, true);

    private final static XPathQuery UNINDEXED = new XPathQuery(null, MatchAllPQuery.getInstance(), 0, ValueType.VALUE, true);

    private final static XPathQuery PATH_MATCH_ALL = new XPathQuery(null, SurroundMatchAll.getInstance(), MINIMAL, ValueType.DOCUMENT, true);
    
    private final static XPathQuery PATH_MATCH_ALL_NODE = new XPathQuery(null, SurroundMatchAll.getInstance(), MINIMAL, ValueType.NODE, true);

    private final static XPathQuery PATH_UNINDEXED = new XPathQuery(null, SurroundMatchAll.getInstance(), 0, ValueType.VALUE, true);
    
    /**
     * @param expr an XPath 2.0 expression
     * @param query a Lucene query
     * @param resultFacts a bitmask with interesting facts about this query
     * @param valueType the type of results returned by the xpath expression, as specifically as 
     * can be determined.
     */
    protected XPathQuery(XQueryExecutable expr, ParseableQuery query, long resultFacts, ValueType valueType, boolean immutable) {
        this.expr = expr;
        this.query = query;
        this.facts = resultFacts;
        setType (valueType);
        this.immutable = immutable;
    }
    
    protected XPathQuery(XQueryExecutable expr, ParseableQuery query, long resultFacts, ValueType valueType) {
        this (expr, query, resultFacts, valueType, false);
    }
    
    /** 
     * @param query the query on which the result is based
     * @param resultFacts the facts to use in the new query
     * @param valueType the result type of the new query
     * @param options the indexer options; controls which type of match-all query may be returned
     * @return a new query (or an immutable query) based on an existing query with some modifications.
     */
    public static XPathQuery getQuery (ParseableQuery query, long resultFacts, ValueType valueType, IndexConfiguration indexConfig) {
        if ((query instanceof MatchAllPQuery && resultFacts == MINIMAL) ||
                query == SurroundMatchAll.getInstance()) {
            if (valueType == ValueType.DOCUMENT) {
                if (indexConfig.isOption(IndexConfiguration.INDEX_PATHS)) {
                    return PATH_MATCH_ALL;
                }
                return MATCH_ALL;
            }
            if (valueType == ValueType.NODE) {
                if (indexConfig.isOption(IndexConfiguration.INDEX_PATHS)) {
                    return PATH_MATCH_ALL_NODE;
                }
                return MATCH_ALL_NODE;
            }
        }
        return new XPathQuery (null, query, resultFacts, valueType);
    }
    
    public static XPathQuery getMatchAllQuery (IndexConfiguration indexConfig) {
        if (indexConfig.isOption(IndexConfiguration.INDEX_PATHS)) {
            return PATH_MATCH_ALL;
        }
        return MATCH_ALL;
    }
    
    public static XPathQuery getUnindexedQuery (IndexConfiguration indexConfig) {
        if (indexConfig.isOption(IndexConfiguration.INDEX_PATHS)) {
            return PATH_UNINDEXED;
        }
        return UNINDEXED;
    }
    
    public static ValueType typeFromFacts (long facts) {
        long typecode = (facts & XPathQuery.RESULT_TYPE_FLAGS); 
        if (typecode == XPathQuery.BOOLEAN_FALSE) {
            return ValueType.BOOLEAN_FALSE;
        } else if (typecode == XPathQuery.BOOLEAN_TRUE) {
            return ValueType.BOOLEAN;
        } else if (typecode == XPathQuery.DOCUMENT_RESULTS) {
            return ValueType.DOCUMENT;
        } 
        return ValueType.VALUE;                
    }
    
    public ParseableQuery getParseableQuery() {
        return query;
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
     * Combines this query with another, while specifying a valueType
     * restriction for the resultant query's results.
     */
    public XPathQuery combineBooleanQueries(Occur occur, XPathQuery precursor, Occur precursorOccur, ValueType type, IndexConfiguration config) {
        long resultFacts = combineQueryFacts (this, precursor);
        ParseableQuery result = combineBoolean (this.query, occur, precursor.query, precursorOccur);
        return getQuery(result, resultFacts, type, config);
    }

    /**
     * Combines this query with another, while specifying a valueType
     * restriction for the resultant query's results, and an allowable
     * distance between the two queries.  Generates Lucene SpanQuerys, and
     * the constituent queries must be span queries as well.
     */
    public XPathQuery combineSpanQueries(XPathQuery precursor, Occur occur, ValueType type, int distance) {
        long resultFacts = combineQueryFacts (this, precursor);
        ParseableQuery result = combineSpans (this.query, occur, precursor.query, distance);
        return new XPathQuery(expr, result, resultFacts, type);
    }

    private static long combineQueryFacts (XPathQuery a, XPathQuery b) {
        if (b.isEmpty()) {
            return a.facts; 
        }
        else if (a.isEmpty()) {
            return b.facts;
        }
        else {
            return combineFacts(a.facts, b.facts);
        }
    }

    private static ParseableQuery combineBoolean (ParseableQuery a, Occur aOccur, ParseableQuery b, Occur bOccur) {
        if (a instanceof MatchAllPQuery) {
            if (bOccur != Occur.MUST_NOT) {
                return b;
            }
        }
        if (b instanceof MatchAllPQuery) {
            if (aOccur != Occur.MUST_NOT) {
                return a;
            }
        }
        return new BooleanPQuery(new BooleanPQuery.Clause(a, aOccur), new BooleanPQuery.Clause(b,  bOccur));
    }
    
    private static ParseableQuery combineSpans (ParseableQuery a, Occur occur, ParseableQuery b, int distance) {

        // distance == 0 means the two queries are adjacent
        if (distance == 0) {
            if (occur != Occur.MUST) {
                throw new IllegalArgumentException ("unsupported boolean combination for span query: " + occur);
            }
            return new SurroundSpanQuery(distance, true, a, b);
        }

        // don't create a span query for //foo; a single term is enough
        // distance < 0 means no distance could be computed
        if (a instanceof SurroundMatchAll && occur != Occur.MUST_NOT && (distance > 90 || distance < 0)) {
            return b;
        }
        if (b instanceof SurroundMatchAll) {
            return a;
        }
        if (distance > 0) {
            if (occur != Occur.MUST) {
                throw new IllegalArgumentException ("unsupported boolean combination for span query: " + occur);
            }
            return new SurroundSpanQuery(distance, true, a, b);
        }
        // distance = -1
        return new SurroundBoolean (occur, a, b);
    }
    
    private static final long combineFacts (long facts2, long facts3) {
        return facts2 & facts3;
    }

    /**
     * Set this query's result type to be the least restrictive type encompassing its type and the given type
     * @param valueType the type to restrict to
     */
    public void restrictType(ValueType type) {
        if (immutable) throw new LuxException ("attempt to modify immutable query");
        valueType = valueType.restrict(type);
    }

    public boolean isEmpty() {
        return this == MATCH_ALL || this == UNINDEXED || this == MATCH_ALL_NODE;
    }
    
    public String toString () {
        return query == null ? "" : query.toString();
    }

  public void setFact(int fact, boolean t) {
      if (immutable) throw new LuxException ("attempt to modify immutable query");
      if (t) {
          facts |= fact;
      } else {
          facts &= (~fact);
      }
  }
  
  public final boolean isFact (int fact) {
      return (facts & fact) == fact;
  }

  public long getFacts() {
      return facts;
  }

  public void setType(ValueType type) {
      if (immutable) throw new LuxException ("attempt to modify immutable query");
      if (type == null) {
          type = ValueType.VALUE;
      }
      valueType = type;
      facts &= (~RESULT_TYPE_FLAGS);
      if (valueType == ValueType.BOOLEAN) {
          facts |= BOOLEAN_TRUE;         
      }
      else if (valueType == ValueType.BOOLEAN_FALSE) {
          facts |= BOOLEAN_FALSE;
      }
      else if (valueType == ValueType.DOCUMENT) {
          facts |= DOCUMENT_RESULTS;
      }
      // no other type info is stored in facts since it's not needed by search()
  }

  public boolean isImmutable() {
      return immutable;
  }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
