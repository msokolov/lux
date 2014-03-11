---
layout: page
title: Release 1.0
group: release
pos: 3
---

# Lux 1.1.0

Code reorganization: the Solr support is moving into its own Maven module.

lux:key() now raises an error when an undefined field is used rather than
returning an empty sequence.  This seems less surprising.

# Lux 1.0.1

Release 1.0.1 crosses a major version milestone.  We did this more to remark
on its level of robustness and maturity, rather than any specific feature
milestone, but also because, with the addition of configurable analysis,
Lux now includes a pretty complete set of XML search engine features.

What happened to 1.0.0?  Well we could tell you some malarky about XPath
sequences counting from 1, not 0, but the truth is a bug slipped in, as
they will, and 1.0.0 was not released. Stuff happens and we move on.
