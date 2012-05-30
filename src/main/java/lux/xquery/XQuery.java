package lux.xquery;

import org.apache.commons.lang.StringUtils;

import lux.xpath.AbstractExpression;
import lux.xpath.FunCall;
import lux.xpath.LiteralExpression;
import lux.xpath.Namespace;

/**
 * represents an XQuery module, its static context (not counting any definitions in the enclosing context),
 * variables, function definitions, and optional body.
 */
public class XQuery {
    
    private final FunctionDefinition[] functionDefinitions;
    private final Namespace[] namespaceDeclarations;
    private final String defaultElementNamespace, defaultFunctionNamespace, defaultCollation;
    private final VariableDefinition[] externalVariables;
    private final AbstractExpression body;
    
    private final Boolean preserveNamespaces;
    private final Boolean inheritNamespaces;
    
    public XQuery (String defaultElementNamespace, String defaultFunctionNamespace, String defaultCollation, 
            Namespace[] namespaceDeclarations, VariableDefinition[] variableDefinitions, FunctionDefinition[] defs, 
            AbstractExpression body, Boolean copyNamespacesPreserve, Boolean copyNamespacesInherit) {
        this.namespaceDeclarations = namespaceDeclarations;
        this.externalVariables = variableDefinitions;
        this.defaultCollation = defaultCollation;
        this.defaultElementNamespace = defaultElementNamespace;
        this.defaultFunctionNamespace = defaultFunctionNamespace;
        this.functionDefinitions = defs;
        this.body = body;
        this.inheritNamespaces = copyNamespacesInherit;
        this.preserveNamespaces = copyNamespacesPreserve;
    }
    
    public XQuery (AbstractExpression body) {
        this.namespaceDeclarations = null;
        this.externalVariables = null;
        this.defaultCollation = null;
        this.defaultElementNamespace = null;
        this.defaultFunctionNamespace = null;
        this.functionDefinitions = null;
        this.body = body;
        this.inheritNamespaces = null;
        this.preserveNamespaces = null;
    }
    
    public String toString () {
        StringBuilder buf = new StringBuilder();
        toString (buf);
        return buf.toString();
    }
    
    public void toString (StringBuilder buf) {
        if (inheritNamespaces != null || preserveNamespaces != null) {
            buf.append ("declare copy-namespaces ");
            if (preserveNamespaces != null && preserveNamespaces == false) {
                buf.append ("no-preserve, ");
            } else {
                buf.append ("preserve, ");
            }
            if ((inheritNamespaces == null) || (inheritNamespaces == true)) {
                buf.append ("inherit");
            } else {
                buf.append ("no-inherit");                
            }
            buf.append (";\n");
        }
        if (StringUtils.isNotBlank(defaultCollation)) {
            buf.append("declare default collation \"").append(defaultCollation).append("\";\n");
        }
        if (StringUtils.isNotBlank(defaultElementNamespace)) {
            buf.append("declare default element namespace \"").append(defaultElementNamespace).append("\";\n");
        }
        if (StringUtils.isNotBlank(defaultFunctionNamespace)  && !defaultFunctionNamespace.equals(FunCall.FN_NAMESPACE)) {
            buf.append("declare default function namespace \"").append(defaultFunctionNamespace).append("\";\n");
        }
        if (namespaceDeclarations != null) {
            for (Namespace ns : namespaceDeclarations) {
                if (ns.getPrefix().isEmpty() || "xml".equals (ns.getPrefix())) {
                    // handle this using specific mappings for element/function default namespaces
                    continue;
                    //buf.append("declare default element namespace ").append('=');                
                } else {
                    buf.append("declare namespace ").append(ns.getPrefix()).append('=');
                }
                LiteralExpression.escapeString (ns.getNamespace(), buf);
                buf.append(";\n");
            }
        }
        if (externalVariables != null) {
            for (VariableDefinition def : externalVariables) {
                def.toString(buf);
            }
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

    public VariableDefinition[] getVariableDefinitions() {
        return externalVariables;
    }
    
    public String getDefaultCollation () {
        return defaultCollation;
    }

    public String getDefaultElementNamespace () {
        return defaultElementNamespace;
    }
    
    public String getDefaultFunctionNamespace () {
        return defaultFunctionNamespace;
    }

    public Boolean isPreserveNamespaces() {
        return preserveNamespaces;
    }

    public Boolean isInheritNamespaces() {
        return inheritNamespaces;
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
