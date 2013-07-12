---
layout: page
title: Lux Release 0.10
group: release
pos: 5
---

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

1. LUX-37: optimize comparisons with lux:key()
2. LUX-44: optimize comparisons with indexed XPath expressions 
3. Improved app-server startup scripts for Unix and provided a Windows batch file

## Bug Fixes

1. LUX-41 incorrect optimization for nested predicate

2. LUX-46 incorrect optimization for paths with explicit call to lux:search and trailing root()

3. LUX-48 incorrect optimization for deep paths following a predicate

4. Fixed a bug with tinybin encoding where some fixed limits could easily
be exceeded.  Added a version number to the tinybin encoding.

5. LUX-39 use xml chrome for default view in search demo

