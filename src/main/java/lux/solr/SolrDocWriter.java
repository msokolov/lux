package lux.solr;

import java.io.IOException;

import javax.xml.stream.XMLStreamException;

import lux.DocWriter;
import lux.exception.LuxException;
import lux.index.XmlIndexer;
import net.sf.saxon.om.NodeInfo;

import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.request.SolrQueryRequestBase;
import org.apache.solr.update.CommitUpdateCommand;
import org.apache.solr.update.DeleteUpdateCommand;
import org.apache.solr.update.UpdateHandler;

public class SolrDocWriter implements DocWriter {

    private final UpdateHandler updateHandler;
    private final XQueryComponent xqueryComponent;

    SolrDocWriter(XQueryComponent xQueryComponent, UpdateHandler updateHandler) {
        this.updateHandler = updateHandler;
        this.xqueryComponent = xQueryComponent;
    }

    @Override
    public void write(NodeInfo node, String uri) {
        XmlIndexer indexer = null;
        try {
            indexer = xqueryComponent.checkoutXmlIndexer();
            try {
                indexer.index (node, uri);
            } catch (XMLStreamException e) {
                throw new LuxException(e);
            }
            // Do we need to pass the correct core?  It gets associated with SolrParams that are
            // never read I think?
            UpdateDocCommand cmd = new UpdateDocCommand(null, indexer.createLuceneDocument());
            try {
                updateHandler.addDoc(cmd);
            } catch (IOException e) {
                throw new LuxException (e);
            }
        } finally {
            if (indexer != null) {
                xqueryComponent.returnXmlIndexer(indexer);
            }
        }
    }

    @Override
    public void delete(String uri) {
        DeleteUpdateCommand cmd = new DeleteUpdateCommand( makeSolrQueryRequest());
        /*
        cmd.fromCommitted = true;
        cmd.fromPending = true;
        */
        cmd.id = uri;
        try {
            updateHandler.delete(cmd);
        } catch (IOException e) {
            throw new LuxException(e);
        }
    }

    @Override
    public void deleteAll() {
        DeleteUpdateCommand cmd = new DeleteUpdateCommand( makeSolrQueryRequest());
        /*
        cmd.fromCommitted = true;
        cmd.fromPending = true;
        */
        cmd.query = "*:*";
        try {
            updateHandler.deleteByQuery(cmd);
        } catch (IOException e) {
            throw new LuxException(e);
        }
    }

    /**
     * @return
     */
    private SolrQueryRequestBase makeSolrQueryRequest() {
        return new SolrQueryRequestBase(null, new ModifiableSolrParams()) {};
    }

    @Override
    public void commit() {
        CommitUpdateCommand cmd = new CommitUpdateCommand(makeSolrQueryRequest(), false);
        cmd.expungeDeletes = false;
        // cmd.waitFlush = true;
        cmd.waitSearcher = true;
        try {
            updateHandler.commit(cmd);
        } catch (IOException e) {
            throw new LuxException(e);
        }
    }

}

/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/.
 */