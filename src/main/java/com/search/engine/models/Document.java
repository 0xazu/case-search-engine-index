package com.search.engine.models;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public class Document {
    private String id;
    private String name;
    private String description;
    private List<String> imagesUrls;
    private Double price;
    private String promotion;
    private DataSource dataSource;
    private DocumentState state;
    private Optional<Instant> productsIndexedTimestamp;
    private Optional<Instant> promotionsIndexedTimestamp;
    private Optional<Instant> pricesIndexedTimestamp;

    @JsonCreator
    public Document(@JsonProperty("name") String name,
                    @JsonProperty("description") String description,
                    @JsonProperty("imagesUrls") List<String> imagesUrls,
                    @JsonProperty("price") Double price,
                    @JsonProperty("promotion") String promotion,
                    @JsonProperty("dataSource") DataSource dataSource) {
        this.name = name;
        this.description = description;
        this.imagesUrls = imagesUrls;
        this.price = price;
        this.promotion = promotion;
        this.dataSource = dataSource;

        this.state = DocumentState.FETCHED;
        this.productsIndexedTimestamp = Optional.empty();
        this.promotionsIndexedTimestamp = Optional.empty();
        this.pricesIndexedTimestamp = Optional.empty();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Double getPrice() {
        return price;
    }

    public void setPrice(Double price) {
        this.price = price;
    }

    public String getPromotion() {
        return promotion;
    }

    public void setPromotion(String promotion) {
        this.promotion = promotion;
    }

    public DataSource getDataSource() {
        return dataSource;
    }

    public void setDataSource(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public Optional<Instant> getProductsIndexedTimestamp() {
        return productsIndexedTimestamp;
    }

    public void setProductsIndexedTimestamp(Optional<Instant> productsIndexedTimestamp) {
        this.productsIndexedTimestamp = productsIndexedTimestamp;
    }

    public Optional<Instant> getPromotionsIndexedTimestamp() {
        return promotionsIndexedTimestamp;
    }

    public void setPromotionsIndexedTimestamp(Optional<Instant> promotionsIndexedTimestamp) {
        this.promotionsIndexedTimestamp = promotionsIndexedTimestamp;
    }

    public Optional<Instant> getPricesIndexedTimestamp() {
        return pricesIndexedTimestamp;
    }

    public void setPricesIndexedTimestamp(Optional<Instant> pricesIndexedTimestamp) {
        this.pricesIndexedTimestamp = pricesIndexedTimestamp;
    }

    public DocumentState getState() {
        return state;
    }

    public void setState(DocumentState state) {
        this.state = state;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public List<String> getImagesUrls() {
        return imagesUrls;
    }

    public void setImagesUrls(List<String> imagesUrls) {
        this.imagesUrls = imagesUrls;
    }
}
