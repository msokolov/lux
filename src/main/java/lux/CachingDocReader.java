package lux;

import java.io.IOException;
import java.io.StringReader;
import java.util.HashSet;
import java.util.Map;

import javax.xml.transform.stream.StreamSource;

import lux.exception.LuxException;
import lux.index.FieldName;
import lux.index.IndexConfiguration;
import lux.index.field.TinyBinaryField;
import lux.xml.tinybin.TinyBinary;
import net.sf.saxon.Configuration;
import net.sf.saxon.s9api.DocumentBuilder;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.tree.tiny.TinyDocumentImpl;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.DocumentStoredFieldVisitor;
import org.apache.lucene.index.AtomicReaderContext;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.util.BytesRef;
import org.slf4j.LoggerFactory;

/**
 * Reads, parses and caches XML documents from a Lucene index. Assigns Lucene
 * docIDs as Saxon document numbers. This reader is intended to survive for a
 * single query only, and is *not thread-safe*. 
 * 
 * TODO: a nice optimization would be to maintain a global cache, shared across threads, 
 * with some tunable resource-based eviction policy.
 * 
 * Not threadsafe.
 */
public class CachingDocReader {
	private final LRUCache<Integer, XdmNode> cache = new LRUCache<Integer, XdmNode>(512);
    private final String xmlFieldName;
    private final String uriFieldName;
    private final HashSet<String> fieldsToRetrieve;
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
        this.xmlFieldName = indexConfig.getFieldName(FieldName.XML_STORE);
        this.uriFieldName = indexConfig.getFieldName(FieldName.URI);
        fieldsToRetrieve = new HashSet<String>();
        fieldsToRetrieve.add(xmlFieldName);
        fieldsToRetrieve.add(uriFieldName);
    }

    /**
     * Reads the document with the given relative id from an atomic reader.
     * If cached, the cached copy is returned. Otherwise the document is read from the index. 
     * If the document does not exist in the index, or has been deleted, results are not
     * well-defined: see {@link IndexReader}.
     * 
     * @param leadDocID the relative docid of the document to read
     * @param context an atomic Lucene index reader context (a leaf of the segmented index tree)
     * @return the document, as a Saxon XdmNode
     * @throws IOException if there is some sort of low-level problem with the index
     * @throws LuxException if there is an error building the document that has been retrieved
     */
    public XdmNode get(int leafDocID, AtomicReaderContext context) throws IOException {
       int docID = leafDocID + context.docBase;
       XdmNode node= cache.get(docID);
       if (node != null) {
           ++cacheHits;
           return node;
       }
       DocumentStoredFieldVisitor fieldSelector = new DocumentStoredFieldVisitor(fieldsToRetrieve);
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
        DocumentStoredFieldVisitor fieldSelector = new DocumentStoredFieldVisitor(fieldsToRetrieve);
        reader.document(docID, fieldSelector);
        Document document = fieldSelector.getDocument();
        return getXdmNode(docID, document);
    }

    private XdmNode getXdmNode(int docID, Document document) throws IOException {
        XdmNode node = null;
        String xml = document.get(xmlFieldName);
        String uri = "lux:/" + document.get(uriFieldName);
        DocIDNumberAllocator docIdAllocator = (DocIDNumberAllocator) config.getDocumentNumberAllocator();
        docIdAllocator.setNextDocID(docID);
        long t0 = System.nanoTime();
        byte[] bytes = null;
        if (xml == null) {
            BytesRef binaryValue = document.getBinaryValue(xmlFieldName);
            if (binaryValue == null) {
                // This is a document without the expected fields, as will happen, eg if we just connect to
                // some random database.
                LoggerFactory.getLogger(CachingDocReader.class).warn ("Document {} has no content", docID);
                bytes = new byte[0];
            } else {
                bytes = binaryValue.bytes;
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
        if (bytes != null) {
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
    
    // from org.apache.lucene.queryparser.xml.builders.CachedFilterBuilder.LRUCache
    // TODO: limit cache by something proportional to *bytes*, rather than number of entries
    static class LRUCache<K, V> extends java.util.LinkedHashMap<K, V> {

        public LRUCache(int maxsize) {
          super(maxsize * 4 / 3 + 1, 0.75f, true);
          this.maxsize = maxsize;
        }

        protected int maxsize;

        @Override
        protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
          return size() > maxsize;
        }

      }

}

/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/.
 */
