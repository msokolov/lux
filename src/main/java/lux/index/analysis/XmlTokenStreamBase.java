package lux.index.analysis;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;

import lux.index.IndexConfiguration;
import lux.index.XmlIndexer;
import lux.index.attribute.QNameAttribute;

import net.sf.saxon.om.NamePool;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.s9api.Processor;
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
    protected HashMap<Integer,ElementVisibility> eltVis;
    protected final QNameAttribute qnameAtt;
    protected final QNameTokenFilter qnameTokenFilter;
    // protected EmptyTokenStream empty;
    protected static final XdmSequenceIterator EMPTY = new EmptyXdmIterator(null);

    XmlTokenStreamBase(String fieldName, Analyzer analyzer, TokenStream wrapped, Processor processor) {
        super (wrapped);
        this.wrapped = wrapped;
        this.fieldName = fieldName;
        this.analyzer = analyzer;
        termAtt = addAttribute(CharTermAttribute.class);
        // empty = new EmptyTokenStream(wrapped);
        eltVis = new HashMap<Integer, ElementVisibility>();
        // FIXME - don't use QNameTokenFilter for this -- that handles prefixing tokens
        // use instead an XmlVisibilityFilter that encapsulatres the logic currently in ElementTokenStream
        if (wrapped instanceof QNameTokenFilter) {
            qnameTokenFilter = (QNameTokenFilter) wrapped;
            defVis = qnameTokenFilter.getDefaultVisibility();
            NamePool namePool = processor.getUnderlyingConfiguration().getNamePool();
            for (Entry<String, ElementVisibility> entry : qnameTokenFilter.getElementVisibility().entrySet()) {
                int namecode = namePool.allocateClarkName(entry.getKey());
                eltVis.put(namecode, entry.getValue());
            }
        } else { 
            defVis = ElementVisibility.OPAQUE;
            qnameTokenFilter = new QNameTokenFilter (getWrappedTokenStream());
        }
        qnameAtt = qnameTokenFilter.addAttribute(QNameAttribute.class);
    }
    
    @Override
    public void reset () throws IOException{
        reset (charStream);
        wrapped.reset();
    }
    
    @Override
    public void close () throws IOException {
        wrapped.close();
    }
    
    public void reset (Reader reader) throws IOException {
        close();
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
            if (! updateNodeAtts ()) {
                continue;
            }
            if (resetTokenizer(nodeInfo.getStringValueCS())) {
                return true;
            }
        }
        return false;
    }

    abstract boolean resetTokenizer(CharSequence cs);

    /** @return false if the node is hidden */
    abstract boolean updateNodeAtts ();

    /**
     * @param clarkName the name of an element as a clarkName ({namespace}name)
     * @return the explicitly-specified visibility of the element name, or null if the element has the default
     * visibility.
     */
    public ElementVisibility getElementVisibility(String clarkName) {
        return eltVis.get(clarkName);
    }

    /**
     * @param namecode the name of an element as a namecode from a {@link net.sf.saxon.om.NamePool}
     * @param visibility the explicitly-specified visibility of the element name, or null to give the element the default
     * visibility.
     */
    public void setElementVisibility(int namecode, ElementVisibility visibility) {
        if (visibility == null) {
            eltVis.remove(namecode);
        } else {
            eltVis.put(namecode, visibility);
        }
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

    public void configureElementVisibility(XmlIndexer indexer) {
        IndexConfiguration config = indexer.getConfiguration();
        if (qnameTokenFilter != null) {
            qnameTokenFilter.setNamespaceAware(config.isOption(IndexConfiguration.NAMESPACE_AWARE));
        }
        NamePool namePool = indexer.getProcessor().getUnderlyingConfiguration().getNamePool();
        if (defVis == null) {
            defVis = config.getDefaultVisibility();
        }
        for (Entry<String, ElementVisibility> e : config.getVisibilityMap().entrySet()) {
            int namecode = namePool.allocateClarkName(e.getKey());
            if (! eltVis.containsKey(namecode)) {
                eltVis.put(namecode, e.getValue());
            }
        }
    }
}

/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/.
 */
