# User installation scenarios

## existing Solr install

plug in lux; configure Solr to serve xquery

For an app server, configure a url rewriter: use mod_jk, or the Jetty-based
Lux AppServer, or squid, etc.

## No existing Solr

Either set it up like above, or: TODO: we deliver an integrated jetty-based
service ?