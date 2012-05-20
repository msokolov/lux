/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */

package lux.saxon;

/**
 * Extends saxon Configuration providing lux-specific configuration.  It provides a function library,
 * so that we can declare functions as returning sequences sorted in document order.  It provides
 * an Optimizer extending Saxon's so that we can make use of such an optimization, and a DocumentNumberAllocator
 * that ensures that document ids are assigned in increasing document order.  Document order is defined
 * by Lucene's internal docid ordering.  This ordering is not stable across multiple queries, but that's OK for the 
 * purpose of optimizing ordering operations within a single query.
 * 
 * This Configuration also provides empty uri resolvers: it returns empty documents for every URI.  This is
 * useful for formally satisfying DOCTYPE declarations, at least.
 */

import java.io.ByteArrayInputStream;
import java.io.IOException;

import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.URIResolver;
import javax.xml.transform.stream.StreamSource;

import lux.functions.LuxFunctionLibrary;


import net.sf.saxon.Configuration;
import net.sf.saxon.lib.ExtensionFunctionDefinition;

import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

public class Config extends Configuration implements URIResolver, EntityResolver {

  private LuxFunctionLibrary luxFunctionLibrary;

    public Config () {
        super();
        luxFunctionLibrary = new LuxFunctionLibrary();;
        // This prevents expansion of internal "parameter entities" among other things
        // getParseOptions().setEntityResolver(this);
        optimizer = new Optimizer(this);
    }
    
    public Source resolve(String href, String base) throws TransformerException {
        return new StreamSource (new ByteArrayInputStream (new byte[0]));
    }

    // disable dtd processing
    public InputSource resolveEntity(String publicId, String systemId) throws SAXException, IOException {
        return new InputSource (new ByteArrayInputStream (new byte[0]));
    }
    
    public DocIDNumberAllocator getDocumentNumberAllocator() {
        return (DocIDNumberAllocator) super.getDocumentNumberAllocator();
    }

  @Override
  public LuxFunctionLibrary getIntegratedFunctionLibrary () {
    return luxFunctionLibrary;
  }

  /** register functions with the lux function library */
  @Override
  public void registerExtensionFunction(ExtensionFunctionDefinition function) {
    getIntegratedFunctionLibrary().registerFunction(function);
  }
  
}