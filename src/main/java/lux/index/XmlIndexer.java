package lux.index;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.xml.stream.XMLStreamException;

import lux.api.LuxException;
import lux.index.field.XmlField;
import lux.xml.JDOMBuilder;
import lux.xml.SaxonDocBuilder;
import lux.xml.Serializer;
import lux.xml.XmlReader;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmNode;

import org.apache.lucene.document.Fieldable;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.LockObtainFailedException;
import org.apache.lucene.util.Version;
import org.jdom.Document;

/**
 * Indexes XML documents.  The constructor accepts a set of flags that define a set of fields 
 * known to XmlIndexer.  The fields are represented by instances of XmlField.  Instances of XmlField are immutable;
 * they hold no data, merely serving as markers.  Additional fields can also be added using addField().
 * A field may be associated with a StAXHandler; the indexer is responsible for feeding the
 * handlers with StAX (XML) events.  Some fields may share the same handler.  The association between
 * field and handler is implicit: the field calls an XmlIndexer getter to retrieve the handler.
 * 
 * This is all kind of a mess, and not readily extendable.  If you want to add a new type of field (a new XmlField instance),
 * you have to modify the indexer, which has knowledge of all the possible fields.  This is not a good design.
 * 
 * Also, not every combination of indexing options will actually work.  We need to consider which things
 * one might actually want to turn on and off.
 * 
 * We could make each field act as a StAXHandler factory?
 * For efficiency though, some fields share the same handler instance.  For now, we leave things as they are;
 * we'll refactor as we add more fields.
 * 
 * Indexing is triggered by a call to indexDocument(). read(InputStream) parses and gathers the values.
 * which are retrieved by calling XmlField.getFieldValues(XmlIndexer) for each field.
 */
public class XmlIndexer {
    
    private XmlReader xmlReader;
    private JDOMBuilder jdomBuilder;
    private SaxonDocBuilder saxonBuilder;
    private Serializer serializer;
    private XmlPathMapper pathMapper;
    private List<XmlField> fields = new ArrayList<XmlField>();
    private MultiFieldAnalyzer fieldAnalyzers;
    private String uri;
    
    private int options = 0;
    
    public static final Version LUCENE_VERSION = Version.LUCENE_34;
    
    public XmlIndexer () {
        this (DEFAULT_OPTIONS);
    }
    
    public final static int BUILD_JDOM=         0x00000001;
    public final static int SERIALIZE_XML=      0x00000002;
    public final static int NAMESPACE_UNAWARE=  0x00000004;
    public final static int STORE_XML=          0x00000008;
    public final static int STORE_PTREE=        0x00000010;
    public final static int INDEX_QNAMES=       0x00000020;
    public final static int INDEX_PATHS=        0x00000040;
    public final static int INDEX_FULLTEXT=     0x00000080;
    public final static int INDEX_VALUES=       0x00000100;
    public final static int INDEXES = INDEX_QNAMES | INDEX_PATHS | INDEX_FULLTEXT | INDEX_VALUES;
    public final static int DEFAULT_OPTIONS = BUILD_JDOM | STORE_XML | INDEX_QNAMES | INDEX_PATHS | INDEX_FULLTEXT;

    
    public XmlIndexer (int options) {
        this.options = options;
        fieldAnalyzers = new MultiFieldAnalyzer();
        xmlReader = new XmlReader();
        addField (XmlField.URI);
        if (isOption (INDEX_QNAMES) || isOption (INDEX_PATHS)) {
            // accumulate XML paths and QNames for indexing
            if (isOption (INDEX_FULLTEXT)) {
                pathMapper = new XmlPathMapper();
            }
            else if (isOption (INDEX_VALUES)) {
                pathMapper = new XPathValueMapper();
            } else {
                pathMapper = new XmlPathMapper();
            }
            pathMapper.setNamespaceAware((options & NAMESPACE_UNAWARE) == 0);        
            xmlReader.addHandler (pathMapper);
        }
        if (isOption (INDEX_QNAMES)) {
            addField(XmlField.ELT_QNAME);
            addField(XmlField.ATT_QNAME);
            if (isOption (INDEX_VALUES)) {
                addField(XmlField.QNAME_VALUE);
            }
        }
        if (isOption (INDEX_PATHS)) {
            addField(XmlField.PATH);
            if (isOption (INDEX_VALUES)) {
                addField(XmlField.PATH_VALUE);                
            }
        }
        if (isOption (INDEX_FULLTEXT)) {
            addField (XmlField.XML_TEXT);
            addField (XmlField.NODE_TEXT);
            try {
                saxonBuilder = new SaxonDocBuilder();
                xmlReader.addHandler(saxonBuilder);
            } catch (SaxonApiException e) {
                throw new LuxException (e);
            }
        }
        if (isOption (STORE_XML)) {
            addField(XmlField.XML_STORE);
            serializer = new Serializer();
            xmlReader.addHandler(serializer);
        }
        if (isOption (BUILD_JDOM)) {
            // TODO: replace w/XdmNode - is there a Saxon builder we can feed StAX events to?  Probably
            // need to convert to SAX
            // build a JDOM in case we want to index XPaths
            jdomBuilder = new JDOMBuilder();
            xmlReader.addHandler (jdomBuilder);
        }
    }
    
