package lux.xquery;

import lux.xpath.AbstractExpression;
import lux.xpath.LiteralExpression;
import lux.xpath.Namespace;

/**
 * represents an XQuery module, its static context (not counting any definitions in the enclosing context),
 * variables, function definitions, and optional body.
 */
public class XQuery {
    
    private final FunctionDefinition[] functionDefinitions;
    private final Namespace[] namespaceDeclarations;
    // private final VariableDeclaration[] externalVariables;
    private final AbstractExpression body;
    
    public XQuery (Namespace[] namespaceDeclarations, FunctionDefinition[] defs, AbstractExpression body) {
        this.namespaceDeclarations = namespaceDeclarations;
        this.functionDefinitions = defs;
        this.body = body;
    }
    
    public String toString () {
        StringBuilder buf = new StringBuilder();
        toString (buf);
        return buf.toString();
    }
    
    public void toString (StringBuilder buf) {
        for (Namespace ns : namespaceDeclarations) {
            if (ns.getPrefix().isEmpty()) {
                // handle this using specific mappings for element/function default namespaces
                continue;
                //buf.append("declare default element namespace ").append('=');                
            } else {
                buf.append("declare namespace ").append(ns.getPrefix()).append('=');
            }
            LiteralExpression.escapeString (ns.getNamespace(), buf);
            buf.append(";\n");
        }
        // TODO: collation, variables, modes, default function namespace, etc.
        if (functionDefinitions != null) {
            for (FunctionDefinition def : functionDefinitions) {
                def.toString(buf);
            }
        }
        body.toString(buf);
    }

    public AbstractExpression getBody() {
        return body;
    }

    public FunctionDefinition[] getFunctionDefinitions() {
        return functionDefinitions;
    }

    public Namespace[] getNamespaceDeclarations() {
        return namespaceDeclarations;
    }
    
}
