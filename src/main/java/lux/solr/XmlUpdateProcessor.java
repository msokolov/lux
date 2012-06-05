/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */

package lux.solr;

import java.io.IOException;
import java.io.StringReader;
import java.util.Map;

import javax.xml.stream.XMLStreamException;

import lux.index.WhitespaceGapAnalyzer;
import lux.index.XmlField;
import lux.index.XmlIndexer;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.KeywordAnalyzer;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Store;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrException.ErrorCode;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.core.SolrCore;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.schema.FieldType;
import org.apache.solr.schema.IndexSchema;
import org.apache.solr.schema.SchemaField;
import org.apache.solr.schema.StrField;
import org.apache.solr.schema.TextField;
import org.apache.solr.update.AddUpdateCommand;
import org.apache.solr.update.DeleteUpdateCommand;
import org.apache.solr.update.processor.UpdateRequestProcessor;
import org.apache.solr.update.processor.UpdateRequestProcessorFactory;
import org.apache.solr.util.plugin.SolrCoreAware;

public class XmlUpdateProcessor extends UpdateRequestProcessorFactory implements SolrCoreAware {

    private XmlIndexer xmlIndexer;
    
    @Override
    public void init(@SuppressWarnings("rawtypes") final NamedList args) {
        // don't need XmlIndexer.STORE_XML since the client passes us the xml_text field
        xmlIndexer = new XmlIndexer (XmlIndexer.INDEX_QNAMES | XmlIndexer.INDEX_PATHS | XmlIndexer.STORE_XML);
        if (args != null) {
            SolrParams params = SolrParams.toSolrParams(args);
            // accept override from configuration so we can piggyback on existing 
            // document storage
            String xmlFieldName = params.get("xmlFieldName", xmlIndexer.getXmlFieldName());
            XmlField.XML_STORE.setName (xmlFieldName);
            // add fields to config??
            // TODO: namespace-awareness?; namespace mapping
            // TODO: read xpath index config
        }
    }
    

    /** Called when each core is initialized; we ensure that lux fields are configured.
     * TODO: drive this from {@link XmlIndexer#getFields()}
     * also: xml_text
     *  */
    public void inform(SolrCore core) {
        IndexSchema schema = core.getSchema();
        Map<String,SchemaField> fields = schema.getFields();
        Map<String,FieldType> fieldTypes = schema.getFieldTypes();
        for (XmlField xmlField : xmlIndexer.getFields()) {
            if (fields.containsKey(xmlField.getName())) {
                // Allow overriding field configuration in schema.xml?
                continue;
            }
            FieldType fieldType = getFieldType(xmlField, schema);
            if (! fieldTypes.containsKey(fieldType.getTypeName())) {
                fieldTypes.put(fieldType.getTypeName(), fieldType);
            } else {
                fieldType = fieldTypes.get(fieldType.getTypeName());
            }
            fields.put(xmlField.getName(), new SchemaField (xmlField.getName(), fieldType, xmlField.getSolrFieldProperties(), ""));
        }
        // must call this after making changes to the field map:
        schema.refreshAnalyzers();
    }
    
    private FieldType getFieldType(XmlField xmlField, IndexSchema schema) {
        // FIXME - we should store a field type name in XmlField and just look that up instead
        // of trying to infer from the analyzer
        Analyzer analyzer = xmlField.getAnalyzer();
        if (analyzer == null) {
            if (! (xmlField.isStored() == Store.YES)) {
                throw new SolrException(ErrorCode.BAD_REQUEST, "invalid xml field: " + xmlField.getName() + "; no analyzer and not stored");
            }
            return new StoredStringField ();
        }
        if (analyzer instanceof KeywordAnalyzer) {
            return new StringField();
        }
        if (analyzer instanceof WhitespaceGapAnalyzer) {
            return new PathField (schema);
        }
        throw new SolrException(ErrorCode.BAD_REQUEST, "invalid xml field: " + xmlField.getName() + "; unknown analyzer type: " + analyzer);
    }
    
    class StoredStringField extends StrField {
        StoredStringField () {
            typeName = "lux_stored_string";
        }
    }
    
    class StringField extends StrField {        
        StringField () {
            typeName = "string";
        }
    }
    
    class PathField extends TextField {

        PathField (IndexSchema schema) {
            typeName = "lux_text_ws";
            setAnalyzer(new WhitespaceGapAnalyzer()); 
            setQueryAnalyzer(new WhitespaceGapAnalyzer());
        }
        
        protected Field.Index getFieldIndex(SchemaField field, String internalVal) {
            return Field.Index.ANALYZED;
        }
        
    }

    @Override
    public UpdateRequestProcessor getInstance(SolrQueryRequest req, SolrQueryResponse rsp, UpdateRequestProcessor next) {
        return new LuxProcessorInstance (next);
    }
    
    public class LuxProcessorInstance extends UpdateRequestProcessor {

        public LuxProcessorInstance (UpdateRequestProcessor next) {
            super(next);
        }

        public void processAdd (AddUpdateCommand cmd) throws IOException {
            Object o = cmd.getSolrInputDocument().getFieldValue(XmlField.XML_STORE.getName());
            if (o != null) {
                String xml = (String) o;
                try {
                    xmlIndexer.read (new StringReader(xml));
                } catch (XMLStreamException e) {
                    log.error ("Failed to parse " + XmlField.XML_STORE, e);
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
