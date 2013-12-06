package lux.index.analysis;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Iterator;

import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmSequenceIterator;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;

/**
 * <p>
 * This is the root of a set of xml-aware TokenStream classes that work by selecting text
 * a node at a time from an XML document, and then 
 * passing that text to the wrapped TokenStream.  The wrapped TokenStream is re-used for each text node.
 * The outermost link in the chain will be a TokenFilter that applies a sequence of structure-related 
 * Attributes to each text token (ie a list of QNames, but can be any kind of structural attribute
 * that should be composed with each text token).
 * <p>
 * The token stream topology is: this( this.wrapped (this.tokenizer ))
 * For example, for the element-text field we have ElementTokenStream (a subclass of this class):
 * </p>
 * <blockquote>
 * <code>ElementTokenStream (QNameTokenFilter (LowerCaseFilter (StandardTokenizer)))</code>
 * </blockquote>
 * <p>
 * We can't follow the standard Lucene pattern of Analyzer as a factory for a TokenStream
 * since we want to be able to extend any arbitrary textual Analyzer, but the constraints 
 * of the Analyzer class design prevent it from being extended in a straightforward manner.
 * Thus we have essentially an outer (XML) stream wrapping an inner (Text) stream.
 * </p>
 * 
 * FIXME: make the constructor protected; allow construction only through static builders
 * defined on each derived class.  This will enable us to hide the complexity of wrapping the
 * token stream, which is the same pattern for each of these; only the classes vary.  But we 
 * can't do the work in the constructor due to Java structural issues.
 */
public abstract class XmlTokenStreamBase extends TokenStream {

    private final String fieldName;
    // The analyzer creates the wrapped TokenStream/Tokenizer that does the text analysis
    private final Analyzer analyzer;
    private TokenStream wrapped;
    protected XdmNode curNode;
    protected Iterator<XdmNode> contentIter; // retrieves the nodes with text to index
    protected CharTermAttribute termAtt;
    protected Reader charStream = new OffsetCharFilter(new StringReader(""));
    protected ElementVisibility defVis;
    protected HashMap<QName,ElementVisibility> eltVis;
    protected EmptyTokenStream empty;
    protected static final XdmSequenceIterator EMPTY = new EmptyXdmIterator(null);

    XmlTokenStreamBase(String fieldName, Analyzer analyzer, TokenStream wrapped) {
        super (wrapped);
        this.wrapped = wrapped;
        this.fieldName = fieldName;
        this.analyzer = analyzer;
        termAtt = addAttribute(CharTermAttribute.class);
        empty = new EmptyTokenStream(wrapped);
        defVis = ElementVisibility.OPAQUE;
        eltVis = new HashMap<QName, ElementVisibility>();
    }
    
    @Override
    public void reset () throws IOException{
        reset (charStream);
        wrapped.reset();
    }
    
    public void reset (Reader reader) throws IOException {
        TokenStream reset = analyzer.tokenStream (fieldName, reader);
        // This must be the same token stream: ie the Analyzer must be re-usable, and the 
        // original token stream must have arisen from it.  We don't check for actual
        // identity with wrapped since that might get wrapped again (eg w/QNameTokenFilter).
        assert (reset.getAttribute(CharTermAttribute.class) == wrapped.getAttribute(CharTermAttribute.class));
    }

    /*
     * Advance the iteration by looping through the following:
     * 1) next text node
     * 2) next token in text
     * 3) next ancestor element node
     * @see org.apache.lucene.analysis.TokenStream#incrementToken()
     */
    @Override
    public boolean incrementToken() throws IOException {
        if (!incrementWrappedTokenStream()) {            // next token in current node
            if (!advanceToTokenNode()) {        // next node with a token
                return false;
            }
        }
        return true;
    }
    
    /**
     * @return the underlying stream of text tokens to which additional xml-related attributes are added by this.
     */
    public TokenStream getWrappedTokenStream () {
        return wrapped;
    }
    
    protected void setWrappedTokenStream (TokenStream wrapped) {
        this.wrapped = wrapped;
    }
    
    protected boolean incrementWrappedTokenStream() throws IOException {
        while (wrapped.incrementToken()) {
            if (termAtt.length() > 0) {
                return true;
            }
        }
        return false;
    }

    private boolean advanceToTokenNode() {
        while (contentIter.hasNext()) {
            curNode = (XdmNode) contentIter.next();            
            // wrap the content in a reader and hand it to the tokenizer
            NodeInfo nodeInfo = curNode.getUnderlyingNode();
            updateNodeAtts ();
            if (resetTokenizer(nodeInfo.getStringValueCS())) {
                return true;
            }
        }
        return false;
    }

    abstract boolean resetTokenizer(CharSequence cs);

    abstract void updateNodeAtts ();

    /**
     * @param qname the name of an element as an s9api QName
     * @return the explicitly-specified visibility of the element name, or null if the element has the default
     * visibility.
     */
    public ElementVisibility getElementVisibility(QName qname) {
        return eltVis.get(qname);
    }

    /** sets the visibility of elements with the given name
     * @param qname the name of an element as an s9api QName
     * @param vis the visibility of the element's content from the perspective of containing elements.
     * visibility.
     */
    public void setElementVisibility(QName qname, ElementVisibility vis) {
        eltVis.put(qname, vis);
    }

    /** @return the visibility of elements not explicitly specified using setElementVisibility.
     * Always {@link ElementVisibility#OPAQUE}.
     */
    public ElementVisibility getDefaultVisibility() {
        return defVis;
    }

    public void setDefaultVisibility(ElementVisibility vis) {
        this.defVis = vis;
    }
}

/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/.
 */
