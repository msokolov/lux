package lux.search.highlight;

import java.io.IOException;
import java.io.Reader;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import lux.exception.LuxException;
import lux.index.FieldName;
import lux.index.IndexConfiguration;
import lux.index.analysis.DefaultAnalyzer;
import lux.index.analysis.XmlTextTokenStream;
import lux.xml.QName;
import lux.xml.SaxonDocBuilder;
import lux.xml.XmlReader;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.tree.tiny.TinyDocumentImpl;

import org.apache.commons.io.input.CharSequenceReader;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.highlight.QueryScorer;
import org.apache.lucene.search.highlight.TextFragment;

public class XmlHighlighter extends SaxonDocBuilder {

    private final HighlightFormatter highlighter;
    private QueryScorer scorer;
    private final XmlStreamTextReader textReader;
    private XMLStreamReader xmlStreamReader;
    private StreamingElementTokens xmlStreamTokens;
    private TokenStream scorerTokens;
    private OffsetAttribute offsetAtt;
    private TokenGroup tokenGroup;
    private int startOffset;
    private int endOffset;
    private int lastEndOffset = 0;
    private int maxDocCharsToAnalyze = Integer.MAX_VALUE;
    private String textFieldName;
    private Analyzer analyzer;
    
    public XmlHighlighter(Processor processor, IndexConfiguration indexConfig, HighlightFormatter highlighter) {
        super(processor);
        textFieldName = indexConfig.getFieldName(FieldName.XML_TEXT);
        analyzer = indexConfig.getFieldAnalyzers();
        this.highlighter = highlighter;
        textReader = new XmlStreamTextReader();
        try {
            // in order to handle highlighting element-text query terms, we need to
            // arrange for element-text tokens to appear in this stream.
            // The other place we do that is in ElementTokenStream, but that isn't
            // really usable in a simple way in this context
            // What do instead is to create yet another TokenStream class
            // StreamingElementTokens, which wraps xmlStreamToken
            xmlStreamTokens = new StreamingElementTokens(analyzer.tokenStream(textFieldName, textReader));
            offsetAtt = xmlStreamTokens.addAttribute(OffsetAttribute.class);
            xmlStreamTokens.addAttribute(PositionIncrementAttribute.class);
        } catch (IOException e) {
            throw new LuxException(e);
        }
        tokenGroup = new TokenGroup(xmlStreamTokens);
    }
    
    public XdmNode highlight (Query query, NodeInfo node) throws XMLStreamException, SaxonApiException {
        if (needsPositions(query)) {
            // A partial workaround for highlighting element text queries with phrases
            query = replaceFields (query, textFieldName);
        }
        scorer = new QueryScorer(query);
        // grab all the text at once so Lucene's lame-ass highlighter can figure out if there are any
        // phrases in it...
        // TODO: is this the Analyzer we're looking for???  OR ... reimplement using different HL
        Analyzer defaultAnalyzer = new DefaultAnalyzer();
        TokenStream textTokens = null;
        try {
            textTokens = defaultAnalyzer.tokenStream("xml_text", new CharSequenceReader(""));
        } catch (IOException e) { }
        init(new XmlTextTokenStream("xml_text", defaultAnalyzer, textTokens, new XdmNode (node), null));
        XmlReader xmlReader = new XmlReader ();
        xmlReader.addHandler(this);
        xmlReader.read(node);
        // setBaseURI (URI.create(node.getBaseURI()));
        if (getDocument().getUnderlyingNode() instanceof TinyDocumentImpl) {
            ((TinyDocumentImpl)getDocument().getUnderlyingNode()).setBaseURI(node.getSystemId());
        }
        return getDocument();
    }
    
