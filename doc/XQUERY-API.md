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

## Indexing and Search functions ##

Functions relating to search and indexing are declared in the
http://luxdb.net namespace, which we always reference using the prefix
"lux".

### `function lux:commit() as empty-sequence()` ###

commits pending updates to the index and blocks until the operation is complete.

### `function lux:count($query as item(), $hints as xs:int?) as xs:integer` ###

counts the number of results of a search.  It is faster and uses less memory 
than calling fn:count() on the search results themselves because it does not need to load
the result documents in memory.  See lux:search() for an explanation of the supported
$query formats.

### `function lux:delete($uri as xs:string?) as empty-sequence()` ###

deletes a document from the index at the given uri.  NOTE: if the $uri document
is empty, *all documents* will be deleted.  This "feature" will be removed in a later release.

### `function lux:exists($query as item(), $hints as xs:int?) as xs:integer` ###

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

### `function lux:field-values($field-name as xs:string, $node as node()) as xs:anyAtomicItem*` ###

accepts the name of a lucene field and optionally, a node, and returns any
stored value(s) of the field for the document containing the node, or the
context item if no node is specified.

If the node (or context item) is not a node drawn from the index, lux:field
will return the empty sequence.

Order by expressions containing lux:field-values calls are subject to
special optimization and are often able to be implemented by
index-optimized sorting in Lucene (only for string-valued fields).  An
error results if an attempt is made to sort by a field that has multiple
values for any of the documents in the sequence.

### `lux:highlight($query as item(), $node as node())` ###

returns the given node with text matching the query surrounded by B tags.
The query may be a string or an element/document of the same types
supported by lux:search.

### `function lux:insert-document($uri as xs:string, $node as node()) as empty-sequence()` ###

inserts a document to the index at the given uri. lux:commit() must be called for the result
to become visible.

### `function lux:search($query as item(), $hints as xs:integer, $sort as xs:string?) as document-node()*` ###

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

## EXPath support ##

If the system property org.expath.pkg.saxon.repo is defined, Compiler
attempts to initialize the EXPath package manager support for Saxon, using
the value of that property as the EXPath repository location.

This provides a mechanism for loading additional function library support,
such as the HTTP client and Zip file modules, which are provided with the
Lux app server.

