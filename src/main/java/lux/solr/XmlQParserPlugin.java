package lux.solr;

import java.io.ByteArrayInputStream;
import java.nio.charset.Charset;

import lux.query.parser.XmlQueryParser;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.queryparser.xml.ParserException;
import org.apache.lucene.search.Query;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.search.QParser;
import org.apache.solr.search.QParserPlugin;
import org.apache.solr.search.SyntaxError;

public class XmlQParserPlugin extends QParserPlugin {

    @Override
    public void init(@SuppressWarnings("rawtypes") NamedList args) {
    }

    @Override
    public QParser createParser(String qstr, SolrParams localParams, SolrParams params, SolrQueryRequest req) {
        return new XmlQParser (qstr, localParams, params, req);
    }
    
    class XmlQParser extends QParser {
        
        private XmlQueryParser xmlParser;

        public XmlQParser(String qstr, SolrParams localParams, SolrParams params, SolrQueryRequest req) {
            super(qstr, localParams, params, req);
        }
        
        @Override
        public Query parse() throws SyntaxError {
            if (qstr == null || qstr.length()==0) return null;

            String defaultField = getParam(CommonParams.DF);
            if (defaultField==null) {
              defaultField = getReq().getSchema().getDefaultSearchFieldName();
            }
            xmlParser = new XmlQueryParser(defaultField, new StandardAnalyzer(req.getCore().getSolrConfig().luceneMatchVersion));

            try {
                return xmlParser.parse(new ByteArrayInputStream(qstr.getBytes(Charset.forName("utf-8"))));
            } catch (ParserException e) {
                throw new SyntaxError (e);
            }
        }
        
    }

}
