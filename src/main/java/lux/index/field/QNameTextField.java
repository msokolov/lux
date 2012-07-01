package lux.index.field;

import java.util.Collections;

import lux.index.QNameTextMapper;
import lux.index.XmlIndexer;
import lux.query.ParseableQuery;
import lux.query.QNameTextQuery;
import lux.xpath.QName;
import net.sf.saxon.s9api.XdmNode;

import org.apache.lucene.document.Field.Index;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.Field.TermVector;
import org.apache.lucene.document.Fieldable;
import org.apache.lucene.index.Term;

public class QNameTextField extends XmlField {
    
    private static final QNameTextField instance = new QNameTextField();
    
    /**
     * @see QNameTextMapper
     */
    //public static final String RECORD_END = " luxeor";
    //public static final String RECORD_START = "luxsor ";
    
    public static QNameTextField getInstance() {
        return instance;
    }
    
    protected QNameTextField () {
        // TODO - better default analyzer w/stemming + diacritic normalization
        // TODO - enable caller to supply analyzer (extending our analyzer so we can ensure that
        // element/attribute text tokens are generated)
        // TODO - provide QName lists as attributes so that analysis doesn't see them embedded in
        // the token text
        super ("lux_node", new QNameAnalyzer(), Store.NO, Type.TOKENS, NameKind.STATIC);
    }
    
    @Override
    public Iterable<Fieldable> getFieldValues(XmlIndexer indexer) {
        XdmNode doc = indexer.getXdmNode();
        return new FieldValues (this, Collections.singleton(
                        new TokenizedField(getName(), new QNameTextTokenStream (doc), 
                        Store.NO, Index.ANALYZED, TermVector.NO)));
    }

    public ParseableQuery makeTextQuery (String value) {
        return new QNameTextQuery(new Term(getName(), value));
    }
    
    public ParseableQuery makeElementValueQuery (QName qname, String value) {
        return new QNameTextQuery(new Term(getName(), value), qname.getEncodedName());
    }

    public ParseableQuery makeAttributeValueQuery (QName qname, String value) {
        return new QNameTextQuery(new Term(getName(), value), "@" + qname.getEncodedName());
    }
    
}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */