/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */

package lux.index;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import javax.xml.stream.XMLStreamException;

import lux.xml.JDOMBuilder;
import lux.xml.XmlReader;

import org.apache.lucene.document.Field;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.LockObtainFailedException;
import org.apache.lucene.util.Version;
import org.jdom.Document;
import org.jdom.filter.ContentFilter;
import org.jdom.output.XMLOutputter;

public class XmlIndexer {
    
    private XmlReader xmlReader;
    private JDOMBuilder jdomBuilder;
    private XMLOutputter jdomSerializer;
    private XmlPathMapper pathMapper;
    private List<XmlField> fields = new ArrayList<XmlField>();
    private MultiFieldAnalyzer fieldAnalyzers;
    
    private int options = 0;
    
    private static final Version luceneVersion = Version.LUCENE_34;
    
    public XmlIndexer () {
        this (DEFAULT_OPTIONS);
    }
    
    public final static int BUILD_JDOM=1;
    public final static int SERIALIZE_XML=2;
    public final static int NAMESPACE_AWARE=4;
    public final static int STORE_XML=8;
    public final static int STORE_PTREE=16;
    public final static int INDEX_QNAMES=32;
    public final static int INDEX_PATHS=64;
    public final static int INDEX_FULLTEXT=128;
    public final static int INDEXES = INDEX_QNAMES | INDEX_PATHS | INDEX_FULLTEXT;
    public final static int DEFAULT_OPTIONS = BUILD_JDOM | STORE_XML | INDEX_QNAMES | INDEX_PATHS;

    
    public XmlIndexer (int options) {
        this.options = options;
        fieldAnalyzers = new MultiFieldAnalyzer();
        xmlReader = new XmlReader();        
        // accumulate XML paths and QNames for indexing
        pathMapper = new XmlPathMapper();
        xmlReader.addHandler (pathMapper);
        if (isOption (INDEX_QNAMES)) {
            addField(XmlField.ELT_QNAME);
            addField(XmlField.ATT_QNAME);
        }
        if (isOption (INDEX_PATHS)) {
            addField(XmlField.PATH);
        }
        if (isOption (INDEX_FULLTEXT)) {
            addField(XmlField.FULL_TEXT);
        }
        if (isOption (STORE_XML)) {
            addField(XmlField.XML_STORE);
        }
        pathMapper.setNamespaceAware((options & NAMESPACE_AWARE) != 0);        
        if (isOption (BUILD_JDOM) || isOption(STORE_XML)) {
         // build a JDOM in case we want to index XPaths
            jdomBuilder = new JDOMBuilder();
            xmlReader.addHandler (jdomBuilder);
            if (isOption (STORE_XML)) {
                jdomSerializer = new XMLOutputter ();
            }
        }
    }
    
    public void addField (XmlField field) {
        fields.add(field);
        fieldAnalyzers.put(field.getName(), field.getAnalyzer());
    }
    
    public void read (InputStream xml) throws XMLStreamException {
        reset();
        xmlReader.read (xml);
    }

    /**
     * 
     * @param option an option flag; one of: NAMESPACE_AWARE, STORE_XML, STORE_PTREE, INDEX_QNAMES, INDEX_PATHS, INDEX_FULLTEXT
     * @return whether the option is set
     */
    public boolean isOption (int option) {
        return (options & option) != 0;
    }
    
    public void read (Reader xml) throws XMLStreamException {
        reset();
        xmlReader.read (xml);
    }
    
    private void reset() {
        pathMapper.clear();
    }
    
    public Collection<XmlField> getFields () {
        return fields;
    }
    
    public Iterable<?> getFieldValues (XmlField field) {
        if (XmlField.ELT_QNAME.equals(field)) {
            return pathMapper.getEltQNameCounts().keySet();
        }
        if (XmlField.ATT_QNAME.equals(field)) {
            return pathMapper.getAttQNameCounts().keySet();
        }
        if (XmlField.PATH.equals(field)) {
            return pathMapper.getPathCounts().keySet();
        }
        if (XmlField.XML_STORE.equals(field)) {
            return Collections.singletonList(jdomSerializer.outputString(getJDOM()));
        }
        if (XmlField.FULL_TEXT.equals(field)) {
           @SuppressWarnings("unchecked")
           final Iterator<Object> textIter = getJDOM().getDescendants (new ContentFilter(ContentFilter.TEXT | ContentFilter.CDATA));
           return new Iterable<Object> () {
            public Iterator<Object> iterator() {
                return textIter;
            }
           };
        }
        return Collections.EMPTY_SET;
    }
    
    public Document getJDOM() {
        if (jdomBuilder == null)
            return null;
        return jdomBuilder.getDocument();
    }
    
    public void indexDocument(IndexWriter indexWriter, String uri, InputStream xml) throws XMLStreamException, CorruptIndexException, IOException {
        reset();
        read (xml);
        addLuceneDocument(indexWriter, uri);        
    }

    public void indexDocument(IndexWriter indexWriter, String uri, String xml) throws XMLStreamException, CorruptIndexException, IOException {
        reset();
        read(new StringReader(xml));
        addLuceneDocument(indexWriter, uri);     
    }

    private void addLuceneDocument(IndexWriter indexWriter, String uri) throws CorruptIndexException, IOException {
        org.apache.lucene.document.Document doc = new org.apache.lucene.document.Document();
        // TODO: rename field lux_uri, add an XmlField for it, make it required. do something!!
        doc.add (new Field ("uri", uri, Field.Store.YES, Field.Index.NOT_ANALYZED_NO_NORMS));
        for (XmlField field : getFields()) {
            for (Object value : getFieldValues(field)) {
                // TODO: handle other primitive value types like int, at least, and collations, and analyzers
                doc.add(new Field(field.getName(), value.toString(), field.isStored(), field.getIndexOptions()));
            }
        }
        indexWriter.addDocument(doc);
    }

    public IndexWriter getIndexWriter(Directory dir) throws CorruptIndexException, LockObtainFailedException, IOException {
        return new IndexWriter(dir, new IndexWriterConfig(luceneVersion, fieldAnalyzers));
    }

    public long getOptions() {
        return options;
    }

    public String getXmlFieldName() {
        return XmlField.XML_STORE.getName();
    }
    
}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
