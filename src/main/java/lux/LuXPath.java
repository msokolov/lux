package lux;

import java.util.List;

import org.jaxen.BaseXPath;
import org.jaxen.Context;
import org.jaxen.JaxenException;
import org.jaxen.Navigator;
import org.jaxen.jdom.DocumentNavigator;
import org.jaxen.expr.Expr;

/**
 * Executes XPath queries against a Lux (Lucene XML) datastore.
 */
public abstract class LuXPath extends BaseXPath
{
    /**
     * Creates an XPathSolr backed by the default org.w3c.DOM-based XPath
     * Navigator implementation.
     * @param xpathExpr the expression to evaluate
     */
    public LuXPath(String xpathExpr) throws JaxenException {
        super (xpathExpr, DocumentNavigator.getInstance());
    }

    /**
     * Creates an XPathSolr 
     * @param xpathExpr the expression to evaluate
     * @param navigator the supporting XPath Navigator implementation. This
     * is used to evaluate any expressions that are not resolvable directly
     * (ie out of the index).
     */
    public LuXPath(String xpathExpr, Navigator navigator) throws JaxenException {
        super (xpathExpr, navigator);
    }

    /** If the context is a Collection, then execute the xpath expression
     * against all documents in the Collection.  Otherwise, evaluate the
     * XPath normally.
     *
     * @param context the Context which gets evaluated
     *
     * @return the node-set of all items selected by this XPath expression
     * @throws JaxenException if an XPath error occurs during expression evaluation
     *
     */
    protected List<?> selectNodesForContext(Context context) throws JaxenException
    {
        if (context instanceof Collection) {
        	XPathQuery xpq = getQuery (getRootExpr(), context);
        	// TODO exec the query against lucene
        	if (xpq.isMinimal()) {
        		// TODO return the query results as a list of values
        		// of the appropriate type
        	}
        	// TODO: foreach result, execute the xpath against it as a document and return the 
        	// resulting nodes
        	return null;
        } else {
            return super.selectNodesForContext (context);
        }
    }
    
    protected abstract XPathQuery getQuery (Expr expr, Context context) throws JaxenException;

}
