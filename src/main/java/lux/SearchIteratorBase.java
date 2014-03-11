package lux;

import lux.exception.LuxException;
import lux.index.FieldRole;
import lux.search.MissingStringLastComparatorSource;
//import lux.solr.MissingStringLastComparatorSource;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.SequenceIterator;

import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;

public abstract class SearchIteratorBase implements SequenceIterator<NodeInfo> {

    protected final Evaluator eval;
    protected final QueryStats stats;
    protected final String [] sortCriteria;
    protected final int start;
    protected NodeInfo current = null;
    protected int position = 0;

    public static final MissingStringLastComparatorSource MISSING_LAST = new MissingStringLastComparatorSource();

    public SearchIteratorBase (Evaluator eval, String[] sortCriteria, int start1) {
        this.eval = eval;
        this.stats = eval.getQueryStats();
        this.sortCriteria = sortCriteria;
        this.start = start1 - 1;
    }

    protected Sort makeSortFromCriteria(String [] criteria) {
        SortField[] sortFields = new SortField [criteria.length];
        for (int i = 0; i < criteria.length; i++) {
            SortField.Type type = SortField.Type.STRING;
            String [] tokens = criteria[i].split("\\s+");
            String field = tokens[0];
            Boolean reverse = null;
            Boolean emptyGreatest = null;
            for (int j = 1; j < tokens.length; j++) {
                if (tokens[j].equals("descending")) {
                    reverse = setBooleanOnce (reverse, true, criteria[i]);
                } else if (tokens[j].equals("ascending")) {
                    reverse = setBooleanOnce (reverse, false, criteria[i]);
                } else if (tokens[j].equals("empty")) {
                    if (j == tokens.length-1) {
                        throw new LuxException ("missing keyword after 'empty' in: " + criteria[i]);
                    }
                    j = j + 1;
                    if (tokens[j].equals("least")) {
                        emptyGreatest = setBooleanOnce(emptyGreatest, false, criteria[i]);
                    } 
                    else if (tokens[j].equals("greatest")) {
                        emptyGreatest = setBooleanOnce(emptyGreatest, true, criteria[i]);
                    }
                    else {
                        throw new LuxException ("missing or invalid keyword after 'empty' in: " + criteria[i]);
                    }
                } else if (tokens[j].equals("int")) {
                    type = SortField.Type.INT;
                } else if (tokens[j].equals("long")) {
                    type = SortField.Type.LONG;
                } else if (tokens[j].equals("string")) {
                    type = SortField.Type.STRING;
                } else {
                    throw new LuxException ("invalid keyword '" + tokens[j] + "' in: " + criteria[i]);
                }
            }
            if (field.equals(FieldRole.LUX_SCORE)) {
                if (reverse == Boolean.FALSE) {
                    throw new LuxException ("not countenanced: attempt to sort by irrelevance");
                }
                sortFields[i] = SortField.FIELD_SCORE;
            }
            else if (field.equals(FieldRole.LUX_DOCID)) {
                if (reverse == Boolean.FALSE) {
                    throw new LuxException ("not countenanced: attempt to sort by descending docid");
                }
                if (criteria.length == 1) {
                    return null;
                }
                sortFields[i] = SortField.FIELD_DOC;
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
            throw new LuxException ("invalid ordering keyword in: " + sortCriteria);
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
