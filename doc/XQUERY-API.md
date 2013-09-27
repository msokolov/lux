---
layout: page
title: Lux XQuery API
group: api
pos: 2
---
# Lux XQuery function library #

## XPath function library ##

The W3C standard [XPath function
library](http://www.w3.org/TR/xpath-functions/) available in Lux, as
implemented by Saxon, includes a large number of useful functions.

In particular we mention the functions collection() and doc() since they
operate in an implementation-specific manner.

### `function fn:doc($uri as xs:string) as document-node() ###

The doc() function accepts a single URI and resolves it in different ways
depending on the scheme of the URI.  The Lux URI resolver implements two
schemes: file: and lux:.  If no scheme is provided, the lux scheme is
assumed.  In short, doc("/test.xml") retrieves a document from the Lux
index, while doc("file://home/me/test.xml") retrieves a document from my
home directory (on UNIX; for Windows this would be something like
doc("file:///C:/Users/me/test/xml")).

### `function fn:collection($uri as xs:string?) as document-node()* ###

If collection() is called with no arguments, the result is a sequence of
all the documents in the index, in an arbitrary order (that is fixed only
for the duration of the query).

If collection() is called with a URI having the lux: scheme, the remainder
of the URI is treated as a Lucene query (extended by Lux node field
syntax).  This provides an alternative mechanism for calling lux:search().
This behavior may change in future releases however, and it is recommended
to use lux:search() instead.

Otherwise collection() is resolved by Saxon in its usual way, generally by
reading the contents of a file system directory.

## Indexing and Search functions ##

Functions relating to search and indexing are declared in the
http://luxdb.net namespace, which we always reference using the prefix
"lux".

### `function lux:commit() as empty-sequence()` ###

commits pending updates to the index and blocks until the operation is complete.

### `function lux:count($query as item()) as xs:integer` ###

counts the number of results of a search.  It is faster and uses less memory 
than calling fn:count() on the search results themselves because it does not need to load
the result documents in memory.  See lux:search() for an explanation of the supported
$query formats.

### `function lux:delete($uri as xs:string?) as empty-sequence()` ###

deletes a document from the index at the given uri.  NOTE: if the $uri document
is empty, *all documents* will be deleted.  This "feature" will be removed in a later release.

### `function lux:exists($query as item()) as xs:integer` ###

tests whether a search has any results.  It is faster and uses less memory
than calling fn:exists() on the search results themselves because it does
not need to load any result documents in memory.  See lux:search() for an
explanation of the supported $query formats.

### `function lux:field-terms($field-name as xs:string?, $start as xs:string?) as xs:anyAtomicItem*` ###

accepts the name of a Lucene field, and a starting value, and returns the
sequence of terms drawn from the field, ordered according to its natural
order, starting with the first term that is >= the starting value.

If the $field-name argument is empty, the terms are drawn from the default
field defined by the IndexConfiguration, generally the XmlTextField.

### `function lux:key($field-name as xs:string, $node as node()) as xs:anyAtomicItem*` ###

accepts the name of a lucene field and optionally, a node, and returns any
stored value(s) of the field for the document containing the node, or the
context item if no node is specified.

If the node (or context item) is not a node drawn from the index, lux:field
will return the empty sequence.

#### Optimized Sorting 

XQuery "order by" expressions containing lux:key calls are subject to
special optimization and are often able to be implemented by
index-optimized sorting in Lucene.  Without this optimization, sorting can
be very inefficient due to the need to load the full contents of every
document into memory and evaluate the sort expression for each document.

An error results if an attempt is made to sort by a field that has multiple
values for any of the documents in the sequence.

### `lux:highlight($node as node()?, $query as item(), $tag as item()?)` ###

returns the given node with text matching the query surrounded by the named
tag (or a B tag if no name is supplied).  The query may be a string or an
xml node of the same types supported by lux:search.

### `function lux:insert-document($uri as xs:string, $node as node()) as empty-sequence()` ###

inserts a document to the index at the given uri. lux:commit() must be called for the result
to become visible.

### `function lux:search($query as item(), $sort as xs:string?) as document-node()*` ###

executes a Lucene search query and returns documents.  If the query
argument is an element or document node, it is parsed using the
XmlQueryParser; otherwise its string value is parsed using the
LuxQueryParser.

A Lucene query parser extension that supports query terms of the form:

  {%raw%}{node}<{nodeName}:{term}{%endraw%}

In which nodeName is either empty, an unqualified element name, a prefixed
element name (ie a QName), or a QName prefixed with "@", indicating an
attribute. nodeName is optional: if it is not present, a full text query of
the entire document is indicated.  The "node" prefix is also
optional. Concrete examples:

     node<:"Alas, poor Yorick"
     node<title:Hamlet
     node<@id:s12340
     node<@xml:id:x2345
     node<math\:equation:3.14159
 
or, equivalently:
 
     <:"Alas, poor Yorick"
     <title:Hamlet
     <@id:s12340
     <@xml:id:x2345
     <math\:equation:3.14159

     lux:transform($stylesheet as node(), $context as node(), $params as item()*) as node()

transforms a node with an XSLT stylesheet.  Parameters are bound from the
$params argument, which must be an even-length list of alternating names
and values.  If the stylesheet produces a result that is not a single node,
an error will be thrown.

## File system functions ##

Lux implements a small (noncompliant) subset of the EXPath file functions.
These functions are declared in the http://expath.org/ns/file namespace,
which we reference using the "file" prefix.

### `file:exists($path as xs:string) as xs:boolean` ###

returns true iff the file at the given path exists

### `file:is-dir($path as xs:string) as xs:boolean` ###

returns true iff the file at the given path exists and is a directory

### `file:list($path as xs:string) as xs:string*` ###

If $path is a directory, returns the names of files (and directories) in
the directory in a system-dependent order. The directory itself and its
parent are not included in the list.  If $path is not a directory, or does
not exist, or an I/O error occurs, an empty list is returned.

## EXPath package support ##

If the system property org.expath.pkg.saxon.repo is defined, Compiler
attempts to initialize the EXPath package manager support for Saxon, using
the value of that property as the EXPath repository location.

This provides a mechanism for loading additional function library support,
such as the HTTP client and Zip file modules, which are provided with the
Lux app server.

## EXPath Request/Response Protocol

Lux provides full support for HTTP request/response handling within XQuery
by implementing the [EXPath webapp specification
draft](http://expath.org/spec/webapp/20130401)'s request/response protocol.
Note that the specification is a draft and may change; we intend to track
those changes here.  Also, there are currently a few variations from the
specification in Lux's implementation:

1. The HTTP request is made available as the value of the global variable $http:input (see example below), as in the specification, but is not also provided as the evaluation context for the query.  The evaluation context for queries in Lux remains the entire collection of documents.
2. Multipart requests are supported, providing access to the parsed request body (or bodies), but binary request parts are not supported, and multipart *responses* are not yet supported.
3. The EXPath specification requires that applications provide an http:response element.  Lux relaxes this requirement: if a query results in a single http:response element, then it is treated as an EXPath response: the attributes and content of this element control the content type, status code, HTTP headers, etc.  Otherwise, the current Lux behavior persists, and serialization is based on the lux.contentType parameter.

Note that this protocol subsumes the functions provided by the $lux:http
variable, which should now be considered as deprecated, and will eventually
be phased out in a future release.

The specification has a number of examples and a thorough explanation of
how this protocol functions, but here is a simple example that echoes back its
input:

        declare namespace http="http://expath.org/ns/webapp";
        declare variable $http:input external;
        <http:response status="200" message="OK">
          <http:body content-type="application/xml">{
              $http:input/http:request
          }</http:body>
        </http:response>
