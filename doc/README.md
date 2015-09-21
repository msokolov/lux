---
layout: page
title: About
group: navbar
pos:   1
---

### :warning: **This fork requires java 7 to build** :warning: ###

# About Lux #

Lux is an open source XML search engine formed by fusing two excellent
technologies: the Apache Lucene/Solr search index and the Saxon XQuery/XSLT
processor.

At its core, Lux provides XML-aware indexing, an XQuery 1.0 optimizer that
rewrites queries to use the indexes, and a function library for interacting
with Lucene via XQuery.  These capabilities are tightly integrated with
Solr, and leverage its application framework in order to deliver a REST
service, application server, and supporting tools.

The REST service is accessible to applications written in almost any language,
but it will be especially convenient for developers already using Solr, for whom
Lux operates as a Solr plugin that provides query services using the same REST APIs
as other Solr search plugins, but using a different query language (XQuery). XML documents
may be inserted (and updated) using standard Solr REST calls: XML-aware indexing
is triggered by the presence of an XML-aware field in a document.  This means that
existing application frameworks written in many different languages are positioned
to use Lux as a drop-in capability for indexing and querying semi-structured content.

The application server is a great way to get started with Lux and XQuery:
it provides the ability to write a complete application in XQuery and XSLT
with data storage backed by Lucene.



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

We've used the XQuery Test Suite (1.0 version) to help ensure that Lux
provides an accurate standards-compliant XQuery service.  Because Lux is
built using Saxon, and relies on Saxon to compile and evaluate XQuery, it
achieves essentially similar results on these tests. However, as part of 
its query optimization, Lux does rewrite
queries, introducing search operations where possible to accelerate
processing. Therefore it was important to run a complete battery of tests
to ensure that the optimizer generates results that are faithful to the
original query.  In addition to XQTS, Lux contains a battery of its own
tests (which are available as part of the source distribution) that help
ensure correct results.

