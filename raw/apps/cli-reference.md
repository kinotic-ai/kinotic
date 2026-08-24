# CLI Reference

> Command reference for the Kinotic CLI.

## Installation

Provisioned project repositories vendor the CLI as a dev dependency, so inside a
project the CLI is already available -- `bun run generate` runs it with no global
install. To use the CLI outside a project:

```bash
bun install -g @kinotic-ai/kinotic-cli
```

## Commands

Project commands (`kinotic generate`, `kinotic sync`) locate the project by searching upward from the current directory for a `.config/kinotic.config.*` file, so they can be run from any directory inside the project.

### `kinotic login`

Log in to a Kinotic server and store credentials for subsequent commands. Runs the OAuth device-authorization flow: the CLI opens a browser once for you to approve, then stores a refresh token so later commands are non-interactive. The server you log in to becomes the default server for commands like `kinotic sync`.

```bash
kinotic login --server http://localhost:58503
kinotic login
```

**Flags:**

<table>
<thead>
  <tr>
    <th>
      Flag
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
        -s, --server
      </code>
    </td>
    
    <td>
      The Kinotic server URL to log in to. When omitted, the stored default server is used
    </td>
  </tr>
</tbody>
</table>

---

### `kinotic generate` / `kinotic gen`

Generate Repository classes from your entity definitions. This runs locally without connecting to the server, and fills in the implementations of [named query](/apps/persistence/named-queries) methods declared with `@Query`.

Each entity's C3Type schema is written as JSON to `.config/c3/entities/<namespace>.<name>.json`, and the named queries declared on a Repository to `.config/c3/queries/<RepositoryName>.json`. Removing the last named query from a Repository removes its queries file.

```bash
kinotic generate
kinotic gen -v
kinotic gen --force
```

**Flags:**

<table>
<thead>
  <tr>
    <th>
      Flag
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
        -v, --verbose
      </code>
    </td>
    
    <td>
      Enable verbose logging
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        -f, --force
      </code>
    </td>
    
    <td>
      Force full regeneration, ignoring incremental change detection
    </td>
  </tr>
</tbody>
</table>

---

### `kinotic sync` / `kinotic synchronize`

Synchronize local entity definitions with the Kinotic server. This uploads your entity classes so the server can set up the backing data stores and register the entity services, and applies any pending [migrations](/apps/persistence/migrations) from the `./migrations` directory. It runs the same generation as `kinotic generate`, so the repository classes and `.config/c3` are refreshed as well. Requires a prior `kinotic login` against the target server.

```bash
kinotic sync -p
kinotic sync -p -v -s http://localhost:58503
```

**Flags:**

<table>
<thead>
  <tr>
    <th>
      Flag
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
        -s, --server
      </code>
    </td>
    
    <td>
      The Kinotic server URL. When omitted, the stored default server is used
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        -p, --publish
      </code>
    </td>
    
    <td>
      Publish each entity after save/update
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        -v, --verbose
      </code>
    </td>
    
    <td>
      Enable verbose logging
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        --dryRun
      </code>
    </td>
    
    <td>
      Enables verbose logging and does not save any changes to the server
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        --force
      </code>
    </td>
    
    <td>
      Force full regeneration, ignoring incremental change detection
    </td>
  </tr>
</tbody>
</table>

---

Run `kinotic --help` or `kinotic <command> --help` for the full list of commands and options.
