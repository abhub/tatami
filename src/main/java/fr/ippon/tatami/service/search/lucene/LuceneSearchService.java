package fr.ippon.tatami.service.search.lucene;

import fr.ippon.tatami.domain.Status;
import fr.ippon.tatami.domain.User;
import fr.ippon.tatami.service.SearchService;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.DateTools;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryParser.MultiFieldQueryParser;
import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.util.Version;
import org.springframework.scheduling.annotation.Async;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class LuceneSearchService implements SearchService {

    private static final Log log = LogFactory.getLog(LuceneSearchService.class);

    @Inject
    private IndexWriter indexWriter;

    @Inject
    private IndexReader indexReader;

    @Inject
    private Analyzer analyzer;

    private Map<String,Float> statusBoosts = new HashMap<String, Float>();

    @PostConstruct
    public void init() {
        statusBoosts.put("username", 1f);
        statusBoosts.put("content", 5f);
    }

    @Override
    public boolean reset() {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    @Async
    public void addStatus(Status status) {
        Document document = new Document();
        document.add(new Field("statusId", status.getStatusId(), Field.Store.YES, Field.Index.NOT_ANALYZED));
        document.add(new Field("username", status.getUsername(), Field.Store.NO, Field.Index.NOT_ANALYZED));
        document.add(new Field("domain", status.getDomain(), Field.Store.NO, Field.Index.NOT_ANALYZED));
        document.add(new Field("content", status.getContent(), Field.Store.NO, Field.Index.ANALYZED));
        document.add(new Field("statusDate",
                DateTools.dateToString(status.getStatusDate(), DateTools.Resolution.SECOND),
                Field.Store.NO,
                Field.Index.NOT_ANALYZED));

        try {
            indexWriter.addDocument(document);
            if (log.isDebugEnabled()) {
                log.debug("Lucene indexed status : " + status);
            }
        } catch (IOException e) {
            log.error("The status wasn't added to the index: " + status, e);
        }
    }

    @Override
    public String removeStatus(Status status) {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public Map<String, String> searchStatus(@SuppressWarnings("rawtypes") String domain,
                                            String query,
                                            String sortField,
                                            String sortOrder,
                                            int page,
                                            int size) {

        if (page < 0) {
            page = 0; //Default value
        }
        if (size <= 0) {
            size = SearchService.DEFAULT_PAGE_SIZE;
        }
        IndexSearcher searcher = new IndexSearcher(indexReader);
        try {
            MultiFieldQueryParser parser =
                    new MultiFieldQueryParser(Version.LUCENE_36,
                            new String[]{"username", "firstName", "lastName", "content"},
                            analyzer,
                            statusBoosts);

            parser.setDateResolution(DateTools.Resolution.SECOND);
            parser.setDefaultOperator(QueryParser.Operator.AND);
            Query luceneQuery = parser.parse(query);
            if (log.isDebugEnabled()) {
                log.debug("Lucene query : " + query);
            }

            TermsFilter filter = new TermsFilter();
            Term domainTerm = new Term("domain", domain);
            filter.addTerm(domainTerm);

            TopDocs topDocs = searcher.search(luceneQuery, filter, size);
            int totalHits = topDocs.totalHits;
            if (totalHits == 0) {
                return new HashMap<String, String>(0);
            }

            ScoreDoc[] scoreDocArray = topDocs.scoreDocs;
            if (log.isDebugEnabled()) {
                log.debug("Lucene found " + topDocs.totalHits + " results");
            }
            final Map<String, String> items = new LinkedHashMap<String, String>(totalHits);
            for (int i = 0; i < scoreDocArray.length; i++) {
                int documentId = scoreDocArray[i].doc;
                Document document = searcher.doc(documentId);
                String statusId = document.get("statusId");
                items.put(statusId, null);
            }
            return items;
        } catch (ParseException e) {
            log.error("A Lucene query could not be parsed : " + e.getMessage());
            if (log.isDebugEnabled()) {
                e.printStackTrace();
            }
        } catch (IOException e) {
            log.error("A Lucene query had a I/O error : " + e.getMessage());
            if (log.isDebugEnabled()) {
                e.printStackTrace();
            }
        }
        return new HashMap<String, String>(0);
    }

    @Override
    public <T> List<String> searchPrefix(String domain, Class<T> clazz, String searchField, String uidField, String query, int page, int size) {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    @Async
    public void addUser(User user) {

    }
}
