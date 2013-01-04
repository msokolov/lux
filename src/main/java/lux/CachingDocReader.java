package lux;

import java.io.IOException;
import java.io.StringReader;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import javax.xml.transform.stream.StreamSource;

import lux.exception.LuxException;
import lux.index.FieldName;
import lux.index.IndexConfiguration;
import net.sf.saxon.s9api.DocumentBuilder;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmNode;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.FieldSelector;
import org.apache.lucene.document.SetBasedFieldSelector;
import org.apache.lucene.index.IndexReader;

/**
 *  Reads, parses and caches XML documents from a Lucene index.  Assigns Lucene docIDs as Saxon document numbers.
 *  This reader is intended to survive for a single query only.  TODO: a nice optimization would be to maintain
 *  a global cache, shared across threads, with some tunable resource-based eviction policy.
 */
public class CachingDocReader {
    private final HashMap <Integer, XdmNode> cache = new HashMap<Integer, XdmNode>();
    private final String xmlFieldName;
    private final String uriFieldName;
    private final FieldSelector fieldSelector;
    private final DocumentBuilder builder;
    private final DocIDNumberAllocator docIDNumberAllocator;
    private int cacheHits=0;
    private int cacheMisses=0;
    private long buildTime=0;
    
    /**
     * Create a CachingDocReader that will use the provided objects to read and parse XML documents.
     * @param builder will be used to construct XML documents as XdmNodes
     * @param docIDNumberAllocator assigns the proper document ID to each constructed document
     * @param indexConfig supplies the names of the xml storage and uri fields
     */
    public CachingDocReader (DocumentBuilder builder, DocIDNumberAllocator docIDNumberAllocator, IndexConfiguration indexConfig) {
        this.builder = builder;
        this.docIDNumberAllocator = docIDNumberAllocator;
        this.xmlFieldName = indexConfig.getFieldName(FieldName.XML_STORE);
        this.uriFieldName = indexConfig.getFieldName(FieldName.URI);
        HashSet<String> fieldNames = new HashSet<String>();
        fieldNames.add(xmlFieldName);
        fieldNames.add(uriFieldName);
        Set<String> empty = Collections.emptySet();
        fieldSelector = new SetBasedFieldSelector(fieldNames, empty);
    }
    
    /**
     * Reads the document with the given id.  If cached, the cached copy is returned.  Otherwise
     * the document is read from the index.  If the document does not exist in the index, or has been 
     * deleted, results are not well-defined: see {@link IndexReader}.
     * @param docID the id of the document to read
     * @param reader the Lucene index reader
     * @return the document, as a Saxon XdmNode
     * @throws IOException if there is some sort of low-level problem with the index
     * @throws LuxException if there is an error building the document that has been retrieved
     */
    public XdmNode get (int docID, IndexReader reader) throws IOException {
        if (cache.containsKey(docID)) {
            ++cacheHits;
            return cache.get(docID);
        }
        Document document;
        document = reader.document(docID, fieldSelector);
        String xml = document.get(xmlFieldName);
        String uri = "lux:/" + document.get(uriFieldName);
        long t0 = System.nanoTime();
        docIDNumberAllocator.setNextDocID(docID);
        StreamSource source = new StreamSource (new StringReader (xml));
        source.setSystemId(uri);
        XdmNode node;
        try {
            node = builder.build(source);
        } catch (SaxonApiException e) {
            // shouldn't normally happen since the document would generally have been parsed when indexed.
            throw new LuxException(e);
        }
        buildTime += (System.nanoTime() - t0);
        if (node != null) {
            cache.put(docID, node);
        }
        ++cacheMisses;
        return node;
    }
    
    /**
     * @param docID
     * @return true if the a document with the given id is found in the cache
     */
    public final boolean isCached (final int docID) {
        return cache.containsKey(docID);
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
     * @return the total time spent building documents (in nanoseconds).  This includes time spent
     * parsing and constructing a Saxon NodeInfo/XdmNode.
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

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
