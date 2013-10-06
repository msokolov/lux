package lux.solr;

import java.io.IOException;
import java.util.ArrayList;

import javax.xml.stream.XMLStreamException;

import lux.DocWriter;
import lux.Evaluator;
import lux.exception.LuxException;
import lux.index.FieldName;
import lux.index.IndexConfiguration;
import lux.index.XmlIndexer;
import lux.xml.tinybin.TinyBinary;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.Serializer;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.tree.tiny.TinyNodeImpl;

import org.apache.solr.client.solrj.request.UpdateRequestExt;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.params.ShardParams;
import org.apache.solr.core.SolrCore;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.request.SolrQueryRequestBase;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.update.DeleteUpdateCommand;
import org.apache.solr.update.UpdateHandler;
import org.apache.solr.update.processor.UpdateRequestProcessor;
import org.apache.solr.update.processor.UpdateRequestProcessorChain;
import org.slf4j.LoggerFactory;

/**
 * Used for updates (write, delete and commit) from within XQuery (lux:insert) and XSLT (xsl:result-document)
 * TODO: refactor into two classes: one for cloud, one for local?
 */
public class SolrDocWriter implements DocWriter {

    private final SolrCore core;
    private final XQueryComponent xqueryComponent;
    private final String uriFieldName;
    private final String xmlFieldName;

    SolrDocWriter(XQueryComponent xQueryComponent, SolrCore core) {
        this.core = core;
        this.xqueryComponent = xQueryComponent;
        IndexConfiguration indexConfig = xQueryComponent.getSolrIndexConfig().getIndexConfig();
        uriFieldName = indexConfig.getFieldName(FieldName.URI);
        xmlFieldName = indexConfig.getFieldName(FieldName.XML_STORE);
    }

    @Override
    public void write(NodeInfo node, String uri) {
        UpdateHandler updateHandler = core.getUpdateHandler();

        // Create a version of the document for saving to the transaction log,
        // or for cloud update via HTTP
        SolrInputDocument solrDoc = new SolrInputDocument();
        solrDoc.addField(uriFieldName, uri);
        if (isCloud()) {
            // TODO: write as binary, but we need to enable the binary update request writer for this
            // TinyBinary tinybin = new TinyBinary(((TinyNodeImpl)node).getTree());
            // solrDoc.addField(xmlFieldName, tinybin.getByteBuffer().array());
            Serializer serializer = xqueryComponent.solrIndexConfig.checkoutSerializer();
            try {
                String xmlString = serializer.serializeNodeToString(new XdmNode(node));
                solrDoc.addField(xmlFieldName,  xmlString);
            } catch (SaxonApiException e) {
                throw new LuxException (e);
            } finally {
                xqueryComponent.solrIndexConfig.returnSerializer(serializer);
            }
            // TODO -- if we can determine this doc only gets added locally??
            // solrDoc.addField(xmlFieldName, node);
        }
        else if (updateHandler.getUpdateLog() != null) {
            if (node instanceof TinyNodeImpl) {
                TinyBinary tinybin = new TinyBinary(((TinyNodeImpl)node).getTree());
                solrDoc.addField(xmlFieldName, tinybin.getByteBuffer());
            } else {
                String xml = node.toString();
                solrDoc.addField(xmlFieldName, xml);
            }
        }
        if (isCloud()) {
            writeToCloud (solrDoc, uri);
        } else {
            writeLocal (solrDoc, node, uri);
        }
    }

    private void writeToCloud (SolrInputDocument solrDoc, String uri) {
        ArrayList<String> urls = xqueryComponent.getShardURLs(true);
        LoggerFactory.getLogger(getClass()).debug ("writing " + uri + " to cloud at " + urls); 
        SolrQueryResponse rsp = new SolrQueryResponse();
        SolrQueryRequest req = UpdateDocCommand.makeSolrRequest(core);
        ((ModifiableSolrParams)req.getParams()).add(ShardParams.SHARDS, urls.toArray(new String[urls.size()]));
        UpdateRequestExt updateReq = new UpdateRequestExt();
        updateReq.add(solrDoc);
        UpdateDocCommand cmd = new UpdateDocCommand(req, solrDoc, null, uri);
        UpdateRequestProcessorChain updateChain = xqueryComponent.getCore().getUpdateProcessingChain("lux-update-chain");
        try {
            UpdateRequestProcessor processor = updateChain.createProcessor(req, rsp);
            processor.processAdd(cmd);
            processor.finish();
        } catch (IOException e) {
            throw new LuxException (e);
        }
    }
    
    private void writeLocal (SolrInputDocument solrDoc, NodeInfo node, String uri) {
        XmlIndexer indexer = null;
        try {
            indexer = xqueryComponent.getSolrIndexConfig().checkoutXmlIndexer();
            try {
                indexer.index (node, uri);
            } catch (XMLStreamException e) {
                throw new LuxException(e);
            }
            UpdateDocCommand cmd = new UpdateDocCommand(core, indexer.createLuceneDocument(), uri);
            cmd.solrDoc = solrDoc;
            core.getUpdateHandler().addDoc(cmd);
        } catch (IOException e) {
            throw new LuxException (e);
        } finally {
            if (indexer != null) {
                xqueryComponent.getSolrIndexConfig().returnXmlIndexer(indexer);
            }
        }
    }

    @Override
    public void delete(String uri) {
        DeleteUpdateCommand cmd = new DeleteUpdateCommand(makeSolrQueryRequest());
        /*
        cmd.fromCommitted = true;
        cmd.fromPending = true;
        */
        cmd.id = uri;
        try {
            if (isCloud()) {
                deleteCloud(cmd);
            } else {
                core.getUpdateHandler().delete(cmd);
            }
        } catch (IOException e) {
            throw new LuxException (e);
        }

    }
    
    private void deleteCloud (DeleteUpdateCommand cmd) throws IOException {
        UpdateRequestProcessorChain updateChain = xqueryComponent.getCore().getUpdateProcessingChain("lux-update-chain");
            SolrQueryResponse rsp = new SolrQueryResponse();
            SolrQueryRequest req = UpdateDocCommand.makeSolrRequest(core);
            UpdateRequestProcessor processor = updateChain.createProcessor(req, rsp);
            processor.processDelete(cmd);
            processor.finish();
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
            if (isCloud()) {
                deleteCloud(cmd);
            } else {
                core.getUpdateHandler().deleteByQuery(cmd);
            }
        } catch (IOException e) {
            throw new LuxException(e);
        }
    }

    private SolrQueryRequestBase makeSolrQueryRequest() {
        return new SolrQueryRequestBase(core, new ModifiableSolrParams()) {};
    }

    @Override
    public void commit(Evaluator eval) {
        SolrQueryContext context = (SolrQueryContext) eval.getQueryContext();
        context.setCommitPending(true);
    }
    
    /**
     * commits, but does not close the underlying index
     */
    @Override
    public void close(Evaluator eval) {
    	// do not attempt to close the index; Solr will take care of that
        commit (eval);
    }
    
    private boolean isCloud () {
        return xqueryComponent.getCurrentShards() != null;
    }

}

/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/.
 */
