package lux;

import static lux.index.IndexConfiguration.*;

import java.io.IOException;

import javax.xml.stream.XMLStreamException;

import lux.index.FieldRole;
import lux.index.XmlIndexer;
import lux.search.LuxSearcher;
import net.sf.saxon.s9api.SaxonApiException;

import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.Fields;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.MultiFields;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.LockObtainFailedException;
import org.apache.lucene.store.RAMDirectory;
import org.apache.lucene.util.BytesRef;

/**
 * Test support class that sets up a lucene index and generates and indexes documents from hamlet.xml or
 * other xml files.
 */
public class IndexTestSupport extends IndexTestSupportBase {

    Directory dir;
    LuxSearcher searcher;
    public LuxSearcher getSearcher() {
		return searcher;
	}

    IndexWriter indexWriter;
    
    public IndexTestSupport() throws XMLStreamException, IOException, SaxonApiException {
        this ("lux/hamlet.xml");
    }
    
    public IndexTestSupport(String ... xmlFiles) throws XMLStreamException, IOException, SaxonApiException {
        this (xmlFiles,
                new XmlIndexer (INDEX_QNAMES|INDEX_PATHS|STORE_DOCUMENT|INDEX_FULLTEXT),
                new RAMDirectory());
    }
    
    public IndexTestSupport(XmlIndexer indexer, Directory dir) throws XMLStreamException, IOException, SaxonApiException {
        this (new String[] {}, indexer, dir);
    }
    
    public IndexTestSupport(String xmlFile, XmlIndexer indexer, Directory dir) throws XMLStreamException, IOException, SaxonApiException {
        this (new String[] { xmlFile }, indexer, dir);
    }

    public IndexTestSupport(String [] xmlFiles, XmlIndexer indexer, Directory dir) throws XMLStreamException, IOException, SaxonApiException {
        // create an in-memory Lucene index, index some content
        this.indexer = indexer;
        this.dir = dir;
        indexWriter = indexer.newIndexWriter(dir);
        if (xmlFiles != null) {
            for (String file : xmlFiles) {
                indexAllElements (file);
            }
        }
        reopen();
        compiler = new Compiler (indexer.getConfiguration());
    }

    public Evaluator makeEvaluator() throws CorruptIndexException, LockObtainFailedException, IOException {
        DirectDocWriter docWriter = new DirectDocWriter(indexer, indexWriter);
        return new Evaluator(compiler, searcher, docWriter);
    }
    
    public void reopen () throws IOException {
        indexWriter.close(true);
        indexWriter = indexer.newIndexWriter(dir);
        searcher = new LuxSearcher(DirectoryReader.open(indexWriter, true));
    }

    public void close() throws Exception {
        searcher.close();
        indexWriter.close();
    }
    
    @Override
    public void addDocument (String uri, String xml) throws XMLStreamException, IOException {
        indexer.indexDocument(indexWriter, uri, xml);
    }
    
    @Override 
    public void commit () throws IOException {
        indexWriter.commit();
        reopen();
    }
    
    public IndexWriter getIndexWriter () {
        return indexWriter;
    }
    
    public void printAllTerms() throws IOException {
        printAllTerms (dir, indexer);
    }
    
    public static void printAllTerms(Directory dir, XmlIndexer indexer) throws IOException {
        DirectoryReader reader = DirectoryReader.open(dir);
        Fields fields = MultiFields.getFields(reader); 
        System.out.println ("Printing all terms (except uri)");
        String uriFieldName = indexer.getConfiguration().getFieldName(FieldRole.URI);
        for (String field : fields) {
            if (field.equals(uriFieldName)) {
                continue;
            }
            Terms terms = fields.terms(field);
            TermsEnum termsEnum = terms.iterator(null);
            BytesRef text;
            while ((text = termsEnum.next()) != null) {
                System.out.println (field + " " + text.utf8ToString() + ' ' + termsEnum.docFreq());
            }
        }
        reader.close();
    }
}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
