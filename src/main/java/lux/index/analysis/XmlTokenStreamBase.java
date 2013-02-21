package lux.index.analysis;

import java.io.IOException;
import java.io.Reader;
import java.util.Iterator;

import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmSequenceIterator;

import org.apache.commons.io.input.CharSequenceReader;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharStream;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;

/**
 * <p>
 * This is the root of a set of xml-aware TokenStream classes that work by selecting text
 * a node at a time (using {@link XmlTokenStreamBase#contentIterator}) from an XML document, and then 
 * passing that text to the wrapped TokenStream.  The wrapped TokenStream is re-used for each text node.
 * The outermost link in the chain will be a TokenFilterthat applies a sequence of structure-related 
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
 * There is an oddity here in that the first link in the chain (the ElementTokenStream above)
 * needs to supply the structural information to the final link (the QNameTokenFilter), since 
 * the QNames are iterated in an *inner* loop - ie for each text token.  This is accomplished 
 * using the 
 * </p>
 * <p>
 * So this class and its descendants serve a dual function: as factories for the analysis
 * chain, and their instances serve as the first links in those chains.  This contrasts with 
 * the usual approach in Lucene, where an Analyzer serves to construct a TokenStream, but
 * is necessary since Analyzers work on character-based Readers, and here we need a more abstract
 * notion of Analysis that can encompass structured documents.
 * </p>
 */
/*
 * TODO: refactor so as to make this more intelligible / similar to existing Lucene code
 * 1) Separate factory from TokenStream - create a StructureAnalyzer or something of that sort
 *    this should accept an Analyzer, and use it to create a TokenStream based on a:
 * 2) NodesReader, say, which reads the text of a sequence of nodes?  Note: we also have
 * a special CharFilter that applies Offsets gleaned from XML parsing.  And there is CharSequenceStream,
 * which extends CharStream wrapping a CharSequence.
 */
/*
 * FIXME first!  I think we can straighten this out considerably (and
 * expose the ability to provide a custom analysis chain), by: 
 * (1) accepting an Analyzer in the constructor
 * (2) Use the analyzer to acquire a new (or reused) TokenStream in resetTokenizer()
 * (3) merge TextOffsetTokenStream with this class???  Or merge most of it and leave separate
 * just to model the offsets behavior.
 */
abstract class XmlTokenStreamBase extends TokenStream {

    private final String fieldName;
    // The analyzer creates the wrapped TokenStream/Tokenizer that does the text analysis
    private final Analyzer analyzer;
    private TokenStream wrapped;
    protected XdmNode curNode;
    protected Iterator<XdmNode> contentIter; // retrieves the nodes with text to index
    protected CharTermAttribute termAtt;
    protected CharStream charStream = new OffsetCharFilter(null);
    protected static final XdmSequenceIterator EMPTY = new EmptyXdmIterator(null);

    XmlTokenStreamBase(String fieldName, Analyzer analyzer) {
        this.fieldName = fieldName;
        this.analyzer = analyzer;
        // initialize the wrapped token stream with an empty stream so it can be further 
        // wrapped and configured by subclass's constructors.
        try {
            reset ( new CharSequenceReader (""));
        } catch (IOException e) { }
        termAtt = wrapped.addAttribute(CharTermAttribute.class);
        //tokenizer = new StandardTokenizer(IndexConfiguration.LUCENE_VERSION, this, new CharSequenceReader(""));
    }
    
    public void reset (Reader reader) throws IOException {
        wrapped = analyzer.reusableTokenStream (fieldName, reader);
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
}

/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/.
 */
