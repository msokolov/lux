package lux.xpath;

/**
 * Represents an equivalence class of expressions; Each PropEquiv wraps an AbstractExpression;
 * two PropEquivs are equal() iff their respective expressions are equivalent(), which is to say they 
 * are of the same type and have the same local properties.
 */
public class PropEquiv {

	private AbstractExpression expr;
	
	public PropEquiv (AbstractExpression expr) {
		this.expr = expr;
	}
	
	public void setExpression (AbstractExpression expr) {
		this.expr = expr;
	}
	public AbstractExpression getExpression() {
		return expr;
	}
	
	@Override 
	public boolean equals (Object other) {
		return other != null && (other instanceof PropEquiv) && 
				(other == this || expr.equivalent(((PropEquiv)other).expr));
	}
	
	@Override
	public int hashCode() {
		return expr.equivHash();
	}
	
}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
