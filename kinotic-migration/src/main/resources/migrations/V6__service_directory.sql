-- Create the service_directory table if it does not exist
CREATE TABLE IF NOT EXISTS kinotic_service_directory (
    id KEYWORD,
    serviceAddress KEYWORD,
    organizationId KEYWORD,
    applicationId KEYWORD,
    projectId KEYWORD,
    namespace KEYWORD,
    name KEYWORD,
    version KEYWORD,
    zone KEYWORD,
    description TEXT,
    serviceDefinition JSON NOT INDEXED,
    advertised BOOLEAN,
    mcpExposed BOOLEAN,
    mcpTools OBJECT (
        toolName KEYWORD,
        title TEXT NOT INDEXED,
        description TEXT NOT INDEXED,
        inputSchema KEYWORD NOT INDEXED,
        cri KEYWORD NOT INDEXED,
        readOnlyHint BOOLEAN NOT INDEXED,
        destructiveHint BOOLEAN NOT INDEXED,
        idempotentHint BOOLEAN NOT INDEXED
    ),
    online BOOLEAN,
    lastStatusChange DATE
);
