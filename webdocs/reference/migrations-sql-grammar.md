---
title: Structures Migration SQL Grammar Reference
description: Comprehensive reference for all statements, options, and syntax in the SQL dialect used for Structures migration scripts.
---

# Structures Migration SQL Grammar Reference

> **Note:** This grammar reference applies **only to migration scripts** (files used for schema and data migrations).

This document provides a detailed reference for the SQL dialect used in Structures migration scripts. It covers all supported statements, options, and syntax as defined in the migration grammar.

## Statements Overview

- `CREATE TABLE`
- `CREATE COMPONENT TEMPLATE`
- `CREATE INDEX TEMPLATE`
- `ALTER TABLE`
- `REINDEX`
- `INSERT`
- `UPDATE`
- `DELETE`
- Comments

---

## CREATE TABLE

**Syntax:**
```sql
CREATE TABLE [IF NOT EXISTS] <index_name> (<column_name> <type> [, <column_name> <type>]*) ;
```
- `IF NOT EXISTS` (optional): Only create if the index does not exist.
- `<type>`: See [Supported Types](#supported-types).

---

## CREATE COMPONENT TEMPLATE

**Syntax:**
```sql
CREATE COMPONENT TEMPLATE <template_name> (<definition> [, <definition>]*) ;
```
- `<definition>` can be:
  - `NUMBER_OF_SHARDS = <integer>`: Sets the number of primary shards for indices using this template.
  - `NUMBER_OF_REPLICAS = <integer>`: Sets the number of replica shards for indices using this template.
  - `<column_name> <type>`: Adds a field mapping (see Supported Types below).

| Definition                | Allowed Values         | Description                                             |
|---------------------------|-----------------------|----------------------------------------------------------|
| NUMBER_OF_SHARDS          | Integer (e.g., 1, 3)  | Number of primary shards for the index                   |
| NUMBER_OF_REPLICAS        | Integer (e.g., 0, 1)  | Number of replica shards for the index                   |
| &lt;column_name&gt; &lt;type&gt;      | See Supported Types   | Field mapping (name and type)                            |

---

## CREATE INDEX TEMPLATE

**Syntax:**
```sql
CREATE INDEX TEMPLATE <template_name> FOR '<pattern>' USING '<component_template>' [WITH (<definition> [, <definition>]*) ] ;
```
- `WITH (...)` (optional): Additional definitions as in component templates.

| Definition                | Allowed Values         | Description                                             |
|---------------------------|-----------------------|----------------------------------------------------------|
| NUMBER_OF_SHARDS          | Integer (e.g., 1, 3)  | Number of primary shards for the index                   |
| NUMBER_OF_REPLICAS        | Integer (e.g., 0, 1)  | Number of replica shards for the index                   |
| &lt;column_name&gt; &lt;type&gt;      | See Supported Types   | Field mapping (name and type)                            |

---

## REINDEX

**Syntax:**
```sql
REINDEX <source_index> INTO <dest_index> [WITH (<option> [, <option>]*) ] ;
```
- **Options:**

| Option           | Allowed Values                | Description                                                                 |
|------------------|------------------------------|-----------------------------------------------------------------------------|
| CONFLICTS        | ABORT, PROCEED               | How to handle version conflicts: abort or proceed                           |
| MAX_DOCS         | Integer (e.g., 1000)         | Maximum number of documents to reindex                                      |
| SLICES           | AUTO, Integer (e.g., 2)      | Number of slices (parallelism); AUTO lets Elasticsearch decide              |
| SIZE             | Integer (e.g., 500)          | Batch size for reindexing                                                   |
| SOURCE_FIELDS    | Comma-separated list (e.g., 'field1,field2') | Restrict source fields to copy                                 |
| QUERY            | String (Lucene query syntax)  | Query to filter source documents                                            |
| SCRIPT           | String (Painless script)      | Script to transform documents during reindex                                |
| WAIT             | TRUE, FALSE                  | If TRUE, wait for completion before continuing; if FALSE, return task ID    |
| SKIP_IF_NO_SOURCE | TRUE, FALSE                  | If TRUE, skip reindex if the source index does not exist (default FALSE)     |

> **Note:** If `SKIP_IF_NO_SOURCE = TRUE`, the reindex operation will be skipped (no error) if the source index does not exist. This is useful for idempotent migrations or when the source may be missing in some environments.

---

## INSERT

**Syntax:**
```sql
INSERT INTO <index_name> [(<column_name> [, <column_name>]*)] VALUES (<expression> [, <expression>]*) [WITH REFRESH] ;
```
- `WITH REFRESH` (optional): Immediately refresh the index after insert.
- `<expression>`: Literal, parameter (`?`), or field reference.

---

## UPDATE

**Syntax:**
```sql
UPDATE <index_name> SET <field> = <expression> [, <field> = <expression>]* WHERE <where_clause> [WITH REFRESH] ;
```
- `WITH REFRESH` (optional): Immediately refresh the index after update.
- `<expression>`: Literal, parameter, or binary expression (e.g., `age + 1`).
- `<where_clause>`: See [Where Clauses](#where-clauses).

---

## DELETE

**Syntax:**
```sql
DELETE FROM <index_name> WHERE <where_clause> [WITH REFRESH] ;
```
- `WITH REFRESH` (optional): Immediately refresh the index after delete.

---

## Comments

**Syntax:**
```sql
-- This is a comment
```
- Comments start with `--` and continue to the end of the line.

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
- **Operators:** `==`, `!=`, `<`, `>`, `<=`, `>=`
- **Values:** Literal, parameter (`?`), string, integer, boolean

---

## Supported Types

| Type        | Elasticsearch Mapping   |
|-------------|------------------------|
| TEXT        | text                   |
| KEYWORD     | keyword                |
| KEYWORD NOT INDEXED | keyword (not indexed, no doc_values) |
| INTEGER     | integer                |
| INTEGER NOT INDEXED | integer (not indexed, no doc_values) |
| LONG        | long                   |
| LONG NOT INDEXED | long (not indexed, no doc_values) |
| FLOAT       | float                  |
| FLOAT NOT INDEXED | float (not indexed, no doc_values) |
| DOUBLE      | double                 |
| DOUBLE NOT INDEXED | double (not indexed, no doc_values) |
| BOOLEAN     | boolean                |
| BOOLEAN NOT INDEXED | boolean (not indexed, no doc_values) |
| DATE        | date                   |
| DATE NOT INDEXED | date (not indexed, no doc_values) |
| JSON        | object (flattened)     |
| JSON NOT INDEXED | object (not indexed) |
| BINARY      | binary                 |
| GEO_POINT   | geo_point              |
| GEO_SHAPE   | geo_shape              |
| UUID        | keyword                |
| UUID NOT INDEXED | keyword (not indexed, no doc_values) |
| DECIMAL     | double                 |
| DECIMAL NOT INDEXED | double (not indexed, no doc_values) |

---

## Expressions

- **Literals:** `'string'`, `123`, `true`, `false`
- **Parameters:** `?`
- **Binary Expressions:** `<field> + <value>`, `<field> - <value>`, etc.

---

## Reserved Keywords

All keywords in the grammar are reserved and case-insensitive. Examples: `CREATE`, `TABLE`, `INSERT`, `UPDATE`, `DELETE`, `WITH`, `REFRESH`, etc.

---

## Notes

- All statements must end with a semicolon (`;`).
- Identifiers (`<index_name>`, `<column_name>`, etc.) must start with a letter or underscore and can contain letters, numbers, and underscores.
- Strings are enclosed in single quotes (`'...'`).
- Comments are ignored by the parser. 