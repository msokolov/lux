package lux.xquery;

import lux.xpath.AbstractExpression;
import lux.xpath.FunCall;
import lux.xpath.LiteralExpression;
import lux.xpath.Namespace;

import org.apache.commons.lang.StringUtils;

/**
 * represents an XQuery module, its static context (not counting any definitions in the enclosing context),
 * variables, function definitions, and optional body.
 */
public class XQuery {
    
    private final FunctionDefinition[] functionDefinitions;
    private final Namespace[] namespaceDeclarations;
    private final String defaultElementNamespace, defaultFunctionNamespace, defaultCollation;
    private final VariableDefinition[] externalVariables;
    private final ModuleImport[] importedModules;
    private final AbstractExpression body;
    private final String baseURI;
    private final Boolean preserveNamespaces;
    private final Boolean inheritNamespaces;
    private final boolean emptyLeast;
    
    public XQuery (String defaultElementNamespace, String defaultFunctionNamespace, String defaultCollation, 
            ModuleImport[] importedModules, Namespace[] namespaceDeclarations, VariableDefinition[] variableDefinitions, FunctionDefinition[] defs, 
            AbstractExpression body, String baseURI, Boolean copyNamespacesPreserve, Boolean copyNamespacesInherit, boolean emptyLeast) {
        this.namespaceDeclarations = namespaceDeclarations;
        this.externalVariables = variableDefinitions;
        this.defaultCollation = defaultCollation;
        this.defaultElementNamespace = defaultElementNamespace;
        this.defaultFunctionNamespace = defaultFunctionNamespace;
        this.functionDefinitions = defs;
        this.body = body;
        this.baseURI = baseURI;
        this.inheritNamespaces = copyNamespacesInherit;
        this.preserveNamespaces = copyNamespacesPreserve;
        this.emptyLeast = emptyLeast;
        this.importedModules = importedModules;
    }
    
    @Override
    public String toString () {
        StringBuilder buf = new StringBuilder();
        toString (buf);
        return buf.toString();
    }
    
    public void toString (StringBuilder buf) {
        if (inheritNamespaces != null || preserveNamespaces != null) {
            buf.append ("declare copy-namespaces ");
            if (preserveNamespaces == null || preserveNamespaces == false) {
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
        if (baseURI != null) {
            buf.append ("declare base-uri ");
            LiteralExpression.quoteString(baseURI, buf);
            buf.append(";\n");
        }
        if (StringUtils.isNotBlank(defaultCollation)) {
            buf.append("declare default collation ");
            LiteralExpression.quoteString(defaultCollation, buf);
            buf.append(";\n");
        }
        if (! emptyLeast) {
        	buf.append ("declare default order empty greatest;\n");
        }
        if (StringUtils.isNotBlank(defaultElementNamespace)) {
            buf.append("declare default element namespace ");
            LiteralExpression.quoteString(defaultElementNamespace, buf);
            buf.append(";\n");
        }
        if (StringUtils.isNotBlank(defaultFunctionNamespace)  && !defaultFunctionNamespace.equals(FunCall.FN_NAMESPACE)) {
            buf.append("declare default function namespace ");
            LiteralExpression.quoteString(defaultFunctionNamespace, buf);
            buf.append(";\n");
        }
        if (namespaceDeclarations != null) {
            OUTER:
            for (Namespace ns : namespaceDeclarations) {
                if (ns.getPrefix().isEmpty() || "xml".equals (ns.getPrefix())) {
                    // handle this using specific mappings for element/function default namespaces
                    continue;
                    //buf.append("declare default element namespace ").append('=');                
                }
                for (ModuleImport importedModule : importedModules) {
                    if (importedModule.getPrefix().equals(ns.getPrefix())) {
                        continue OUTER;
                    }
                }
                buf.append("declare namespace ").append(ns.getPrefix()).append('=');
                LiteralExpression.quoteString (ns.getNamespace(), buf);
                buf.append(";\n");
            }
        }
        if (importedModules != null) {
            for (ModuleImport importedModule : importedModules) {
                importedModule.toString(buf);
            }
        }
        if (externalVariables != null) {
            for (VariableDefinition def : externalVariables) {
                def.toString(buf);
            }
        }
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

    public String getBaseURI() {
        return baseURI;
    }

    public boolean isEmptyLeast() {
        return emptyLeast;
    }

    public ModuleImport[] getModuleImports() {
        return importedModules;
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
