package com.search.engine.solr;

import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertTrue;


public class MockSolrIndexerTest {
    private MockSolrIndexer mockSolrIndexer;
    @ClassRule
    public static TestKitJunitResource testkit = new TestKitJunitResource();

    @Before
    public void setUp() {
        mockSolrIndexer = new MockSolrIndexer(testkit.system());
    }

    @Test
    public void indexDocument() {
        String id = UUID.randomUUID().toString();
        assertTrue(mockSolrIndexer.indexDocument(id));
    }

    @Test
    public void deleteDocument() {
        String id = UUID.randomUUID().toString();
        assertTrue(mockSolrIndexer.deleteDocument(id));
    }
}