    private Query replaceFields(Query query, String fieldName) {
        if (query instanceof PhraseQuery) {
            PhraseQuery pq = new PhraseQuery();
            for (Term t : ((PhraseQuery)query).getTerms()) {
                if (t.field().equals(fieldName)) {
                    return query;
                }
                pq.add (replaceField(fieldName, t));
            }
            return pq;
        }
        if (query instanceof BooleanQuery) {
            for (BooleanClause clause : ((BooleanQuery)query).getClauses()) {
                clause.setQuery(replaceFields (clause.getQuery(), fieldName));
            }
            return query;
        }
        if (query instanceof TermQuery) {
            TermQuery tq = (TermQuery)query;
            if (! tq.getTerm().field().equals(fieldName)) {
                return new TermQuery (new Term (fieldName, tq.getTerm().text().split(":")[1]));
            }
        }
        // MultiTermQuery ?
        return query;
    }

    private Term replaceField(String fieldName, Term t) {
        String[] parts =  t.text().split(":");
        if (parts.length > 1) {
            return new Term (fieldName,parts[1]);
        } else {
            return new Term (fieldName, t.text()); // just in case?
        }
    }

    private boolean needsPositions(Query query) {
        if ((query instanceof PhraseQuery)) {
            return true;
        }
        if (query instanceof BooleanQuery) {
            for (BooleanClause clause : ((BooleanQuery)query).getClauses()) {
                if (needsPositions (clause.getQuery())) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void reset () {
        super.reset();
    }

    private void init (TokenStream tokenStream) {
        try {
            tokenStream.reset();
            scorer.setMaxDocCharsToAnalyze(maxDocCharsToAnalyze);
            scorerTokens = scorer.init(tokenStream);
            if (scorerTokens == null) {
                // The scorer didn't consume any tokens (it does that for PhraseQuery),
                // in which case we must give it the live token stream
                scorer.init(xmlStreamTokens);
            }
            // we score the entire document as a single fragment
            scorer.startFragment(new TextFragment("", 0, 0));
        } catch (IOException e) {
            throw new LuxException (e);
        }
    }
    
    @Override
    public void handleEvent(XMLStreamReader reader, int eventType) throws XMLStreamException {
        
        switch (eventType) {
        case XMLStreamConstants.START_DOCUMENT:
            xmlStreamReader = reader;   // cache the reader so we can pull events
            super.handleEvent(reader, eventType);
            break;
            
        case XMLStreamConstants.START_ELEMENT:
            super.handleEvent(reader, eventType);
            xmlStreamTokens.pushElement(new QName(reader.getNamespaceURI(), reader.getLocalName(), reader.getPrefix()));
            break;
            
        case XMLStreamConstants.END_ELEMENT:
            super.handleEvent(reader, eventType);
            xmlStreamTokens.popElement();
            break;
            
        case XMLStreamConstants.COMMENT:
        case XMLStreamConstants.PROCESSING_INSTRUCTION:
            super.handleEvent(reader, eventType);
            break;

        case XMLStreamConstants.CDATA:
            throw new XMLStreamException ("unexpected CDATA event");
        
        case XMLStreamConstants.SPACE:
            super.handleEvent(reader, eventType);
            break;
            
        case XMLStreamConstants.CHARACTERS:
            textReader.text();
            try {
                highlightTextNode ();
            } catch (IOException e) {
                throw new XMLStreamException(e);
            }
            break;

        case XMLStreamConstants.ENTITY_REFERENCE:
            throw new XMLStreamException("unexpected entity reference event");
            
        default:
            super.handleEvent(reader, eventType);
        }
        
    }

    /**
     * inspired by org.apache.lucene.search.highlight.Highlighter *
     * 
     * send highlighted events to the writer
     * @param reader the input document stream
     * @param characterOffset beginning of the text to highlight
     * @param textLength length of the text to highlight
     * @throws XMLStreamException 
     */
    private void highlightTextNode() throws IOException, XMLStreamException {
        xmlStreamTokens.reset(analyzer.tokenStream(textFieldName, textReader));
        lastEndOffset = 0;
        for (boolean next = xmlStreamTokens.incrementToken(); 
                    next && (offsetAtt.startOffset() < maxDocCharsToAnalyze); 
                    next = xmlStreamTokens.incrementToken()) {
            if (scorerTokens != null && xmlStreamTokens.isPlainToken()) {
                scorerTokens.incrementToken();
            }
            if (tokenGroup.isDistinct()) {
                // write out any accumulated tokens
                handleTokenGroup();
                tokenGroup.clear();
            }
            if (scorerTokens == null || xmlStreamTokens.isPlainToken()) {
                tokenGroup.addToken(scorer.getTokenScore());
            }
        }
        handleTokenGroup();
        tokenGroup.clear();
        writeTrailingText();
    }

    private void writeTrailingText() throws XMLStreamException {
        // Test what remains of the original text beyond the point where we stopped analyzing 
        int textOffset = lastEndOffset;
        int totalTextLength = xmlStreamReader.getTextStart() + xmlStreamReader.getTextLength();
        if (textOffset < totalTextLength) {
            // append it to the output stream
            writeText (lastEndOffset, totalTextLength);
        }
    }
        
    private void handleTokenGroup () throws XMLStreamException {
        if(tokenGroup.numTokens>0)
        {
            // flush the accumulated text
            startOffset = tokenGroup.matchStartOffset;
            endOffset = tokenGroup.matchEndOffset;
            // write any whitespace etc from between this and last group
            if (startOffset > lastEndOffset) {
                writeText (lastEndOffset, startOffset);
            }
            if (tokenGroup.getTotalScore() > 0) {
                // TODO allocate and re-use a single buffer here
                char[] tokenText = new char[endOffset - startOffset];
                xmlStreamReader.getTextCharacters(startOffset, tokenText, 0, endOffset - startOffset);
                highlighter.highlightTerm(writer, new String(tokenText));
            } else {
                writeText (startOffset, endOffset);
            }
            lastEndOffset=Math.max(lastEndOffset, endOffset);
        }
    }
    
    // start and end are absolute numbers, not relative to the current text node
    private void writeText (int start, int end) throws XMLStreamException {
        // Note: Saxon's StAX "bridge" copies a lot of characters here; we would do better
        // grabbing the entire buffer when the text event hits and then parceling it out,
        // or else rewriting to use native Saxon API for iterating over XML events...
        int length = end - start;
        writer.writeCharacters(xmlStreamReader.getTextCharacters(), start, length);
    }
    
    final class XmlStreamTextReader extends Reader {
        
        private int offset; // the offset of a text event in the XMLStreamReader
        private int len;    // the length of the text event
        private int pos;    // the number of characters read from this text event

        /**
         * call this method whenever the XMLStreamReader generates a text event
         */
        void text () {
            pos = 0;
            offset = xmlStreamReader.getTextStart();
            len = xmlStreamReader.getTextLength();
        }
        
        @Override
        public void close() {
        }

        @Override
        public int read(char[] target, int off, int count) throws IOException {
            /*
            while (remaining() <= 0) {
                // pull more events from the stream until we get some text or EOF
                try {
                    int evt = xmlStreamReader.next();
                    if (evt == XMLStreamReader.END_DOCUMENT) {
                        return -1;
                    }
                    handleEvent (xmlStreamReader, evt);
                } catch (XMLStreamException e) {
                    throw new IOException("Error reading XML stream: " + e.getMessage(), e);
                }
            }
            */
            if (remaining() <= 0) {
                return -1; // end of text node
            }
            int nread = remaining() > count ? count : remaining();
            try {
                xmlStreamReader.getTextCharacters(offset + pos, target, off, nread);
            } catch (XMLStreamException e) {
                throw new IOException (e);
            }
            pos += nread;
            return nread;
        }
        
        private int remaining() {
            return len - pos;
        }
        
        public int length() {
            return len;
        }
        
    }
}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
