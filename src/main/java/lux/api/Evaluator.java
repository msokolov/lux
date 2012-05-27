package lux.api;

import lux.index.XmlIndexer;
import lux.lucene.LuxSearcher;
import lux.xml.XmlBuilder;

/** Represents an expression engine: interpreter, compiler, evaluator 
 * 
 * compile(string) -> CompiledQuery
 *   compileInternal() -> translate() -> aex -> optimize() -> toString() -> compileInternal()
 *   
 * evaluate(CompiledQuery) -> results; Object, Iterable<?>
 * 
 * */
public abstract class Evaluator {
    
    protected QueryStats queryStats;
    
    private LuxSearcher searcher;
    
    private XmlIndexer indexer; 
    
    public Evaluator (LuxSearcher searcher, XmlIndexer indexer) {
        this.searcher = searcher;
        this.indexer = indexer;
    }
    
    public abstract Expression compile (String exprString) throws LuxException;
    
    public abstract Object evaluate (Expression expr);

    public abstract Object evaluate (Expression xpath, QueryContext context);
    
    public abstract Iterable<?> iterate (Expression xpath, QueryContext context);
    
    /**
     * declare a prefix-namespace binding that will remain in effect for all expressions
     * evaluated by this Evaluator. Bindings can be undone by passing an empty namespace.
     * @param prefix the namespace prefix to bind
     * @param namespace the namespace (sometimes referred to as namespace URI) to bind to the prefix
     */
    public abstract void declareNamespace (String prefix, String namespace);

    /**
     * Return a builder that creates document models in a form that is useful for this 
     * Evaluator.  Implementations are not expected to provide pooling or thread-safety
     * guarantees; it is expected that the Evaluator is pooled and allocated per thread
     * and will cache a builder.
     * @return
     */
    public abstract XmlBuilder getBuilder();

    public QueryStats getQueryStats() {
        return queryStats;
    }
    
    public void setQueryStats (QueryStats stats) {
        queryStats = stats;
    }

    public LuxSearcher getSearcher() {
        return searcher;
    }
    
    public XmlIndexer getIndexer () {
        return indexer;
    }
    
    public void setSearcher(LuxSearcher searcher) {
        this.searcher = searcher;
    }
    
    public void setIndexer (XmlIndexer indexer) {
        this.indexer = indexer;
    }
    
}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
