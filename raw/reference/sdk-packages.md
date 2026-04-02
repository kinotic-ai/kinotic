# SDK Packages

> Reference for Kinotic TypeScript SDK packages.

## Overview

The Kinotic TypeScript SDK is organized into several packages, each providing a focused set of functionality. All packages are published under the `@kinotic-ai` scope.

---

## @kinotic-ai/core

Core connectivity, service proxying, events, and CRUD abstractions. This is the foundation package required by all Kinotic applications.

### Key Exports

<table>
<thead>
  <tr>
    <th>
      Export
    </th>
    
    <th>
      Description
    </th>
  </tr>
</thead>

<tbody>
  <tr>
    <td>
      <code>
        Kinotic
      </code>
    </td>
    
    <td>
      Main entry point for connecting to a Kinotic server
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        ConnectionInfo
      </code>
    </td>
    
    <td>
      Connection configuration (host, port, headers)
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        ConnectedInfo
      </code>
    </td>
    
    <td>
      Information about the established connection
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        IServiceRegistry
      </code>
    </td>
    
    <td>
      Interface for the service registry
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        ServiceRegistry
      </code>
    </td>
    
    <td>
      Service registry implementation
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        IEventBus
      </code>
    </td>
    
    <td>
      Interface for the event bus
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        EventBus
      </code>
    </td>
    
    <td>
      Event bus implementation
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        CRI
      </code>
    </td>
    
    <td>
      Kinotic Resource Identifier interface
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        createCRI
      </code>
    </td>
    
    <td>
      Factory function for creating CRI instances
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        CrudServiceProxy
      </code>
    </td>
    
    <td>
      Generic CRUD service proxy
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        ICrudServiceProxy
      </code>
    </td>
    
    <td>
      CRUD service proxy interface
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        Page
      </code>
    </td>
    
    <td>
      Paginated result container
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        Pageable
      </code>
    </td>
    
    <td>
      Pagination request parameters
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        IterablePage
      </code>
    </td>
    
    <td>
      Async-iterable page for streaming results
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        Sort
      </code>
    </td>
    
    <td>
      Sort configuration for queries
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        KinoticError
      </code>
    </td>
    
    <td>
      Base error class
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        AuthenticationError
      </code>
    </td>
    
    <td>
      Authentication failure error
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        AuthorizationError
      </code>
    </td>
    
    <td>
      Authorization failure error
    </td>
  </tr>
</tbody>
</table>

### Decorators

<table>
<thead>
  <tr>
    <th>
      Decorator
    </th>
    
    <th>
      Description
    </th>
  </tr>
</thead>

<tbody>
  <tr>
    <td>
      <code>
        @Publish(namespace, name?)
      </code>
    </td>
    
    <td>
      Publish a class as a remote service
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        @Version(version)
      </code>
    </td>
    
    <td>
      Set semantic version for a service
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        @Scope
      </code>
    </td>
    
    <td>
      Mark a property as the service scope
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        @Context()
      </code>
    </td>
    
    <td>
      Inject request context into a method parameter
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        @AbacPolicy(expression)
      </code>
    </td>
    
    <td>
      ABAC policy for service method authorization
    </td>
  </tr>
</tbody>
</table>

---

## @kinotic-ai/persistence

Entity persistence layer for defining, syncing, and interacting with Elasticsearch-backed entities.

### Key Exports

<table>
<thead>
  <tr>
    <th>
      Export
    </th>
    
    <th>
      Description
    </th>
  </tr>
</thead>

<tbody>
  <tr>
    <td>
      <code>
        PersistencePlugin
      </code>
    </td>
    
    <td>
      Plugin to register with Kinotic for persistence support
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        IEntityService
      </code>
    </td>
    
    <td>
      Interface for entity CRUD operations
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        IAdminEntityService
      </code>
    </td>
    
    <td>
      Interface for admin-level entity operations
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        IEntitiesService
      </code>
    </td>
    
    <td>
      Interface for managing entity definitions
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        IAdminEntitiesService
      </code>
    </td>
    
    <td>
      Interface for admin entity definition management
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        QueryParameter
      </code>
    </td>
    
    <td>
      Query parameter for named queries
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        QueryOptions
      </code>
    </td>
    
    <td>
      Options for query execution
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        MultiTenancyType
      </code>
    </td>
    
    <td>
      Enum: <code>
        NONE
      </code>
      
      , <code>
        SHARED
      </code>
      
      , <code>
        DEDICATED
      </code>
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        EntityType
      </code>
    </td>
    
    <td>
      Enum: <code>
        TABLE
      </code>
      
      , <code>
        DATA_STREAM
      </code>
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        PrecisionType
      </code>
    </td>
    
    <td>
      Enum: <code>
        INT
      </code>
      
      , <code>
        SHORT
      </code>
      
      , <code>
        LONG
      </code>
      
      , <code>
        FLOAT
      </code>
      
      , <code>
        DOUBLE
      </code>
    </td>
  </tr>
