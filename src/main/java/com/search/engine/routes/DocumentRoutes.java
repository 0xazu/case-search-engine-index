package com.search.engine.routes;

import akka.Done;
import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Scheduler;
import akka.actor.typed.javadsl.AskPattern;
import akka.http.javadsl.marshallers.jackson.Jackson;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.server.ExceptionHandler;
import akka.http.javadsl.server.PathMatchers;
import akka.http.javadsl.server.RejectionHandler;
import akka.http.javadsl.server.Route;
import akka.pattern.StatusReply;
import com.search.engine.DocumentRegistry;
import com.search.engine.exceptions.CaseSearchEngineException;
import com.search.engine.models.Document;
import com.search.engine.models.DocumentCreatedResponse;
import com.search.engine.models.DocumentUpdatedResponse;
import com.search.engine.validators.RequestValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.CompletionStage;

import static akka.http.javadsl.server.Directives.*;

/**
 * Routes exposed in the system
 * <p>
 * POST /documents. Creates a new document in the system.
 * PATCH /documents/{id}. Modifies the document with the id indicated in the request path.
 * DELETE /documents/{id}. Deletes the document from Solr and marks it as DELETED in the system.
 */
public class DocumentRoutes {
    private final static Logger log = LoggerFactory.getLogger(DocumentRoutes.class);

    private final RequestValidator<Document> validator;
    private final ActorRef<DocumentRegistry.Command> documentRegistry;
    private final Scheduler scheduler;
    private final Duration askTimeout;

    public DocumentRoutes(ActorSystem<?> system, ActorRef<DocumentRegistry.Command> documentRegistry, RequestValidator<Document> validator) {
        this.documentRegistry = documentRegistry;
        this.validator = validator;

        scheduler = system.scheduler();
        askTimeout = system.settings().config().getDuration("case-search-engine-index.routes.ask-timeout");
    }

    private CompletionStage<StatusReply<DocumentCreatedResponse>> createDocument(Document document) {
        validator.validatePostEntity(document);
        return AskPattern.ask(documentRegistry, ref -> new DocumentRegistry.CreateDocument(document, ref), askTimeout, scheduler);
    }

    private CompletionStage<StatusReply<DocumentUpdatedResponse>> updateDocument(String id, Document document) {
        validator.validateId(id);
        validator.validatePatchEntity(document);
        return AskPattern.ask(documentRegistry, ref -> new DocumentRegistry.UpdateDocument(id, document, ref), askTimeout, scheduler);
    }

    private CompletionStage<StatusReply<Done>> deleteDocument(String id) {
        validator.validateId(id);
        return AskPattern.ask(documentRegistry, ref -> new DocumentRegistry.DeleteDocument(id, ref), askTimeout, scheduler);
    }

    public Route documentRoutes() {
        final ExceptionHandler fieldRequiredHandler = ExceptionHandler.newBuilder()
                .match(CaseSearchEngineException.class, ex -> complete(StatusCodes.BAD_REQUEST, ex.getMessage())).build();
        final RejectionHandler defaultHandler = RejectionHandler.defaultHandler();

        return pathPrefix("documents", () ->
                concat(
                        pathEnd(() ->
                                concat(
                                        post(() -> entity(
                                                Jackson.unmarshaller(Document.class),
                                                document -> onSuccess(createDocument(document), createdMessage -> {
                                                    log.info("Created document: {}", createdMessage.getValue());
                                                    return complete(StatusCodes.CREATED, createdMessage.getValue(), Jackson.marshaller());
                                                }))
                                        )
                                )
                        ),
                        path(PathMatchers.segment(), (String id) ->
                                concat(
                                        patch(() -> entity(
                                                Jackson.unmarshaller(Document.class),
                                                document -> onSuccess(updateDocument(id, document), updatedMessage -> {
                                                    log.info("Updated document: {}", updatedMessage.getValue());
                                                    return complete(StatusCodes.OK, updatedMessage.getValue(), Jackson.marshaller());
                                                }))
                                        )
                                )
                        ),
                        path(PathMatchers.segment(), (String id) ->
                                concat(
                                        delete(() -> onSuccess(deleteDocument(id), deletedMessage -> {
                                            log.info("Delete of document with id {} performed", id);
                                            return complete(StatusCodes.NO_CONTENT);
                                        }))
                                )
                        )
                ).seal(defaultHandler, fieldRequiredHandler)
        );
    }
}

