package lux.index.field;

import java.util.ArrayList;

import lux.index.QNameTextMapper;
import lux.index.XmlIndexer;
import lux.lucene.LuxTermQuery;
import lux.xpath.QName;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Index;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.Field.TermVector;
import org.apache.lucene.document.Fieldable;
import org.apache.lucene.index.Term;
import org.apache.lucene.util.Version;

public class QNameTextField extends XmlField {
    
    private static final QNameTextField instance = new QNameTextField();
    
    /**
     * @see QNameTextMapper
     */
    public static final String RECORD_END = " luxeor";
    public static final String RECORD_START = "luxsor ";
    
    public static QNameTextField getInstance() {
        return instance;
    }
    
    protected QNameTextField () {
        super ("lux_node_", new StandardAnalyzer(Version.LUCENE_34), Store.NO, Type.STRING, NameKind.PREFIX);
    }
    
    @Override
    public Iterable<Fieldable> getFieldValues(XmlIndexer indexer) {
        QNameTextMapper mapper = (QNameTextMapper) indexer.getPathMapper();
        final int count = mapper.getNames().size();
        ArrayList<Fieldable> fields = new ArrayList<Fieldable>(count);
        StringBuilder namebuf = new StringBuilder();
        for (int i = 0; i < count; i++) {
            namebuf.append(getName()).append(mapper.getNames().get(i));
            fields.add (new Field (namebuf.toString(), 
                        mapper.getValues().get(i),
                        Store.NO, Index.ANALYZED, TermVector.NO));
            namebuf.setLength(0);
        }
        return fields;
    }
    
    public LuxTermQuery makeElementValueQuery (QName qname, String value) {
        String fieldName = getName() + qname.getEncodedName();
        return makeValueQuery (fieldName, value);
    }

    public LuxTermQuery makeAttributeValueQuery (QName qname, String value) {
        String fieldName = getName() + '@' + qname.getEncodedName();
        return makeValueQuery (fieldName, value);
    }

    // FIXME: come up with a query that can match an entire text range
    // without relying on magic start/end of record values.  It's a bit hard to see how this can be
    // done without some "sentinels" to mark these positions.  Consider how queries work: find matching terms
    // and then do logic on them, maybe using positions.  Searching for the absence of a term won't wash.
    // We can at least use safer tokens (that will never collide with actual terms) by creating a wrapping 
    // TokenStream that pulls tokens using the user-provided Analyzer, and whenever it sees a 
    // large position gap, inserts end/start tokens (plus handling edge cases at field start/end)
    // There, we can use \0 as a separator since it can't appear in XML and we can
    // protect it from deletion via analysis.

    private LuxTermQuery makeValueQuery (String fieldName, String value) {
        StringBuilder valueBuf = new StringBuilder ();
        valueBuf.append (RECORD_START).append(value).append(RECORD_END);
        return new LuxTermQuery(new Term(fieldName, valueBuf.toString()));        
    }
    
}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