</tbody>
</table>

### Entity Decorators

<table>
<thead>
  <tr>
    <th>
      Decorator
    </th>
    
    <th>
      Description
    </th>
  </tr>
</thead>

<tbody>
  <tr>
    <td>
      <code>
        @Entity(multiTenancyType?, entityType?)
      </code>
    </td>
    
    <td>
      Mark a class as a persisted entity
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        @EntityServiceDecorators(config)
      </code>
    </td>
    
    <td>
      Configure per-operation decorators
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        @AutoGeneratedId
      </code>
    </td>
    
    <td>
      Server-generated ID field
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        @Id
      </code>
    </td>
    
    <td>
      Manually-assigned ID field
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        @NotNull
      </code>
    </td>
    
    <td>
      Required field
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        @Text
      </code>
    </td>
    
    <td>
      Full-text search indexing
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        @Precision(PrecisionType)
      </code>
    </td>
    
    <td>
      Numeric precision
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        @NotIndexed
      </code>
    </td>
    
    <td>
      Exclude from indexing
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        @Flattened
      </code>
    </td>
    
    <td>
      Flattened object mapping
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        @Nested
      </code>
    </td>
    
    <td>
      Nested object mapping
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        @Discriminator(propertyName)
      </code>
    </td>
    
    <td>
      Polymorphic type discriminator
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        @TenantId
      </code>
    </td>
    
    <td>
      Tenant identifier field
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        @Version
      </code>
    </td>
    
    <td>
      Optimistic locking
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        @TimeReference
      </code>
    </td>
    
    <td>
      Time reference for data streams
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        @Query(statement)
      </code>
    </td>
    
    <td>
      Named query definition
    </td>
  </tr>
</tbody>
</table>

### Policy Factory Functions

<table>
<thead>
  <tr>
    <th>
      Function
    </th>
    
    <th>
      Description
    </th>
  </tr>
</thead>

<tbody>
  <tr>
    <td>
      <code>
        $AbacPolicy(expression)
      </code>
    </td>
    
    <td>
      ABAC policy for entity operations
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        $Policy(policies)
      </code>
    </td>
    
    <td>
      Policy rules matrix
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        $Role(roles)
      </code>
    </td>
    
    <td>
      Role-based access
    </td>
  </tr>
</tbody>
</table>

---

## @kinotic-ai/os-api

Platform-level management APIs for interacting with Kinotic OS services like application management, project management, and migrations.

### Key Exports

<table>
<thead>
  <tr>
    <th>
      Export
    </th>
    
    <th>
      Description
    </th>
  </tr>
</thead>

<tbody>
  <tr>
    <td>
      <code>
        OsApiPlugin
      </code>
    </td>
    
    <td>
      Plugin to register with Kinotic for OS API access
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        IApplicationService
      </code>
    </td>
    
    <td>
      Application CRUD operations
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        IProjectService
      </code>
    </td>
    
    <td>
      Project management operations
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        IMigrationService
      </code>
    </td>
    
    <td>
      Migration execution and management
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        IEntityDefinitionService
      </code>
    </td>
    
    <td>
      Entity definition management
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        INamedQueriesDefinitionService
      </code>
    </td>
    
    <td>
      Named queries definition management
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        IDataInsightsService
      </code>
    </td>
    
    <td>
      Data insights and analytics
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        ILogManager
      </code>
    </td>
    
    <td>
      Log level management
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        LogManager
      </code>
    </td>
    
    <td>
      Log manager implementation
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        Application
      </code>
    </td>
    
    <td>
      Application model
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        Project
      </code>
    </td>
    
    <td>
      Project model
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        ProjectType
      </code>
    </td>
    
    <td>
      Project type enum
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        EntityDefinition
      </code>
    </td>
    
    <td>
      Entity definition model
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        MigrationDefinition
      </code>
    </td>
    
    <td>
      Migration definition model
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        MigrationRequest
      </code>
    </td>
    
    <td>
      Migration execution request
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        MigrationResult
      </code>
    </td>
    
    <td>
      Migration execution result
    </td>
  </tr>
</tbody>
</table>

---

## @kinotic-ai/idl

Interface Definition Language types used internally by the SDK for service and type definitions. Useful when building tooling or working with the Kinotic type system programmatically.

### Key Exports

<table>
<thead>
  <tr>
    <th>
      Export
    </th>
    
    <th>
      Description
    </th>
  </tr>
</thead>

<tbody>
  <tr>
    <td>
      <code>
        C3Type
      </code>
    </td>
    
    <td>
      Base type in the Kinotic type hierarchy
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        ServiceDefinition
      </code>
    </td>
    
    <td>
      Complete definition of a published service
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        FunctionDefinition
      </code>
    </td>
    
    <td>
      Definition of a service method
    </td>
  </tr>
</tbody>
</table>

The IDL package provides the type system that powers code generation, entity synchronization, and service registration. Most application developers will not need to interact with this package directly.
