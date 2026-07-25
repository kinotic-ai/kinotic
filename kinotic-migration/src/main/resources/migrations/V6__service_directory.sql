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
        name KEYWORD,
        title TEXT NOT INDEXED,
        description TEXT NOT INDEXED,
        inputSchema JSON NOT INDEXED,
        cri KEYWORD NOT INDEXED,
        annotations OBJECT (
            readOnlyHint BOOLEAN,
            destructiveHint BOOLEAN,
            idempotentHint BOOLEAN
        ) NOT INDEXED
    ),
    online BOOLEAN,
    lastStatusChange DATE
);
