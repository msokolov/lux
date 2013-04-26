package lux.solr;

import java.io.IOException;

import javax.xml.stream.XMLStreamException;

import lux.DocWriter;
import lux.exception.LuxException;
import lux.index.XmlIndexer;
import net.sf.saxon.om.NodeInfo;

import org.apache.solr.core.SolrCore;
import org.apache.solr.update.CommitUpdateCommand;
import org.apache.solr.update.DeleteUpdateCommand;
import org.apache.solr.update.UpdateHandler;

public class SolrDocWriter implements DocWriter {

    private final SolrCore core;
    private final XQueryComponent xqueryComponent;

    SolrDocWriter(XQueryComponent xQueryComponent, SolrCore core) {
        this.core = core;
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
            UpdateDocCommand cmd = new UpdateDocCommand(core, indexer.createLuceneDocument(), uri);
            UpdateHandler updateHandler = core.getUpdateHandler();
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
        DeleteUpdateCommand cmd = new DeleteUpdateCommand();
        cmd.fromCommitted = true;
        cmd.fromPending = true;
        cmd.id = uri;
        try {
            core.getUpdateHandler().delete(cmd);
        } catch (IOException e) {
            throw new LuxException(e);
        }
    }

    @Override
    public void deleteAll() {
        DeleteUpdateCommand cmd = new DeleteUpdateCommand();
        cmd.fromCommitted = true;
        cmd.fromPending = true;
        cmd.query = "*:*";
        try {
            core.getUpdateHandler().deleteByQuery(cmd);
        } catch (IOException e) {
            throw new LuxException(e);
        }
    }

    @Override
    public void commit() {
        CommitUpdateCommand cmd = new CommitUpdateCommand(false);
        cmd.expungeDeletes = false;
        cmd.waitFlush = true;
        cmd.waitSearcher = true;
        try {
            core.getUpdateHandler().commit(cmd);
        } catch (IOException e) {
            throw new LuxException(e);
        }
    }
    
    /**
     * commits, but does not close the underlying index
     */
    @Override
    public void close() {
    	// do not attempt to close the index; Solr will take care of that
    	commit ();
    }

}

/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/.
 */