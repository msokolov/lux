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

* [Lux app server 0.10.5, bzip](dist/lux-appserver-0.10.5-bin.tar.bz2)
* [Lux app server 0.10.5, gzip](dist/lux-appserver-0.10.5-bin.tar.gz)
* [Lux app server 0.10.5, zip](dist/lux-appserver-0.10.5-bin.zip)

The war-only distribution is useful if you want to run this in an existing
J2EE web app container.

* [Lux 0.10.5-enabled Solr war (web application) only](dist/lux-appserver-0.10.5.war)

Download the Lux library (jar) if you want to embed Lux in a Java
application that will manage its own local index using Lucene (not Solr).

* [Lux 0.10.5 library (jar) only](dist/lux-0.10.5.jar)

We also maintain a folder of older artifacts:

* [older artifacts](dist/?C=N;O=D)

## Source code ##

The complete Lux source code is available under the [Mozilla Public License 2.0](http://www.mozilla.org/MPL/2.0/) on GitHub at (https://github.com/msokolov/lux).

## Lux jar on maven ##

The Lux library is available as a maven dependency using groupId=luxdb.org
and artifactId=lux.  To add lux as a dependency, you would place the
following in your pom.xml:

                  <dependency>
                     <groupId>org.luxdb</groupId>
                     <artifactId>lux<artifactId>
                     <version>0.10.5</version>
                  </dependency>

