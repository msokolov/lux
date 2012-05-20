package lux.xquery;

public class OrderByClause extends FLWORClause {

    private final SortKey[] sortKeys;
    
    public OrderByClause(SortKey[] sortKeys) {
        this.sortKeys = sortKeys;
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append ("order by ");
        buf.append (sortKeys[0].toString());
        for (int i = 1; i < sortKeys.length; i++) {
            buf.append(", ");
            buf.append(sortKeys[i].toString());
        }
        return buf.toString();
    }

}
