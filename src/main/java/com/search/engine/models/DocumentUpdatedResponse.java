package com.search.engine.models;

import java.util.List;

/**
 * Representation of the document resource returned to the user when it is updated
 */
public class DocumentUpdatedResponse implements Response {
    public String id;
    public String name;
    public String description;
    public List<String> imagesUrls;
    public Double price;
    public String promotion;

    public DocumentUpdatedResponse() {}

    public DocumentUpdatedResponse(Document document) {
        this.id = document.getId();
        this.name = document.getName();
        this.description = document.getDescription();
        this.imagesUrls = document.getImagesUrls();
        this.price = document.getPrice();
        this.promotion = document.getPromotion();
    }

}
