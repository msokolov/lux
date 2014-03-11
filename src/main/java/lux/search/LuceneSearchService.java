package lux.search;

import java.io.IOException;

import lux.Evaluator;
import lux.SearchResultIterator;
import lux.index.field.FieldDefinition;
import lux.query.parser.LuxSearchQueryParser;
import net.sf.saxon.om.AtomicArray;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.LazySequence;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.EmptySequence;
import net.sf.saxon.value.Int64Value;
import net.sf.saxon.value.StringValue;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Scorer;

// TODO: possibly we should consolidate LuxSearcher and DocWriter methods here?
public class LuceneSearchService implements SearchService {
    
    private final LuxSearchQueryParser parser;
    private Evaluator eval;
    
    public LuceneSearchService (LuxSearchQueryParser parser) {
        this.parser = parser;
    }

    @Override
    public Sequence search(Item queryArg, String[] sortCriteria, int start)
            throws XPathException {
        Query query = parser.parse(queryArg, eval);
        SequenceIterator<?> iterator;
        try {
            iterator = new SearchResultIterator(eval, query, sortCriteria, start);
        } catch (Exception e) {
            throw new XPathException (e);
        }
        return new LazySequence(iterator);
    }

    @Override
    public long count(Item queryArg) throws XPathException {
        int count = 0;
        Query query = parser.parse(queryArg, eval);
        try {
            DocIdSetIterator counter = eval.getSearcher().search(query);
            while (counter.nextDoc() != Scorer.NO_MORE_DOCS) {
                ++count;
            }
        } catch (IOException e) {
            throw new XPathException (e);
        }
        return count;
    }

    @Override
    public boolean exists(Item queryArg) throws XPathException {
        Query query = parser.parse(queryArg, eval);
        try {
            DocIdSetIterator iter = eval.getSearcher().search(query);
            return (iter.nextDoc() != Scorer.NO_MORE_DOCS);
        } catch (IOException e) {
            throw new XPathException (e);
        }        
    }

    @Override
    public Sequence key(FieldDefinition field, NodeInfo node) throws XPathException {
        if (node == null) {
            return EmptySequence.getInstance();
        }
        Document doc = (Document) node.getDocumentRoot().getUserData(Document.class.getName());
        assert doc != null;
        return getFieldValue (doc, field);
    }
    
    private Sequence getFieldValue (Document doc, FieldDefinition field) throws XPathException {
        String fieldName = field.getName(); // TODO: is this right?  we used to pass in the fieldName
        if (field == null || field.getType() == FieldDefinition.Type.STRING || field.getType() == FieldDefinition.Type.TEXT) {
            Object[] values = doc.getValues(fieldName);
            StringValue[] valueItems = new StringValue[values.length];
            for (int i = 0; i < values.length; i++) {
                valueItems[i] = new StringValue (values[i].toString());
            }
            return new AtomicArray(valueItems);
        }
        if (field.getType() == FieldDefinition.Type.INT || field.getType() == FieldDefinition.Type.LONG) {
            IndexableField [] fieldValues = doc.getFields(fieldName);
            Int64Value[] valueItems = new Int64Value[fieldValues.length];
            for (int i = 0; i < fieldValues.length; i++) {
                valueItems[i] = Int64Value.makeIntegerValue(fieldValues[i].numericValue().longValue());
            }
            return new AtomicArray(valueItems);
        }
        return EmptySequence.getInstance();
    }
    
    @Override
    public Sequence terms(String fieldName, String startValue) throws XPathException {
        try {
            if (fieldName == null) {
                fieldName = eval.getCompiler().getIndexConfiguration().getDefaultFieldName();
                if (fieldName == null) {
                    return EmptySequence.getInstance();
                }
            }
            Term term = new Term(fieldName, startValue);
            return new LazySequence(new TermsIterator(eval, term));
        } catch (IOException e) {
            throw new XPathException("failed getting terms from field " + fieldName, e);
        }
    }

    @Override
    public void commit() throws XPathException {
        // callers could just use the doc writer?
        eval.getDocWriter().commit(eval);
    }

    public LuxSearchQueryParser getParser() {
        return parser;
    }

    public Evaluator getEvaluator() {
        return eval;
    }

    /**
     * Must be set before any service operations are called.
     * @param eval
     */
    public void setEvaluator(Evaluator eval) {
        this.eval = eval;
    }

}

/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/.
 */
