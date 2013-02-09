Documentation updated; now includes documentation for the XQuery function
library, for the Java API and for the Solr-based REST API.

Fixed some over-optimization bugs; one was related to path queries
generated from paths with predicates in them, and another one was related
to let clauses in flwor expression.

Fixed a thread-safety issue where the solr index wouldn't be closed
properly in the presence of a lot of concurrent indexing requests, leading
to lock exceptions.

Enhanced the optimizer with better support for variables and FLWOR
expressions.  It now maintains a variable reference map so that it's
possible to more fully optimize expressions containing variables.  Also,
constraints from for and where clauses are now taken into account when
optimizing.

The app-server now includes the connect4 app

