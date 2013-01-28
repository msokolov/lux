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

XmlIndexer provides methods for indexing documents.

TODO: finish writing javadoc for XmlIndexer

To store a document in an index, you must first acquire an IndexWriter -
this is a Lucene class that - yes you guessed it - writes to an index.



lux.Compiler, lux.Evaluator, lux.index.XmlIndexer