package lux.index.analysis;

import lux.index.XmlIndexer;
import lux.index.attribute.QNameAttribute;
import net.sf.saxon.expr.parser.Token;
import net.sf.saxon.pattern.CombinedNodeTest;
import net.sf.saxon.pattern.NodeKindTest;
import net.sf.saxon.s9api.Axis;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmNodeKind;
import net.sf.saxon.s9api.XdmSequenceIterator;

import org.apache.lucene.analysis.LowerCaseFilter;

/**
 * A TokenStream that extracts text from a Saxon Document model (XdmNode) and generates
 * a token for every "word" for every element that contains it.
 * TODO: control over element transparency
 */
public final class ElementTokenStream extends TextOffsetTokenStream {
    
    private final QNameAttribute qnameAtt = addAttribute(QNameAttribute.class);

    public ElementTokenStream(XdmNode doc, Offsets offsets) {
        super(doc, offsets);
        wrapped = new QNameTokenFilter(new LowerCaseFilter(XmlIndexer.LUCENE_VERSION, tokenizer));
        contentIter = new ContentIterator(doc);
    }
    
    protected void updateNodeAtts () {
        getAncestorQNames();        
    }
    
    private void getAncestorQNames() {
        AncestorIterator nodeAncestors = new AncestorIterator(curNode);
        qnameAtt.clearQNames();
        while (nodeAncestors.hasNext()) {
            XdmNode e = (XdmNode) nodeAncestors.next();
            if (e.getNodeKind() == XdmNodeKind.ELEMENT) {
                QName qname = e.getNodeName();
                qnameAtt.addQName(new lux.xpath.QName(qname.getNamespaceURI(),  qname.getLocalName()));
                // if (! isTransparent (e))
                // return;
            }
        }
    }
    
    /**
     * iterates over ancestor-or-self::node()[self::text() or self::element() or self::attribute()]
     * @author sokolov
     *
     */
    private static class AncestorIterator extends XdmSequenceIterator {

        protected AncestorIterator(XdmNode node) {
            super(node.getUnderlyingNode().iterateAxis(Axis.ANCESTOR_OR_SELF.getAxisNumber(), 
                    new CombinedNodeTest (NodeKindTest.TEXT, Token.UNION,
                            new CombinedNodeTest (NodeKindTest.ELEMENT, Token.UNION, NodeKindTest.ATTRIBUTE))));
        }
        
    }
    
}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
