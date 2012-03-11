package lux;

import java.io.IOException;
import java.util.Set;

import org.jaxen.XPath;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Searcher;
import org.apache.lucene.search.Weight;

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
@SuppressWarnings("deprecation")
public class XPathQuery extends Query {

    private final Query query;
    private XPath xpath;
    private final boolean isMinimal;
    private final ValueType valueType;

    enum ValueType {
        VALUE(false, false), DOCUMENT(true, false), NODE(true, false), ELEMENT(true, false), ATTRIBUTE(true, false), 
            TEXT(true, false), ATOMIC(false, true), STRING(false, true), INT(false, true), NUMBER(false, true);

        public final boolean isNode;
        public final boolean isAtomic;

        ValueType(boolean isNode, boolean isAtomic) {
            this.isNode = isNode;
            this.isAtomic = isAtomic;
        }
    }

    public XPathQuery(Query query, boolean isMinimal, ValueType valueType) {
        this.query = query;
        this.isMinimal = isMinimal;
        this.valueType = valueType;
    }
    
    public Query getQuery() {
        return query;
    }
    
    public XPath getXPath() {
        return xpath;
    }
    
    public void setXPath (XPath xpath) {
        this.xpath = xpath;
    }

    /**
     * @return whether it is known that the query will return the minimal set of
     *         documents containing the required result value. If false, some
     *         documents may be returned that will eventually need to be
     *         discarded if they don't match the xpath.
     */
    public boolean isMinimal() {
        return isMinimal;
    }

    public ValueType getValueType() {
        return valueType;
    }

    public final static XPathQuery EMPTY = new XPathQuery(new MatchAllDocsQuery(), true, ValueType.DOCUMENT);

    public final static XPathQuery UNINDEXED = new XPathQuery(new MatchAllDocsQuery(), false, ValueType.VALUE);

    /**
     * Combines this query with another according to the logic of occur. The
     * valueType of the resulting query will be the same as that of this query
     * if occur is AND (or NOT). If occur is OR, the valueType is the most
     * specific type that includes the types of each query. The combined query
     * is minimal iff both queries are.
     * 
     * @param precursor
     *            the query to combine with this; precursor since it corresponds
     *            to the preceding expr's query.
     * @param occur
     *            whether the two queries MUST occur (this AND precursor) or MAY
     *            occur (this OR precursor).
     * @return the combined query
     */
    public XPathQuery combine(XPathQuery precursor, Occur occur) {
        ValueType combinedType = occur == Occur.SHOULD ? promoteType(this.valueType, precursor.valueType) : this.valueType;
        return combine(precursor, occur, combinedType);
    }

    /**
     * Combines this query with another, while specifying a valueType for the
     * resultant query.
     */
    public XPathQuery combine(XPathQuery precursor, Occur occur, ValueType valueType) {
        if (precursor.isEmpty())
            return typedQuery (this, valueType);
        if (this.isEmpty())
            return typedQuery (precursor, valueType);
        Query q;
        if (precursor.query instanceof MatchAllDocsQuery) {
            q = this.query;
        } else if (this.query instanceof MatchAllDocsQuery) {
            q = precursor.query;
        } else {
            BooleanQuery bq = new BooleanQuery();
            bq.add(new BooleanClause(precursor.query, occur));
            bq.add(new BooleanClause(this.query, occur));
            q = bq;
        }
        // Union implies possible type promotion since two sequences of
        // different types could be combined
        return new XPathQuery(q, isMinimal && precursor.isMinimal, valueType);
    }

    // possibly re-type the query
    private XPathQuery typedQuery(XPathQuery query, ValueType valueType) {
        if (query.getValueType() == valueType) 
            return query;
        return new XPathQuery (query.query, query.isMinimal, valueType);
    }

    public boolean isEmpty() {
        return this == EMPTY;
    }

    /**
     * @return the most specific type that includes both atype and btype.
     * @param atype
     *            a type
     * @param btype
     *            another type
     */
    private ValueType promoteType(ValueType atype, ValueType btype) {
        if (atype == btype)
            return atype;
        if (atype.isNode && btype.isNode)
            return ValueType.NODE;
        if (atype.isAtomic && btype.isAtomic)
            return ValueType.ATOMIC;
        return ValueType.VALUE;
    }
    
    public String toString () {
        return query.toString();
    }

    /* 
     * Wrapped methods from org.apache.lucene.search.Query
     */

    @Override
    public String toString(String field) {
       return query.toString(field);
    }

    public Weight createWeight(Searcher searcher) throws IOException {
        return query.createWeight (searcher);
    }

    public Query rewrite(IndexReader reader) throws IOException {
        return query.rewrite (reader);
    }

    public Query combine(Query[] queries) {
        return query.combine (queries);
    }

    public void extractTerms(Set<Term> terms) {
        query.extractTerms (terms);
    }

    /** Returns a hash code value for this object.*/
    @Override public int hashCode() {
        return query.hashCode();
    }

  /** Returns true iff <code>o</code> is equal to this. */
  @Override
  public boolean equals(Object o) {
    if (!(o instanceof XPathQuery))
      return false;
    XPathQuery other = (XPathQuery)o;
    return (xpath == null ? (other.xpath == null) : xpath.equals (other.xpath)) &&
            super.equals (o);
  }

}