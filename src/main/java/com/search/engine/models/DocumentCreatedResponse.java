package com.search.engine.models;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Representation of the document resource returned to the user when it is created
 */
public class DocumentCreatedResponse implements Response {
    public final String id;

    @JsonCreator
    public DocumentCreatedResponse(@JsonProperty String id) {
        this.id = id;
    }
}
