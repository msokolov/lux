package lux.index.field;

import java.util.Iterator;
import java.util.Map.Entry;

import lux.index.XmlIndexer;

import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.document.Field.Store;

/**
 * Indexes each occurrence of each path as a separate term
 * TODO: also store freqs (but not positions), so as to enable path-cardinality queries
 */
public class PathOccurrenceField extends FieldDefinition {
    
    private static final PathOccurrenceField instance = new PathOccurrenceField();
    
    public static PathOccurrenceField getInstance() {
        return instance;
    }
    
    protected PathOccurrenceField () {
        super ("lux_path", new KeywordAnalyzer(), Store.NO, Type.STRING);
    }
    
    @Override
    public Iterable<?> getValues(XmlIndexer indexer) {
        return new PathOccurrenceIterator (indexer);
    }
    
    class PathOccurrenceIterator implements Iterable<String>, Iterator<String> {
        private Iterator<Entry<String, Integer>> pathCounts;
        private Entry<String, Integer> pathCount;
        private int iPathCount;
        
        public PathOccurrenceIterator(XmlIndexer indexer) {
            pathCounts = indexer.getPathMapper().getPathCounts().entrySet().iterator();
            if (pathCounts.hasNext()) {
                pathCount = pathCounts.next();
            }
            iPathCount = 0;
        }
        
        @Override
        public Iterator<String> iterator() {
            // better only call this once!
            return this;
        }

        @Override
        public boolean hasNext() {
            return pathCounts.hasNext() || pathCount != null;
        }

        @Override
        public String next() {
            StringBuilder buf = new StringBuilder();
            String path = pathCount.getKey(); 
            String [] names = path.split(" ");
            if (names.length > 1) {
                buf.append (names[names.length-1]);
                // stop at 1 so we trim off leading "{}", reverse the names and splice with "/"
                for (int i = names.length-2; i > 0; i--) {
                    // in reverse order
                    buf.append ('/');
                    buf.append (names[i]);
                }
            }
            // advance the iteration
            if (iPathCount++ >= pathCount.getValue()) {
                iPathCount = 0;
                if (pathCounts.hasNext()) {
                    pathCount = pathCounts.next();
                } else {
                    pathCount = null;
                }
            }
            return buf.toString();
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
        
    }

}
