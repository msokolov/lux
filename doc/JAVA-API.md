---
layout: page
title: Java API
group: api
pos: 3
---
# Lux Java API overview #

This is a high level, brief introduction to the main entry points to the
Java API, not an exhaustive walk through all the classes provided as part
of Lux: the javadocs in the source code provide more detail.

These methods will be useful for those intending to embed Lux in a Java
application that uses Lucene directly and not Solr.  It's not necessary to
read this if you plan to write XQuery applications using the application
server, or if you plan to talk to Lux/Solr using its REST API, via SolrJ,
pysolr, or some other Solr REST client.

## Installation ##

Once you've installed Lux and its dependent jars on your classpath, you
should have access to classes in the "lux" package.  

## Indexing Documents ##

XmlIndexer provides methods for indexing and storing documents.  A typical
sequence of events is:

           XmlIndexer indexer = new XmlIndexer();
           // allocate directory using Lucene methods...
           IndexWriter indexWriter indexer.newIndexWriter(directory);
           // get a document from somewhere (read a file, say).
           indexer.indexDocument (indexWriter, documentUri, document);
           indexWriter.close(); // and commit

One key thing to understand is that no documents will be visible to
searches until indexWriter.commit() (or close(), which commits implicitly)
is called.  Another thing to know is that IndexWriter holds a lock on the
Directory, so only one IndexWriter may be open per index Directory at once.

## Executing Queries ##

Evaluator is the main entry point for evaluating queries; it relies on a
Compiler to compile queries, a LuxSearcher to perform searches, and a
DocWriter to write documents.  If your queries won't be writing documents,
it's OK to pass null for the writer, but a typical sequence might go like
this:

        // construct an Evaluator and its dependencies:
        IndexReader indexReader = IndexReader.open (indexWriter);
        LuxSearcher searcher = new LuxSearcher (indexReader);
        DocWriter docWriter = new DirectDocWriter (indexWriter);
        Compiler compiler = new Compiler (indexer.getIndexConfig());
        Evaluator evaluator = new Evaluator (compiler, searcher, docWriter);

        // evaluate a query that finds all documents titled "Hello, World!"
        String query = "//title[.='Hello, World!']";
        XdmResultSet results = evaluator.evaluate (query);

Evaluator is lightweight and although it is reusable, it is not
thread-safe, so it is intended to be thrown away and recreated as needed.
Compiler is thread-safe and holds more heavyweight long-lived objects, such
as the Saxon Processor and its Configuration.

