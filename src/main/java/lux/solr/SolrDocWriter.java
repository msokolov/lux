package lux.solr;

import java.io.IOException;

import javax.xml.stream.XMLStreamException;

import lux.DocWriter;
import lux.exception.LuxException;
import lux.index.XmlIndexer;
import net.sf.saxon.om.NodeInfo;

import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.update.AddUpdateCommand;
import org.apache.solr.update.CommitUpdateCommand;
import org.apache.solr.update.DeleteUpdateCommand;
import org.apache.solr.update.UpdateHandler;

public class SolrDocWriter implements DocWriter {
    
    private final UpdateHandler updateHandler;
    private final XmlIndexer indexer;
    
    SolrDocWriter (XmlIndexer indexer, UpdateHandler updateHandler) {
        this.updateHandler = updateHandler;
        this.indexer = indexer;
    }

    @Override
    public void write(NodeInfo node, String uri) {
        try {
            indexer.read (node, uri);
        } catch (XMLStreamException e) {
            throw new LuxException(e);
        }
        AddUpdateCommand cmd = new AddUpdateCommand();
        cmd.overwriteCommitted = true;
        cmd.overwritePending = true;
        cmd.allowDups = false;
        cmd.solrDoc = new SolrInputDocument();
        LuxUpdateProcessor.addDocumentFields(indexer, cmd.solrDoc);
        try {
            updateHandler.addDoc(cmd);
        } catch (IOException e) {
            throw new LuxException (e);
        }
    }

    @Override
    public void delete(String uri) {
        DeleteUpdateCommand cmd = new DeleteUpdateCommand();
        cmd.fromCommitted = true;
        cmd.fromPending = true;
        cmd.id = uri;
        try {
            updateHandler.delete(cmd);
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
            updateHandler.commit(cmd);
        } catch (IOException e) {
            throw new LuxException (e);
        }
    }

}
