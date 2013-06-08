---
layout: page
title: Release 0.9
group: release
pos: 6
---

# Changes in Lux release 0.9.1

* Enabling mapping of external filesystem paths to webapp locations in
  AppServer component configuration in solrconfig.xml via lux.baseUri
  parameter.

* Added argument to lux:highlight for controlling the highlight tag name.

* fixed an internal parser error when searching for an empty value like <code>(//a[@id=""])</code>

* fixed a tinybin error encoding empty-valued attributes

* Adding to the setup/getting started documentation

# Changes in Lux release 0.9.0

* This is the First release to Maven Central. The Lux library is now available as luxdb.org/lux (version 0.9.0)

* Solr/Lucene 4.2.1; Lux is now build with Solr/Lucene version 4.2.1

* AppServer path mapping restructured; The path mapping done by the app server is now specified explicitly in configuration so it is possible to deploy applications from any directory.

  AppServer now looks for lux.baseUri parameter, which should be specified as
  an "invariant" parameter in solrconfig.xml for the requestHandler.
  Supported schemes are `resource:`, `context:`, and `file:`

  Be careful because if the parameter is *not* specified, then it can be
  passed on the URL, which makes it possible to fetch resources from anywhere
  on the server using a clever URL.

* `lux.content-type` parameter renamed to `lux.contentType` to be consistent with the naming of the other parameters.

* Added highlighter tagName option.  Previously the highlighter always surrounds highlighted words with a <code>&lt;B></code> tag.  The tagName parameter now provides control over the highlighting tag name.