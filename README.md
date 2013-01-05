# About Lux #

Lux is an open source XML search engine formed by fusing two excellent
technologies: the Apache Lucene/Solr search index and the Saxon XQuery/XSLT
processor.

At its core, Lux provides XML-aware indexing, an XQuery 1.0 optimizer that
rewrites queries to use the indexes, and a function library for interacting
with Lucene via XQuery.  These capabilities are tightly integrated with
Solr, and leverage its application framework in order to deliver a REST
service and application server.

## Goals ##

We designed Lux with three main priorities in mind:
1. Top quality
2. Excellent performance
3. Convenient features

In order to achieve these goals, we decided to re-use excellent existing
open-source software wherever possible. This enables us to keep our own
code footprint small, and to test that code rigorously and thoroughly to
ensure that results are as expected.

After correctness, our chief focus is to achieve the best possible query
and indexing performance using standard XQuery constructs enhanced with
custom search functions as needed.

Finally, we want it to be a pleasure to work with Lux.  We think the best
way to do this is to provide features that appeal to developers and make
their lives easier, to support standards initiatives like EXPath, and to
integrate with other widely-used technologies.

## Quality ##

We've used the XQuery Test Suite (TODO: link) to help ensure that Lux
provides an accurate standards-compliant XQuery service.  Because Lux is
built using Saxon, and relies on Saxon to compile and evaluate XQuery, it
is capable of achieving essentially similar results on these
tests. However, as part of its query optimization, Lux does rewrite
queries, introducing search operations where possible to accelerate
processing. Therefore it was important to run a complete battery of tests
to ensure that the optimizer generates results that are faithful to the
original query.  In addition to XQTS, Lux contains a battery of its own
tests (which are available as part of the source distribution) that help
ensure correct results.

### Performance ###

TODO: performance measurements

### XML indexes ###

Lux provides several kinds of index: XML full-text indexes, an XML path
index, and configurable XPath indexes.  All indexes are stored and searched
using Lucene.  The XML full-text indexes provide the ability to query
full-text within a particular XML context.  XPath indexes

### Document storage ###

In spite of its status as a "search engine," Lucene can also function as a
reliable, fault-tolerant, transactional document store.

Optimizer

Function library

REST service

Application server

## How is Lux different? ##

Do we really need another XML database? Many excellent XML search engines
(content stores, databases, etc) already exist, including some open-source
ones.  Every SQL database has some form of built-in XQuery technology. Lux
does not provide the wealth of features that many of these other systems
do, and will not be the best choice for everybody. However, it does have
unique features that we believe will make it an attractive alternative for
some.

One key requirement for Lux was to provide an XQuery capability on top of
an existing Solr index and document store.  To this end, Lux provides a
Solr UpdateRequestProcessor chain; configuring this as the update chain for
a Solr request handler augments an existing indexing pipeline with Lux's
XML-aware fields.

We think this positions it as an attractive drop-in technology for
organizations with an investment in Solr looking to add an XQuery search
capability.

Another differentiator is Lux's relatively small footprint, which should
make it an appealing choice for embedding into other applications.

## What's Next? ##

Learning, some ideas...

XQuery 3.0

Binary (XML) storage

Binary document storage


## Acknowledgements ##

Lux relies on many underlying open-source software packages.  It makes
particular use of Solr/Lucene, Saxon, and also Woodstox. For more
information about Lucene and Solr, see [http://lucene.apache.org/], and for
more information about The Saxon XSLT and XQuery Processor from Saxonica
Limited, see [http://www.saxonica.com/].  Woodstox is hosted at
[http://woodstox.codehaus.org/].
