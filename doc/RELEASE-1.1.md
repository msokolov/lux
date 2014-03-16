---
layout: page
title: Release 1.1
group: release
pos: 3
---

# Lux 1.1.0

Release 1.1.0 incorporates Lucene and Solr 4.6.1.

With this release you can now configure the scope of the element-text index
using the concept of element visibility by setting attributes of the
QNameFilterFactory declared in schema.xml.  This provides a way to limit
the element-tagging of text to only those that are needed for querying.

Each element name may be one of: transparent, opaque, hidden, or container.
The default may be set to either opaque or transparent. Unless hidden, text
is tagged with its parent element.  If its parent is transparent, it is
also tagged with ancestor elements, stopping at the first opaque or
container element. In addition, visible (non-hidden) text is tagged with
all ancestor container elements.

In the following example, elements are opaque by default,
meaning text is indexed in association with immediate parent elements
only. The other settings override this default for specific elements: the i
(and namespaced i) elements are declared as transparent, meaning that text
with parent element i is indexed in association with element i, and is also
visible to i's ancestral elements, in accordance with *their* visibility
rules.  IE, by default only i's parent will "see" i's children, since it
will be opaque.

     <filter class="lux.index.analysis.QNameFilterFactory" visibility="opaque" transparent="i,{urn:ns}i" container="div" hidden="hidden" />

