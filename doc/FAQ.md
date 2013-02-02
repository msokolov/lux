# FAQ #

This page lists some nonobvious quirks and idiosyncracies, with solutions
or workarounds where available.

## Why doesn't it work when I call lux:log(), ##
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

## Why do absolute paths (like //document) work only in the outer scope, ##
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

## I want to use Lux with Saxon PE/EE.  How do I do that? ##

Lux will work with any (9.x) version of Saxon.  It inspects the loaded
classes and attempts to instantiate a licensed Saxon Processor if it
detects that the you have a non-HE version installed.  However, there are
some caveats about using Lux with PE/EE that you should be aware of if you
choose to do this.

### Eager evaluation of document-ordered sequences ### 

Search result sets that need to be document-ordered cannot be evaluated
lazily in Lux when using Saxon PE/EE.

This means a simple xpath like:

       (//foo)[1]

has to be evaluated by retrieving every foo element, sorting them in document
order (which is a no-op), before retrieving the first element.

With Saxon-HE we are able to arrange for expressions like this to be
evaluated lazily by supplying a custom Configuration object, which supplies
Lux's Optimizer and function library.  However Saxon-PE/EE have their own
Configuration, which cannot be replaced without losing PE/EE licensed
functionality, and any optimizations they may provide are not applicable to
to Lux search result sequences.

## What is the Lux security model? ##

Currently there is no security model for Lux.

Our philosophy about security is that, because it just gets in your way, it
should not be the first area of concern. We should make sure everything is
working well and serving a need before we go about figuring out how to make
it inaccessible.  Later on when we have valuables, we can throw up barriers
around them.

The next obvious step to take to secure Lux is to enable authz in the app
server, and to provide a way to restrict access to the /solr urls that it
exposes, which enable unfettered access to all database content.  The next
area of concern for security is document-level security: document ownership
and rights, and possibly even functional rights (and the users and roles
that would underpin all this).

