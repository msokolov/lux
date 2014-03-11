package lux.search;

import lux.Evaluator;
import lux.index.field.FieldDefinition;
import lux.query.parser.LuxSearchQueryParser;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;

/**
 * SearchService abstracts access to the underlying search engine implementations in terms of objects in the Saxon API.
 * It is used by the XPath functions in the Lux library.
 */
public interface SearchService {
    
    Sequence search (Item queryArg, String [] sortCriteria, int start) throws XPathException;
    
    long count (Item queryArg) throws XPathException;

    boolean exists(Item queryArg) throws XPathException;

    Sequence key(FieldDefinition field, NodeInfo node) throws XPathException;
    
    Sequence terms (String fieldName, String startValue) throws XPathException;
    
    void commit() throws XPathException;

    Evaluator getEvaluator();

    LuxSearchQueryParser getParser();

}
