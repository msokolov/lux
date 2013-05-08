## Changes in Lux release 0.8

### binary XML storage

This release includes support for a high-speed binary XML storage format,
TinyBinary.  When documents are stored as TinyBinary, we can avoid
virtually all of the overhead of XML parsing and serialization when
retrieving and storing documents from and to the index.  The default
storage format is still serialized XML. In order to use TinyBinary w/solr,
add the following element:

    <str name="xml-format">tiny</str>

to the Lux updateRequestProcessorChain definition in solrconfig.xml. Note
that older versions of Lux cannot read the tiny binary format: once you
have stored TinyBinary documents in an index, you must upgrade any Lux
clients that connect to it to 0.8 or later.

