# Applications and Projects

> Learn how to configure Kinotic applications and the projects that compose them.

An application is the deployable unit in Kinotic. Projects are the building blocks that make up an application. This page explains how to configure both.

## Applications

Every application has:

- **Name** -- A human-readable name (e.g., `Inventory App`).
- **Id** -- Derived from the name at creation: the slugified name, made of lowercase letters, digits, and interior dashes (e.g., `inventory-app`). The id forms the final label of the application's [zone](/platform/reference/cri-format).
- **Description** -- A human-readable summary of what the application does.

Applications are created and managed through the Kinotic CLI and the Kinotic OS dashboard.

## Projects

A project is a directory with its own `package.json` and a `.config/kinotic.config.ts` configuration file. The CLI uses that config to understand how the project fits into the broader application.

### The `.config/kinotic.config.ts` File

Every project carries a `.config/kinotic.config.ts`, created when Kinotic OS provisions
the project repository. It is a TypeScript module exporting a `KinoticProjectConfig`, so
your editor type-checks it:

```ts
import type { KinoticProjectConfig } from '@kinotic-ai/management-api'

const config: KinoticProjectConfig = {
  organizationId: "my-org",
  applicationId: "my-app",
  entitiesPaths: [
    {
      path: "packages/domain/model",
      repositoryPath: "packages/domain/repositories",
      mirrorFolderStructure: true
    }
  ],
  fileExtensionForImports: ".js",
  validate: false
}

export default config
```

The CLI reads the first `kinotic.config.*` file it finds in `.config`, so a `.js` or `.json` config
is loaded the same way.

#### Fields

<table>
<thead>
  <tr>
    <th>
      Field
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
        organizationId
      </code>
    </td>
    
    <td>
      <strong>
        Required.
      </strong>
      
       The id of the Kinotic organization this project belongs to.
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        applicationId
      </code>
    </td>
    
    <td>
      <strong>
        Required.
      </strong>
      
       The application id this project belongs to. Must match the application id registered with Kinotic OS: lowercase letters, digits, and interior dashes.
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        name
      </code>
    </td>
    
    <td>
      Optional project name. When omitted, the <code>
        name
      </code>
      
       from the project's <code>
        package.json
      </code>
      
       is used.
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        description
      </code>
    </td>
    
    <td>
      Optional project description. When omitted, the <code>
        description
      </code>
      
       from the project's <code>
        package.json
      </code>
      
       is used.
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        entitiesPaths
      </code>
    </td>
    
    <td>
      <strong>
        Required.
      </strong>
      
       An array whose entries are either a plain path string or an <code>
        EntitiesPathConfig
      </code>
      
       object with <code>
        path
      </code>
      
       (the directory containing entity definitions), <code>
        repositoryPath
      </code>
      
       (where the CLI writes generated repository classes), and <code>
        mirrorFolderStructure
      </code>
      
       (whether to replicate the entity directory structure in the output, default <code>
        true
      </code>
      
      ). The CLI scans these paths when you run <code>
        kinotic sync
      </code>
      
      .
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        generatedPath
      </code>
    </td>
    
    <td>
      The default output path for generated files, used for <code>
        entitiesPaths
      </code>
      
       entries that are plain strings. Ignored for entries that use <code>
        EntitiesPathConfig
      </code>
      
      .
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        fileExtensionForImports
      </code>
    </td>
    
    <td>
      The extension the CLI writes on import paths in generated code. Defaults to <code>
        .js
      </code>
      
      .
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        validate
      </code>
    </td>
    
    <td>
      When true, generated repository classes validate data before sending it to the server.
    </td>
  </tr>
</tbody>
</table>

### Project Dependencies

Projects within the same application can depend on each other. For example, a microservice project might import entity types defined in a persistence project. These dependencies are managed through standard `package.json` -- you add the dependency just like any other Bun package.

```json
{
    "dependencies": {
        "@my-org/data": "workspace:*"
    }
}
```

When the application is deployed, the platform resolves these internal dependencies and ensures all projects are available to each other through the Service Directory.

## Deployable packages

When a push deploys a project (see [Push to Deploy](/apps/deployment/push-to-deploy)), the
platform finds the packages it deploys by where they sit in the workspace:

- **Microservices** live directly under `packages/microservices`, one package each. A
microservice runs in a VM of its own from its `package.json` `main`, or `src/main.ts` when
it declares none.
- **UIs** live directly under `packages/ui`, one package each, and declare a `build` script
that writes `dist/index.html`. A package under `packages/ui` without a `build` script is a
library and is left alone.

A package's identity is the unscoped part of its `package.json` `name` (`@acme/admin` is
`admin`), which must be lowercase letters, digits, and interior dashes and unique among the
packages of its kind. The directory name never matters.

### The UI build contract

Every UI is built during the deployment with `bun run build`, and the build is handed the
platform's address as three variables. Vite exposes `VITE_*` variables to the page on its
own, so a Vite project reads them with no configuration; a UI with another build tool must
pass them through itself:

<table>
<thead>
  <tr>
    <th>
      Variable
    </th>
    
    <th>
      Value
    </th>
    
    <th>
      What the UI does with it
    </th>
  </tr>
</thead>

<tbody>
  <tr>
    <td>
      <code>
        VITE_KINOTIC_HOST
      </code>
    </td>
    
    <td>
      e.g. <code>
        api.kinotic.ai
      </code>
    </td>
    
    <td>
      The host the UI connects to Kinotic on from a browser
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        VITE_KINOTIC_PORT
      </code>
    </td>
    
    <td>
      e.g. <code>
        443
      </code>
    </td>
    
    <td>
      Its port
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        VITE_KINOTIC_USE_SSL
      </code>
    </td>
    
    <td>
      <code>
        true
      </code>
      
       or <code>
        false
      </code>
    </td>
    
    <td>
      Whether to connect over TLS
    </td>
  </tr>
</tbody>
</table>

```ts
await Kinotic.connect({
    server: {
        host: import.meta.env.VITE_KINOTIC_HOST,
        port: parseInt(import.meta.env.VITE_KINOTIC_PORT),
        useSSL: import.meta.env.VITE_KINOTIC_USE_SSL === 'true',
    },
})
```

A build that does not write `dist/index.html` fails the deployment naming the UI.

`dist` is published as it is, from the site's root: files under `assets/` carry a content
hash in their name and are cached for a year, everything else is never cached. Each site
also publishes the commit it serves as `version.json` next to its `index.html`, never
cached, as `{ "commitSha": "<commit>" }`. A publish replaces the previous commit's files;
a tab left open on the previous commit loads the new commit when it next reloads.

## Typical Setup

Most applications start with a single project created through Kinotic OS, which provisions a GitHub repository scaffolded as a Bun workspace mono repo: `packages/domain` (entity model and generated repositories), `packages/microservices`, `packages/ui`, and a `.config/kinotic.config.ts` wired to the owning organization and application. Clone the provisioned repository and start building.

As the application grows, you can add additional projects for microservices, batch jobs, or frontends -- each with its own `.config/kinotic.config.ts` pointing to the same application identifier.
