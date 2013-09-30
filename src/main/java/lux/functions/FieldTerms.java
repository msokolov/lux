package lux.functions;

import java.io.IOException;

import lux.Evaluator;
import lux.index.IndexConfiguration;
import lux.index.field.XmlTextField;
import lux.solr.CloudQueryRequest;
import lux.solr.SolrQueryContext;
import lux.solr.XQueryComponent;
import lux.xpath.FunCall;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.lib.ExtensionFunctionCall;
import net.sf.saxon.lib.ExtensionFunctionDefinition;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.LazySequence;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.EmptySequence;
import net.sf.saxon.value.SequenceType;
import net.sf.saxon.value.StringValue;

import org.apache.commons.lang.StringUtils;
import org.apache.lucene.index.Fields;
import org.apache.lucene.index.MultiFields;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.util.BytesRef;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.params.ShardParams;
import org.apache.solr.common.params.TermsParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.request.SolrRequestHandler;
import org.apache.solr.response.SolrQueryResponse;
import org.slf4j.LoggerFactory;

/**
 * <code>function lux:field-terms($field-name as xs:string?, $start as xs:string?) as xs:anyAtomicItem*</code>
 * <p>
 * This function accepts the name of a Lucene field, and a starting value, and
 * returns the sequence of terms drawn from the field, ordered according to its
 * natural order, starting with the first term that is >= the starting value.
 * </p>
 * <p>
 * If the $field-name argument is empty, the terms are drawn from the default
 * field defined by the {@link IndexConfiguration}, generally the
 * {@link XmlTextField}.
 * </p>
 */
public class FieldTerms extends ExtensionFunctionDefinition {

    @Override
    public StructuredQName getFunctionQName() {
        return new StructuredQName("lux", FunCall.LUX_NAMESPACE, "field-terms");
    }

    @Override
    public SequenceType[] getArgumentTypes() {
        return new SequenceType[] { SequenceType.OPTIONAL_STRING, SequenceType.OPTIONAL_STRING };
    }

    @Override
    public int getMinimumNumberOfArguments() {
        return 0;
    }

    @Override
    public int getMaximumNumberOfArguments() {
        return 2;
    }

    @Override
    public boolean trustResultType() {
        return true;
    }

    @Override
    public SequenceType getResultType(SequenceType[] suppliedArgumentTypes) {
        return SequenceType.ATOMIC_SEQUENCE;
    }

    @Override
    public ExtensionFunctionCall makeCallExpression() {
        return new FieldTermsCall();
    }

    class FieldTermsCall extends ExtensionFunctionCall {

