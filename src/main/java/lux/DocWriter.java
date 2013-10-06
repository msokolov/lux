package lux;

import net.sf.saxon.om.NodeInfo;

/**
 * DocWriter supports the insertion and deletion of documents via xquery.
 * Writes and deletes are not visible until commit() is called, and never
 * within the same query.
 */
public interface DocWriter {
    
    /**
     * Writes a document to the index at the given uri, with the node as its root element.
     * Any existing document with the same uri will be overwritten.
     * @param node A node to write as a document
     * @param uri The uri of the document
     */
    void write (NodeInfo node, String uri);
    
    /**
     * Deletes the document having the given uri, if it exists.
     * @param uri
     */
    void delete (String uri);
    
    /**
     * Deletes all documents in the index.
     */
    void deleteAll ();
    
    /**
     * Commits all changes to the index, making them visible to readers.
     */
    void commit (Evaluator eval);

    /**
     * Perform any cleanup, including a commit.
     */
    void close();

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
