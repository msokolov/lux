## Changes in Lux release 0.7.1

Now ships with/depends on Solr/Lucene 4.2.  This is actually a breaking change since we rely on some internal APIs
that changed since 4.1 (in the order by /empty greatest support we copied from Solr).

### New features

Calling lux:delete() with no arguments used to delete all documents.
Now the argument to lux:delete($uri) is required. Deleting the special uri "lux:/" deletes all documents.

XPath fields may now be of type LONG, in addition to INT and TEXT.

Sorting by integer and long-valued fields is now implemented.

Added support for "order by ... empty greatest"

### Bug fixes

Improved test coverage uncovered some bugs, which are now fixed.  Most of these were fairly unusual and/or difficult to reproduce,

Queries in which the exists() function's return value is used in a complex expression (rather than a simple predicate)
were incorrectly optimized.

Fixed bugs in the marshalling and serialization of various primitive datatypes; we now have full coverage of all the built in primitive types

Fixed an NPE when an XSLT transform didn't produce any result

Fixed incorrect optimizations involving where clauses. We no longer include queries derived from where 
clause expressions as constraints on their containing FLWOR's results at all.  Some of these were not 
optimizable (like those including "at" variables in their constraints, and other weird cases supplied
by Daniela Florescu). Instead we rely on Saxon to convert optimizable where clauses into XPath predicates,
which it generally does very well.

Fixed an incorrect inference that a predicate is degenerate (in which case we use its base expression to determine
the QName of the last path step when generating element/attribute full text queries).
We now rely on Saxon to collapse paths involving "." so that a degenerate path is simply ".".

We now preserve the declared types of various variables such as let variables and function arguments.  These were
previously being dropped, which could result in subtle type inference errors.

We are now more selective about applying order by optimizations based on lux:field-values; in some degenerate cases
(where the order by expression is unvarying w.r.t. its for clause variables) we were
incorrectly inferring an ordering for the enclosing expression.
