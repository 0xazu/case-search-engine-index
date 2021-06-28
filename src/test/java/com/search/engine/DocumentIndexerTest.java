package com.search.engine;

import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.actor.typed.ActorRef;
import com.search.engine.models.DataSource;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;

public class DocumentIndexerTest {
    @ClassRule
    public static TestKitJunitResource testkit = new TestKitJunitResource();
    private static TestSolrIndexer solrIndexer;
    private static ActorRef<DocumentIndexer.Command> documentIndexer;

    @BeforeClass
    public static void beforeClass() {
        solrIndexer = new TestSolrIndexer();
        documentIndexer = testkit.spawn(DocumentIndexer.create(solrIndexer));
    }

    @AfterClass
    public static void afterClass() {
        testkit.stop(documentIndexer);
    }

    @Test
    public void indexDocumentDocumentIndexed() {
        String id = UUID.randomUUID().toString();
        DataSource dataSource = DataSource.PRODUCTS;

        TestProbe<DocumentRegistry.DocumentStatusToIndexed> probe = testkit.createTestProbe();
        documentIndexer.tell(new DocumentIndexer.IndexDocument(id, dataSource, probe.ref()));

        var documentIndexedMessage = probe.receiveMessage();
        assertEquals(documentIndexedMessage.id, id);
        assertEquals(documentIndexedMessage.dataSource, dataSource);
    }

    @Test
    public void indexDocumentFailedThenSucceeded() {
        String id1 = UUID.randomUUID().toString();
        String id2 = UUID.randomUUID().toString();
        DataSource dataSource1 = DataSource.PRODUCTS;
        DataSource dataSource2 = DataSource.PRICES;

        solrIndexer.indexDocumentSuccess = false;

        TestProbe<DocumentRegistry.DocumentStatusToIndexed> probe = testkit.createTestProbe();
        documentIndexer.tell(new DocumentIndexer.IndexDocument(id1, dataSource1, probe.ref()));

        probe.expectNoMessage();

        solrIndexer.indexDocumentSuccess = true;
        documentIndexer.tell(new DocumentIndexer.IndexDocument(id2, dataSource2, probe.ref()));

        var documentIndexedMessages = probe.receiveSeveralMessages(2);

        assertEquals(2, documentIndexedMessages.size());
        assertEquals(documentIndexedMessages.get(0).id, id1);
        assertEquals(documentIndexedMessages.get(1).id, id2);
        assertEquals(documentIndexedMessages.get(0).dataSource, dataSource1);
        assertEquals(documentIndexedMessages.get(1).dataSource, dataSource2);
    }

    @Test
    public void deleteDocumentDocumentDeleted() {
        String id = UUID.randomUUID().toString();

        TestProbe<DocumentRegistry.DocumentStatusToDeleted> probe = testkit.createTestProbe();
        documentIndexer.tell(new DocumentIndexer.DeleteDocument(id, probe.ref()));

        var documentDeletedMessage = probe.receiveMessage();
        assertEquals(documentDeletedMessage.id, id);
    }

    @Test
    public void deleteDocumentFailedThenSucceeded() {
        String id1 = UUID.randomUUID().toString();
        String id2 = UUID.randomUUID().toString();

        solrIndexer.deleteDocumentSuccess = false;

        TestProbe<DocumentRegistry.DocumentStatusToDeleted> probe = testkit.createTestProbe();
        documentIndexer.tell(new DocumentIndexer.DeleteDocument(id1, probe.ref()));

        probe.expectNoMessage();

        solrIndexer.deleteDocumentSuccess = true;
        documentIndexer.tell(new DocumentIndexer.DeleteDocument(id2, probe.ref()));

        var documentDeletedMessages = probe.receiveSeveralMessages(2);

        assertEquals(2, documentDeletedMessages.size());
        assertEquals(documentDeletedMessages.get(0).id, id1);
        assertEquals(documentDeletedMessages.get(1).id, id2);
    }
}