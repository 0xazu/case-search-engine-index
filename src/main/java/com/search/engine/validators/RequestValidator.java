package com.search.engine.validators;

public interface RequestValidator<T> {
    void validatePostEntity(T entity);
    void validatePatchEntity(T entity);
    void validateId(String id);
}
