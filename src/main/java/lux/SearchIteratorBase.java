package lux;

import lux.exception.LuxException;
import lux.solr.MissingStringLastComparatorSource;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.SequenceIterator;

import org.apache.lucene.search.Query;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;

public abstract class SearchIteratorBase implements SequenceIterator<NodeInfo> {

    protected final Evaluator eval;
    protected final Query query;
    protected final QueryStats stats;
    protected final String sortCriteria;
    protected final int start;
    protected NodeInfo current = null;
    protected int position = 0;

    public static final MissingStringLastComparatorSource MISSING_LAST = new MissingStringLastComparatorSource();

    public SearchIteratorBase (Evaluator eval, Query query, String sortCriteria, int start) {
        this.eval = eval;
        this.query = query;
        this.stats = eval.getQueryStats();
        if (stats != null) {
            stats.query = query.toString();
        }
        this.sortCriteria = sortCriteria;
        this.start = start;
    }

    protected Sort makeSortFromCriteria() {
        String[] fields = sortCriteria.split("\\s*,\\s*");
        SortField[] sortFields = new SortField [fields.length];
        for (int i = 0; i < fields.length; i++) {
            SortField.Type type = SortField.Type.STRING;
            String [] tokens = fields[i].split("\\s+");
            String field = tokens[0];
            Boolean reverse = null;
            Boolean emptyGreatest = null;
            for (int j = 1; j < tokens.length; j++) {
                if (tokens[j].equals("descending")) {
                    reverse = setBooleanOnce (reverse, true, sortCriteria);
                } else if (tokens[j].equals("ascending")) {
                    reverse = setBooleanOnce (reverse, false, sortCriteria);
                } else if (tokens[j].equals("empty")) {
                    if (j == tokens.length-1) {
                        throw new LuxException ("missing keyword after 'empty' in: " + sortCriteria);
                    }
                    j = j + 1;
                    if (tokens[j].equals("least")) {
                        emptyGreatest = setBooleanOnce(emptyGreatest, false, sortCriteria);
                    } 
                    else if (tokens[j].equals("greatest")) {
                        emptyGreatest = setBooleanOnce(emptyGreatest, true, sortCriteria);
                    }
                    else {
                        throw new LuxException ("missing or invalid keyword after 'empty' in: " + sortCriteria);
                    }
                } else if (tokens[j].equals("int")) {
                    type = SortField.Type.INT;
                } else if (tokens[j].equals("long")) {
                    type = SortField.Type.LONG;
                } else if (tokens[j].equals("string")) {
                    type = SortField.Type.STRING;
                } else {
                    throw new LuxException ("invalid keyword '" + tokens[j] + "' in: " + sortCriteria);
                }
            }
            if (field.equals("lux:score")) {
                if (reverse == Boolean.FALSE) {
                    throw new LuxException ("not countenanced: attempt to sort by irrelevance");
                }
                sortFields[i] = SortField.FIELD_SCORE;
            } 
            else if (emptyGreatest == Boolean.TRUE) {
                if (type == SortField.Type.STRING) {
                    sortFields[i] = new SortField(field, MISSING_LAST, reverse == Boolean.TRUE);
                } else {
                    sortFields[i] = new SortField(field, type, reverse == Boolean.TRUE);
                    switch (type) {
                    case INT:
                        sortFields[i].setMissingValue(reverse == Boolean.TRUE ? 0 : Integer.MAX_VALUE);
                        break;
                    case LONG:
                        sortFields[i].setMissingValue(reverse == Boolean.TRUE ? 0 : Long.MAX_VALUE);
                        break;
                    default:
                        throw new LuxException ("unsupported combination of empty greatest and sort field type: " + type);
                    }
                }
            } else {
                sortFields[i] = new SortField(field, type, reverse == Boolean.TRUE);
            }
        }
        return new Sort(sortFields);
    }

    final Boolean setBooleanOnce (Boolean current, boolean value, String sortCriteria) {
        if (current != null) {
            throw new LuxException ("too many ordering keywords in: " + sortCriteria);
        }
        return value;
    }
    
    /**
     * @return the current result.  This is the last result returned by next(), and will be null if there
     * are no more results.
     */
    @Override
    public NodeInfo current() {
        return current;
    }

    /**
     * @return the (0-based) index of the next result: this will be 0 before any calls to next(), and -1 after the last
     * result has been retrieved.
     */
    @Override
    public int position() {
        return position;
    }

    /**
     * does nothing
     */
    @Override
    public void close() {
        // Saxon doesn't call this reliably
    }

    /**
     *  This iterator has no special properties
     * @return 0
     */
    @Override
    public int getProperties() {
        return 0;
    }

}