package lux.functions;

import java.io.IOException;

import lux.Evaluator;
import lux.xpath.FunCall;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.lib.ExtensionFunctionCall;
import net.sf.saxon.lib.ExtensionFunctionDefinition;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.ArrayIterator;
import net.sf.saxon.value.SequenceType;
import net.sf.saxon.value.StringValue;

import org.apache.lucene.document.Document;

/**
* <code>function lux:field-values($field-name as xs:string, $node as node()) as xs:anyAtomicItem*</code>
* 
* <p>Accepts the name of a lucene field and optionally, a node, and returns
* any stored value(s) of the field for the document containing
* the node, or the context item if no node is specified.</p>
* 
* <p>
* If the node (or context item) is not a node drawn from the index, lux:field will return the
* empty sequence.
* </p>
* 
* <p>
* Order by expressions containing lux:field-values calls are subject to special optimization and are often able to be
* implemented by index-optimized sorting in Lucene.  An error results if an attempt is made
* to sort by a field that has multiple values for any of the documents in the sequence.
* </p>
*/
public class FieldValues extends ExtensionFunctionDefinition {

    @Override
    public StructuredQName getFunctionQName() {
        return new StructuredQName ("lux", FunCall.LUX_NAMESPACE, "field-values");
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
        return new FieldValuesCall();
    }
    
    class FieldValuesCall extends ExtensionFunctionCall {

        @SuppressWarnings({ "rawtypes" })
        @Override
        public SequenceIterator<? extends Item> call(SequenceIterator<? extends Item>[] arguments, XPathContext context)
                throws XPathException {
            String fieldName = arguments[0].next().getStringValue();
            NodeInfo node;
            if (arguments.length == 1) {
                Item contextItem = context.getContextItem();
                if (! (contextItem instanceof NodeInfo)) {
                    throw new XPathException ("Call to lux:field-values($field-name) depends on context, but there is no context defined");
                }
                node = (NodeInfo) contextItem;
            } else {
                node = (NodeInfo) arguments[1].next();
            }
            long docID = node.getDocumentNumber();
            Evaluator eval = (Evaluator) context.getConfiguration().getCollectionURIResolver();
            Document doc ;
            try {
                doc = eval.getSearcher().doc((int) docID);
            }  catch (IOException e) {
                throw new XPathException(e);
            }
            String [] values = doc.getValues(fieldName);
            StringValue[] valueItems = new StringValue[values.length];
            for (int i = 0; i < values.length; i++) {
                valueItems[i] = new StringValue (values[i]);
            }
            return new ArrayIterator<StringValue>(valueItems);
        }
        
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
