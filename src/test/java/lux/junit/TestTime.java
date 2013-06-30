package lux.junit;

class TestTime {
    TestTime (String condition, String query, int n) {
        this.condition = condition;
        this.query = query;
        this.times = new long[n];
    }
    
    String condition;
    String query;
    long [] times;
    
    long meanTime() {
        long total = 0;
        for (long t : times) {
            total += t;
        }
        return total / times.length;
    }
    
    @Override public String toString () {
        return String.format ("%s %s %d", condition, query, meanTime()/1000000);
    }
    
    public String comparison (TestTime other) {
        return String.format ("%s\t%d\t%d\t%.2f", query, 
                other.meanTime()/1000000, meanTime()/1000000, 
                100 * ((other.meanTime() - meanTime()) / ((double)other.meanTime())));
    }
}
