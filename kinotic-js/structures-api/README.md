# Structures API Overview

The Structures API provides a robust framework for managing data structures within the C3 Interface Definition Language (IDL) environment. Designed to facilitate efficient data operations, the API offers a comprehensive suite of services that cater to structure and entity management across diverse application namespaces.

## Key Features

- **Structure Management**: The API allows for seamless handling of data structures. Through dedicated services, users can publish, unpublish, find, and count structures within specific namespaces, enabling precise control over data configurations.

- **Entity Operations**: Offering full CRUD capabilities, the API enables the management of entities within a structure. Users can save, update, delete, and query entities, with support for batch operations that enhance performance in high-volume scenarios.

- **Advanced Querying**: With built-in support for named queries and pagination, the API facilitates efficient data retrieval. Users can execute complex queries and manage datasets with ease, leveraging the API's pagination mechanisms for optimized data flow.

- **Namespace Flexibility**: The API includes services for namespace management, ensuring that data operations are contextualized within appropriate domains. Users can create and maintain namespaces dynamically, promoting organized data handling.

- **Sophisticated Data Modelling**: Utilizing classes designed for complex object typing, the API supports intricate data models with features like inheritance. This allows users to define sophisticated data types that align with specific application requirements.

- **Service Integration**: The API integrates several services, such as Namespace, Structure, and Entities services, to provide a seamless experience for managing and interacting with data structures, streamlining workflow efficiency across the application.

- **Customizable Utilities**: A variety of utilities and decorators are available for defining and enforcing data structure properties. These tools support configurations like auto-generated IDs and property constraints, ensuring precise data operation controls.


## Details
In the Structures API for TypeScript, decorators like `@Entity` are used to annotate classes and properties to define entities and configure their behavior within the C3 IDL framework. Here's a breakdown of how some of these decorators are used to define a structure:

### `@Entity` Decorator
- **Purpose**: Signifies that a class is an entity, and optionally specifies multi-tenancy behavior.
- **Usage**: Place this decorator above a class definition to mark it as an entity. It can take a parameter to define multi-tenancy type (`MultiTenancyType`, such as `NONE` or `SHARED`).
- **Example**:
  ```typescript
  @Entity(MultiTenancyType.SHARED)
  export class Person {
      // properties and methods
  }
  ```

### `@Id` Decorator
- **Purpose**: Identifies the primary key of an entity.
- **Usage**: Place above a property that serves as the unique identifier.
- **Example**:
  ```typescript
  export class Person {
    @Id
    public id!: string;
  }
  ```

### `@Precision` Decorator
- **Purpose**: Configures precision for numerical properties.
- **Usage**: Place above a numeric property, specifying its precision type.
- **Example**:
  ```typescript
  export class Person {
    @Precision(PrecisionType.SHORT)
    public age?: number;
  }
  ```

### `@Policy` Decorator
- **Purpose**: Applies access policies to entities or specific properties.
- **Usage**: Can be applied at the class level to control access to methods or properties based on specified policies.
- **Example**:
  ```typescript
  @Policy([['data:read']])
  export class Person {
      // properties and methods
  }
  ```

### `@Flattened` and `@Nested` Decorators
- **Purpose**: Define how complex properties are treated, either integrating them directly inline (`Flattened`) or as separate entities (`Nested`).
- **Usage**: Apply these decorators to properties that are objects, depending on how you want their properties to be included in the main entity.
- **Example**:
  ```typescript
  export class Person{
    @Nested
    public address!: Address;
  }
  ```

Using these decorators, you can effectively map and configure structures in TypeScript, facilitating integration with the C3 IDL framework for entity management and operations. These annotations guide the system on how to handle each property and its various operational contexts, such as storage, retrieval, and permissions.

## Local Development
- Run Tests with ```pnpm run test```

## Build and Publish
- Run ```pnpm run build```
- Run ```export NPM_TOKEN=your_access_token```
- Run ```pnpm publish```
