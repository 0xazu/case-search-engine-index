package com.search.engine.validators;

import com.search.engine.exceptions.CaseSearchEngineException;
import com.search.engine.models.Document;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.UUID;

/**
 * Class responsible of executing the validations on the request
 */
public class DocumentRequestValidator implements RequestValidator<Document> {
    private static final int NAME_MAX_LENGTH = 50;
    private static final int DESCRIPTION_MAX_LENGTH = 200;
    private static final int PROMOTION_MAX_LENGTH = 100;
    private static final int IMAGE_URLS_MAX_ALLOWED = 10;

    @Override
    public void validatePostEntity(Document entity) {
        validateFieldRequired("name", entity.getName());
        validateFieldRequired("dataSource", entity.getDataSource());

        validateFieldSize("name", entity.getName(), NAME_MAX_LENGTH);
        validateFieldSize("description", entity.getDescription(), DESCRIPTION_MAX_LENGTH);
        validateFieldSize("promotion", entity.getPromotion(), PROMOTION_MAX_LENGTH);
        validateFieldSize("imagesUrls", entity.getImagesUrls(), IMAGE_URLS_MAX_ALLOWED);
        validateImagesUrlsFieldFormat(entity.getImagesUrls());
    }

    @Override
    public void validatePatchEntity(Document entity) {
        validateFieldRequired("dataSource", entity.getDataSource());

        validateFieldSize("name", entity.getName(), NAME_MAX_LENGTH);
        validateFieldSize("description", entity.getDescription(), DESCRIPTION_MAX_LENGTH);
        validateFieldSize("promotion", entity.getPromotion(), PROMOTION_MAX_LENGTH);
        validateFieldSize("imagesUrls", entity.getImagesUrls(), IMAGE_URLS_MAX_ALLOWED);
        validateImagesUrlsFieldFormat(entity.getImagesUrls());
    }

    @Override
    public void validateId(String id) {
        try {
            UUID.fromString(id);
        } catch (IllegalArgumentException ex) {
            throw new CaseSearchEngineException("The id provided is invalid");
        }
    }

    private void validateFieldRequired(String fieldName, Object field) {
        if (field == null) throw new CaseSearchEngineException("The field " + fieldName + " is mandatory");
    }


    private void validateFieldSize(String fieldName, String field, int limit) {
        if (field != null && field.length() > limit) {
            throw new CaseSearchEngineException("The field " + fieldName + " has a size of " + field.length() + " and is bigger than the limit " + limit);
        }
    }

    private void validateFieldSize(String fieldName, List<String> field, int limit) {
        if (field != null && field.size() > limit) {
            throw new CaseSearchEngineException("The field " + fieldName + " has more elements '" + field.size() + "' than the max allowed " + limit);
        }
    }

    private void validateImagesUrlsFieldFormat(List<String> field) {
        if (field != null) {
            try {
                for (String s : field) { new URL(s); }
            } catch (MalformedURLException ex) {
                throw new CaseSearchEngineException("The imageUrls field contains malformed URLs");
            }
        }
    }
}
