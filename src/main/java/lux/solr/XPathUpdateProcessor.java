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

public class XPathUpdateProcessor extends UpdateRequestProcessorFactory {
    
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
            xmlIndexer = new XmlIndexer ();
        }

        public void processAdd (AddUpdateCommand cmd) throws IOException {  
            Object o = cmd.getSolrInputDocument().getFieldValue(xmlFieldName);
            if (! (o instanceof String)) {
                log.error(xmlFieldName + " is not a string");
            } else {
                String xml = (String) o;
                try {
                    xmlIndexer.index (new StringReader(xml));
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
            // TODO: create additional documents; create a new AddUpdateCommand
            // (or reset and reuse) and pass that to next.processAdd()
        }

        public void processDelete (DeleteUpdateCommand cmd) throws IOException {
            // TODO: delete any related documents we created
            // expand id -> query?
            // expand query -> query?
            if (next != null) {
                next.processDelete(cmd);
            }
        }
    }
}