We use the travis-ci continuous integration service to monitor the health of our builds. This image reflects the current build status there: [![Build Status](https://travis-ci.org/msokolov/lux.png)](https://travis-ci.org/msokolov/lux) and links to the build history, so you can see when we break any of our tests.

## Performance ##

Query performance varies depending on the query, and the data, and the environment,
so it is impossible to give a meaningful account in a short summary.  However:
Indexed queries are blindingly fast because Lucene is an excellent search index.  Processing 
small-to-medium documents is very fast because Saxon is an excellent query processor.
Lux does a decent job of filtering out irrelevant documents in many cases, and provides
access (via debug-level logging statements) to the optimized queries it produces, so it is 
easy to see where it is applying indexing optimizations, and where it is not.

### SolrCloud support ###

#### :warning: **Currently, SolrCloud support is broken in this solr-4.10.4 based release.** ####

To handle huge datasets (on the order of 1TB and up), it's necessary to
distribute documents among multiple machines. Solr provides a distributed
update and query mechanism (and other stuff) called SolrCloud that makes
this possible.  Lux's update and query mechanisms make use of that, so you
can now store literally zillions of documents in a single multi-node Lux
installation!  Big Data woohoo!

## Features ##

### XML indexes ###

Lux provides several kinds of index: XML full-text indexes, an XML path
index, and configurable XPath indexes.  All indexes are stored and searched
using Lucene.  The XML full-text indexes provide the ability to query
full-text within a particular XML context.  The path index provides rapid lookup
of element and attribute names and path fragments.  Any XPath statement can be indexed
and used in explicit sorting and filtering expressions.

### Document storage ###

In spite of its status as a "search engine," Lucene can also function as a
reliable, fault-tolerant, transactional document store.  We store documents 
in the Lucene index, and find this to be a scalable, efficient and reliable
mechanism.

### Optimizer ###

Lux evaluates absolute expressions in the outer context (essentially: paths beginning with a "/") as if they were 
preceded by a call to collection(): ie they are evaluated for every document in the database,
in some fixed order, which is defined per query, but in general is unpredictable.  The Lux
optimizer's main job is to reduce the number of documents for which a given expression in fact
needs to be evaluated *while producing the same result as if every document were evaluated*.

A key piece of this puzzle was to couple Saxon and Lucene query evaluation
as tightly as possible.  Saxon performs all of its work by pulling results
through Iterators.  In contrast, Lucene expects to push results (via a
Collector object).  Lux manages this in different ways depending on whether
results are ordered by relevance, by value (some other kind of order by
expression), or in document order.

Lux optimizes all XQuery modules, rewriting expressions in order to make
use of its indexes.  It attempts to identify constraints that restrict the
set of documents that need to be evaluated for a given expression,
expresses those constraints as a Lucene query, and writes a new XQuery
expression that includes a call to the lux:search XQuery function, or some
other index-aware function (such as lux:count, lux:exists).  One advantage
of this approach is that the optimized query is simply another XQuery that
can be logged.  Comparing the optimized query in the log with the original
query makes it very clear which optimizations are (or are not) being
applied.

Aside from filtering document sets, the optimizer can also use indexes to sort expressions
that have "order by" expressions which use a special function that names an XPath index,
Lux also attempts to inform Saxon when it can tell that a sequence will be in document order, 
so that it won't need to be re-sorted.  These ordering optimizations are critical since they
allow expressions to be evaluated lazily, without retrieving the entire result set, a typical 
search engine requirement.

### Function library ###

Lux provides a small library of XQuery functions that expose Lucene functionality such as search,
iteration over terms, query highlighting, and document updates. An XSLT transform capability (using Saxon) 
is provided. A subset of the expath:file module is provided, and the EXPath packaging libraries are included so as to 
enable modular extension of the function library in a standard way. The complete library of built-in XQuery 
functions is documented in the (TODO: link) javadoc for the lux.functions package.

### REST service ###

Lux includes a Solr QueryComponent that evaluates queries as XQuery using Saxon.  This component 
appears on the surface like any other Solr QueryComponent: it expects to receive a query as the 
value of the "q" request parameter, and formats its response using a ResponseWriter configured in 
solrconfig.xml.  XQueryComponent, however, ignores the pagination, sorting, highlighting, faceting 
and other search parameters that are typically interpreted by Solr.  Instead, it's assumed that these
functions will be handled within the XQuery itself.

The provided configuration sends results via a Lux ResponseWriter, which 
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

### Solr / Lucene 4

Release 0.7 introduced support for Solr/Lucene 4.  Lux 0.6 will be the last
Solr 3.x release, and unless there is an outcry we will probably not
backport fixes in order to support Solr 3.

### Binary (XML) storage

Lux can now store documents in a quick-to-parse binary XML format called
TinyBinary.  Using this format rather than serialized XML provides a
substantial performance improvement since documents can be loaded directly
from the index with only very minimal parsing and object construction.

### Binary document storage

Lux can now store non-XML documents in the index, including images, XQuery,
PDFs, etc.

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
across a large range of sample data in order to apply evidence-based constraints.  To
this end, Lux provides path- and content- indexes for every element and attribute node,
without the need to pre-configure any indexes, and also provides explicit XPath indexing.

## What's Next? ##

### Responding to feedback ###

The next steps will certainly be shaped by requests and comments from the
community.  Our main focus will be maintaining a high standard of quality
w.r.t. any reported bugs or serious gaps in existing functionality.  Any
contributions along these lines will be welcome.

### Enhancements

Please see the [Plans](PLANS.html) page for more about future plans for Lux
development.

## Acknowledgements ##

Lux relies on many underlying open-source software packages, but it could not exist without Solr/Lucene, and Saxon. For more information about Lucene and Solr, see [http://lucene.apache.org/](http://lucene.apache.org/), 
and for more information about The Saxon XSLT and XQuery Processor from Saxonica
Limited, see [http://www.saxonica.com/](http://www.saxonica.com/).
