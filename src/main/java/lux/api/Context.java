package lux.api;

import lux.lucene.LuxSearcher;


/**
 * A place to hold configuration. function bindings, external variable bindings, namespace bindings
 * URI resolvers and the like.
 * 
 * @author sokolov
 *
 */
public interface Context {

    String getXmlFieldName();

    LuxSearcher getSearcher();
}
