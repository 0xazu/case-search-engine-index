package com.search.engine;

import akka.Done;
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.pattern.StatusReply;
import akka.persistence.typed.PersistenceId;
import akka.persistence.typed.RecoveryCompleted;
import akka.persistence.typed.javadsl.*;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.search.engine.exceptions.CaseSearchEngineException;
import com.search.engine.models.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Represents the actor responsible for handling document changes
 */
public final class DocumentRegistry extends EventSourcedBehavior<DocumentRegistry.Command, DocumentRegistry.Event, DocumentRegistry.State> {
    private final static Logger log = LoggerFactory.getLogger(DocumentRegistry.class);

    private final ActorRef<DocumentIndexer.Command> indexer;
    private final ActorRef<DocumentStatusToIndexed> refDocumentIndexed;
    private final ActorRef<DocumentStatusToDeleted> refDocumentDeleted;

    public interface Command extends JacksonSerializable {
    }

    public interface Event extends JacksonSerializable {
    }

    public static final class State implements JacksonSerializable {
        final Map<String, Document> documents;

        public State(Map<String, Document> documents) {
            this.documents = documents;
        }

        public State() {
            this(new HashMap<>());
        }

        public State indexDocument(String id, DataSource dataSource, Instant timestamp) {
            var document = documents.get(id);
            document.setState(DocumentState.INDEXED);

            switch (dataSource) {
                case PRICES -> document.setPricesIndexedTimestamp(Optional.of(timestamp));
                case PRODUCTS -> document.setProductsIndexedTimestamp(Optional.of(timestamp));
                case PROMOTIONS -> document.setPromotionsIndexedTimestamp(Optional.of(timestamp));
            }

            documents.put(id, document);
            return this;
        }

        public State createDocument(Document document) {
            documents.put(document.getId(), document);
            return this;
        }

        public State deleteDocument(String id) {
            var document = documents.get(id);

            if (document == null || document.getState() == DocumentState.DELETED) {
                return new State();
            }

            document.setState(DocumentState.DELETED);
            return this;
        }

        public State setDocumentToDelete(String id) {
            var document = documents.get(id);

            if (document == null) {
                log.error("The document {} cannot be deleted. It does not exist in the system", id);
                return new State();
            }

            if (document.getState() == DocumentState.DELETED) {
                log.error("The document {} is already deleted in the system", id);
                return new State();
            }

            document.setState(DocumentState.TO_DELETE);
            return this;
        }

        public State updateDocument(Document document) {
            var storedDocument = documents.get(document.getId());

            if (storedDocument == null) {
                log.error("The document {} cannot be updated. It does not exist in the system", document.getId());
                return new State();
            }

            if (storedDocument.getState() == DocumentState.DELETED) {
                log.error("The document {} cannot be updated. It is in a deleted state", document.getId());
                return new State();
            }

            storedDocument.setState(DocumentState.FETCHED);
            storedDocument.setDataSource(document.getDataSource());

            if (document.getName() != null) storedDocument.setName(document.getName());
            if (document.getPrice() != null) storedDocument.setPrice(document.getPrice());
            if (document.getPromotion() != null) storedDocument.setPromotion(document.getPromotion());
            if (document.getDescription() != null) storedDocument.setDescription(document.getDescription());
            if (document.getImagesUrls() != null) {
                document.getImagesUrls().forEach(imageUrl -> {
                    if (!storedDocument.getImagesUrls().contains(imageUrl)) {
                        storedDocument.getImagesUrls().add(imageUrl);
                    }
                });
            }

            return this;
        }

        public Document getDocument(String id) {
            return documents.get(id);
        }

        public DocumentCreatedResponse documentCreatedResponse(String id) {
            return new DocumentCreatedResponse(id);
        }

        public DocumentUpdatedResponse documentUpdatedResponse(Document document) {
            return new DocumentUpdatedResponse(document);
        }
    }

