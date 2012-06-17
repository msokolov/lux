package lux.solr;

import java.io.IOException;
import java.io.StringReader;
import java.util.Map;

import javax.xml.stream.XMLStreamException;

import lux.index.WhitespaceGapAnalyzer;
import lux.index.XmlIndexer;
import lux.index.field.XmlField;
import lux.index.field.XmlField.Type;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.KeywordAnalyzer;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.Fieldable;
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
        // TODO: check if we are unnecessarily serializing the document
        // We don't need XmlIndexer.STORE_XML to do that since the client passes us the xml_text field
        // but we declare the field to the indexer so that it gets defined in the schema
        xmlIndexer = new XmlIndexer (XmlIndexer.INDEX_QNAMES | XmlIndexer.INDEX_PATHS);
        if (args != null) {
            SolrParams params = SolrParams.toSolrParams(args);
            // accept override from configuration so we can piggyback on existing 
            // document storage
            String xmlFieldName = params.get("xmlFieldName", xmlIndexer.getXmlFieldName());
            XmlField.XML_STORE.setName (xmlFieldName);
            String uriFieldName = params.get("uriFieldName", xmlIndexer.getUriFieldName());
            XmlField.URI.setName (uriFieldName);
            // add fields to config??
            // TODO: namespace-awareness?; namespace mapping
            // TODO: read xpath index config
        }
    }
    

    /** Called when each core is initialized; we ensure that Lux fields are configured.
     */
    public void inform(SolrCore core) {
        IndexSchema schema = core.getSchema();
        // XML_STORE is not included in the indexer's field list; we just use what came in on the request
        informField (XmlField.XML_STORE, schema);
        for (XmlField xmlField : xmlIndexer.getFields()) {
            informField (xmlField, schema);
        }
        // must call this after making changes to the field map:
        schema.refreshAnalyzers();
    }
    
    private void informField (XmlField xmlField, IndexSchema schema) {
        Map<String,SchemaField> fields = schema.getFields();
        Map<String,FieldType> fieldTypes = schema.getFieldTypes();
        if (fields.containsKey(xmlField.getName())) {
            // The Solr schema defines this field
            return;
        }
        // look up the type of this field using the mapping in this class
        FieldType fieldType = getFieldType(xmlField, schema);
        if (! fieldTypes.containsKey(fieldType.getTypeName())) {
            // The Solr schema does not define this field type, so add it
            fieldTypes.put(fieldType.getTypeName(), fieldType);
        } else {
            fieldType = fieldTypes.get(fieldType.getTypeName());
        }
        // Add the field to the schema
        fields.put(xmlField.getName(), new SchemaField (xmlField.getName(), fieldType, xmlField.getSolrFieldProperties(), ""));        
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
        if (xmlField.getType() == Type.TOKENS) {
            return new FieldableField();
        }
        if (analyzer == null || analyzer instanceof KeywordAnalyzer) {
            return new StringField();
        }
        if (analyzer instanceof WhitespaceGapAnalyzer) {
            return new PathField ();
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

        PathField () {
            typeName = "lux_text_ws";
            setAnalyzer(new WhitespaceGapAnalyzer()); 
            setQueryAnalyzer(new WhitespaceGapAnalyzer());
        }
        
        protected Field.Index getFieldIndex(SchemaField field, String internalVal) {
            return Field.Index.ANALYZED;
        }
        
    }
    
    /**
     * enable pass-through of a Fieldable to Solr; this enables analysis to be performed outside of Solr
     */
    class FieldableField extends StrField {
        FieldableField () {
            typeName = "fieldable";
        }

        Fieldable createField(SchemaField field, Object val, float boost) {
            return (Fieldable) val;
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
                String uri = (String) cmd.getSolrInputDocument().getFieldValue(XmlField.URI.getName());
                try {
                    xmlIndexer.read (new StringReader(xml), uri);
                } catch (XMLStreamException e) {
                    log.error ("Failed to parse " + XmlField.XML_STORE, e);
                }
                for (XmlField field : xmlIndexer.getFields()) {
                    if (field == XmlField.URI || field == XmlField.XML_STORE) {
                        // uri and xml are provided externally
                        continue;
                    }
                    Iterable<?> values = field.getValues(xmlIndexer);
                    if (values != null) {
                        for (Object value : values) {
                            cmd.getSolrInputDocument().addField(field.getName(), value);
                        }
                    } else {
                        for (Fieldable value : field.getFieldValues(xmlIndexer)) {
                            cmd.getSolrInputDocument().addField(field.getName(), value);
                        }
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
