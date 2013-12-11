---
layout: page
title: Release 0.11
group: release
pos: 4
---

# New Features

The full array of Lucene analysis tools can now be applied to Lux's XML
text fields simply by configuring the fields in the Solr schema (or in the
Java API by supplying a Lucene Analyzer to a Lux FieldDefinition).

Lux's API now provides control over element visibility as a tradeoff
between index size and power.

SolrCloud support!  Lux now handles distributed, sharded indexes by using
Solr's distributed query and update functionality.  The short story is that
everything works when you have your documents spread across multiple
machines, and this paves the way to scale out to very large indexes.  All
the xquery functions are now cloud-aware as well: lux:insert() will
distribute documents using the default Solr hashing strategy based on the
document URI as the unique key.  Please be aware the implementation is
complete, but has seen only limited testing, so there will be bugs.
However, the bulk of the complexity is handled in Solr, not in Lux.

Solr schema types are now supported for XPath fields.  Previously, if you
declared an XPath field (in Lux's configuration in solrconfig.xml), and its
name matched a type in the Solr schema (in schema.xml), that fact was
ignored, and its value was always treated as a string.  Now we recognize
the association and use Solr's conversion rules to index the
appropriately-typed value.  Thanks to Mark Lawson for pointing this out.

# Changes in release 0.11.3

Analyzers declared in solr schema for type of field named lux_text (or
whatever the xml text field is called) are used when indexing and querying
xml text field, and element and attribute text fields.

We've simplified and reorganized the index configuration representation of
fields and field naming: created field roles, eliminated static singleton
field definitions, tracked field renaming in the field object rather than
in a map in the configuration object.

lux:field-values() was deprecated (in favor of lux:key) and is now eliminated 

Element transparency: the contents of *transparent* elements are also
indexed as part of enclosing elements, while the contents of *opaque*
elements are not.  Further, elements may be declared (by name) to be
*container* elements, which "see into" descendant opaque elements, indexing
all descendant content, with the exception of *hidden* elements, whose
content is not indexed at all.  Elements are opaque by default - in
previous releases they were effectively transparent - all text was tagged
with all enclosing element names.

# Other Changes in release 0.11.2

Fixed a bug (introduced w/0.11.0) that caused exceptions in the URI
resolver when calling doc().

# Changes in Lux release 0.11.0

1. Upgraded to Saxon 9.5.  This change should be largely invisible to
users, but people who roll their own installations will also need to
upgrade since we use API calls in Saxon 9.5 that did not exist in 9.4.

2. Upgraded to Solr 4.4.0.  Again, the changes are largely invisible, but
installations must upgrade due to backwards incompatible changes.  The
index format did not change, though.

3. Experimental SolrCloud support.