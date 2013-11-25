lux_uri should be a simple path with URI-valid characters.  Lux doesn't actually enforce URI validity - you can store documents with almost any string as an identifier, but you may run into difficulties attempting to resolve relative references if you use URI's with weird characters like spaces.

Committing, transactions, etc.