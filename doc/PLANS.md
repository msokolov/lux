---
layout: page
title: Plans
group: navbar
pos:   5
---
# Plans

This document gives an overview of some initiatives we have planned.  There
is a complete list of open issues that includes everything here in the
project Jira at issues.luxdb.org.

There is no schedule for any of this work, not any commitment that all (or
any) of it will be completed. However, we do continue to make improvements,
and this list does reflect our priorities. We hope it may be useful for you
insofar as it gives a general idea of the direction in which we want to
take Lux development in the future.  And of course, we welcome
contributions.

## Performance enhancements

We have numerous ideas for improving performance, including: 

### More indexing options, and query optimizations that use them

#### Automatically optimize indexed order by expressions

Currently, in order to sort expressions efficiently, the query writer must
explicitly invoke the lux:key() function as a marker for the
optimizer.  We could enhance the optimizer to detect indexed expressions
itself.

#### Optimize deep paging with sorted queries

There are currently some inefficiencies here since sorting is done using a
fixed-size priority queue; if the requested result falls beyond the end of
that queue, the current algorithm expands the queue and starts over.  A
better approach would be to remember the last value and use it as a range
filter on a subsequent pass.

### Cache compiled and optimized queries

This will save time spent reading, parsing and optimizing queries that are
repeated.

## XQuery 3.0

We will track whatever features are made available in the open source
version of Saxon, at least to the extent of ensuring the optimizer doesn't
break them.  Other than that, we don't have any immediate plans to support
a full range of XQuery 3.0 features, although we may add some.

## Extensible text analysis
Lucene allows for a wide variety of text
analysis techniques (lower casing, diacritics, stemming, synonyms, etc).
Currently, Lux's xml text fields are all case- and diacritic- insensitive,
and perform no stemming or synonym expansion.  Lux has most of the internal
plumbing in place to enable these to be configurable, but requires some
additional work to expose the configuration to users, and to expose these
settings to the query logic; possibly this could be done using collations.

## Indexing boundaries / element transparency

In some cases, you may want to exclude the content of certain elements from
consideration by search queries that reference an enclosing context.  It is
also useful to be able to specify that phrases may not cross certain
element boundaries.

## Index path occurrence counts

We'd like to be able to evaluate queries like count (/a/b/c) efficiently -
ie out of the indexes.  In particular a very useful optimization would be
the ability to compute <code>exists (/a/b/c[2])</code>in constant time. We often want
to know if an element ever occurs more than once in some context.

## Provide convenient access to advanced Solr and Lucene features

One basic thing we plan to do is to use the Solr query parser to parse queries in lux:search,
rather than the Lucene query parser as we do now.  This will add some capabilities like date range
query parsing.

More broadly,
Solr provides a number of advanced query capabilities such as spelling
suggestions, faceting, grouping, function queries, aggregate computations
and so on.  Within-query features like function queries may be available by
lux:search, but not in a truly integrated way.  Solr components are available 
via the REST service, alongside XQuery, but again, not truly integrated.

We will add XQuery functions that bind to Solr and Lucene functions, but we
should also think about how to offer features that Solr provides as
"components".  These generally provide a REST interface, rather than a Java
functional interface, and make assumptions about query parameters that
aren't generally in line with the conventions used by Lux. For example,
what does it mean to run faceting alongside an XQuery that might execute
multiple underlying Lucene queries. Do we identify a primary one?  Run
multiple faceting episodes?

## Implement hexBinary and base64Binary response types
Currently these cannot be serialized by the REST API

## Provide a document processing pipeline 

Possibly support update triggers that run XQuery/XSLT, or incorporate XProc

## Enhance the query box

It would be nice to add more advanced editing capabilities, including
syntax highlighting, syntax checking, and term completion.

## Reindexing

We should provide either a manual, or an automatic reindexing capability so
that when field definitions change, we can apply them to existing documents
without the need to reload.

