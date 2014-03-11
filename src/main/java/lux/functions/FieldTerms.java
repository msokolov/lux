package lux.functions;

import lux.index.IndexConfiguration;
import lux.index.field.XmlTextField;
import lux.xpath.FunCall;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.lib.ExtensionFunctionCall;
import net.sf.saxon.lib.ExtensionFunctionDefinition;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.SequenceType;

/**
 * <code>function lux:field-terms($field-name as xs:string?, $start as xs:string?) as xs:anyAtomicItem*</code>
 * <p>
 * This function accepts the name of a Lucene field, and a starting value, and
 * returns the sequence of terms drawn from the field, ordered according to its
 * natural order, starting with the first term that is >= the starting value.
 * </p>
 * <p>
 * If the $field-name argument is empty, the terms are drawn from the default
 * field defined by the {@link IndexConfiguration}, generally the
 * {@link XmlTextField}.
 * </p>
 * <p>
 * Note that only string- or text-valued fields are handled properly. FieldTerms is not schema-aware, which means that  it returns the encoded form of a field.  For string and text 
 * fields, that is the same as the input form, but for fields that are encoded when indexed (like dates or numbers), the results
 * here will not be usable in any straightforward way.  This behavior should not be relied upon, though: we plan to 
 * add schema-based decoding.
 * </p>
 */
public class FieldTerms extends ExtensionFunctionDefinition {

    @Override
    public StructuredQName getFunctionQName() {
        return new StructuredQName("lux", FunCall.LUX_NAMESPACE, "field-terms");
    }

    @Override
    public SequenceType[] getArgumentTypes() {
        return new SequenceType[] { SequenceType.OPTIONAL_STRING, SequenceType.OPTIONAL_STRING };
    }

    @Override
    public int getMinimumNumberOfArguments() {
        return 0;
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
    public SequenceType getResultType(SequenceType[] suppliedArgumentTypes) {
        return SequenceType.ATOMIC_SEQUENCE;
    }

    @Override
    public ExtensionFunctionCall makeCallExpression() {
        return new FieldTermsCall();
    }

    class FieldTermsCall extends ExtensionFunctionCall {

        @Override
        public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
            String fieldName = null, start = "";
            if (arguments.length > 0) {
                Item arg0 = arguments[0].head();
                if (arg0 != null) {
                	fieldName = arg0.getStringValue();
                }
                if (arguments.length > 1) {
                    Item arg1 = arguments[1].head();
                    start = arg1 == null ? "" : arg1.getStringValue();
                }
            }
            return SearchBase.getSearchService(context).terms(fieldName, start);
        }

    }

}

/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/.
 */
