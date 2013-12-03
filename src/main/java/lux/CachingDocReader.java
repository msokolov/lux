package lux;

import java.io.IOException;
import java.io.StringReader;
import java.util.Map;

import javax.xml.transform.stream.StreamSource;

import lux.exception.LuxException;
import lux.index.IndexConfiguration;
import lux.index.field.TinyBinaryField;
import lux.xml.tinybin.TinyBinary;
import net.sf.saxon.Configuration;
import net.sf.saxon.s9api.DocumentBuilder;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.tree.tiny.TinyDocumentImpl;
import net.sf.saxon.tree.tiny.TinyTree;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.DocumentStoredFieldVisitor;
import org.apache.lucene.index.AtomicReaderContext;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.util.BytesRef;
import org.slf4j.LoggerFactory;

/**
 * Reads, parses and caches XML documents from a Lucene index. Assigns Lucene
 * docIDs as Saxon document numbers. This reader and its cache are intended to survive for a
 * single query only, and is *not thread-safe*.
 */
public class CachingDocReader {
    // the (inverse of the) portion of heap to allocate to each per-request document cache.  
    // This should really rely on the number of clients
    private final static int CACHE_RATIO = 100;
	private final NodeCache cache = new NodeCache(java.lang.Runtime.getRuntime().maxMemory() / CACHE_RATIO);
    private final String xmlFieldName;
    private final String uriFieldName;
    private final DocumentBuilder builder;
    private final Configuration config;
    private int cacheHits = 0;
    private int cacheMisses = 0;
    private long buildTime = 0;

    /**
     * Create a CachingDocReader that will use the provided objects to read and
     * parse XML documents.
     * 
     * @param builder
     *            will be used to construct XML documents as XdmNodes
     * @param config
     *            assigns the proper document ID to each constructed document
     * @param indexConfig
     *            supplies the names of the xml storage and uri fields
     */
    public CachingDocReader(DocumentBuilder builder, Configuration config, IndexConfiguration indexConfig) {
        this.builder = builder;
        this.config = config;
        this.xmlFieldName = indexConfig.getXmlFieldName();
        this.uriFieldName = indexConfig.getUriFieldName();
    }

    /**
     * Reads the document with the given relative id from an atomic reader.
     * If cached, the cached copy is returned. Otherwise the document is read from the index. 
     * If the document does not exist in the index, or has been deleted, results are not
     * well-defined: see {@link IndexReader}.
     * 
     * @param leafDocID the relative docid of the document to read
     * @param context an atomic Lucene index reader context (a leaf of the segmented index tree)
     * @return the document, as a Saxon XdmNode
     * @throws IOException if there is some sort of low-level problem with the index
     * @throws LuxException if there is an error building the document that has been retrieved
     */
    public XdmNode get(int leafDocID, AtomicReaderContext context) throws IOException {
       long docID = leafDocID + context.docBase;
       XdmNode node= cache.get(docID);
       if (node != null) {
           ++cacheHits;
           return node;
       }
       DocumentStoredFieldVisitor fieldSelector = new DocumentStoredFieldVisitor();
       context.reader().document(leafDocID, fieldSelector);
       Document document = fieldSelector.getDocument();
       return getXdmNode(docID, document);
    }
    
    /**
     * Reads the document with the given id. If cached, the cached copy is
     * returned. Otherwise the document is read from the index. If the document
     * does not exist in the index, or has been deleted, results are not
     * well-defined: see {@link IndexReader}.
     * 
     * @param docID the absolute docid of the document to read
     * @param reader the Lucene index reader
     * @return the document, as a Saxon XdmNode
     * @throws IOException if there is some sort of low-level problem with the index
     * @throws LuxException if there is an error building the document that has been retrieved
     */
    public XdmNode get(int docID, IndexReader reader) throws IOException {
        XdmNode node = cache.get(docID);
        if (node != null) {
            ++cacheHits;
            return node;
        }
        DocumentStoredFieldVisitor fieldSelector = new DocumentStoredFieldVisitor();
        reader.document(docID, fieldSelector);
        Document document = fieldSelector.getDocument();
        return getXdmNode(docID, document);
    }
    
    /**
     * @param docID
     * @return a document from the cache, or null if no document matching the docID is in the cache
     */
    public XdmNode get(long docID) {
        return cache.get(docID);
    }

    private XdmNode getXdmNode(long docID, Document document) throws IOException {
        String xml = document.get(xmlFieldName);
        String uri = document.get(uriFieldName);
        BytesRef binaryValue = document.getBinaryValue(xmlFieldName);
        byte[] bytes;
        if (binaryValue != null) {
            bytes = binaryValue.bytes;
        } else {
            bytes = null;
        }
        XdmNode node = createXdmNode (docID, uri, xml, bytes);
        document.removeField(xmlFieldName);
        node.getUnderlyingNode().getDocumentRoot().setUserData (Document.class.getName(), document);
        return node;
    }

