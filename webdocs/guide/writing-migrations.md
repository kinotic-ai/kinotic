---
title: Writing Migrations
description: Learn how to write migrations in Structures to manage schema evolution and data transformations.
---

# Writing Migrations

Migrations in Structures allow you to define and apply changes to your Elasticsearch indices in a controlled, versioned manner. Each migration is a SQL-like script identified by a unique version in its filename, ensuring idempotency and traceability. This guide explains how to write migration scripts and details the supported statements. If you're new to Structures, start with the [What is Structures?](/guide/overview) guide.

## Migration Script Structure

Migration scripts are written in a SQL-like syntax and consist of one or more statements. The version is determined by the filename in the format `V<number>__<description>.sql`.

### Filename Format

```
V<number>__<description>.sql
```

- `<number>`: A sequential number (e.g., `1`, `2`, `3`) to track applied migrations.
- `<description>`: A brief description of the migration's purpose.

### Example

```sql
-- Initial schema setup
CREATE TABLE users (name TEXT, age INTEGER);

-- Add a field and populate data
ALTER TABLE users ADD COLUMN active BOOLEAN;
UPDATE users SET active = true WHERE age > 18;
```

The above content would be saved in a file named `V1__create_users_table.sql`.

## How Migrations Work

1. **Discovery**: Migrations are discovered from files (or other sources) and represented as `Migration` objects. The version is extracted from the filename (e.g., `V1__desc.sql` → version 1) by the migration abstraction, not from the file content.
2. **Parsing**: The `MigrationParser` parses the script only if the migration has not already been applied, producing a `MigrationContent` object containing the list of statements.
3. **Execution**: The `MigrationExecutor` processes each `Migration`:
   - If not applied, executes all statements from the `MigrationContent` and records the version.
   - If already applied, skips the migration.
4. **Idempotency**: Ensures changes are applied only once.

> **Note:** The migration system is efficient and scalable: only unapplied migrations are parsed and loaded into memory, and versioning is handled by the migration abstraction (not the script content).

## Supported Statements

Below are the SQL-like statements supported in migration scripts, with examples.

### 1. CREATE TABLE
Creates a new Elasticsearch index with specified column mappings.

- **Syntax**: `CREATE TABLE <index_name> (<column_name> <type> [, <column_name> <type>]*) ;`
- **Types**: See the Supported Types table in the [Migrations SQL Grammar Reference](/reference/migrations-sql-grammar#supported-types) for a full list and backend mappings.
- **Example**:
```sql
CREATE TABLE users (name TEXT, age INTEGER, active BOOLEAN);
```

### 2. CREATE COMPONENT TEMPLATE
Defines a reusable component template with settings and mappings.

- **Syntax**: `CREATE COMPONENT TEMPLATE <template_name> (<definition> [, <definition>]*) ;`
- **Definitions**: `NUMBER_OF_SHARDS = <integer>`, `NUMBER_OF_REPLICAS = <integer>`, `<column_name> <type>`
- **Example**:
```sql
CREATE COMPONENT TEMPLATE user_settings (NUMBER_OF_SHARDS = 1, NUMBER_OF_REPLICAS = 1, status KEYWORD);
```

### 3. CREATE INDEX TEMPLATE
Creates an index template with a pattern, component templates, and optional settings/mappings.

- **Syntax**: `CREATE INDEX TEMPLATE <template_name> FOR '<pattern>' USING '<component_template>' [WITH (<definition> [, <definition>]*) ] ;`
- **Example**:
```sql
CREATE INDEX TEMPLATE user_idx FOR 'users-*' USING 'user_settings' WITH (age INTEGER, active BOOLEAN);
```

### 4. ALTER TABLE
Adds a new field to an existing Elasticsearch index.

- **Syntax**: `ALTER TABLE <index_name> ADD COLUMN <column_name> <type> ;`
- **Example**:
```sql
ALTER TABLE users ADD COLUMN active BOOLEAN;
```

### 5. REINDEX
Reindexes data from one index to another with optional settings.

- **Syntax**: `REINDEX <source_index> INTO <dest_index> [WITH (<option> [, <option>]*) ] ;`
- **Options**: 
  - `CONFLICTS = (ABORT | PROCEED)`
  - `MAX_DOCS = <integer>`
  - `SLICES = (AUTO | <integer>)`
  - `SIZE = <integer>`
  - `SOURCE_FIELDS = '<fields>'`
  - `QUERY = '<lucene_query>'`
  - `SCRIPT = '<script>'`
  - `WAIT = (TRUE | FALSE)` (if `WAIT = TRUE`, the migration will poll for completion and only continue when reindexing is done; otherwise, it returns immediately with the task ID)
  - `SKIP_IF_NO_SOURCE = (TRUE | FALSE)` (if `TRUE`, the reindex will be skipped if the source index does not exist; default is `FALSE`)
- **Example**:
```sql
REINDEX users INTO users_new WITH (CONFLICTS = PROCEED, MAX_DOCS = 1000, SLICES = AUTO, QUERY = 'active:true', WAIT = TRUE);
REINDEX users INTO users_new WITH (SKIP_IF_NO_SOURCE = TRUE);
```
> **Note:** REINDEX is always executed asynchronously at the Elasticsearch level. If `WAIT = TRUE`, Structures will poll for completion before proceeding to the next statement. If `WAIT = FALSE` or omitted, the migration continues immediately and returns the Elasticsearch task ID.
> **Note:** If `SKIP_IF_NO_SOURCE = TRUE`, the reindex operation will be skipped (no error) if the source index does not exist. This is useful for idempotent migrations or when the source may be missing in some environments.

### 6. INSERT
Inserts new documents into an index with specified field values.

- **Syntax**: `INSERT INTO <index_name> [(<column_name> [, <column_name>]*)] VALUES (<expression> [, <expression>]*) [WITH REFRESH] ;`
- **Options**: 
  - `WITH REFRESH` (immediately refreshes the index after insert)
- **Expressions**: Literals (`'value'`, `123`, `true`), parameters (`?`), field references (`field_name`)
- **Example**:
```sql
-- Insert a single document with column names
INSERT INTO users (name, age, active) VALUES ('John', 30, true);

-- Insert without specifying columns (all fields must be provided in order)
INSERT INTO users VALUES ('Jane', 25, false);

-- Insert and refresh the index
INSERT INTO users (name, age) VALUES ('Alice', 22) WITH REFRESH;
```

### 7. UPDATE
Updates documents in an index based on a WHERE clause and SET assignments.

- **Syntax**: `UPDATE <index_name> SET <field> = <expression> [, <field> = <expression>]* WHERE <where_clause> [WITH REFRESH] ;`
- **Options**: 
  - `WITH REFRESH` (immediately refreshes the index after update)
- **Expressions**: Literals (`'value'`, `123`, `true`), parameters (`?`), binary expressions (`field + 1`)
- **Where Clause**: Conditions (`field < value`), `AND`, `OR`, parentheses
- **Example**:
```sql
UPDATE users SET status = 'active', age = age + 1 WHERE name = 'John' AND age > 30 WITH REFRESH;
```

### 8. DELETE
Deletes documents from an index based on a WHERE clause.

- **Syntax**: `DELETE FROM <index_name> WHERE <where_clause> [WITH REFRESH] ;`
- **Options**: 
  - `WITH REFRESH` (immediately refreshes the index after delete)
- **Example**:
```sql
DELETE FROM users WHERE active = false WITH REFRESH;
```

## Further Reference

For a comprehensive, technical reference of all supported statements, options, and syntax, see the [Migrations SQL Grammar Reference](/reference/migrations-sql-grammar).