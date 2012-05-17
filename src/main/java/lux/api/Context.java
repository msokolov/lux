/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */

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