    /**
     * Either the xml or the bytes parameter carries the document contents.  If the xml is non-null, it is assumed to be a serialized
     * XML document, and is parsed.  If the bytes is non-null and begins with a tinybin signature, it is interpreted as 
     * tiny binary.  Otherwise, the bytes are treated as a binary (non-XML) document.  If both xml and bytes are null, a
     * warning is logged and an empty binary document created.
     * 
     * @param docID The Solr/Lucene docid; or a shard/docid combo under Solr Cloud
     * @param uri The uri path (no scheme) of the document to create
     * @param xml The contents of the document, if an XML document
     * @param bytes The contents of the document
     * @return a new document made from the arguments
     */
    public XdmNode createXdmNode (long docID, String uri, String xml, byte[] bytes) {
        uri = "lux:/" + uri;
        DocIDNumberAllocator docIdAllocator = (DocIDNumberAllocator) config.getDocumentNumberAllocator();
        docIdAllocator.setNextDocID(docID);
        long t0 = System.nanoTime();
        XdmNode node = null;
        if (xml == null) {
            if (bytes == null) {
                // This is a document without the expected fields, as will happen, eg if we just connect to
                // some random database.
                LoggerFactory.getLogger(CachingDocReader.class).warn ("Document {} has no XML content", docID);
                bytes = new byte[0];
            }
            if (bytes.length > 4 && bytes[0] == 'T' && bytes[1] == 'I' && bytes[2] == 'N') {
                // An XML document stored in tiny binary format
                TinyBinary tb = new TinyBinary(bytes, TinyBinaryField.UTF8);
                node = new XdmNode (tb.getTinyDocument(config));
            } else {
                xml = "<binary xmlns=\"http://luxdb.net\" />";
            }
        }
        if (node == null) {
            StreamSource source = new StreamSource(new StringReader(xml));
            source.setSystemId(uri);
            try {
                node = builder.build(source);
            } catch (SaxonApiException e) {
                // shouldn't normally happen since the document would generally have
                // been parsed when indexed.
                throw new LuxException(e);
            }
        }
        // associate the bytes with the xml stub (for all non-XML content)
        if (bytes != null && xml == null) {
            ((TinyDocumentImpl)node.getUnderlyingNode()).setUserData("_binaryDocument", bytes);
        }
        // doesn't seem to do what one might think:
        // ((TinyDocumentImpl) node.getUnderlyingNode()).setBaseURI(uri);
        ((TinyDocumentImpl) node.getUnderlyingNode()).setSystemId(uri);
        buildTime += (System.nanoTime() - t0);
        cache.put(docID, node);
        ++cacheMisses;
        return node;
    }

    /**
     * @return the number of items retrieved from the cache
     */
    public int getCacheHits() {
        return cacheHits;
    }

    /**
     * @return the number of items retrieved and added to the cache
     */
    public int getCacheMisses() {
        return cacheMisses;
    }

    /**
     * @return the total time spent building documents (in nanoseconds). This
     *         includes time spent parsing and constructing a Saxon
     *         NodeInfo/XdmNode.
     */
    public long getBuildTime() {
        return buildTime;
    }

    /**
     * Clears all cached documents.
     */
    public void clear() {
        cache.clear();
    }
    
    static class NodeCache extends java.util.LinkedHashMap<Long, XdmNode> {
        
        private long bytes;
        private final long maxbytes;

        public NodeCache (long maxbytes) {
            super((int) ((maxbytes / 100) * 4 / 3 + 1), 0.75f, true);
            this.maxbytes = maxbytes;
            this.bytes = 0;
        }
        
        @Override
        public XdmNode put (Long key, XdmNode value) {
            bytes += calculateSize (value);
            return super.put(key, value);
        }
        
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, XdmNode> eldest) {
            while (bytes > maxbytes) {
                remove(eldest.getKey());
                bytes -= calculateSize (eldest.getValue());
                for (Map.Entry<Long, XdmNode> entry : entrySet()) {
                    // get the next eldest
                    eldest = entry;
                    break;
                }
            }
            return false;
        }

        /**
         * @param value a document whose size is to be calculated
         * @return the size of a TinyBinary representation of the document, which should be pretty close to the actual
         * heap size used.
         */
        private long calculateSize(XdmNode value) {
            TinyTree tree = ((TinyDocumentImpl)value.getUnderlyingNode()).getTree();
            int nodeCount = tree.getNumberOfNodes();
            int attCount = tree.getNumberOfAttributes();
            int nsCount = tree.getNumberOfNamespaces();
            byte[] binary = (byte[]) ((TinyDocumentImpl)value.getUnderlyingNode()).getUserData("_binaryDocument");
            int binSize = binary == null ? 0 : binary.length;
            // gross estimate of the number of string pointers
            // Note: we don't count the size of the names in the name pool, on the assumption they will be shared
            // by a lot of documents.
            int stringLen = 12 + 2 * nodeCount + 2 * attCount + 2 * nsCount;
            // actually count the lengths of all the attributes
            CharSequence[] attValueArray = tree.getAttributeValueArray();
            for (int i = 0; i < attCount; i++) {
                stringLen += attValueArray[i].length();
            }
            return 36
                    + binSize
                    + nodeCount * 19
                    + attCount * 8
                    + nsCount * 8
                    + tree.getCharacterBuffer().length() * 2
                    + (tree.getCommentBuffer() == null ? 0 : tree.getCommentBuffer().length() * 2) 
                    + stringLen;
        }
    }

}


/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/.
 */
