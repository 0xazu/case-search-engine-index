package com.search.engine.solr;

import akka.actor.typed.ActorSystem;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;

import java.time.Duration;
import java.util.Random;

/**
 * Mock implementation of the SolrIndexer. This implementation generates a random response (success or failure).
 * When it generates a failure, a retry mechanism gets triggered to retry for the configured number of times
 */
public class MockSolrIndexer implements SolrIndexer {
    private final int solrIndexerRetries;
    private final Duration solrIndexerWaitBetweenRetries;
    private final int solrIndexerSuccessProbability;

    public MockSolrIndexer(ActorSystem<?> system) {
        this.solrIndexerRetries = system.settings().config().getInt("case-search-engine-index.solrIndexer.retries");
        this.solrIndexerWaitBetweenRetries = system.settings().config().getDuration("case-search-engine-index.solrIndexer.waitBetweenRetries");
        this.solrIndexerSuccessProbability = system.settings().config().getInt("case-search-engine-index.solrIndexer.successProbability");
    }

    @Override
    public boolean indexDocument(String id) {
        var retry = configureRetry();
        return retry.executeSupplier(this::generateResponse);
    }

    @Override
    public boolean deleteDocument(String id) {
        var retry = configureRetry();
        return retry.executeSupplier(this::generateResponse);
    }

    /**
     * Generates a random response:
     * True: The request to Solr succeeded
     * False: The request to Solr failed
     *
     * @return
     */
    private Boolean generateResponse() {
        return new Random().nextInt(solrIndexerSuccessProbability) == 0;
    }

    private Retry configureRetry() {
        var config = RetryConfig.<Boolean>custom()
                .maxAttempts(solrIndexerRetries)
                .waitDuration(solrIndexerWaitBetweenRetries)
                .retryOnResult(response -> !response)
                .build();

        RetryRegistry registry = RetryRegistry.of(config);
        return registry.retry("solrIndexer", config);
    }
}
