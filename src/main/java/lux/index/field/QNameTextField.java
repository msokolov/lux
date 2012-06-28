package lux.index.field;

import java.util.ArrayList;

import lux.index.QNameTextMapper;
import lux.index.XmlIndexer;
import lux.query.TermPQuery;
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
    //public static final String RECORD_END = " luxeor";
    //public static final String RECORD_START = "luxsor ";
    
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
            // this.fieldName + QName = lucene field name
            namebuf.append(getName()).append(mapper.getNames().get(i));
            fields.add (new Field (namebuf.toString(), 
                        mapper.getValues().get(i),
                        Store.NO, Index.ANALYZED, TermVector.NO));
            namebuf.setLength(0);
        }
        return fields;
    }
    
    public TermPQuery makeElementValueQuery (QName qname, String value) {
        String fieldName = getName() + qname.getEncodedName();
        return makeValueQuery (fieldName, value);
    }

    public TermPQuery makeAttributeValueQuery (QName qname, String value) {
        String fieldName = getName() + '@' + qname.getEncodedName();
        return makeValueQuery (fieldName, value);
    }

    private TermPQuery makeValueQuery (String fieldName, String value) {
        return new TermPQuery(new Term(fieldName, value));        
    }
    
}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
