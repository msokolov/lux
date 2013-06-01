---
layout: page
title: Release 0.9.1
group: release
pos: 7
---

# Changes in Lux release 0.9.1

## AppServer path mapping restructured

### Locked down path mapping in configuration

AppServer now looks for lux.baseUri parameter, which should be specified as
an "invariant" parameter in solrconfig.xml for the requestHandler.
Supported schemes are `resource:`, `context:`, and `file:`

Be careful because if the parameter is *not* specified, then it can be
passed on the URL, which makes it possible to fetch resources from anywhere
on the server using a clever URL.

### renamed lux.content-type parameter to lux.contentType
to be consistent with the naming of the other parameters.






