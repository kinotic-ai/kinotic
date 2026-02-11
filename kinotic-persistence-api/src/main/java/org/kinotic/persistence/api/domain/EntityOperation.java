package org.kinotic.persistence.api.domain;

/**
 * Represents an operation that can be performed on an entity.
 * This list must be kept in sync with the list of operations in the EntityService interface
 */
public enum EntityOperation {

    BULK_SAVE("bulkSave"),
    BULK_UPDATE("bulkUpdate"),
    COUNT("count"),
    COUNT_BY_QUERY("countByQuery"),
    DELETE_BY_ID("deleteById"),
    DELETE_BY_QUERY("deleteByQuery"),
    FIND_ALL("findAll"),
    FIND_BY_ID("findById"),
    FIND_BY_IDS("findByIds"),
    NAMED_QUERY("namedQuery"),
    NAMED_QUERY_PAGE("namedQueryPage"),
    SYNC_INDEX("syncIndex"),
    SAVE("save"),
    SEARCH("search"),
    UPDATE("update");

    private final String methodName;

    EntityOperation(String methodName) {
        this.methodName = methodName;
    }

    public String methodName() {
        return methodName;
    }

    public static EntityOperation fromMethodName(String methodName) {
        return switch (methodName) {
            case "bulkSave" -> BULK_SAVE;
            case "bulkUpdate" -> BULK_UPDATE;
            case "count" -> COUNT;
            case "countByQuery" -> COUNT_BY_QUERY;
            case "deleteById" -> DELETE_BY_ID;
            case "deleteByQuery" -> DELETE_BY_QUERY;
            case "findAll" -> FIND_ALL;
            case "findById" -> FIND_BY_ID;
            case "findByIds" -> FIND_BY_IDS;
            case "namedQuery" -> NAMED_QUERY;
            case "namedQueryPage" -> NAMED_QUERY_PAGE;
            case "syncIndex" -> SYNC_INDEX;
            case "save" -> SAVE;
            case "search" -> SEARCH;
            case "update" -> UPDATE;
            default -> throw new IllegalArgumentException("No EntityOperation found for methodName: " + methodName);
        };
    }
}

