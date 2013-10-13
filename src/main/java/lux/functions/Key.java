package lux.functions;

import java.util.Collection;

import lux.Evaluator;
import lux.index.field.FieldDefinition;
import lux.index.field.XPathField;
import lux.xpath.FunCall;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.lib.ExtensionFunctionCall;
import net.sf.saxon.lib.ExtensionFunctionDefinition;
import net.sf.saxon.om.AtomicArray;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.EmptySequence;
import net.sf.saxon.value.Int64Value;
import net.sf.saxon.value.SequenceType;
import net.sf.saxon.value.StringValue;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.IndexableField;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.schema.SchemaField;
import org.slf4j.LoggerFactory;

/**
* <code>function lux:key($field-name as xs:string, $node as node()) as xs:anyAtomicItem*</code>
* <code>function lux:key($field-name as xs:string) as xs:anyAtomicItem*</code>
* 
* <p>Accepts the name of a lucene field and optionally, a node, and returns
* any stored value(s) of the field for the document containing
* the node, or the context item if no node is specified.  Analogous to the XSLT key() function.
* </p>
* 
* <p>
* If the node (or context item) is not a node drawn from the index, lux:key will return the
* empty sequence.
* </p>
* 
* <p>
* Order by expressions containing lux:key calls are subject to special optimization and are often able to be
* implemented by index-optimized sorting in Lucene (for fields whose values are string-, integer-, or long-valued only).  
* An error results if an attempt is made
* to sort by a field that has multiple values for any of the documents in the sequence.
* </p>
*/
public class Key extends ExtensionFunctionDefinition {

    @Override
    public StructuredQName getFunctionQName() {
        return new StructuredQName ("lux", FunCall.LUX_NAMESPACE, "key");
    }

    @Override
    public SequenceType[] getArgumentTypes() {
        return new SequenceType[] {
                SequenceType.SINGLE_STRING,
                SequenceType.OPTIONAL_NODE
        };
    }
    
    @Override
    public int getMinimumNumberOfArguments() {
        return 1;
    }

    @Override
    public int getMaximumNumberOfArguments() {
        return 2;
    }
    
    @Override
    public boolean trustResultType() {
        return true;
    }
    
    @Override
    public boolean dependsOnFocus () {
        return true;
    }
    
    @Override
    public net.sf.saxon.value.SequenceType getResultType(net.sf.saxon.value.SequenceType[] suppliedArgumentTypes) {
        return SequenceType.ATOMIC_SEQUENCE;
    }

    @Override
    public ExtensionFunctionCall makeCallExpression() {
        return new KeyCall();
    }
    
    class KeyCall extends ExtensionFunctionCall {

        @Override
        public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
            String fieldName = arguments[0].head().getStringValue();
            NodeInfo node;
            if (arguments.length == 1) {
                Item contextItem = context.getContextItem();
                if (! (contextItem instanceof NodeInfo)) {
                    throw new XPathException ("Call to lux:key($field-name) depends on context, but there is no context defined");
                }
                node = (NodeInfo) contextItem;
            } else {
                node = (NodeInfo) arguments[1].head();
            }
            if (node == null) {
                return EmptySequence.getInstance();
            }
            Evaluator eval = SearchBase.getEvaluator(context);
            Document doc = (Document) node.getDocumentRoot().getUserData(Document.class.getName());
            FieldDefinition field = eval.getCompiler().getIndexConfiguration().getField(fieldName);
            if (field == null) {
                LoggerFactory.getLogger(Key.class).warn("Attempt to retrieve values of non-existent field: {}", fieldName);
            }
            else if (field.isStored() == Field.Store.NO) {
                LoggerFactory.getLogger(Key.class).warn("Attempt to retrieve values of non-stored field: {}", fieldName);
            }
            if (doc != null) {
                return getFieldValue (doc, eval, fieldName, field);
            } else {
                SolrDocument solrDoc = (SolrDocument) node.getDocumentRoot().getUserData(SolrDocument.class.getName());
                if (solrDoc != null) {
                    return getFieldValue (solrDoc, eval, fieldName, field);
                }
            }
            return EmptySequence.getInstance();
        }
        
        private Sequence getFieldValue (Document doc, Evaluator eval, String fieldName, FieldDefinition field) throws XPathException {
            // TODO refactor the repeated code here
            if (field == null || field.getType() == FieldDefinition.Type.STRING || field.getType() == FieldDefinition.Type.TEXT) {
                Object[] values = doc.getValues(fieldName);
                StringValue[] valueItems = new StringValue[values.length];
                for (int i = 0; i < values.length; i++) {
                    valueItems[i] = new StringValue (values[i].toString());
                }
                return new AtomicArray(valueItems);
            }
            if (field.getType() == FieldDefinition.Type.INT || field.getType() == FieldDefinition.Type.LONG) {
                IndexableField [] fieldValues = doc.getFields(fieldName);
                Int64Value[] valueItems = new Int64Value[fieldValues.length];
                for (int i = 0; i < fieldValues.length; i++) {
                    valueItems[i] = Int64Value.makeIntegerValue(fieldValues[i].numericValue().longValue());
                }
                return new AtomicArray(valueItems);
            }
            // TODO: convert Solr dates to xs:dateTime?  but the user can manage that, perhaps, for now
            if (field.getType() == FieldDefinition.Type.SOLR_FIELD) {
                SchemaField schemaField = ((XPathField)field).getSchemaField();
                IndexableField [] fieldValues = doc.getFields(fieldName);
                StringValue[] valueItems = new StringValue[fieldValues.length];
                for (int i = 0; i < fieldValues.length; i++) {
                    valueItems[i] = StringValue.makeStringValue(schemaField.getType().toExternal(fieldValues[i]));
                }
                return new AtomicArray(valueItems);
            }
            return EmptySequence.getInstance();
        }
        
        private Sequence getFieldValue (SolrDocument doc, Evaluator eval, String fieldName, FieldDefinition field) throws XPathException {
            Collection<?> valuesCollection = doc.getFieldValues(fieldName);
            if (valuesCollection == null) {
                return EmptySequence.getInstance();
            }
            Object[] values = valuesCollection.toArray();
            if (field == null || field.getType() == FieldDefinition.Type.STRING || field.getType() == FieldDefinition.Type.TEXT) {
                StringValue[] valueItems = new StringValue[values.length];
                for (int i = 0; i < values.length; i++) {
                    valueItems[i] = new StringValue (values[i].toString());
                }
                return new AtomicArray(valueItems);
            }
            if (field.getType() == FieldDefinition.Type.INT || field.getType() == FieldDefinition.Type.LONG) {
                Int64Value[] valueItems = new Int64Value[values.length];
                for (int i = 0; i < values.length; i++) {
                    valueItems[i] = Int64Value.makeIntegerValue(((Number)values[i]).longValue());
                }
                return new AtomicArray(valueItems);
            }
            return EmptySequence.getInstance();
        }
        
    }
}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
