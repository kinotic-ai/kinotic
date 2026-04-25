# Migration SQL Grammar

> Complete SQL grammar reference for migration scripts.

## Overview

This grammar reference applies to migration scripts used for schema and data migrations in Kinotic. Migration scripts use a SQL dialect designed for Elasticsearch index management and data operations.

All statements must end with a semicolon (`;`). Identifiers must start with a letter or underscore and can contain letters, numbers, and underscores. Strings are enclosed in single quotes (`'...'`).

## Statements Overview

- `CREATE TABLE`
- `CREATE COMPONENT TEMPLATE`
- `CREATE INDEX TEMPLATE`
- `REINDEX`
- `INSERT`
- `UPDATE`
- `DELETE`
- Comments

---

## CREATE TABLE

Creates an Elasticsearch index with the specified field mappings.

**Syntax:**

```sql
CREATE TABLE [IF NOT EXISTS] <index_name> (<column_name> <type> [, <column_name> <type>]*) ;
```

- `IF NOT EXISTS` (optional): Only create the index if it does not already exist.
- `<type>`: See [Supported Types](#supported-types).

**Example:**

```sql
CREATE TABLE IF NOT EXISTS products (
    name TEXT,
    sku KEYWORD,
    price DOUBLE,
    inStock BOOLEAN,
    createdAt DATE
) ;
```

---

## CREATE COMPONENT TEMPLATE

Creates a reusable component template that can be referenced by index templates.

**Syntax:**

```sql
CREATE COMPONENT TEMPLATE <template_name> (<definition> [, <definition>]*) ;
```

**Definitions:**

<table>
<thead>
  <tr>
    <th>
      Definition
    </th>
    
    <th>
      Allowed Values
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
        NUMBER_OF_SHARDS
      </code>
    </td>
    
    <td>
      Integer (e.g., 1, 3)
    </td>
    
    <td>
      Number of primary shards for the index
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        NUMBER_OF_REPLICAS
      </code>
    </td>
    
    <td>
      Integer (e.g., 0, 1)
    </td>
    
    <td>
      Number of replica shards for the index
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        <column_name> <type>
      </code>
    </td>
    
    <td>
      See Supported Types
    </td>
    
    <td>
      Field mapping (name and type)
    </td>
  </tr>
</tbody>
</table>

**Example:**

```sql
CREATE COMPONENT TEMPLATE base_settings (
    NUMBER_OF_SHARDS = 3,
    NUMBER_OF_REPLICAS = 1,
    createdAt DATE,
    updatedAt DATE
) ;
```

---

## CREATE INDEX TEMPLATE

Creates an index template that applies settings and mappings to indices matching a pattern.

**Syntax:**

```sql
CREATE INDEX TEMPLATE <template_name> FOR '<pattern>' USING '<component_template>'
    [WITH (<definition> [, <definition>]*)] ;
```

- `WITH (...)` (optional): Additional definitions as in component templates.

**Example:**

```sql
CREATE INDEX TEMPLATE logs_template FOR 'logs-*' USING 'base_settings'
    WITH (NUMBER_OF_REPLICAS = 2, level KEYWORD) ;
```

---

## REINDEX

Copies documents from one index to another with optional transformations.

**Syntax:**

```sql
REINDEX <source_index> INTO <dest_index> [WITH (<option> [, <option>]*)] ;
```

**Options:**

<table>
<thead>
  <tr>
    <th>
      Option
    </th>
    
    <th>
      Allowed Values
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
        CONFLICTS
      </code>
    </td>
    
    <td>
      <code>
        ABORT
      </code>
      
      , <code>
        PROCEED
      </code>
    </td>
    
    <td>
      How to handle version conflicts: abort or proceed
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        MAX_DOCS
      </code>
    </td>
    
    <td>
      Integer (e.g., 1000)
    </td>
    
    <td>
      Maximum number of documents to reindex
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        SLICES
      </code>
    </td>
    
    <td>
      <code>
        AUTO
      </code>
      
      , Integer (e.g., 2)
    </td>
    
    <td>
      Number of slices (parallelism); AUTO lets Elasticsearch decide
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        SIZE
      </code>
    </td>
    
    <td>
      Integer (e.g., 500)
    </td>
    
    <td>
      Batch size for reindexing
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        SOURCE_FIELDS
      </code>
    </td>
    
    <td>
      Comma-separated list (e.g., <code>
        'field1,field2'
      </code>
      
      )
    </td>
    
    <td>
      Restrict source fields to copy
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        QUERY
      </code>
    </td>
    
    <td>
      String (Lucene query syntax)
    </td>
    
    <td>
      Query to filter source documents
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        SCRIPT
      </code>
    </td>
    
    <td>
      String (Painless script)
    </td>
    
    <td>
      Script to transform documents during reindex
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        WAIT
      </code>
    </td>
    
    <td>
      <code>
        TRUE
      </code>
      
      , <code>
        FALSE
      </code>
    </td>
    
    <td>
      If TRUE, wait for completion; if FALSE, return task ID
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        SKIP_IF_NO_SOURCE
      </code>
    </td>
    
    <td>
      <code>
        TRUE
      </code>
      
      , <code>
        FALSE
      </code>
    </td>
    
    <td>
      If TRUE, skip if source index does not exist (default FALSE)
    </td>
  </tr>
</tbody>
</table>

**Example:**

```sql
REINDEX old_products INTO new_products WITH (
    CONFLICTS = PROCEED,
    SLICES = AUTO,
    QUERY = 'status:active',
    SKIP_IF_NO_SOURCE = TRUE
) ;
```

If `SKIP_IF_NO_SOURCE = TRUE`, the reindex operation will be skipped (no error) if the source index does not exist. This is useful for idempotent migrations.

---

## INSERT

Inserts a document into an index.

**Syntax:**

```sql
INSERT INTO <index_name> [(<column_name> [, <column_name>]*)]
    VALUES (<expression> [, <expression>]*) [WITH REFRESH] ;
```

- `WITH REFRESH` (optional): Immediately refresh the index after insert, making the document searchable.
- `<expression>`: Literal value, parameter (`?`), or field reference.

**Example:**

```sql
INSERT INTO products (name, sku, price)
    VALUES ('Widget', 'WDG-001', 9.99) WITH REFRESH ;
```

### The `id` column

When the column list includes `id`, its value is used as the Elasticsearch `_id` of the inserted document. If the column list omits `id`, Elasticsearch auto-generates a random `_id`.

This matters because every find-by-id lookup resolves the document by its `_id`. A row inserted without an `id` column cannot be retrieved by its logical identifier later — it will only be discoverable via search.

As a rule: if the target table has a logical `id` field, always include the `id` column in the INSERT so `_id` stays in sync with the document's `id` value.

```sql
-- Recommended: id column promoted to _id
INSERT INTO users (id, email, active)
    VALUES ('user-001', 'jane@example.com', true)
    WITH REFRESH ;

-- Avoid when the table has a logical id field: the row will get a random _id
INSERT INTO users (email, active) VALUES ('jane@example.com', true) ;
```

---

## UPDATE

Updates documents matching a where clause.

**Syntax:**

```sql
UPDATE <index_name> SET <field> = <expression> [, <field> = <expression>]*
    WHERE <where_clause> [WITH REFRESH] ;
```

- `WITH REFRESH` (optional): Immediately refresh the index after update.
- `<expression>`: Literal, parameter, or binary expression (e.g., `age + 1`).

**Example:**

```sql
UPDATE products SET price = 12.99, updatedAt = '2024-01-15'
    WHERE sku == 'WDG-001' WITH REFRESH ;
```

---

## DELETE

Deletes documents matching a where clause.

**Syntax:**

```sql
DELETE FROM <index_name> WHERE <where_clause> [WITH REFRESH] ;
```

- `WITH REFRESH` (optional): Immediately refresh the index after delete.

**Example:**

```sql
DELETE FROM products WHERE inStock == false WITH REFRESH ;
```

---

## Comments

```sql
-- This is a comment
```

Comments start with `--` and continue to the end of the line. Comments are ignored by the parser.

---

## Where Clauses

Where clauses are used in `UPDATE` and `DELETE` statements.

**Syntax:**

```sql
<field> <operator> <value>
(<where_clause>)
<where_clause> AND <where_clause>
<where_clause> OR <where_clause>
```

**Operators:** `==`, `!=`, `<`, `>`, `<=`, `>=`

**Values:** Literal, parameter (`?`), string, integer, boolean

**Example:**

```sql
WHERE status == 'archived' AND createdAt < '2023-01-01'
WHERE (category == 'electronics' OR category == 'appliances') AND price > 100
```

---

## Supported Types

<table>
<thead>
  <tr>
    <th>
      Type
    </th>
    
    <th>
      Elasticsearch Mapping
    </th>
  </tr>
</thead>

<tbody>
  <tr>
    <td>
      <code>
        TEXT
      </code>
    </td>
    
    <td>
      text
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        KEYWORD
      </code>
    </td>
    
    <td>
      keyword
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        KEYWORD NOT INDEXED
      </code>
    </td>
    
    <td>
      keyword (not indexed, no doc_values)
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        INTEGER
      </code>
    </td>
    
    <td>
      integer
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        INTEGER NOT INDEXED
      </code>
    </td>
    
    <td>
      integer (not indexed, no doc_values)
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        LONG
      </code>
    </td>
    
    <td>
      long
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        LONG NOT INDEXED
      </code>
    </td>
    
    <td>
      long (not indexed, no doc_values)
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        FLOAT
      </code>
    </td>
    
    <td>
      float
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        FLOAT NOT INDEXED
      </code>
    </td>
    
    <td>
      float (not indexed, no doc_values)
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        DOUBLE
      </code>
    </td>
    
    <td>
      double
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        DOUBLE NOT INDEXED
      </code>
    </td>
    
    <td>
      double (not indexed, no doc_values)
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        BOOLEAN
      </code>
    </td>
    
    <td>
      boolean
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        BOOLEAN NOT INDEXED
      </code>
    </td>
    
    <td>
      boolean (not indexed, no doc_values)
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        DATE
      </code>
    </td>
    
    <td>
      date
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        DATE NOT INDEXED
      </code>
    </td>
    
    <td>
      date (not indexed, no doc_values)
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        JSON
      </code>
    </td>
    
    <td>
      object (flattened)
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        JSON NOT INDEXED
      </code>
    </td>
    
    <td>
      object (not indexed)
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        BINARY
      </code>
    </td>
    
    <td>
      binary
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        GEO_POINT
      </code>
    </td>
    
    <td>
      geo_point
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        GEO_SHAPE
      </code>
    </td>
    
    <td>
      geo_shape
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        UUID
      </code>
    </td>
    
    <td>
      keyword
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        UUID NOT INDEXED
      </code>
    </td>
    
    <td>
      keyword (not indexed, no doc_values)
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        DECIMAL
      </code>
    </td>
    
    <td>
      double
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        DECIMAL NOT INDEXED
      </code>
    </td>
    
    <td>
      double (not indexed, no doc_values)
    </td>
  </tr>
</tbody>
</table>

The `NOT INDEXED` variant of each type stores the value but excludes it from the inverted index and doc_values. This reduces storage and indexing overhead for fields that only need to be returned in results, not queried.

---

## Expressions

- **Literals:** `'string'`, `123`, `true`, `false`
- **Parameters:** `?`
- **Binary Expressions:** `<field> + <value>`, `<field> - <value>`, etc.

---

## Reserved Keywords

All keywords in the grammar are reserved and case-insensitive:

`CREATE`, `TABLE`, `IF`, `NOT`, `EXISTS`, `COMPONENT`, `TEMPLATE`, `INDEX`, `FOR`, `USING`, `WITH`, `REINDEX`, `INTO`, `INSERT`, `VALUES`, `UPDATE`, `SET`, `DELETE`, `FROM`, `WHERE`, `AND`, `OR`, `REFRESH`, `TRUE`, `FALSE`, `NUMBER_OF_SHARDS`, `NUMBER_OF_REPLICAS`, `CONFLICTS`, `ABORT`, `PROCEED`, `MAX_DOCS`, `SLICES`, `AUTO`, `SIZE`, `SOURCE_FIELDS`, `QUERY`, `SCRIPT`, `WAIT`, `SKIP_IF_NO_SOURCE`
