---
layout: page
title: FAQ
group: navbar
pos: 4
---
# FAQ #

#### Where can I find general information about what Lux is for and whether it's for me?

The [README](Readme.md) page provides a general overview and description of Lux.  You might also be looking
for the [Balisage 2013 paper](http://www.balisage.net/Proceedings/vol10/html/Sokolov01/BalisageVol10-Sokolov01.html).

#### How do I get started with Lux?

Try reading and following the easy instructions on the [Setup](Setup.md) page.  They will get you as far as running a 
demo application that comes with Lux.  Once that's in place it's a short hop to setting up your own.

#### Where is the mailing list / discussion group?

luxdb@luxdb.org -- it forwards to a google group luxdb@googlegroups.com which is [archived here](https://groups.google.com/forum/?fromgroups#!forum/luxdb).  You will need to join the "group" in order to post.  With a gmail address it is obvious how; you can also sign up with a non-google email address by tacking your email address on to this URL: `http://groups.google.com/group/luxdb/boxsubscribe?email=`.

#### Can Lux manage its XML fields alongside other (normal) Solr fields?

Yes, updates are handled exactly as for Solr without Lux.  The only
difference is that if a document has a value for its Lux-enabled XML
document field, then Lux springs into action and supplies additional fields
as well, representing the document decomposed into XML nodes.

On the query side, the full power of Lucene queries are available via the
XQuery function lux:search().  This function accepts a query and uses
Lucene/Solr to search for documents that match the query, so you can embed
Lucene queries within an XQuery, and you can sort query results efficiently using fields in the index, without retrieving all the documents. 

Those queries can reference all the usual Solr fields, plus they can contain Lucene
XML-aware field queries.  These queries allow you to find a term (or terms, or phrase) within a given element or attribute scope.

The query argument to lux:search() can take one of two forms:

1) a string, parsed with an extended version of the classic Lucene query parser.  The extensions take the form:

     <[nodeName]:[term]

where nodeName can be an element name, or an attribute name preceded by '@', or not present, in which case the entire XML document text is searched.

2) a query expressed as an XML element; this is parsed using an extension of Lucene's org.apache.lucene.queryparser.xml.CoreParser.  Eventually we would like to have an XQuery library that generates these so that queries can be expressed in a functional manner


#### Why doesn't it work when I call lux:log(), ####
lux:insert(), lux:commit(),
or some other function whose work is all done as a side effect?

The Saxon XQuery compiler will often optimize away a function call if its
return value is unused. We've tried a few measures to prevent this, but
they didn't work well in general.  You can generally force a function to be
called by pretending to care about its return value within the immediately
enclosing scope (even if it is declared as returning the empty sequence).
For example:

    let $insert-dummy := (lux:insert ($uri, $document), lux:commit())
    ...
    return ($actual-function-return-value, $insert-dummy)

#### Why do absolute paths (like //document) work only in the outer scope, ####
and not within a function definition?

Lux supplies a special implicit context for such expressions.  It rewrites
expressions, prefixing all absolute paths with a search expression
(collection() or some filtered subset). However, this rewriting cannot be
performed within a function definition because a static error is raised by
Saxon prior to the rewriting pass of the Lux compiler.  A simple workaround 
is to supply the context yourself: instead of //foo, write:

        collection()//foo

Lux will optimize this expression so that only documents containing
elements named "foo" will be returned.

#### I want to use Lux with Saxon PE/EE.  How do I do that? ####

Lux will work with any (9.x) version of Saxon.  It inspects the loaded
classes and attempts to instantiate a licensed Saxon Processor if it
detects that the you have a non-HE version installed.  However, there are
some caveats about using Lux with PE/EE that you should be aware of if you
choose to do this.

##### Eager evaluation of document-ordered sequences #####

Search result sets that need to be document-ordered cannot be evaluated
lazily in Lux when using Saxon PE/EE.

This means a simple xpath like:

       (//foo)[1]

has to be evaluated by retrieving every foo element, sorting them in
document order, before retrieving the first element.  This consumes
unnecessary time and space.  If the sequence is long enough (easy to
achieve with even a medium-sized data set), the query may well fail to
complete.

The reason is that with Saxon-HE we are able to arrange for such
expressions to be evaluated lazily by supplying a custom Configuration
object, which in turn supplies Lux's optimizer and function library.
However Saxon-PE/EE have their own Configurations, which cannot be replaced
without losing PE/EE licensed functionality.

#### What should I do with my 10GB XML document?

You will find that you get better performance if you break your large files into smaller components when indexing them.  The index is optimized for a very large number of smaller documents: its entire function is to make it very fast to find a document. If you only have a small number of very large documents, then you won't be getting any advantage from the index.

There is an [open issue](http://issues.luxdb.org/browse/LUX-3) calling for internal fragmentation that aims to make it easier to work with very large documents, but at the moment it is up to you to fragment documents when you load them.  You can do this using XSLT, for example, either within Lux or otherwise.

In any case when working with large XML files, you need to take care to allocate sufficient heap space for an entire document to be loaded into memory.

#### Does Lux support XForms? ####

Not directly, but we do have reports of folks successfully building XForms applications using XSLTForms with Lux.

#### What is the Lux security model? ####

Currently there is no security model for Lux; applications using Lux may
implement their own security restrictions.  Users should be aware that
exposing the Lux app server directly to users poses a security risk since
the app server exposes internal APIs as web services, which allow, for
example, deleting all documents in the index.

This should not pose a great problem for prototyping and internal
administrative use.  However in order to deploy a public-facing web
application using Lux, it is strongly advised to host the service behind a
proxy that allows access only to the app server urls (/lux in the supplied
configuration) and shields all of the other Solr service points.
