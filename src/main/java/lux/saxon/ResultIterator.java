package lux.saxon;

import java.io.IOException;

import lux.api.LuxException;
import lux.api.QueryStats;
import lux.search.LuxSearcher;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.tiny.TinyDocumentImpl;

import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Scorer;

@SuppressWarnings("rawtypes")
public class ResultIterator implements SequenceIterator<Item>{
    
    private final DocIdSetIterator docIter;
    private final Query query;
    private final QueryStats stats;
    private final LuxSearcher searcher;
    private final Saxon saxon;
    private CachingDocReader docCache;
    private Item current = null;
    private int position = 0;
    
    public ResultIterator (Saxon saxon, Query query) throws IOException {
        this.query = query;
        this.saxon = saxon;
        this.stats = saxon.getQueryStats();
        if (stats != null) {
            stats.query = query.toString();
        }
        searcher = saxon.getSearcher();
        docCache = saxon.getDocReader();
        docIter = searcher.searchOrdered(query);
    }

    public Item next() throws XPathException {
        long t = System.nanoTime();
        try {
            int docID = docIter.nextDoc();
            //System.out.println ("GET " + docID + " " + query.toString());
            if (docID == Scorer.NO_MORE_DOCS) {
                position = -1;
                current = null;
            } else {
                if (stats != null) {
                    if (docCache.isCached(docID)) {
                        stats.cacheHits ++;
                    } else {
                        stats.cacheMisses ++;
                    }
                }
                XdmItem doc = docCache.get(docID);
                Item item = (Item) doc.getUnderlyingValue();
                // assertion for safety
                if (current != null && ((TinyDocumentImpl)item).getDocumentNumber() <= ((TinyDocumentImpl)current).getDocumentNumber()) {
                    throw new LuxException ("out of order");
                }
                current = item;
                ++position;
                if (stats != null) {
                    stats.retrievalTime += System.nanoTime() - t;
                }
            }
        } catch (IOException e) {
            throw new XPathException(e);
        } finally {
            if (stats != null) {
                if (position >= 0) {
                    stats.docCount = position;
                }
                stats.totalTime += System.nanoTime() - t;
            }
        }
        return current;
    }

    public Item current() {
        return current;
    }

    public int position() {
        return position;
    }

    public void close() {
        // Saxon doesn't call this reliably
    }

    public SequenceIterator<Item> getAnother() throws XPathException {
        try {
            return new ResultIterator (saxon, query);
        } catch (IOException e) {
            throw new XPathException (e);
        }
    }

    public int getProperties() {
        return SequenceIterator.LOOKAHEAD;
    }
    

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