    public static Behavior<Command> create(PersistenceId persistenceId, ActorRef<DocumentIndexer.Command> indexer) {
        return Behaviors.setup(ctx -> new DocumentRegistry(ctx, persistenceId, indexer));
    }

    private DocumentRegistry(ActorContext<DocumentRegistry.Command> context, PersistenceId persistenceId, ActorRef<DocumentIndexer.Command> indexer) {
        super(persistenceId);
        this.indexer = indexer;
        this.refDocumentIndexed = context.getSelf().narrow();
        this.refDocumentDeleted = context.getSelf().narrow();
    }

    @Override
    public State emptyState() {
        return new State();
    }

    // Commands
    public final static class CreateDocument implements DocumentRegistry.Command {
        public final Document document;
        public final ActorRef<StatusReply<DocumentCreatedResponse>> replyTo;

        public CreateDocument(Document document, ActorRef<StatusReply<DocumentCreatedResponse>> replyTo) {
            this.document = document;
            this.replyTo = replyTo;
            this.document.setId(UUID.randomUUID().toString());
        }
    }

    public final static class UpdateDocument implements DocumentRegistry.Command {
        public final Document document;
        public final ActorRef<StatusReply<DocumentUpdatedResponse>> replyTo;

        public UpdateDocument(String id, Document document, ActorRef<StatusReply<DocumentUpdatedResponse>> replyTo) {
            this.document = document;
            this.replyTo = replyTo;
            this.document.setId(id);
        }
    }

    public final static class DeleteDocument implements DocumentRegistry.Command {
        public final String id;
        public final ActorRef<StatusReply<Done>> replyTo;

        public DeleteDocument(String id, ActorRef<StatusReply<Done>> replyTo) {
            this.id = id;
            this.replyTo = replyTo;
        }
    }

    public final static class DocumentStatusToIndexed implements DocumentRegistry.Command {
        public final String id;
        public final DataSource dataSource;
        public final Instant timestamp;

        public DocumentStatusToIndexed(String id, DataSource dataSource, Instant timestamp) {
            this.id = id;
            this.dataSource = dataSource;
            this.timestamp = timestamp;
        }
    }

    public final static class DocumentStatusToDeleted implements DocumentRegistry.Command {
        public final String id;

        public DocumentStatusToDeleted(String id) {
            this.id = id;
        }
    }

    @Override
    public CommandHandler<Command, Event, State> commandHandler() {
        return newCommandHandlerBuilder()
                .forAnyState()
                .onCommand(CreateDocument.class, this::onCreateDocument)
                .onCommand(UpdateDocument.class, this::onUpdateDocument)
                .onCommand(DeleteDocument.class, this::onDeleteDocument)
                .onCommand(DocumentStatusToIndexed.class, this::onDocumentStatusToIndexed)
                .onCommand(DocumentStatusToDeleted.class, this::onDocumentStatusToDeleted)
                .build();
    }

    // Effects
    private Effect<Event, State> onCreateDocument(CreateDocument createDocumentCommand) {
        var payload = createDocumentCommand.document;

        return Effect()
                .persist(new DocumentCreated(payload))
                .thenRun(() -> indexer.tell(new DocumentIndexer.IndexDocument(payload.getId(), payload.getDataSource(), refDocumentIndexed)))
                .thenReply(createDocumentCommand.replyTo, documentCreated -> StatusReply.success(
                        documentCreated.documentCreatedResponse(payload.getId())
                ));
    }

    private Effect<Event, State> onUpdateDocument(UpdateDocument updateDocumentCommand) {
        var payload = updateDocumentCommand.document;

        return Effect()
                .persist(new DocumentUpdated(payload))
                .thenRun(() -> indexer.tell(new DocumentIndexer.IndexDocument(payload.getId(), payload.getDataSource(), refDocumentIndexed)))
                .thenReply(updateDocumentCommand.replyTo, documentUpdated -> {
                    if (documentUpdated.documents.isEmpty()) { // There was a problem updating the document
                        return StatusReply.error(new CaseSearchEngineException("Document was not updated. Could not be found or it was in an invalid state"));
                    } else {
                        return StatusReply.success(documentUpdated.documentUpdatedResponse(documentUpdated.getDocument(payload.getId())));
                    }
                });
    }

