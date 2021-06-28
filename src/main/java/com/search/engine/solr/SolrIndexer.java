package com.search.engine.solr;

/**
 * Contains the operations exposed by Solr
 */
public interface SolrIndexer {
    boolean indexDocument(String id);
    boolean deleteDocument(String id);
}