    public void addField (XmlField field) {
        fields.add(field);
        fieldAnalyzers.put(field.getName(), field.getAnalyzer());
    }
    
    public void read (InputStream xml, String uri) throws XMLStreamException {
        reset();
        this.uri = uri;
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
    
    public void read (Reader xml, String uri) throws XMLStreamException {
        reset();
        this.uri = uri;
        xmlReader.read (xml);
    }
    
    private void reset() {
        xmlReader.reset();
    }
    
    public Collection<XmlField> getFields () {
        return fields;
    }
    
    public Document getJDOM() {
        if (jdomBuilder == null)
            return null;
        return jdomBuilder.getDocument();
    }
    
    public XdmNode getXdmNode () {
        if (saxonBuilder == null) {
            return null;
        }
        try {
            return saxonBuilder.getDocument();
        } catch (SaxonApiException e) {
            throw new LuxException (e);
        }
    }
    
    public SaxonDocBuilder getSaxonDocBuilder () {
        return saxonBuilder;
    }    

    public void indexDocument(IndexWriter indexWriter, String uri, String xml) throws XMLStreamException, CorruptIndexException, IOException {
        reset();
        uri = "lux:/" + uri;
        read(new StringReader(xml), uri);
        addLuceneDocument(indexWriter);
    }
    
    public void indexDocument(IndexWriter indexWriter, String uri, InputStream xmlStream) throws XMLStreamException, CorruptIndexException, IOException {
        reset();
        String path = uri.startsWith("lux:/") ? uri.substring(5) : uri;
        path = path.replace('\\', '/');
        read(xmlStream, path);
        addLuceneDocument(indexWriter);
    }

    private void addLuceneDocument(IndexWriter indexWriter) throws CorruptIndexException, IOException {
        org.apache.lucene.document.Document doc = new org.apache.lucene.document.Document();
        for (XmlField field : getFields()) {
            for (Fieldable f : field.getFieldValues(this)) {
                doc.add(f);
            }
        }
        indexWriter.addDocument(doc);
    }

    public IndexWriter getIndexWriter(Directory dir) throws CorruptIndexException, LockObtainFailedException, IOException {
        return new IndexWriter(dir, new IndexWriterConfig(LUCENE_VERSION, fieldAnalyzers));
    }

    public long getOptions() {
        return options;
    }

    public String getXmlFieldName() {
        return XmlField.XML_STORE.getName();
    }

    public String getUriFieldName() {
        return XmlField.URI.getName();
    }
    
    public XmlPathMapper getPathMapper() {
        return pathMapper;
    }

    public String getDocumentText() {
        if (serializer != null) {
            return serializer.getDocument();
        }
        return null;        
    }
    
    public String getURI() {
        return uri;
    }
    
}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