        @Override
        public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
            String fieldName = null, start = "";
            if (arguments.length > 0) {
                Item arg0 = arguments[0].head();
                if (arg0 != null) {
                	fieldName = arg0.getStringValue();
                }
                if (arguments.length > 1) {
                    Item arg1 = arguments[1].head();
                    start = arg1 == null ? "" : arg1.getStringValue();
                }
            }
            Evaluator eval = SearchBase.getEvaluator(context);
            try {
                if (fieldName == null) {
                    fieldName = eval.getCompiler().getIndexConfiguration().getDefaultFieldName();
                    if (fieldName == null) {
                        return EmptySequence.getInstance();
                    }
                }
                Term term = new Term(fieldName, start);
                if (eval.getQueryContext() instanceof SolrQueryContext) {
                    XQueryComponent xqueryComponent = ((SolrQueryContext) eval.getQueryContext()).getQueryComponent();
                    if (xqueryComponent.getCurrentShards() != null) {
                        return new LazySequence (new SolrTermsIterator(eval, term));
                    }
                }
                return new LazySequence(new TermsIterator(eval, term));
            } catch (IOException e) {
                throw new XPathException("failed getting terms from field " + fieldName, e);
            }
        }

    }

    /**
     * Retrieves terms from the index using Solr's TermsComponent.  Currently used only for cloud requests,
     * but in the future we may want to use it to get expose Solr's Terms functionality, which is richer 
     * than the basic TermsEnum API in Lucene.  Be aware thoughthat this iterator retrieves terms 
     * via Solr's HTTP API.
     */
    class SolrTermsIterator implements SequenceIterator<AtomicValue> {
        private final Evaluator eval;
        private Term term;  // the requested field and starting position (inclusive)
        private int offset; // the starting position of the current batch
        private int pos;    // the absolute position from the start of the entire iteration
        private String current; // the last value returned
        private XQueryComponent xqueryComponent;
        private SolrQueryResponse response;
        
        SolrTermsIterator(Evaluator eval, Term term) {
            this.term = term;
            this.eval = eval;
            pos = -1;
            offset = 0;
            xqueryComponent = ((SolrQueryContext)eval.getQueryContext()).getQueryComponent();
        }

        @Override
        public AtomicValue next() throws XPathException {
            for (;;) {
                if (response == null) {
                    getMoreTerms ();
                }
                NamedList<?> termFields = (NamedList<?>) response.getValues().get("terms");
                NamedList<?> terms = (NamedList<?>) termFields.get(term.field());
                if (terms.size() == 0) {
                    return null;
                }
                int idx = pos - offset;
                if (idx >= terms.size()) {
                    response = null;
                } else {
                    current = terms.getName(idx);
                    // Integer fieldTermCount = (Integer) terms.getVal(pos);
                    pos += 1;
                    return new StringValue(current);
                }
            }
        }

        private void getMoreTerms() {
            SolrRequestHandler termsHandler = xqueryComponent.getCore().getRequestHandler("/terms");
            if (termsHandler == null) {
                LoggerFactory.getLogger(getClass()).error("No /terms handler configured; lux:field-terms giving up");
                return;
            }
            ModifiableSolrParams params = new ModifiableSolrParams();
            params.add(TermsParams.TERMS_FIELD, term.field());
            if (current != null) {
                params.add(TermsParams.TERMS_LOWER, current);
                params.add(TermsParams.TERMS_LOWER_INCLUSIVE, "false");
                offset = pos;
            } else {
                pos = 0;
                params.add(TermsParams.TERMS_LOWER, term.text());
            }
            params.add(TermsParams.TERMS_SORT, TermsParams.TERMS_SORT_INDEX);
            params.add(TermsParams.TERMS_LIMIT, Integer.toString(100));
            params.add("distrib", "true");
            xqueryComponent.getCurrentShards();
            params.add(ShardParams.SHARDS, StringUtils.join(xqueryComponent.getCurrentShards(), ","));
            params.add(ShardParams.SHARDS_QT, "/terms"); // this gets passed to the shards to tell them what the request is
            SolrQueryRequest req = new CloudQueryRequest(xqueryComponent.getCore(), params, null);
            response = new SolrQueryResponse();
            termsHandler.handleRequest(req, response);
        }

        @Override
        public AtomicValue current() {
            return new StringValue(current);
        }

        @Override
        public int position() {
            return pos;
        }

        @Override
        public void close() {

        }

        @Override
        public SequenceIterator<AtomicValue> getAnother() throws XPathException {
            return new SolrTermsIterator(eval, term);
        }

        @Override
        public int getProperties() {
            return 0;
        }

    }

    /**
     * Retrieves terms from the Lucene index directly, using TermsEnum.
     */
    class TermsIterator implements SequenceIterator<AtomicValue> {
        private TermsEnum terms;
        private final Evaluator eval;
        private Term term;
        private int pos;
        private String current;
        private String next;

        TermsIterator(Evaluator eval, Term term) throws IOException {
            this.term = term;
            this.eval = eval;
            pos = 0;
            createTermsEnum(term);
        }

        private void createTermsEnum(Term t) throws IOException {
            String fieldName = t.field();
            // TODO: get atomic sub readers and iterate values from those 
            /*  From: http://lucene.apache.org/core/4_0_0-BETA/MIGRATE.html

                Note that the MultiFields approach entails a performance
                hit on MultiReaders, as it must merge terms/docs/positions
                on the fly. It's generally better to instead get the
                sequential readers (use oal.util.ReaderUtil) and then step
                through those readers yourself, if you can (this is how
                Lucene drives searches).
            */
            Fields fields = MultiFields.getFields(eval.getSearcher().getIndexReader());
            if (fields != null) {
                Terms fieldTerms = fields.terms(fieldName);
                if (fieldTerms != null) {
                    terms = fieldTerms.iterator(null);
                    if (t != null) {
                        if (terms.seekCeil(new BytesRef(t.text().getBytes("utf-8"))) != TermsEnum.SeekStatus.END) {
                            next = terms.term().utf8ToString();
                        }
                    }
                }
            }
        }

        @Override
        public AtomicValue next() throws XPathException {
            try {
                if (next == null) {
                    pos = -1;
                    return null;
                }
                ++pos;
                current = next;
                BytesRef bytesRef = terms.next();
                if (bytesRef == null) {
                    next = null;
                } else {
                    next = bytesRef.utf8ToString();
                }
                return new net.sf.saxon.value.StringValue(current);
            } catch (IOException e) {
                throw new XPathException(e);
            }
        }

        @Override
        public AtomicValue current() {
            return new net.sf.saxon.value.StringValue(current);
        }

        @Override
        public int position() {
            return pos;
        }

        @Override
        public void close() {
        }

        @Override
        public SequenceIterator<AtomicValue> getAnother() throws XPathException {
            try {
                return new TermsIterator(eval, term);
            } catch (IOException e) {
                throw new XPathException(e);
            }
        }

        @Override
        public int getProperties() {
            return 0;
        }

    }

}

/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/.
 */
