package lux.api;

import lux.xml.XmlBuilder;

/** Represents an expression engine: interpreter, compiler, evaluator */
/*
 * TODO: add getBuilder(), Context.getXmlFieldName(), setContextItem(), 
 * add a method to return an iterator: evalResults(Expression)
 */
public abstract class Evaluator {
    
    private Context context;
    
    protected QueryStats queryStats;
    
    public abstract Expression compile (String exprString) throws LuxException;
    
    public abstract Object evaluate (Expression expr);

    public abstract Object evaluate (Expression xpath, Object contextItem);
    
    public abstract Iterable<?> iterate (Expression xpath, Object contextItem);

    /**
     * Return a builder that creates document models in a form that is useful for this 
     * Evaluator.  Implementations are not expected to provide pooling or thread-safety
     * guarantees; it is expected that the Evaluator is pooled and allocated per thread
     * and will cache a reader.
     * @return
     */
    public abstract XmlBuilder getBuilder();

    public QueryStats getQueryStats() {
        return queryStats;
    }
    
    public void setContext(Context context) {
        this.context =  context;
    }
    
    public Context getContext() {
        return context;
    }

}
