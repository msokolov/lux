package lux.lucene;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.TermQuery;

/**
 * Surround parser doesn't support multiple fields in a query?  This term
 * query simply suppresses its field in toString() so its output can be parsed by
 * the surround parser.
 *
 */
public class SurroundTerm extends TermQuery {

    public SurroundTerm(Term t) {
        super(t);
    }

    @Override
    public String toString(String field) {
        return LuxTermQuery.toString(field, getTerm(), getBoost());
    }

}
