package com.search.engine.routes;

import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.actor.typed.ActorRef;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.MediaTypes;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.testkit.JUnitRouteTest;
import akka.http.javadsl.testkit.TestRoute;
import akka.persistence.typed.PersistenceId;
import com.search.engine.DocumentIndexer;
import com.search.engine.DocumentRegistry;
import com.search.engine.TestSolrIndexer;
import com.search.engine.models.Document;
import com.search.engine.validators.DocumentRequestValidator;
import com.search.engine.validators.RequestValidator;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

public class DocumentRoutesTest extends JUnitRouteTest {
    @ClassRule
    public static TestKitJunitResource testkit = new TestKitJunitResource();

    private final RequestValidator<Document> validator = new DocumentRequestValidator();
    private final TestSolrIndexer solrIndexer = new TestSolrIndexer();
    private final ActorRef<DocumentIndexer.Command> documentIndexer = testkit.spawn(DocumentIndexer.create(solrIndexer));
    private final ActorRef<DocumentRegistry.Command> documentRegistry = testkit.spawn(DocumentRegistry.create(PersistenceId.ofUniqueId("DocumentRegistryTest"), documentIndexer));
    private TestRoute appRoute;

    @Before
    public void beforeEach() {
        DocumentRoutes documentRoutes = new DocumentRoutes(testkit.system(), documentRegistry, validator);
        appRoute = testRoute(documentRoutes.documentRoutes());
    }

    @Test
    public void createUpdateAndDeleteDocument() {
        var created = appRoute.run(HttpRequest.POST("/documents")
                .withEntity(MediaTypes.APPLICATION_JSON.toContentType(), "{\"name\": \"Test\", \"description\": \"Test\", \"dataSource\": \"PRODUCTS\"}"))
                .assertStatusCode(StatusCodes.CREATED)
                .assertMediaType("application/json");

        // extract the id
        var id = created.entityString().substring(7, created.entityString().length() - 2);

        appRoute.run(HttpRequest.PATCH("/documents/" + id)
                .withEntity(MediaTypes.APPLICATION_JSON.toContentType(), "{\"dataSource\": \"PRICES\", \"price\": \"10.25\"}"))
                .assertStatusCode(StatusCodes.OK)
                .assertMediaType("application/json");

        appRoute.run(HttpRequest.DELETE("/documents/" + id))
                .assertStatusCode(StatusCodes.NO_CONTENT);
    }
}