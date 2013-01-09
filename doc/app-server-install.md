Problem w/Solr search handlers:

Can't use URL to convey useful information: it *only* identifies the handler.

We tried fixing this in a few ways, but they all involve adding something
to Solr's web.xml, which means repacking the Solr war somehow.  What we
*really* want is an *external* app server: basically a URL rewriter.

For example, create a context for the app server (say /app - could be /, too) 
and another context for solr (/solr ftsoa).

Then in the app server context, deploy an app which is just a single
filter that maps xquery requests like:

/app/foo/bar.xqy/trailing/info?query-string=stuff

to:

/solr/lux?q=/foo/bar.xqy&lux.pathinfo=/trailing/info&query-string=stuff

I tried URL rewrite in Jetty, but: this rewrites the URL part OK: there's no
way to add anything to the query string

Next thing to try is to create a second context with a single filter that
forwards across to the main context

2) if not, do we have to act as a proxy and introduce an http client (ew).

