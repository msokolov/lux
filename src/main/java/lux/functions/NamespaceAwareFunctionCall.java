package lux.functions;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.StaticContext;
import net.sf.saxon.expr.instruct.SavedNamespaceContext;
import net.sf.saxon.lib.ExtensionFunctionCall;
import net.sf.saxon.om.NamespaceResolver;

public abstract class NamespaceAwareFunctionCall extends ExtensionFunctionCall {

    private NamespaceResolver namespaceResolver;
    
    public NamespaceResolver getNamespaceResolver() {
        return namespaceResolver;
    }

    @Override
    public void supplyStaticContext (StaticContext context, int locationId, Expression[] arguments) {
        namespaceResolver = context.getNamespaceResolver();
        if (!(namespaceResolver instanceof SavedNamespaceContext)) {
            namespaceResolver = new SavedNamespaceContext(namespaceResolver);
        }
    }
    
    @Override
    public void copyLocalData (ExtensionFunctionCall destination) {
        ((NamespaceAwareFunctionCall) destination).namespaceResolver = namespaceResolver;
    }
    
}
