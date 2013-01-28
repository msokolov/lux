= Lux Java API documentation =

This is a high level description of API, not an exhaustive walk through all
the Java classes provided as part of Lux: the javadocs in the source code
provide more detail.

These methods will be useful for those intending to embed Lux in a Java
application that uses Lucene directly and not Solr.  It's not necessary to
read this if you plan to write XQuery applications using the application
server, or if you plan to talk to Lux/Solr using its REST API, via SolrJ,
pysolr, or some other Solr REST client.

== Getting Started ==

Once you've installed Lux and its dependent jars on your classpath, you
should have access to classes in the "lux" package.

=== Indexing Documents ===

XmlIndexer provides methods for indexing and storing documents.  The typical sequence of events is:

           XmlIndexer indexer = new XmlIndexer();
           // allocate directory using Lucene methods...
           IndexWriter indexWriter indexer.newIndexWriter(dir);
           // get a document from somewhere (read a file, say).
           indexer.indexDocument (indexWriter, documentUri, document);
           indexWriter.close(); // and commit

One key thing to understand is that no documents will be visible to
searches until indexWriter.commit() (or close(), which commits implicitly)
is called.  Another thing to know is that IndexWriter holds a lock on the
Directory, so only one IndexWriter may be open per index Directory at once.

=== Executing Queries ===



lux.Compiler, lux.Evaluator, lux.index.XmlIndexer