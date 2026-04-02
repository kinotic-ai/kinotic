# CLI Reference

> Command reference for the Kinotic CLI.

## Installation

```bash
bun install -g @kinotic-ai/kinotic-cli
```

## Commands

### `kinotic init` / `kinotic initialize`

Initialize a new Kinotic project. Creates the configuration file and directory structure for entity definitions and generated services.

```bash
kinotic init --application my.app --entities src/entities --generated src/generated
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
        -a, --application
      </code>
    </td>
    
    <td>
      The application name
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        -e, --entities
      </code>
    </td>
    
    <td>
      Path to entity definitions directory
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        -g, --generated
      </code>
    </td>
    
    <td>
      Path for generated service classes
    </td>
  </tr>
</tbody>
</table>

---

### `kinotic generate` / `kinotic gen`

Generate Entity Service classes from synced entity definitions. This reads the entity definitions that have been synchronized with the server and produces TypeScript service classes you can import and use.

```bash
kinotic generate
kinotic gen -v
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
</tbody>
</table>

---

### `kinotic sync` / `kinotic synchronize`

Synchronize local entity definitions with the Kinotic server. This uploads your entity classes so the server can set up the backing data stores and register the entity services.

```bash
kinotic sync -p --server http://localhost:9090
kinotic sync -p -v -s http://localhost:9090
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
      The Kinotic server URL
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
        -f, --authHeaderFile
      </code>
    </td>
    
    <td>
      JSON file containing authentication headers
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        --dryRun
      </code>
    </td>
    
    <td>
      Preview changes without saving
    </td>
  </tr>
</tbody>
</table>

---

### `kinotic update`

Update the Kinotic CLI to the latest version.

```bash
kinotic update
kinotic update --version 1.0.3
```

---

Run `kinotic --help` or `kinotic <command> --help` for the full list of commands and options.
