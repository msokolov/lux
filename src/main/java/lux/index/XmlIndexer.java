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
import lux.xml.OffsetDocBuilder;
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

import org.apache.commons.io.IOUtils;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.store.Directory;

/**
 * Indexes XML documents.  The constructor accepts a set of flags that
 * define a set of fields known to XmlIndexer.  The fields are represented
 * by instances of XmlField.  Instances of XmlField are immutable; they
 * hold no data, merely serving as markers.  Additional fields can also be
 * added using addField().  A field may be associated with a StAXHandler;
 * the indexer is responsible for feeding the handlers with StAX (XML)
 * events.  Some fields may share the same handler.  The association
 * between field and handler is implicit: the field calls an XmlIndexer
 * getter to retrieve the handler.
 * 
 * Also, this class is not thread-safe 
 * 
 * This is all kind of a mess, and not readily extendable.  If you want to
 * add a new type of field (a new XmlField instance), you have to modify
 * the indexer, which has knowledge of all the possible fields.  This is
 * not a good design.
 * 
 * Also, not every combination of indexing options will actually work.  We
 * need to consider which things one might actually want to turn on and
 * off.
 * 
 * We could make each field act as a StAXHandler factory?  For efficiency
 * though, some fields share the same handler instance.  For now, we leave
 * things as they are; we'll refactor as we add more fields.
 * 
 * Indexing is triggered by a call to indexDocument(). read(InputStream)
 * parses and gathers the values.  which are retrieved by calling
 * XmlField.getFieldValues(XmlIndexer) for each field.
 */
public class XmlIndexer {
    
    private final IndexConfiguration configuration;
    private XmlReader xmlReader;
    private Processor processor;
    private XPathCompiler compiler;
    private SaxonDocBuilder saxonBuilder;
    private Serializer serializer;
    private XmlPathMapper pathMapper;
    private String uri;
    private byte[] documentBytes;
    private HashMap<String,XPathExecutable> xpathCache;
    
    /**
     * Make a new instance with default options
     */
    public XmlIndexer () {
        this (new IndexConfiguration());
    }
        
    /**
     * Make a new instance with the given configuration. Options in the configuration control
     * how documents are indexed, and which kinds of indexed values will be available after indexing
     * a document.
     * @param config the index configuration to use
     */
    public XmlIndexer (IndexConfiguration config) {
        this.configuration = config;
        xpathCache = new HashMap<String, XPathExecutable>();
        init();
    }
    
    /**
     * Make a new instance with the given options. Used mostly for testing.
     * @param options the index configuration options to use
     */
    public XmlIndexer (long options) {
        this (new IndexConfiguration(options));
    }
    
