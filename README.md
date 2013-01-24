# About Lux #

Lux is an open source XML search engine formed by fusing two excellent
technologies: the Apache Lucene/Solr search index and the Saxon XQuery/XSLT
processor (as well as numerous other supporting open source packages: see
THIRDPARTY-LICENSES.md for details).

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

We need to acknowledge here
though that Lux is new and is missing many features users will expect from a mature
database product.  Also, disclaimer: please be aware this is a first public 
release: even though it benefits from being built on a very solid base, the 
Lux code itself has seen limited usage, and we are sure to identify gaps
that need addressing in order to make it a more useful system.  In spite of these
limitations, we felt there would be some real value in sharing this effort with
a broader community: both for us, because we would appreciate feedback,
and for the community at large that has a need for an XQuery search capability 
embedded in Solr.

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
full-text within a particular XML context.  The path and full-text indexes

### Document storage ###

In spite of its status as a "search engine," Lucene can also function as a
reliable, fault-tolerant, transactional document store.  We store documents 
in the Lucene index, and find this to be a scalable, efficient and reliable
mechanism.

### Optimizer ###

Lux evaluates absolute expressions (essentially: paths beginning with a "/") as if they were 
preceded by a call to collection(): ie they are evaluated for every document in the database,
in some fixed order, which is defined per query, but in general is unpredictable.  The Lux
optimizer's main job is to reduce the number of documents for which a given expression in fact
needs to be evaluated *while producing the same result as if every document were evaluated*.

A key piece of this puzzle was to solve an impedance mismatch between Saxon and Lucene.  Saxon
performs all of its work by pulling results through Iterators.  In contrast, Lucene expects to 
push results (through an object called a Collector).  Lux manages this in different ways depending
on whether results are ordered by relevance, by value (some other kind of order by expression),
or in document order.

Lux optimizes all XQuery modules, rewriting expressions in order to make use of its indexes.
It attempts to identify constraints that restrict the set of documents that need to be evaluated
for a given expression, expresses those constraints as a Lucene query, and writes a new XQuery 
expression that includes a call to the lux:search XQuery function, or some other index-aware 
function (such as lux:count, lux:exists).  One advantage of this approach 
is that the optimized query is simply another XQuery and can be displayed to the user, 
which can help the user to understand what tricks the optimizer may (or may not) be playing.

Aside from filtering document sets, the optimizer can also use indexes to sort expressions
that have "order by" expressions which use a special function that names an XPath index,
Lux also attempts to inform Saxon when it can tell that a sequence will be in document order, 
so that it won't need to be re-sorted.  These ordering optimizations are critical since they
allow expressions to be evaluated lazily, without retrieving the entire result set, a typical 
search engine requirement.

### Function library ###

Lux provides a small library of XQuery functions that expose Lucene functionality such as search,
iteration over terms, query highlighting, and document updates. An XSLT transform capability is provided.
A subset of the expath:file module is provided, and the EXPath packaging libraries are included so as to 
enable modular extension of the function library in a standard way. The complete library of built-in XQuery 
functions is documented in the (TODO: link) javadoc for the lux.functions package.

### REST service ###

Lux includes a Solr QueryComponent that evaluates queries as XQuery using Saxon.  This component 
appears on the surface like any other Solr QueryComponent: it expects to receive a query as the 
value of the "q" request parameter, and formats its response using a ResponseWriter configured in 
solrconfig.xml.  XQueryComponent, however, ignores the pagination, sorting, highlighting, faceting 
and other search parameters that are typically interpreted by Solr.  Instead, it's assumed that these
functions will be handled within the XQuery itself.

The provided configuration sends receive results via a Lux ResponseWriter, which 
serializes XML *as HTML*, so as to support the application server, but in theory any of the Solr 
ResponseWriters (BinaryResponseWriter, XMLResponseWriter, etc.) may be configured instead.

### Application server ###

Lux also extends the basic XQuery support in Solr to provide an application server capability.  This
component interprets urls whose paths end ".xqy", or contains ".xqy/" as requests for XQuery evaluation.
An initial path component (the "context") is stripped from the left to obtain an XQuery path, and any 
trailing component (after ".xqy/") is provided as "path extra" information to the query.  We plan to 
implement the [EXQuery request specification](http://exquery.github.com/expath-specs-playground/request-module-1.0-specification.html),
but for now we provide a simple XML formatted structure containing request parameters and other info
to the XQuery module as an external variable.

## Why Lux? ##

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

We think this positions it as an attractive drop-in technology for people and
organizations with an investment in Solr looking to add an XQuery search
capability.

Another differentiator is Lux's relatively small footprint, which
makes it an appealing choice for embedding into applications using Saxon that
wish to extend their XQuery and XSLT capability with a persistent indexed document
store.

Finally, the original motivation for Lux was to provide a content exploration
tool for analyzing new and unfamiliar XML structures.  We often encounter XML
data that is either known not to conform to its schema/DTD, or may not be constrained
by any schema whatsoever.  In such cases it is very useful to be able to test assertions
across a large range of sample data in order to apply evidence-based constraints.

## What's Next? ##

### Responding to feedback ###

The next steps will certainly be shaped by requests and comments from the community.  Our main
focus will be maintaining a high standard of quality w.r.t. any reported bugs or serious gaps
in existing functionality.

### Performance enhancements
We have numerous ideas for improving performance, including: 
* caching various internal objects to make better use of memory
* caching compiled and optimized queries
* Skipping unused documents in a result set (deep paging)

### XQuery 3.0

We would like to support XQuery 3.0 and plan to track whatever features are made available
in both the open source and commercial versions of Saxon.  At the moment however, Lux is developed 
and tested against version 9.4.0.3 HE (the open source version) only.  We've performed some basic
tests with PE/EE, but some Lux optimizations can only be provided with the HE version.

### Binary (XML) storage

We should get substantial performance gains by using Saxon's ptree format internally once we can 
resolve some of the compatibility issues w/Saxon PE/EE.

### Binary document storage

It may be convenient to store unindexed non-XML documents in Lux.  Currently these have to be managed 
separately.

### Standard HTTP request handling

We're looking at implementing the EXQuery request specification

### Extensible text analysis 

It should be possible to configure the analysis chain (lower casing, diacritics, stemming, synonyms, etc) for
the XML text fields.  Right now those fields are all case- and diacritic- insensitive, and perform no stemming
or synonym expansion.

### Indexing boundaries / element transparency

In some cases, you may want to exclude the content of certain elements from consideration by search queries that 
reference an enclosing context.  It is also useful to be able to specify that phrases may not cross certain element 
boundaries.

### Index path occurrence counts

We'd like to be able to evaluate queries like count (/a/b/c) efficiently - ie out of the indexes.
In particular a very useful optimization would be the ability to compute "exists (/a/b/c[2])" in constant
time. We often want to know if an element ever occurs more than once in some context.

## Acknowledgements ##

Lux relies on many underlying open-source software packages, but its main value is serving as a bridge
between Solr/Lucene, and Saxon. For more information about Lucene and Solr, see [http://lucene.apache.org/], 
and for more information about The Saxon XSLT and XQuery Processor from Saxonica
Limited, see [http://www.saxonica.com/].
