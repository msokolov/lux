/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */

package lux.solr;

import java.io.IOException;
import java.io.StringReader;

import javax.xml.stream.XMLStreamException;

import lux.index.XmlField;
import lux.index.XmlIndexer;

import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.update.AddUpdateCommand;
import org.apache.solr.update.DeleteUpdateCommand;
import org.apache.solr.update.processor.UpdateRequestProcessor;
import org.apache.solr.update.processor.UpdateRequestProcessorFactory;

public class XmlUpdateProcessor extends UpdateRequestProcessorFactory {
    
    private String xmlFieldName;
    
    @Override
    public void init(@SuppressWarnings("rawtypes") final NamedList args) {
        if (args != null) {
            SolrParams params = SolrParams.toSolrParams(args);
            xmlFieldName = params.get("xml-field-name", "xml_text");
            /*
            eltNameFieldName = params.get("elt-name-field-name", "lux_elt_name_ms");
            attNameFieldName = params.get("att-name-field-name", "lux_att_name_ms");
            pathFieldName = params.get("path-field-name", "lux_path_ms");
            */
            // TODO: read namespace-aware; read namespace mapping
            // TODO: read xpath index config
        }
    }
    
    @Override
    public UpdateRequestProcessor getInstance(SolrQueryRequest req, SolrQueryResponse rsp, UpdateRequestProcessor next) {
        return new LuxProcessorInstance (next);
    }
    
    public class LuxProcessorInstance extends UpdateRequestProcessor {
        private XmlIndexer xmlIndexer;

        public LuxProcessorInstance (UpdateRequestProcessor next) {
            super(next);
            // don't XmlIndexer.STORE_XML since we the client passes us the xml_text field
            xmlIndexer = new XmlIndexer (XmlIndexer.INDEX_QNAMES | XmlIndexer.INDEX_PATHS);
        }

        public void processAdd (AddUpdateCommand cmd) throws IOException {  
            Object o = cmd.getSolrInputDocument().getFieldValue(xmlFieldName);
            if (! (o instanceof String)) {
                log.error(xmlFieldName + " is not a string");
            } else {
                String xml = (String) o;
                try {
                    xmlIndexer.read (new StringReader(xml));
                } catch (XMLStreamException e) {
                    log.error ("Failed to parse " + xmlFieldName, e);
                }
                for (XmlField field : xmlIndexer.getFields()) {
                    for (Object value : xmlIndexer.getFieldValues(field)) {
                        // TODO: handle other primitive value types
                        cmd.getSolrInputDocument().addField(field.getName(), value.toString());
                    }
                }
            }
            if (next != null) {
                next.processAdd(cmd);
            }
        }

        public void processDelete (DeleteUpdateCommand cmd) throws IOException {
            if (next != null) {
                next.processDelete(cmd);
            }
        }
    }
}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
