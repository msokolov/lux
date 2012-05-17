/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */

package lux.saxon;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.SlashExpression;
import net.sf.saxon.expr.sort.DocumentSorter;

public class Optimizer extends net.sf.saxon.expr.parser.Optimizer {
    
    private SaxonTranslator translator;    
    
    public Optimizer(Config config) {
        super(config);
        translator = new SaxonTranslator((Config) config);
    }
    
    /**
     * Make a conditional document sorter. This optimization is attempted
     * when a DocumentSorter is wrapped around a path expression. Saxon-HE doesn't
     * provide any optimizations of this sort, but this is critical for lux:search
     * expressions since they may potentially return a huge number of unused documents.
     * @param sorter the document sorter
     * @param path the path expression
     * @return the path expression, if it is proven to be in document order, 
     * or the original sorter unchanged when no such optimization is possible
     */
    @Override
    public Expression makeConditionalDocumentSorter(DocumentSorter sorter, SlashExpression path) {
        // TODO: this is kind of expensive since we generate a translated expression tree
        // for every sub-expression we attempt to optimize in this way.
        // But what can we do instead?  It seems we have to re-walk the Saxon Expression tree.
        // maybe we could simply use the visiting machinery in SaxonTranslator without bothering
        // to create the AbstractExpression tree...
        if (translator.exprFor(path).isDocumentOrdered())
            return path;
        return sorter;
    }

}