    private Effect<Event, State> onDeleteDocument(DeleteDocument deleteDocumentCommand) {
        return Effect()
                .persist(new DocumentToDelete(deleteDocumentCommand.id))
                .thenRun(() ->indexer.tell(new DocumentIndexer.DeleteDocument(deleteDocumentCommand.id, refDocumentDeleted)))
                .thenReply(deleteDocumentCommand.replyTo, documentDeleted -> StatusReply.Ack());
    }

    private Effect<Event, State> onDocumentStatusToIndexed(DocumentStatusToIndexed documentStatusToIndexedCommand) {
        return Effect().persist(new DocumentIndexed(documentStatusToIndexedCommand.id, documentStatusToIndexedCommand.dataSource, documentStatusToIndexedCommand.timestamp));
    }

    private Effect<Event, State> onDocumentStatusToDeleted(DocumentStatusToDeleted documentStatusToDeletedCommand) {
        return Effect().persist(new DocumentDeleted(documentStatusToDeletedCommand.id));
    }

    @Override
    public EventHandler<State, Event> eventHandler() {
        return newEventHandlerBuilder()
                .forAnyState()
                .onEvent(DocumentCreated.class, (state, event) -> state.createDocument(event.document))
                .onEvent(DocumentUpdated.class, (state, event) -> state.updateDocument(event.document))
                .onEvent(DocumentIndexed.class, (state, event) -> state.indexDocument(event.id, event.dataSource, event.timestamp))
                .onEvent(DocumentToDelete.class, (state, event) -> state.setDocumentToDelete(event.id))
                .onEvent(DocumentDeleted.class, (state, event) -> state.deleteDocument(event.id))
                .build();
    }

    // Events
    public final static class DocumentCreated implements Event {
        public final Document document;

        @JsonCreator
        public DocumentCreated(Document document) {
            this.document = document;
        }

    }

    public final static class DocumentUpdated implements Event {
        public final Document document;

        @JsonCreator
        public DocumentUpdated(Document document) {
            this.document = document;
        }
    }

    public final static class DocumentToDelete implements Event {
        public final String id;

        @JsonCreator
        public DocumentToDelete(String id) {
            this.id = id;
        }
    }

    public final static class DocumentIndexed implements Event {
        public final String id;
        public final DataSource dataSource;
        public final Instant timestamp;

        @JsonCreator
        public DocumentIndexed(String id, DataSource dataSource, Instant timestamp) {
            this.id = id;
            this.dataSource = dataSource;
            this.timestamp = timestamp;
        }
    }

    public final static class DocumentDeleted implements Event {
        public final String id;

        @JsonCreator
        public DocumentDeleted(String id) {
            this.id = id;
        }
    }

    @Override
    public SignalHandler<State> signalHandler() {
        // Recover to a healthy state after a shutdown or restart
        return newSignalHandlerBuilder()
                .onSignal(
                        RecoveryCompleted.instance(),
                        state -> {
                            // After recovered, we should iterate through the documents and:
                            // send those in a FETCHED state to index
                            state.documents.values().stream()
                                    .filter(document -> document.getState().equals(DocumentState.FETCHED))
                                    .forEach(document -> indexer.tell(new DocumentIndexer.IndexDocument(document.getId(), document.getDataSource(), refDocumentIndexed)));

                            // send those in a TO_DELETE state to index
                            state.documents.values().stream()
                                    .filter(document -> document.getState().equals(DocumentState.TO_DELETE))
                                    .forEach(document -> indexer.tell(new DocumentIndexer.DeleteDocument(document.getId(), refDocumentDeleted)));
                        })
                .build();
    }
}