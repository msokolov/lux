package lux.functions;

import java.io.IOException;
import java.util.Collections;

import lux.Evaluator;
import lux.index.field.FieldDefinition;
import lux.xpath.FunCall;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.lib.ExtensionFunctionCall;
import net.sf.saxon.lib.ExtensionFunctionDefinition;
import net.sf.saxon.om.AtomicArray;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.ArrayIterator;
import net.sf.saxon.value.EmptySequence;
import net.sf.saxon.value.Int64Value;
import net.sf.saxon.value.SequenceType;
import net.sf.saxon.value.StringValue;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.IndexableField;
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

        @SuppressWarnings({ "rawtypes" })
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
            long docID = node.getDocumentNumber();
            Evaluator eval = SearchBase.getEvaluator(context);
            FieldDefinition field = eval.getCompiler().getIndexConfiguration().getField(fieldName);
            if (field == null) {
                LoggerFactory.getLogger(Key.class).warn("Attempt to retrieve values of non-existent field: {}", fieldName);
            }
            else if (field.isStored() == Field.Store.NO) {
                LoggerFactory.getLogger(Key.class).warn("Attempt to retrieve values of non-stored field: {}", fieldName);
            }
            Document doc ;
            try {
                doc = eval.getSearcher().doc((int) docID, Collections.singleton(fieldName));
            }  catch (IOException e) {
                throw new XPathException(e);
            }
            if (field == null || field.getType() == FieldDefinition.Type.STRING) {
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
            return EmptySequence.getInstance();
        }

    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
