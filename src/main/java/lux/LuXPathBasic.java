package lux;

import java.util.List;

import lux.XPathQuery.ValueType;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.TermQuery;
import org.jaxen.saxpath.Axis;
import org.jaxen.Context;
import org.jaxen.JaxenException;
import org.jaxen.expr.*;

public class LuXPathBasic extends LuXPath {
	
    private boolean namespaceAware = false;    private String attrQNameField = "lux_att_name";
    private String elementQNameField = "lux_elt_name";

    /**
     * @return whether element and attribute names (QNames) are processed in a namespace-aware manner.
     * 
     * <p>If true, then QName prefixes are mapped to namespaces using the
     * mapping provided by the Context, and queries are performed using a
     * field name formed from the namespace uri combined with the local
     * name. In this case, it is an error if a prefix appearing in a QName
     * does not have a mapping defined.</p>
     * 
     * <p>If namespaceAware is false (the default), then any
     * prefix-to-namespace mapping in the Context is ignored, and index
     * lookups are performed using a simple concatenation of prefix, ':', and
     * local name (ie as the prefixed names appear in serialized XML).  </p>
     * 
     * <p>Operating without namespace awareness breaks conformance with
     * widely-accepted XML standards.  However in almost all cases it
     * provides identical results with less effort.</p>
     * 
     * <p>In either case, the setting must match the index configuration in
     * order to get sensible results.<p>
     */
    public boolean isNamespaceAware() {
        return namespaceAware;
    }

    public void setNamespaceAware(boolean namespaceAware) {
        this.namespaceAware = namespaceAware;
    }

    public LuXPathBasic(String xpathExpr) throws JaxenException {
        super(xpathExpr);
    }

    @Override
    public XPathQuery getQuery(Expr expr, Context context) throws JaxenException {
        XPathQuery query = getRootQuery(expr, context);
        query.setXPath(this);
        return query;
    }

    private XPathQuery getRootQuery(Expr expr, Context context) throws JaxenException {
        if (expr == null) {
            return XPathQuery.EMPTY;
        }
        if (expr instanceof PathExpr) {
            return getQuery ((PathExpr) expr, context);
        }
        if (expr instanceof BinaryExpr) {
            return getQuery ((BinaryExpr) expr, context);
        }
        if (expr instanceof FilterExpr) {
            return getQuery ((FilterExpr) expr, context);
        }
        if (expr instanceof LocationPath) {
            return getQuery ((LocationPath) expr, context);
        }
        if (expr instanceof UnaryExpr) {
            return getQuery ((UnaryExpr) expr, context);
        }
        // FunctionCallExpr
        // LiteralExpr
        // NumberExpr
        // VariableReferenceExpr
        return XPathQuery.UNINDEXED;
    }
	
    protected XPathQuery getQuery (PathExpr pathExpr, Context context) throws JaxenException {
        Expr filterExpr = pathExpr.getFilterExpr();
        LocationPath locationPath = pathExpr.getLocationPath();

        XPathQuery filterQuery = getQuery(filterExpr, context);

        if (locationPath == null)
            return filterQuery;
		
        XPathQuery locationQuery = getQuery(locationPath, context);
				
        return filterQuery.combine (locationQuery, Occur.MUST);
    }
	
    protected XPathQuery getQuery (BinaryExpr binaryExpr, Context context) throws JaxenException {
        if (binaryExpr instanceof UnionExpr) {
            return getQuery ((UnionExpr) binaryExpr, context);
        }
        // AdditiveExpr
        // MultiplicativeExpr
        // EqualityExpr
        // RelationalExpr
        XPathQuery lhsQuery = getQuery(binaryExpr.getLHS(), context);
        XPathQuery rhsQuery = getQuery(binaryExpr.getRHS(), context);
        String operator = binaryExpr.getOperator();
        Occur occur = operator.equals ("or") ? Occur.SHOULD : Occur.MUST;
        return lhsQuery.combine (rhsQuery, occur, ValueType.ATOMIC);
    }

    protected XPathQuery getQuery (UnionExpr unionExpr, Context context) throws JaxenException {
        XPathQuery lhsQuery = getQuery(unionExpr.getLHS(), context);
        XPathQuery rhsQuery = getQuery(unionExpr.getRHS(), context);
        return rhsQuery.combine (lhsQuery, Occur.SHOULD);
    }

    protected XPathQuery getQuery (FilterExpr filterExpr, Context context) throws JaxenException {
        XPathQuery query = getQuery (filterExpr.getExpr(), context);
        return applyPredicates (filterExpr, query, context);
    }

    protected XPathQuery getQuery (UnaryExpr unaryExpr, Context context) throws JaxenException {
        return getQuery (unaryExpr.getExpr(), context);
    }

