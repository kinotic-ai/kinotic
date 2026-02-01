-- Create the application table if it does not exist
CREATE TABLE IF NOT EXISTS struct_application (
    id KEYWORD,
    description TEXT,
    updated DATE,
    enableGraphQL BOOLEAN,
    enableOpenAPI BOOLEAN
);

-- Create the named_query_service_definition table if it does not exist
CREATE TABLE IF NOT EXISTS struct_named_query_service_definition (
    id KEYWORD,
    applicationId KEYWORD,
    projectId KEYWORD,
    structure KEYWORD,
    namedQueries JSON NOT INDEXED
); 

-- Create the project table if it does not exist
CREATE TABLE IF NOT EXISTS struct_project (
    id KEYWORD,
    applicationId KEYWORD,
    name KEYWORD,
    description TEXT,
    sourceOfTruth KEYWORD,
    updated DATE
);

-- Create the structure table if it does not exist
CREATE TABLE IF NOT EXISTS struct_structure (
    id KEYWORD,
    name KEYWORD,
    applicationId KEYWORD,
    projectId KEYWORD,
    description TEXT,
    multiTenancyType KEYWORD,
    entityType KEYWORD,
    entityDefinition JSON NOT INDEXED,
    created DATE,
    updated DATE,
    published BOOLEAN,
    publishedTimestamp DATE,
    itemIndex KEYWORD,
    decoratedProperties JSON NOT INDEXED,
    versionFieldName KEYWORD NOT INDEXED,
    tenantIdFieldName KEYWORD NOT INDEXED,
    timeReferenceFieldName KEYWORD NOT INDEXED
);

-- Reindex data from namespace index to struct_application index
REINDEX namespace INTO struct_application 
WITH (SCRIPT == 'ctx._source.enableGraphQL = true;
                 ctx._source.enableOpenAPI = true;',
      SKIP_IF_NO_SOURCE == TRUE, 
      WAIT == TRUE);

-- Reindex data from named_query_service_definition index to struct_named_query_service_definition index
REINDEX named_query_service_definition INTO struct_named_query_service_definition 
WITH (SCRIPT == 'ctx._source.applicationId = ctx._source.namespace;
                 ctx._source.projectId = ctx._source.namespace + "_default";
                 ctx._source.remove("namespace");',
      SKIP_IF_NO_SOURCE == TRUE, 
      WAIT == TRUE);

-- Create default project records from namespace records
REINDEX namespace INTO struct_project
WITH (SCRIPT == 'ctx._source.applicationId = ctx._source.id;
                 ctx._source.id = ctx._source.id + "_default";
                 ctx._id = ctx._source.id;
                 ctx._source.name = "Default";
                 ctx._source.description = "Default project";
                 ctx._source.sourceOfTruth = "TYPESCRIPT";
                 ctx._source.updated = new Date().getTime();',
      SKIP_IF_NO_SOURCE == TRUE,
      WAIT == TRUE);

-- Reindex data from structures index to struct_structure table
REINDEX structure INTO struct_structure 
WITH (SCRIPT == 'ctx._source.applicationId = ctx._source.namespace;
                 ctx._source.projectId = ctx._source.namespace + "_default";
                 ctx._source.remove("namespace");',
      SKIP_IF_NO_SOURCE == TRUE,
      WAIT == TRUE);




