package lux;

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

import lux.compiler.SaxonTranslator;
import lux.functions.LuxFunctionLibrary;
import net.sf.saxon.Configuration;
import net.sf.saxon.lib.ExtensionFunctionDefinition;

import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * Config provides an Optimizer and a FunctionLibrary to Saxon.  The Optimizer identifies certain
 * expressions as "already sorted", enabling Saxon to skip an expensive sorting operation.  The 
 * FunctionLibrary performs a similar function, allowing certain functions to be identified as returning
 * results in document order, so they won't need to be sorted again.  These optimizations do more than 
 * simply skip a (no-op) sorting step: they also enable Saxon to evaluate these sorted sequences 
 * lazily: if the sequences needed to be sorted, the entire sequence would have to be retrieved.
 */
public class Config extends Configuration implements EntityResolver {

    private final LuxFunctionLibrary luxFunctionLibrary;
    
    public Config () {
        super();
        luxFunctionLibrary = new LuxFunctionLibrary();
        optimizer = new Optimizer(this, new SaxonTranslator(this));
    }

    /** This resolver effectively ignores DOCTYPE declarations by returning an empty stream for every entity.
     */
    public InputSource resolveEntity(String publicId, String systemId) throws SAXException, IOException {
        return new InputSource (new ByteArrayInputStream (new byte[0]));
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

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
