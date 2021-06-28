package com.search.engine;

import akka.NotUsed;
import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.Behaviors;
import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.server.Route;
import akka.persistence.typed.PersistenceId;
import com.search.engine.models.Document;
import com.search.engine.routes.DocumentRoutes;
import com.search.engine.solr.MockSolrIndexer;
import com.search.engine.solr.SolrIndexer;
import com.search.engine.validators.DocumentRequestValidator;
import com.search.engine.validators.RequestValidator;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletionStage;

public class CaseSearchEngineIndexApp {

    public static void main(String[] args) {
        Behavior<NotUsed> rootBehavior = Behaviors.setup(context -> {
            SolrIndexer solrIndexer = new MockSolrIndexer(context.getSystem());
            ActorRef<DocumentIndexer.Command> documentIndexer = context.spawn(DocumentIndexer.create(solrIndexer), "DocumentIndexer");
            ActorRef<DocumentRegistry.Command> documentRegistry = context.spawn(
                    DocumentRegistry.create(PersistenceId.ofUniqueId("DocumentRegistry"), documentIndexer), "DocumentRegistry");

            RequestValidator<Document> validator = new DocumentRequestValidator();
            DocumentRoutes documentRoutes = new DocumentRoutes(context.getSystem(), documentRegistry, validator);
            startHttpServer(documentRoutes.documentRoutes(), context.getSystem());

            return Behaviors.empty();
        });

        // boot up server using the route as defined below
        ActorSystem.create(rootBehavior, "CaseSearchEngineIndexHttpServer");
    }

    static void startHttpServer(Route route, ActorSystem<?> system) {
        CompletionStage<ServerBinding> futureBinding =
                Http.get(system)
                        .newServerAt("localhost", 8080)
                        .bind(route);

        futureBinding.whenComplete((binding, exception) -> {
            if (binding != null) {
                InetSocketAddress address = binding.localAddress();
                system.log().info("Server online at http://{}:{}/",
                        address.getHostString(),
                        address.getPort());
            } else {
                system.log().error("Failed to bind HTTP endpoint, terminating system", exception);
                system.terminate();
            }
        });
    }
}
