package lux.xquery;

public class OrderByClause extends FLWORClause {

    private final SortKey[] sortKeys;
    
    public OrderByClause(SortKey[] sortKeys) {
        this.sortKeys = sortKeys;
    }

    @Override
    public void toString(StringBuilder buf) {
        buf.append ("order by ");
        sortKeys[0].toString(buf);
        for (int i = 1; i < sortKeys.length; i++) {
            buf.append(", ");
            sortKeys[i].toString(buf);
        }
    }

}
