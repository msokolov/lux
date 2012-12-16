package lux.index;

import static lux.index.IndexConfiguration.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map.Entry;

import javax.xml.stream.XMLStreamException;

import lux.exception.LuxException;
import lux.index.field.FieldDefinition;
import lux.xml.Offsets;
import lux.xml.SaxonDocBuilder;
import lux.xml.Serializer;
import lux.xml.XmlReader;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XPathCompiler;
import net.sf.saxon.s9api.XPathExecutable;
import net.sf.saxon.s9api.XPathSelector;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmValue;

import org.apache.lucene.document.Fieldable;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.LockObtainFailedException;

/**
 * Indexes XML documents.  The constructor accepts a set of flags that define a set of fields 
 * known to XmlIndexer.  The fields are represented by instances of XmlField.  Instances of XmlField are immutable;
 * they hold no data, merely serving as markers.  Additional fields can also be added using addField().
 * A field may be associated with a StAXHandler; the indexer is responsible for feeding the
 * handlers with StAX (XML) events.  Some fields may share the same handler.  The association between
 * field and handler is implicit: the field calls an XmlIndexer getter to retrieve the handler.
 * 
 * Also, this class is not thread-safe 
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
    
    private final IndexConfiguration configuration;
    private XmlReader xmlReader;
    // TODO: consolidate with XCompiler 
    private Processor processor;
    private XPathCompiler compiler;
    private SaxonDocBuilder saxonBuilder;
    private Serializer serializer;
    private XmlPathMapper pathMapper;
    private String uri;
    private HashMap<String,XPathExecutable> xpathCache;
    
    public XmlIndexer (IndexConfiguration config) {
        this.configuration = config;
        xpathCache = new HashMap<String, XPathExecutable>();
        init();
    }
    
    public XmlIndexer (long options) {
        this (new IndexConfiguration(options));
    }
    
    public XmlIndexer () {
        this (new IndexConfiguration());
    }
        
    protected void init () {
        xmlReader = new XmlReader();
        if (isOption (INDEX_QNAMES) || isOption (INDEX_PATHS)) {
            // accumulate XML paths and QNames for indexing
            if (isOption (INDEX_VALUES)) {
                pathMapper = new XPathValueMapper();
            } else {
                pathMapper = new XmlPathMapper();
            }
            pathMapper.setNamespaceAware(isOption(NAMESPACE_AWARE));        
            xmlReader.addHandler (pathMapper);
        }
        if (isOption (INDEX_FULLTEXT)) {
            initDocBuilder();
        }   
        if (isOption (STORE_XML)) {
            serializer = new Serializer();
            xmlReader.addHandler(serializer);
        }
        if (isOption (BUILD_DOCUMENT) && saxonBuilder == null) {
            initDocBuilder();
        }
        if (isOption (STRIP_NAMESPACES)) {
            xmlReader.setStripNamespaces(true);
        }
    }
    
    public Processor getProcessor () {
        if (processor == null) {
            processor = new Processor(false);
        }
        return processor;
    }
    
    public XPathCompiler getXPathCompiler () {
        if (compiler == null) {
            compiler = getProcessor().newXPathCompiler();
            for (Entry<String, String> nsmap : configuration.getNamespaceMap().entrySet()) {
                compiler.declareNamespace(nsmap.getKey(), nsmap.getValue());
            }
        }
        return compiler;
    }

    public XdmValue evaluateXPath(String xpath) throws SaxonApiException {
        XPathExecutable xpathExec = xpathCache.get(xpath);
        if (xpathExec == null) {
            xpathExec = getXPathCompiler().compile(xpath);
            xpathCache.put(xpath, xpathExec);
        }
        XPathSelector xps  = xpathExec.load();
        xps.setContextItem(getXdmNode());
        return xps.evaluate();
    }
    
    private void initDocBuilder () {
        try {
            if (isOption (COMPUTE_OFFSETS)) {
                saxonBuilder = new SaxonDocBuilder(getProcessor(), new Offsets());                    
            } else {
                saxonBuilder = new SaxonDocBuilder(getProcessor());
            }
            xmlReader.addHandler(saxonBuilder);
        } catch (SaxonApiException e) {
            throw new LuxException (e);
        }
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
    private boolean isOption (int option) {
        return configuration.isOption(option);
    }
    
    public void read (Reader xml, String uri) throws XMLStreamException {
        reset();
        this.uri = uri;
        xmlReader.read (xml);
    }

    public void read(NodeInfo doc, String uri) throws XMLStreamException {
        reset();
        this.uri = uri;
        getXmlReader().read(doc);
    }

    /** Clears out internal storage used while indexing a document */
    public void reset() {
        xmlReader.reset();
    }
    
    private Collection<FieldDefinition> getFields () {
        return configuration.getFields();
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

    // FIXME: why does this method prefix the uri w/lux but the next one strip it off?
    public void indexDocument(IndexWriter indexWriter, String uri, String xml) throws XMLStreamException, CorruptIndexException, IOException {
        reset();
        /*
        String path = uri.startsWith("lux:/") ? uri.substring(5) : uri;
        path = path.replace('\\', '/');
        */
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

    public void indexDocument(IndexWriter indexWriter, String path, NodeInfo node) throws XMLStreamException, CorruptIndexException, IOException {
        reset();
        read(node, path);
        addLuceneDocument(indexWriter);
    }

    /**
     * @return a Lucene {@link org.apache.lucene.document.Document} created from the field values stored in this indexer. The document
     * is ready to be inserted into Lucene via {@link IndexWriter#addDocument}.
     */
    public org.apache.lucene.document.Document createLuceneDocument () {
        org.apache.lucene.document.Document doc = new org.apache.lucene.document.Document();
        for (FieldDefinition field : getFields()) {
            for (Fieldable f : field.getFieldValues(this)) {
                doc.add(f);
            }
        }
        return doc;
    }

    private void addLuceneDocument(IndexWriter indexWriter) throws CorruptIndexException, IOException {
        indexWriter.addDocument(createLuceneDocument());
    }

    public IndexWriter getIndexWriter(Directory dir) throws CorruptIndexException, LockObtainFailedException, IOException {
        return new IndexWriter(dir, new IndexWriterConfig(LUCENE_VERSION, configuration.getFieldAnalyzers()));
    }

    public XmlPathMapper getPathMapper() {
        return pathMapper;
    }
    
    public XmlReader getXmlReader () {
        return xmlReader;
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
    
    public IndexConfiguration getConfiguration() {
        return configuration;
    }


}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