    /**
     * initialize the indexer; an extension of the constructors.  Creates subsidiary objects
     * required for indexing based on the index options.
     */
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
        if (isOption (STORE_DOCUMENT)) {
        	if (! isOption(STORE_TINY_BINARY)) {
        		serializer = new Serializer();
        		xmlReader.addHandler(serializer);
        	}
        }
        if (isOption (BUILD_DOCUMENT) && saxonBuilder == null) {
            initDocBuilder();
        }
        if (isOption (STRIP_NAMESPACES)) {
            xmlReader.setStripNamespaces(true);
        }
    }
    
    /**
     * Constructs a new Lucene IndexWriter for the given index directory
     * supplied with the proper analyzers for each field.  The directory
     * must exist: if there is no index in the directory, a new one will be
     * created.  If there is an existing directory, it will be locked for
     * writing until the writer is closed.
     * @param dir the directory where the index is stored
     * @return the IndexWriter
     * @throws IOException if there is a problem with the index
     */
    public IndexWriter newIndexWriter(Directory dir) throws IOException {
        return new IndexWriter(dir, new IndexWriterConfig(LUCENE_VERSION, configuration.getFieldAnalyzers()));
    }

    /**
     * this is primarily for internal use
     * @return an XPathCompiler 
     */
    public XPathCompiler getXPathCompiler () {
        if (compiler == null) {
            compiler = getProcessor().newXPathCompiler();
            for (Entry<String, String> nsmap : configuration.getNamespaceMap().entrySet()) {
                compiler.declareNamespace(nsmap.getKey(), nsmap.getValue());
            }
        }
        return compiler;
    }

    private Processor getProcessor () {
        if (processor == null) {
            processor = new Processor(false);
        }
        return processor;
    }
    
    /**
     * this is primarily for internal use
     * @param xpath an xpath expression to evaluate
     * @return the result of evaluating the xpath expression with the last indexed as context 
     * @throws SaxonApiException if there is an error during compilation or evaluation
     */
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
                saxonBuilder = new OffsetDocBuilder(getProcessor());                    
            } else {
                saxonBuilder = new SaxonDocBuilder(getProcessor());
            }
            xmlReader.addHandler(saxonBuilder);
        } catch (SaxonApiException e) {
            throw new LuxException (e);
        }
    }
    
    /**
     * Index the document read from the stream, caching field values to be written
     * to the Lucene index.
     * @param xml the document, as a byte-based InputStream
     * @param inputUri the uri to assign to the document
     * @throws XMLStreamException 
     */
    public void index (InputStream xml, String inputUri) throws XMLStreamException {
        reset();
        this.uri = inputUri;
        xmlReader.read (xml);
    }
    
    /**
     * Index the document read from the Reader, caching field values to be written
     * to the Lucene index.
     * @param xml the document, as a character-based Reader
     * @param inputUri the uri to assign to the document
     * @throws XMLStreamException 
     */
    public void index (Reader xml, String inputUri) throws XMLStreamException {
        reset();
        this.uri = inputUri;
        xmlReader.read (xml);
    }

    /**
     * Index the document read from the String, caching field values to be
     * written to the Lucene index.
     * @param doc the document, as a String
     * @param inputUri the uri to assign to the document
     * @throws XMLStreamException 
     */
    public void index (NodeInfo doc, String inputUri) throws XMLStreamException {
        reset();
        this.uri = inputUri;
        xmlReader.read(doc);
    }

    /** Clear out internal storage cached by #index when indexing a document */
    public void reset() {
        xmlReader.reset();
        documentBytes = null;
    }

    /**
     * 
     * @param option an option flag; one of: NAMESPACE_AWARE, STORE_XML,
     * STORE_PTREE, INDEX_QNAMES, INDEX_PATHS, INDEX_FULLTEXT
     * @return whether the option is set
     */
    private boolean isOption (int option) {
        return configuration.isOption(option);
    }
    
    private Collection<FieldDefinition> getFields () {
        return configuration.getFields();
    }
    
    /**
     * @return the uri cached from the last invocation of #index
     */
    public String getURI() {
        return uri;
    }
    
    /**
     * @return the document cached from the last invocation of #index, as a Saxon XdmNode.
     * This will be null if the indexer options don't require the generation of an XdmNode.
     */
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
    
    /**
     * @return the document cached from the last invocation of #index, as a
     * String.  This will be null if the indexer options don't require the
     * generation of a serialized document.  The document is always re-serialized
     * after parsing.
     */
    public String getDocumentText() {
        if (serializer != null) {
            return serializer.getDocument();
        }
        return null;        
    }

    /**
     * @return the document bytes; this will be non-null if {@link #storeDocument(IndexWriter, String, InputStream)}
     * was called.
     */
    public byte[] getDocumentBytes() {
        return documentBytes;
    }
    
    /**
     * Index and write a document to the Lucene index.
     * @param indexWriter the Lucene IndexWriter for the index to write to
     * @param docUri the uri to assign to the document; any scheme will
     * be stripped: only the path is stored in the index
     * @param xml the text of an xml document to index
     * @throws XMLStreamException if there is an error parsing the document
     * @throws IOException if there is an error writing to the index
     */
    public void indexDocument(final IndexWriter indexWriter, final String docUri, final String xml) throws XMLStreamException, IOException {
        reset();
        String path = normalizeUri(docUri);
        index(new StringReader(xml), path);
        addLuceneDocument(indexWriter);
    }
    
    /**
     * Index and write a document to the Lucene index.
     * @param indexWriter the Lucene IndexWriter for the index to write to
     * @param docUri the uri to assign to the document; any scheme will
     * be stripped: only the path is stored in the index
     * @param xmlStream a stream from which the text of an xml document is to be read
     * @throws XMLStreamException if there is an error parsing the document
     * @throws IOException if there is an error writing to the index
     */
    public void indexDocument(final IndexWriter indexWriter, final String docUri, final InputStream xmlStream) throws XMLStreamException, IOException {
        reset();
        String path = normalizeUri(docUri);
        index(xmlStream, path);
        addLuceneDocument(indexWriter);
    }

    /**
     * Fully read the stream and store it as a document without attempting to parse or index it.  Used for
     * binary and other non-XML text.
     * @param indexWriter the Lucene IndexWriter for the index to write to
     * @param docUri the uri to assign to the document; any scheme will be stripped: only the path is stored in the index
     * @param input the stream to read the document from
     * @throws IOException if there is an error writing to the index
     */
    public void storeDocument(final IndexWriter indexWriter, final String docUri, final InputStream input) throws IOException {
        storeDocument (indexWriter, docUri, IOUtils.toByteArray(input));
    }
    
    /**
     * Fully read the stream and store it as a document without attempting to parse or index it.  Used for
     * binary and other non-XML text.
     * @param indexWriter the Lucene IndexWriter for the index to write to
     * @param docUri the uri to assign to the document; any scheme will be stripped: only the path is stored in the index
     * @param bytes the document bytes to store
     * @throws IOException if there is an error writing to the index
     */
    public void storeDocument(final IndexWriter indexWriter, final String docUri, final byte[] bytes) throws IOException {
        reset();
        String path = normalizeUri(docUri);
        uri = path;
        documentBytes = bytes;
        addLuceneDocument(indexWriter);
    }
    
    private static String normalizeUri(String uri) {
        String path = uri.replaceFirst("^\\w+:/+", "/"); // strip the scheme part (file:/, lux:/, etc), if any
        path = path.replace('\\', '/');
        return path;
    }

    /**
     * Index and write a document to the Lucene index.
     * @param indexWriter the Lucene IndexWriter for the index to write to
     * @param path the uri to assign to the document
     * @param node an xml document to index, as a Saxon NodeInfo
     * @throws XMLStreamException if there is an error parsing the document
     * @throws IOException if there is an error writing to the index
     */
    public void indexDocument(IndexWriter indexWriter, String path, NodeInfo node) throws XMLStreamException, IOException {
        reset();
        index(node, path);
        addLuceneDocument(indexWriter);
    }

    /**
     * @return a Lucene {@link org.apache.lucene.document.Document} created
     * from the field values stored in this indexer. The document is ready
     * to be inserted into Lucene via {@link IndexWriter#addDocument}.
     */
    public org.apache.lucene.document.Document createLuceneDocument () {
        org.apache.lucene.document.Document doc = new org.apache.lucene.document.Document();
        for (FieldDefinition field : getFields()) {
            for (IndexableField f : field.getFieldValues(this)) {
                doc.add(f);
            }
        }
        return doc;
    }

    private void addLuceneDocument(IndexWriter indexWriter) throws CorruptIndexException, IOException {
        indexWriter.addDocument(createLuceneDocument());
    }

    /** Primarily for internal use.
     * @return the {@link SaxonDocBuilder} used by the indexer to construct XdmNodes.
     */
    public SaxonDocBuilder getSaxonDocBuilder () {
        return saxonBuilder;
    }

    /** Primarily for internal use.
     * @return the {@link XmlPathMapper} used by the indexer to gather node paths.
     */
    public XmlPathMapper getPathMapper() {
        return pathMapper;
    }

    /** @return the index configuration */
    public IndexConfiguration getConfiguration() {
        return configuration;
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
