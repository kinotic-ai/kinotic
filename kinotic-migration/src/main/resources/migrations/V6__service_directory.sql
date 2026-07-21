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
    sourceVersion KEYWORD,
    published BOOLEAN,
    mcpExposed BOOLEAN,
    mcpTools JSON NOT INDEXED,
    online BOOLEAN,
    lastStatusChange DATE
);
