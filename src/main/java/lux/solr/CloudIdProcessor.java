package lux.solr;

import java.io.IOException;

import lux.index.FieldRole;
import lux.index.IndexConfiguration;

import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.update.AddUpdateCommand;
import org.apache.solr.update.processor.UpdateRequestProcessor;

/**
 * Adds a timestamp-based identifier to represent XQuery "document order"
 * Note that this id must be the same on all replicas, so the id must be created *before*
 * the request is dispatched by DistributedUpdateProcessor: ergo, this processor
 * must come before DUP in the update chain configuration in solrconfig.xml.
 * 
 * Note: we can't use URI for this purpose since we need to be able to translate our ordering
 * field into a long value for Saxon to use as its document number, since that it is what *it*
 * uses for document order.
 */
public class CloudIdProcessor extends UpdateRequestProcessor {

    private final String idFieldName;
    private final String uriFieldName;
    
    public CloudIdProcessor (SolrIndexConfig config, SolrQueryRequest req, UpdateRequestProcessor next) {
        super(next);
        IndexConfiguration indexConfig = config.getIndexConfig();
        idFieldName = indexConfig.getFieldName(FieldRole.ID);
        uriFieldName = indexConfig.getFieldName(FieldRole.URI);
    }

    @Override
    public void processAdd (final AddUpdateCommand cmd) throws IOException {
        SolrInputDocument solrInputDocument = cmd.getSolrInputDocument();
        
        String uri = (String) solrInputDocument.getFieldValue(uriFieldName);
        if (uri != null) {
            // we actually only need about 42 bits to count up to about to 2070, so use the remaining 22
            // for some bits from a uri hash to make this (more likely to be) globally unique.
            long t = System.currentTimeMillis() << 22;
            long hashCode = uri.hashCode() & 0x2fffff;
            // would the high-order bits be more random?
            long luxDocId = t | hashCode;
            solrInputDocument.addField(idFieldName, luxDocId);
        }
        
        if (next != null) {
            next.processAdd(cmd);
        }
        
    }
    
}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
