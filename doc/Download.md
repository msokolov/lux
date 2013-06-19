---
layout: page
title: Download
group: navbar
pos: 2
---

# Download Lux #

## Binary releases ##

The Lux app server bundle is a complete server bundle, including Jetty.  It
does require a working Java installation.  It will probably work with a
recent OpenJDK JVM, but has only been tested with Oracle/Sun JVMs.

* [Lux app server 0.9.1, bzip](dist/lux-appserver-0.9.1-bin.tar.bz2)
* [Lux app server 0.9.1, gzip](dist/lux-appserver-0.9.1-bin.tar.gz)
* [Lux app server 0.9.1, zip](dist/lux-appserver-0.9.1-bin.zip)

The war-only distribution is useful if you want to run this in an existing
J2EE web app container.

* [Lux 0.9.1-enabled Solr war (web application) only](dist/lux-appserver-0.9.1.war)

Download the Lux library (jar) if you want to embed Lux in a Java
application that will manage its own local index using Lucene (not Solr).

* [Lux 0.9.1 library (jar) only](dist/lux-0.9.1.jar)

We also maintain a folder of older artifacts:

* [older artifacts](dist/?C=N;O=D)

## Source code ##

The complete Lux source code is available under the [Mozilla Public License 2.0](http://www.mozilla.org/MPL/2.0/) on GitHub at (https://github.com/msokolov/lux).