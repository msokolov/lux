package lux.compiler;

import lux.exception.LuxException;
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
 *  * TODO: cleanup XPathQuery 
 *  
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

    private final ParseableQuery pquery;
    private ValueType valueType;
    private final boolean immutable;
    
    /** bitmask holding facts proven about the query; generally these facts enable different
     * optimizations.  In the comments, we refer to the "result type" of the query meaning the
     * result type of the xpath expression that the query was generated from.
     */
    private long facts;

    private XPathQuery baseQuery;
    
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
            setFact (IGNORABLE, false); // TODO seems a bit hacky
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
    

    // FIXME: the result type flags should not be stored this way: they are not independent and combinable in a boolean
    // manner; rather they are mutually exclusive
    public static final int RESULT_TYPE_FLAGS = 0x00000018;

    /**
     * A query is singular if its expression returns the same number of results as the lucene query;
     * ie the expression returns a single result for every matching document.
     */
    public static final int SINGULAR=0x00000004;

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
    
    /**
     * If a query a is ignorable, then combine(a,b) = b unless b is also ignorable,
     * in which case combine(a,b) = a|b
     */
    public static final int IGNORABLE=0x00000020;
    
    public final static XPathQuery MATCH_ALL = new XPathQuery(MatchAllPQuery.getInstance(), MINIMAL|SINGULAR, ValueType.DOCUMENT, true);
    
    private final static XPathQuery MATCH_ALL_NODE = new XPathQuery(MatchAllPQuery.getInstance(), MINIMAL, ValueType.NODE, true);

    private final static XPathQuery PATH_MATCH_ALL = new XPathQuery(SpanMatchAll.getInstance(), MINIMAL|SINGULAR, ValueType.DOCUMENT, true);
    
    private final static XPathQuery PATH_MATCH_ALL_NODE = new XPathQuery(SpanMatchAll.getInstance(), MINIMAL, ValueType.NODE, true);

    /**
     * @param expr an XPath 2.0 expression
     * @param query a Lucene query
     * @param resultFacts a bitmask with interesting facts about this query
     * @param valueType the type of results returned by the xpath expression, as specifically as 
     * can be determined.
     */
    protected XPathQuery(ParseableQuery query, long resultFacts, ValueType valueType, boolean immutable) {
        this.pquery = query;
        this.facts = resultFacts;
        setType (valueType);
        this.immutable = immutable;
    }
    
    protected XPathQuery(ParseableQuery query, long resultFacts, ValueType valueType) {
        this (query, resultFacts, valueType, false);
    }
    
    /** 
     * @param query the query on which the result is based
     * @param resultFacts the facts to use in the new query
     * @param valueType the result type of the new query
     * @param indexConfig the indexer configuration; controls which type of match-all query may be returned
     * @param sortFields the sort fields to record in the query
     * @return a new query (or an immutable query) based on an existing query with some modifications.
     */
    public static XPathQuery getQuery (ParseableQuery query, long resultFacts, ValueType valueType, IndexConfiguration indexConfig, SortField[] sortFields) {
        if (sortFields == null &&
                ( (query instanceof MatchAllPQuery && resultFacts == MINIMAL && sortFields == null) ||
                  ( query == SpanMatchAll.getInstance())) ) 
        {
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
        XPathQuery q = new XPathQuery (query, resultFacts, valueType);
        q.setSortFields(sortFields);
        return q;
    }
    
    public static XPathQuery getMatchAllQuery (IndexConfiguration indexConfig) {
        if (indexConfig.isOption(IndexConfiguration.INDEX_PATHS)) {
            return PATH_MATCH_ALL;
        }
        return MATCH_ALL;
    }
    
    public static ValueType typeFromFacts (long facts) {
        long typecode = (facts & XPathQuery.RESULT_TYPE_FLAGS); 
        if (typecode == XPathQuery.BOOLEAN_FALSE) {
            return ValueType.BOOLEAN_FALSE;
        } else if (typecode == XPathQuery.BOOLEAN_TRUE) {
            return ValueType.BOOLEAN;
        } else if (typecode == XPathQuery.DOCUMENT_RESULTS) {
            return ValueType.DOCUMENT;
        } else if (typecode == XPathQuery.SINGULAR) {
            return ValueType.INT;
        }
        return ValueType.VALUE;                
    }
    
    public ParseableQuery getParseableQuery() {
        return pquery;
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
        ParseableQuery result;
        if (isFact(IGNORABLE)) {
            if (precursor.isFact(IGNORABLE)) {
                precursorOccur = occur = Occur.SHOULD;
            }
            return precursor.setFact(MINIMAL, false);
        }
        else if (precursor.isFact(IGNORABLE)) {
            return setFact (MINIMAL, false);
        }
        long resultFacts = combineQueryFacts (this, precursor);
        result = combineBoolean (this.pquery, occur, precursor.pquery, precursorOccur);
        SortField[] combined = combineSortFields(precursor);
        return getQuery(result, resultFacts, type, config, combined);
    }

    private SortField[] combineSortFields(XPathQuery precursor) {
        if (sortFields != null) {
            if (precursor.sortFields != null) {
                SortField[] combined = new SortField [sortFields.length + precursor.sortFields.length];
                System.arraycopy(sortFields, 0, combined, 0, sortFields.length);
                System.arraycopy(precursor.sortFields, 0, combined, sortFields.length, precursor.sortFields.length);
                return combined;
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
     * @param type the return type of the combined query
     * @param distance the distance between the queries
     * @param config the index configuration
     * @return the combined query
     */
    public XPathQuery combineSpanQueries(XPathQuery precursor, Occur occur, ValueType type, int distance, IndexConfiguration config) {
        if (isFact(IGNORABLE)) {
            return precursor.setFact (MINIMAL, false);
        }
        if (precursor.isFact(IGNORABLE)) {
            return setFact(MINIMAL, false);
        }
        long resultFacts = combineQueryFacts (this, precursor);
        ParseableQuery result = combineSpans (this.pquery, occur, precursor.pquery, distance);
        SortField[] combinedSorts = combineSortFields(precursor);
        XPathQuery q = new XPathQuery(result, resultFacts, type);
        q.setSortFields(combinedSorts);
        if (baseQuery != null) {
            if (precursor.getBaseQuery() != null) {
                q.setBaseQuery(baseQuery.combineBooleanQueries(occur, precursor.getBaseQuery(), occur, baseQuery.getResultType(), config));
            } else {
                q.setBaseQuery(baseQuery);
            }
        } else if (precursor.getBaseQuery() != null) {
            q.setBaseQuery(precursor.getBaseQuery());
        }
        return q;
    }

    private static long combineQueryFacts (XPathQuery a, XPathQuery b) {
        // TODO: get rid of these special cases, and the immutable queries
        // the logic for maintaining them is tortured and the purported benefit is dubious
        if (b.isEmpty()) {
            return a.facts; 
        }
        else if (a.isEmpty()) {
            return b.facts;
        }
        else {
            return combineFacts(a.facts, b.facts) ;
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
        if (a == null || a.equals(b)) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return new BooleanPQuery(new BooleanPQuery.Clause(a, aOccur), new BooleanPQuery.Clause(b,  bOccur));
    }
    
    private static ParseableQuery combineSpans (ParseableQuery a, Occur occur, ParseableQuery b, int distance) {

        // distance == 0 means the two queries are adjacent
        if (distance == 0) {
            return combineFiniteSpan(a, occur, b, distance);
        }

        // don't create a span query for //foo; a single term is enough
        // distance < 0 means no distance could be computed
        if (a instanceof SpanMatchAll && occur != Occur.MUST_NOT && (distance > 90 || distance < 0)) {
            if (occur == Occur.SHOULD) {
                return a;
            }
            return b;
        }
        if (b instanceof SpanMatchAll) {
            if (occur == Occur.SHOULD) {
                return b;
            }
            return a;
        }
        if (distance > 0) {
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
        if (a instanceof SpanBooleanPQuery && ((SpanBooleanPQuery) a).getOccur() == Occur.MUST) {
            return combineBooleanWithSpan(a, b, distance);
        } else if (b instanceof SpanBooleanPQuery && ((SpanBooleanPQuery) b).getOccur() == Occur.MUST) {
            return combineBooleanWithSpan(b, a, distance);
        }
        return new SpanNearPQuery(distance, true, a, b);
    }

    private static ParseableQuery combineBooleanWithSpan(ParseableQuery a, ParseableQuery b, int distance) {
        // ((A NEAR B) AND C) NEAR D => ((A NEAR B) AND (C NEAR D))
        SpanBooleanPQuery abool = (SpanBooleanPQuery) a;
        Clause[] clauses = new Clause [abool.getClauses().length];
        System.arraycopy(abool.getClauses(), 0, clauses, 0, clauses.length);
        int i = clauses.length-1;
        clauses[i] = new Clause (new SpanNearPQuery (distance, true, clauses[i].getQuery(), b), clauses[i].getOccur());
        return new SpanBooleanPQuery (clauses);
    }
    
    private static final long combineFacts (long facts2, long facts3) {
        return facts2 & facts3;
    }

    /**
     * Set this query's result type to be the least restrictive type encompassing its type and the given type
     * @param type the type to restrict to
     */
    public void restrictType(ValueType type) {
        if (immutable) throw new LuxException ("attempt to modify immutable query");
        valueType = valueType.restrict(type);
    }

    public boolean isEmpty() {
        return this == MATCH_ALL || this == MATCH_ALL_NODE || this == PATH_MATCH_ALL;
    }
    
    @Override
    public String toString () {
        return pquery == null ? "" : pquery.toString();
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

  public void setType(ValueType type) {
      if (immutable) throw new LuxException ("attempt to modify immutable query");
      if (type == null) {
          type = ValueType.VALUE;
      }
      // FIXME: kill kill kill! this is so confusing and wrong - we need to extract the return type info, facts, etc this bitmask is so painful
      valueType = type;
      facts &= (~RESULT_TYPE_FLAGS);
      if (valueType == ValueType.BOOLEAN) {
          facts |= BOOLEAN_TRUE;
          facts &= ~SINGULAR;
      }
      else if (valueType == ValueType.BOOLEAN_FALSE) {
          facts |= BOOLEAN_FALSE;
          facts &= ~SINGULAR;
      }
      else if (valueType == ValueType.DOCUMENT) {
          facts |= DOCUMENT_RESULTS;
          facts |= SINGULAR;
      }
      // no other type info is stored in facts since it's not needed by search()
  }

  public boolean isImmutable() {
      return immutable;
  }

  public AbstractExpression toXmlNode(String defaultField) {
      return getParseableQuery().toXmlNode(defaultField);
  }

  public String toQueryString(String defaultField, IndexConfiguration indexConfig) {
      return getParseableQuery().toQueryString(defaultField, indexConfig);
  }
  
  /**
   * A query generated by a predicate expression.  Predicates store their base query,
   * rather than their predicated filter query, as the base for path combinations, and
   * also set the filter query to add in as an additional filter.
   * @return the filter query.
   */
  public XPathQuery getBaseQuery() {
      return baseQuery;
  }

  public void setBaseQuery(XPathQuery baseQuery) {
      this.baseQuery = baseQuery;
  }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