    protected XPathQuery getQuery (LocationPath locationPath, Context context) throws JaxenException {
        XPathQuery query = XPathQuery.EMPTY;
        boolean isMinimal = true;
        List<?> steps = locationPath.getSteps();
        int istep = 0;
        for (Object ostep : steps) {
            Step step = (Step) ostep;            
            /*
             * the path to an element can be expressed minimally by a simple QName query if it consists of some expression that matches only
             * the document node followed by a single descendant-or-self, any number of reflexive steps, a name step,
             * and any number of reflexive steps.  So basically you can have a single non-reflexive descendant-or-self step surrounded
             * by reflexive steps.
             * 
             * As we traverse the path we have several states: (1) empty, (2) having seen 1 minimal non-empty step, (3) non-minimal
             * We go from 1->2 when seeing //{non-empty name step}, and from 2->3 on any non-empty step.   
             * (1) <-> query.isEmpty(); (2) <-> isMinimal=true, query not empty. (3) <-> isMinimal = false
             * 
             * Minimal paths to attributes may include one additional step: a "*"
             * 
             * NOTE: this is all sort of an interesting exercise, but computing this for more
             * complicated indexing structures could be kind of nightmarish!
             */
            if (isMinimal == true) {
                //if (! query.isEmpty()) {
                if (! (query.getQuery() instanceof MatchAllDocsQuery)) {
                    if (! isEmptyStep (step)) {
                        isMinimal = false;
                    }
                } else if (!isReflexiveAxis (step.getAxis())) {
                    if (! (step.getAxis() == Axis.CHILD || step.getAxis() == Axis.ATTRIBUTE)) {
                        isMinimal = false;
                    } else if (istep == 0) {
                        if (step.getAxis() == Axis.CHILD) {                    
                            // not minimal since the path is not relocatable - it has a step that is a fixed distance from the root
                            isMinimal = false;
                        } // however if axis == ATTRIBUTE this is still minimal [@foo]
                    } else {
                        Step lastStep = (Step) steps.get(istep-1);
                        if (! ((lastStep.getAxis() == Axis.DESCENDANT_OR_SELF && lastStep instanceof AllNodeStep)
                               || 
                               (step.getAxis() == Axis.ATTRIBUTE &&
                                  (lastStep instanceof AllNodeStep ||
                                        (lastStep instanceof NameStep && "*".equals (((NameStep) lastStep).getLocalName())))))) {
                            isMinimal = false;
                        }
                    }
                }
            }
            XPathQuery stepQuery=getQuery (step, context, isMinimal);
            query = stepQuery.combine(query, Occur.MUST);
            ++istep;
        }
        return query;
    }

    private static final boolean isEmptyStep(Step step) {
        int axis = step.getAxis();
        if ((step instanceof AllNodeStep) && 
                isReflexiveAxis(axis)) {
            return true;
        }
        return false;
    }

    private static final boolean isReflexiveAxis(int axis) {
        return axis == Axis.SELF || axis == Axis.ANCESTOR_OR_SELF || axis == Axis.DESCENDANT_OR_SELF;
    }

    private XPathQuery getQuery(Step step, Context context, boolean isMinimal) throws JaxenException {
        XPathQuery query = null;
        if (step instanceof AllNodeStep) {
            if (isMinimal) {
                query = XPathQuery.EMPTY;
            } else {
                query = new XPathQuery (new MatchAllDocsQuery(), false, ValueType.NODE);
            }
        }
        else if (step instanceof NameStep) {
            query = getQuery ((NameStep) step, context, isMinimal);
        }
        else if (step instanceof TextNodeStep) {
            query = new XPathQuery (new MatchAllDocsQuery(), false, ValueType.TEXT);
        }
        /*
          else if (step instanceof CommentNodeStep) {
          query = getQuery ((CommentNodeStep) step, context);
          }
          else if (step instanceof ProcessingInstructionNodeStep) {
          query = getQuery ((ProcessingInstructionNodeStep) step, context);
          }
        */
        else {
            return XPathQuery.UNINDEXED;
        }
        query = applyPredicates(step, query, context);
        return query;
    }

    private XPathQuery applyPredicates(Predicated predicated, XPathQuery query, Context context) throws JaxenException {
        List<?> predicates = predicated.getPredicates();
        for (Object o : predicates) {
            Predicate predicate = (Predicate) o;
            XPathQuery predicateQuery = getQuery (predicate.getExpr(), context);
            query = query.combine(predicateQuery, Occur.MUST);
        }
        return query;
    }
	
    protected XPathQuery getQuery (NameStep nameStep, Context context, boolean isMinimal) throws JaxenException {

        int axis = nameStep.getAxis();
        if (axis == Axis.NAMESPACE) { // NAMESPACE_DECL? 
            return XPathQuery.UNINDEXED;
        }
        ValueType valueType = (axis == Axis.ATTRIBUTE) ? ValueType.ATTRIBUTE : ValueType.ELEMENT;
        String prefix = nameStep.getPrefix();
        String localName = nameStep.getLocalName();
        String qname = null;
        if (prefix.isEmpty()) {
            if (localName.equals("*")) {
                return new XPathQuery (new MatchAllDocsQuery(), isMinimal, valueType);
            }
            qname = localName;
        }
        else if (namespaceAware) {
            String namespace = context.translateNamespacePrefixToUri(prefix);
            if (namespace == null) {
                throw new JaxenException ("undeclared namespace prefix '" + prefix + "'");				 
            }
            qname = '{' + namespace + '}' + localName;
        }
        else {
            qname = prefix + ':' + localName;
        }
        String fieldName = (axis == Axis.ATTRIBUTE) ? attrQNameField : elementQNameField;
        return new XPathQuery (new TermQuery (new Term (fieldName, qname)), isMinimal, valueType);
    }

        public String getAttrQNameField() {
        return attrQNameField;
    }

    public void setAttrQNameField(String attrQNameField) {
        this.attrQNameField = attrQNameField;
    }

    public String getElementQNameField() {
        return elementQNameField;
    }

    public void setElementQNameField(String elementQNameField) {
        this.elementQNameField = elementQNameField;
    }

}
