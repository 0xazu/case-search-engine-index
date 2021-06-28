package com.search.engine;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import com.search.engine.models.DataSource;
import com.search.engine.solr.SolrIndexer;

import java.time.Instant;
import java.util.LinkedList;
import java.util.Queue;

/**
 * Represents the actor responsible for indexing and deleting documents in the search engine
 */
public class DocumentIndexer extends AbstractBehavior<DocumentIndexer.Command> {
    private final SolrIndexer solrIndexer;
    private final Queue<IndexDocument> documentsFailedToIndex;
    private final Queue<DeleteDocument> documentsFailedToDelete;
    public interface Command {}

    private DocumentIndexer(ActorContext<DocumentIndexer.Command> context, final SolrIndexer solrIndexer) {
        super(context);
        this.solrIndexer = solrIndexer;
        this.documentsFailedToIndex = new LinkedList<>();
        this.documentsFailedToDelete = new LinkedList<>();
    }

    public static Behavior<DocumentIndexer.Command> create(final SolrIndexer solrIndexer) {
        return Behaviors.setup(context -> new DocumentIndexer(context, solrIndexer));
    }

    @Override
    public Receive<DocumentIndexer.Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(IndexDocument.class, this::onIndexDocument)
                .onMessage(DeleteDocument.class, this::onDeleteDocument)
                .build();
    }

    private Behavior<DocumentIndexer.Command> onIndexDocument(IndexDocument command) {
        var solrIndexerOk = true;
        while(!documentsFailedToIndex.isEmpty() && solrIndexerOk) {
            solrIndexerOk = indexDocument(documentsFailedToIndex.poll());
        }

        indexDocument(command);
        return this;
    }

    private boolean indexDocument(IndexDocument command) {
        var indexed = solrIndexer.indexDocument(command.id);

        if (indexed) {
            command.respondTo.tell(new DocumentRegistry.DocumentStatusToIndexed(command.id, command.dataSource, Instant.now()));
        } else {
            documentsFailedToIndex.add(command);
        }

        return indexed;
    }

    private Behavior<DocumentIndexer.Command> onDeleteDocument(DeleteDocument command) {
        var solrIndexerOk = true;
        while(!documentsFailedToDelete.isEmpty() && solrIndexerOk) {
            solrIndexerOk = deleteDocument(documentsFailedToDelete.poll());
        }

        deleteDocument(command);
        return this;
    }

    private boolean deleteDocument(DeleteDocument command) {
        var deleted = solrIndexer.deleteDocument(command.id);

        if (deleted) {
            command.respondTo.tell(new DocumentRegistry.DocumentStatusToDeleted(command.id));
        } else {
            documentsFailedToDelete.add(command);
        }

        return deleted;
    }

    public final static class IndexDocument implements DocumentIndexer.Command {
        private final String id;
        private final DataSource dataSource;
        public final ActorRef<DocumentRegistry.DocumentStatusToIndexed> respondTo;

        public IndexDocument(String id, DataSource dataSource, ActorRef<DocumentRegistry.DocumentStatusToIndexed> respondTo) {
            this.id = id;
            this.dataSource = dataSource;
            this.respondTo = respondTo;
        }
    }

    public final static class DeleteDocument implements DocumentIndexer.Command {
        private final String id;
        public final ActorRef<DocumentRegistry.DocumentStatusToDeleted> respondTo;

        public DeleteDocument(String id, ActorRef<DocumentRegistry.DocumentStatusToDeleted> respondTo) {
            this.id = id;
            this.respondTo = respondTo;
        }
    }
}
