package lux;

import org.apache.lucene.document.FieldSelector;
import org.apache.lucene.document.FieldSelectorResult;

public final class SingleFieldSelector implements FieldSelector {

    private final String fieldName;

    public SingleFieldSelector (String fieldName) {
        this.fieldName = fieldName;
    }

    public FieldSelectorResult accept(String fieldName) {
        return this.fieldName.equals(fieldName) ? FieldSelectorResult.LOAD : FieldSelectorResult.NO_LOAD;
    }

}

