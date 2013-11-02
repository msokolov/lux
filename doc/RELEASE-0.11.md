---
layout: page
title: Release 0.11
group: release
pos: 4
---

# New Features

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

# Other Changes in Lux release 0.11.2

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