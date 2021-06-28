package com.search.engine;

import com.search.engine.solr.SolrIndexer;

public class TestSolrIndexer implements SolrIndexer {
    public boolean indexDocumentSuccess = true;
    public boolean deleteDocumentSuccess = true;

    @Override
    public boolean indexDocument(String id) {
        return indexDocumentSuccess;
    }

    @Override
    public boolean deleteDocument(String id) {
        return deleteDocumentSuccess;
    }
}
