package com.search.engine;

import akka.Done;
import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.actor.typed.ActorRef;
import akka.pattern.StatusReply;
import akka.persistence.testkit.javadsl.EventSourcedBehaviorTestKit;
import akka.persistence.typed.PersistenceId;
import com.search.engine.models.*;
import com.typesafe.config.ConfigFactory;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class DocumentRegistryTest {

    @ClassRule
    public static final TestKitJunitResource testkit =
            new TestKitJunitResource(
                    ConfigFactory.parseString(
                            "akka.actor.serialization-bindings {\n"
                                    + "  \"com.search.engine.JacksonSerializable\" = jackson-json\n"
                                    + "}")
                            .withFallback(EventSourcedBehaviorTestKit.config()));

    private final TestSolrIndexer solrIndexer = new TestSolrIndexer();
    private final ActorRef<DocumentIndexer.Command> documentIndexer = testkit.spawn(DocumentIndexer.create(solrIndexer));
    private final EventSourcedBehaviorTestKit<DocumentRegistry.Command, DocumentRegistry.Event, DocumentRegistry.State>
            documentRegistryTestKit =
            EventSourcedBehaviorTestKit.create(
                    testkit.system(), DocumentRegistry.create(PersistenceId.ofUniqueId("DocumentRegistryTest"), documentIndexer));

    @Before
    public void beforeEach() {
        documentRegistryTestKit.clear();
    }

    @Test
    public void createAndUpdateDocumentFromDifferentSources() {
        // Create a document
        var productsDocument = generateTestDocumentFromProducts();
        EventSourcedBehaviorTestKit.CommandResultWithReply<
                DocumentRegistry.Command,
                DocumentRegistry.Event,
                DocumentRegistry.State,
                StatusReply<DocumentCreatedResponse>> documentCreatedResult = documentRegistryTestKit.runCommand(
                replyTo -> new DocumentRegistry.CreateDocument(productsDocument, replyTo));

        // Assert response
        assertTrue(documentCreatedResult.reply().isSuccess());
        DocumentCreatedResponse createdResponse = documentCreatedResult.reply().getValue();
        assertNotNull(createdResponse.id);

        // Assert events
        assertEquals(productsDocument.getId(), documentCreatedResult.eventOfType(DocumentRegistry.DocumentCreated.class).document.getId());
        assertEquals(productsDocument.getName(), documentCreatedResult.eventOfType(DocumentRegistry.DocumentCreated.class).document.getName());
        assertEquals(productsDocument.getDescription(), documentCreatedResult.eventOfType(DocumentRegistry.DocumentCreated.class).document.getDescription());
        assertEquals(productsDocument.getImagesUrls(), documentCreatedResult.eventOfType(DocumentRegistry.DocumentCreated.class).document.getImagesUrls());
        assertEquals(productsDocument.getState(), documentCreatedResult.eventOfType(DocumentRegistry.DocumentCreated.class).document.getState());
        assertEquals(productsDocument.getDataSource(), documentCreatedResult.eventOfType(DocumentRegistry.DocumentCreated.class).document.getDataSource());

        // Assert state
        assertEquals(DocumentState.INDEXED, documentCreatedResult.state().getDocument(productsDocument.getId()).getState());
        assertNotNull(documentCreatedResult.state().getDocument(productsDocument.getId()).getProductsIndexedTimestamp());

        // Update with data from prices
        var pricesDocument = generateTestDocumentFromPrices();
        EventSourcedBehaviorTestKit.CommandResultWithReply<
                DocumentRegistry.Command,
                DocumentRegistry.Event,
                DocumentRegistry.State,
                StatusReply<DocumentUpdatedResponse>> documentUpdatedPricesResult = documentRegistryTestKit.runCommand(
                replyTo -> new DocumentRegistry.UpdateDocument(createdResponse.id, pricesDocument, replyTo));

        // Assert response
        assertTrue(documentUpdatedPricesResult.reply().isSuccess());
        DocumentUpdatedResponse updatedPricesResponse = documentUpdatedPricesResult.reply().getValue();
        assertEquals(updatedPricesResponse.id, createdResponse.id);
        assertEquals(updatedPricesResponse.price, pricesDocument.getPrice());

        // Assert events
        assertEquals(updatedPricesResponse.price, documentUpdatedPricesResult.eventOfType(DocumentRegistry.DocumentUpdated.class).document.getPrice());

        // Assert state
        assertEquals(DocumentState.INDEXED, documentUpdatedPricesResult.state().getDocument(updatedPricesResponse.id).getState());
        assertNotNull(documentUpdatedPricesResult.state().getDocument(updatedPricesResponse.id).getPricesIndexedTimestamp());

        // Update with data from promotions
        var promotionsDocument = generateTestDocumentFromPromotions();
        EventSourcedBehaviorTestKit.CommandResultWithReply<
                DocumentRegistry.Command,
                DocumentRegistry.Event,
                DocumentRegistry.State,
                StatusReply<DocumentUpdatedResponse>> documentUpdatedPromotionsResult = documentRegistryTestKit.runCommand(
                replyTo -> new DocumentRegistry.UpdateDocument(createdResponse.id, promotionsDocument, replyTo));

        // Assert response
        assertTrue(documentUpdatedPromotionsResult.reply().isSuccess());
        DocumentUpdatedResponse updatedPromotionsResponse = documentUpdatedPromotionsResult.reply().getValue();
        assertEquals(updatedPromotionsResponse.id, createdResponse.id);
        assertEquals(updatedPromotionsResponse.promotion, promotionsDocument.getPromotion());

        // Assert events
        assertEquals(updatedPromotionsResponse.promotion, documentUpdatedPromotionsResult.eventOfType(DocumentRegistry.DocumentUpdated.class).document.getPromotion());

        // Assert state
        assertEquals(DocumentState.INDEXED, documentUpdatedPromotionsResult.state().getDocument(updatedPromotionsResponse.id).getState());
        assertNotNull(documentUpdatedPromotionsResult.state().getDocument(updatedPromotionsResponse.id).getPromotionsIndexedTimestamp());
    }

    @Test
    public void createAndDeleteDocument() {
        // Create a document
        EventSourcedBehaviorTestKit.CommandResultWithReply<
                DocumentRegistry.Command,
                DocumentRegistry.Event,
                DocumentRegistry.State,
                StatusReply<DocumentCreatedResponse>> documentCreatedResult = documentRegistryTestKit.runCommand(
                replyTo -> new DocumentRegistry.CreateDocument(generateTestDocumentFromProducts(), replyTo));

        assertTrue(documentCreatedResult.reply().isSuccess());
        DocumentCreatedResponse createdResponse = documentCreatedResult.reply().getValue();
        assertNotNull(createdResponse.id);

        // Delete the document
        EventSourcedBehaviorTestKit.CommandResultWithReply<
                DocumentRegistry.Command,
                DocumentRegistry.Event,
                DocumentRegistry.State,
                StatusReply<Done>> documentToDelete = documentRegistryTestKit.runCommand(
                replyTo -> new DocumentRegistry.DeleteDocument(createdResponse.id, replyTo));

        // Assert response
        assertTrue(documentToDelete.reply().isSuccess());
        assertEquals(StatusReply.ack(), documentToDelete.reply());

        // Assert events
        assertEquals(createdResponse.id, documentToDelete.eventOfType(DocumentRegistry.DocumentToDelete.class).id);

        // Assert state
        assertEquals(DocumentState.DELETED, documentToDelete.state().getDocument(createdResponse.id).getState());
    }

    private Document generateTestDocumentFromProducts() {
        return new Document("Test document", "Document used for testing", List.of(
                "http://www.example.org",
                "http://www.google.com"
        ), null, null, DataSource.PRODUCTS);
    }

    private Document generateTestDocumentFromPrices() {
        return new Document(null, null, null, 10.25, null, DataSource.PRICES);
    }

    private Document generateTestDocumentFromPromotions() {
        return new Document(null, null, null, null, "Black Friday", DataSource.PROMOTIONS);
    }
}