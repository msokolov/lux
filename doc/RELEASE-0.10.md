---
layout: page
title: Release 0.10
group: release
pos: 5
---

# New Features

This release introduces full-featured support for HTTP request/response
handling within XQuery by implementing the [EXPath webapp specification](http://expath.org/spec/webapp/20130401)'s request/response protocol with a few exceptions.  See the [XQuery API documentation](API.md) for details.

Another major improvement in this release is optimization of comparisons
between indexed expressions and suitable constants. For example, if an
int-valued XPath index on `//@counter` is defined, an expression such as
`//section[@counter > 10]` will be optimized using the index, and rewritten
as something like: `lux:search('counter:{10 TO *}')//section[@counter >
10]`.

Configurable analysis; you can now specify which text analyzers to use with
xml fields.

# Changes in Lux release 0.10.7

Configurable analysis; see [0.11.3 release notes](RELEASE-0.11.md)

# Changes in Lux release 0.10.6

This release includes a fix for LUX-71, which caused NullPointerExceptions
while indexing non-xml documents.

# Changes in Lux release 0.10.5

1. LUX-66. Provide improved HTTP request/response support: access to POST bodies, request headers, redirect responses, etc.

2. LUX-61. lux:field() now returns values for all Lucene fields, not just those created by lux.


# Changes in Lux release 0.10.4

Fixed a memory leak relating to the LuxURIResolver that could lead to
OutOfMemoryExceptions during periods of intensive use.

1. LUX-51 Bound the amount of memory used by any single request so as to
prevent OutOfMemory Exceptions.

# Changes in Lux release 0.10.3

This is a minor release containing mostly bug fixes.

## Tickets Closed

1. LUX-21. Iterate over Lucene readers more efficiently: performance
improvement for large fragmented indexes.

2. LUX-49. Pool Serializers in the app server, and make the default serialization the natural one (xml for the xquery service, and html for the app server).

3. LUX-50. Fixed an exception that would arise if a core name was not provided as part of an app server request url.  Now the dispatch filter attempts to provide a default core name, and if that fails, returns a 404 rather than a 500 error.

4. LUX-24. Reduced heap usage during indexing.

5. LUX-53. We now optimize certain predicates that were not optimized before; mostly this includes predicates with mixed booleans and comparisons.

6. LUX-55. Use a regular match-all query when no optimizations are
possible, rather than a SpanTerm query matching document root, which we
were doing in some cases.  This fixes a bug where binary (non-XML)
documents would not be found in some cases, and makes for more efficient
queries generally.

7. Lux now plays nicely with Solr's transaction log, so that recovery after
a crash works as expected.

# Changes in Lux release 0.10.2

This is a minor release containing mostly bug fixes.  One new feature
is (LUX-45) the introduction of the lux:key() function as a replacement for
lux:field-values().

1. Fix for field renaming; this is typically only relevant when integrating
with an existing Solr installation.  If you renamed the uri field, its new
name wasn't propagated to the Compiler, causing document not found errors
when calling doc().

2. Fix for incorrect path distance computation for some predicates like: foo\[bar or .//baz\].

3. Fix for an NPE in the Solr ResponseWriter when an error occurs but there
is no message (eg if an NPE occurs downstream).

4. Fix for an NPE when optimizing comparison with Dot.

# Changes in Lux release 0.10.1

This is a minor release containing bug fixes only.

1. LUX-48; don't cache a base query for PathExpressions (only for
Predicates).  This fixes some incorrect optimizations for complex path
expressions.

# Changes in Lux release 0.10

The major improvement in this release is optimization of comparisons
between indexed expressions and suitable constants.  Comparisons between
lux:field-values('index') and a constant of a compatible type are now
handled using Lucene queries.  In many cases it is also possible to simply
use the indexed expression in a comparison. If the optimizer can match it,
it will substitute the Lucene query.

This release also includes fixes for some isolated, but fairly major bugs
in basic path optimization that would cause queries to return empty results
incorrectly.

## New Features

1. LUX-37: optimize comparisons with lux:field-values (now lux:key())
2. LUX-44: optimize comparisons with indexed XPath expressions 
3. Improved app-server startup scripts for Unix and provided a Windows batch file

## Bug Fixes

1. LUX-41 incorrect optimization for nested predicate

2. LUX-46 incorrect optimization for paths with explicit call to lux:search and trailing root()

3. LUX-48 incorrect optimization for deep paths following a predicate

4. Fixed a bug with tinybin encoding where some fixed limits could easily
be exceeded.  Added a version number to the tinybin encoding.

5. LUX-39 use xml chrome for default view in search demo

