# @kinotic-ai/spawn

Host-agnostic engine for rendering Spawns: file trees containing liquid
templates, liquid expressions in file paths, and an optional `spawn.json`
declaring globals, a property schema, and inheritance.

The engine performs no IO — it takes an in-memory tree of `path -> content`
and returns the rendered tree. Hosts supply the adapters:

- the Kinotic CLI loads spawns from disk and prompts for missing properties
  via a `PropertyResolver`
- the Kinotic server runs the same engine embedded in the JVM (GraalJS) to
  render project baselines into freshly provisioned repositories, with the
  context derived from the `Project` plus a version range for every published
  `@kinotic-ai` package (see "Package version globals")

## What a Spawn looks like

A Spawn is just a directory. `spawn.json` is optional metadata read from the
root of the tree; every other file is either copied verbatim or, if it ends in
`.liquid`, rendered and written with the suffix stripped.

```
project/
  spawn.json                     # metadata — never copied to the output
  package.json.liquid            # rendered -> package.json
  .gitignore                     # copied verbatim
  packages/domain/package.json.liquid
  packages/domain/model/.gitkeep
```

`spawn.json` and `.DS_Store` are excluded from the output by filename, anywhere
in the tree.

## spawn.json

Every key is optional — a Spawn with no `spawn.json` at all renders fine. Only
the three top-level keys below are recognized; anything else is ignored.

```jsonc
{
  // Render the named parent spawn first, then overlay this one on top of it.
  // Optional. See "Inheritance".
  "inherits": "../base",

  // Values made available to every template. Optional.
  // A caller-supplied context value of the same name wins over a global.
  "globals": {
    "kinoticCoreVersion": "^4.0.0"
  },

  // Properties the spawn needs but does not hardcode. Optional.
  // Any entry not already in the context is requested from the host's
  // PropertyResolver (the CLI prompts; the server supplies it from the Project).
  "propertySchema": {
    "projectName": {
      "type": "string",
      "description": "The name of the project you want to create"
    }
  }
}
```

### `propertySchema` entries

Each entry describes one property. All fields are optional:

```jsonc
{
  "libraryName": {
    // One of: "string" | "number" | "integer" | "boolean".
    "type": "string",

    // Shown to the user when the property is prompted for. May contain liquid,
    // rendered against the context resolved so far (see "Resolution order").
    "description": "Library name for {{ projectName }}",

    // Pre-filled value offered at the prompt. A string default may contain
    // liquid, also rendered against the context resolved so far.
    "default": "{{ projectName }}-lib",

    // Allowed values, for hosts that render a choice list.
    "enum": ["mit", "apache-2.0", "none"]
  }
}
```

`type`, `enum`, `description`, and `default` are **hints passed to the
`PropertyResolver`** — the engine renders the `description`/`default` liquid and
hands the schema to the resolver, but does not itself coerce or validate the
returned value against `type` or `enum`. Enforcement is the host's job (e.g. the
CLI prompt). A property already present in the context is used as-is and the
resolver is never called for it, so its schema entry is not consulted either.

When a property is missing from the context **and** no resolver is supplied, the
render fails:

```
No value provided for required property 'projectName'
```

## How values resolve

For a single spawn (no inheritance) the precedence, lowest to highest, is:

```
globals  <  caller context  <  PropertyResolver
```

1. `globals` seed the context.
2. The caller's `context` is layered on top — a context value overrides a global
   of the same name and is never re-requested from the resolver.
3. Any `propertySchema` key still missing is obtained from the resolver (or
   fails the render when there is no resolver).

```jsonc
// spawn.json
{ "globals": { "projectName": "from-globals", "apiVersion": "^1.0.9" } }
```

```ts
// caller overrides projectName; apiVersion falls through from globals
renderSpawn(spawn, {context: {projectName: 'from-context'}})
// final context: { projectName: 'from-context', apiVersion: '^1.0.9' }
```

### Resolution order (liquid in `description` / `default`)

Properties are resolved in the order their keys appear in the merged
`propertySchema`. The liquid in a property's `description` and `default` is
rendered against the context **as it stands when that property is reached** —
globals, caller context, and any properties resolved before it. A `default` that
references another property must therefore reference one that is already known
(a global, a caller value, or an earlier-resolved property).

## Inheritance

`inherits` names a parent spawn. The engine walks the chain, then merges and
renders **base-first so the derived spawn wins**:

- derived `globals` and `propertySchema` entries override inherited ones of the
  same name
- a derived file overwrites an inherited file at the same destination path

```jsonc
// base/spawn.json
{ "globals": { "flavor": "base", "baseOnly": "yes" } }
// derived/spawn.json
{ "inherits": "../base", "globals": { "flavor": "derived" } }
// rendered with flavor=derived, baseOnly=yes
```

The `inherits` value is passed verbatim to the host's `loadInherited` callback;
resolving it is the host's job. On disk the Node host resolves each ref relative
to the spawn that declared it and confines all reads to the spawn root, so a ref
cannot escape via `..`. A spawn that uses `inherits` but is rendered without a
`loadInherited` callback fails:

```
Spawn inherits '../base' but no loadInherited callback was provided
```

## Templating rules (context for authoring spawn.json)

These govern the templates `spawn.json` feeds, and explain why a variable must
be declared:

- **Every referenced variable must be declared.** Rendering uses liquid's
  `strictVariables`, so a reference to a variable that is neither a global, a
  `propertySchema` key, nor a caller context value throws
  (`undefined variable: x`) instead of rendering empty. This holds even inside
  `{% if x %}` or with `{{ x | default: "y" }}` — liquid looks the variable up
  before the branch or filter runs. Declare optionals as `globals` with a real
  value.
- **Paths are templated too.** `{{ }}` in a file path is rendered, so a missing
  variable in a path fails the same way.
- **`.liquid` suffix** is stripped after the content is rendered. Non-`.liquid`
  files are copied verbatim; binary files are never rendered.
- **Filters available** in paths and content: `packageToPath`
  (`org.kinotic.x` -> `org/kinotic/x`), `encodePackage`, `camelCase`,
  `upperFirst`.

```
src/{{ package | packageToPath }}/{{ name | upperFirst }}.ts.liquid
  -> src/org/kinotic/demo/Widget.ts   (package=org.kinotic.demo, name=widget)
```

Use `lint` (or `lintSpawnDir` in the Node host) to find variables referenced by
the templates but declared in neither `globals` nor `propertySchema` before
shipping a spawn.

## Package version globals

A spawn that generates a Kinotic project pins the `@kinotic-ai` packages through
a global per package, named `kinotic<Package>Version`:

```jsonc
// spawn.json
{
  "globals": {
    "kinoticCliVersion": "^4.0.0",
    "kinoticCoreVersion": "^4.0.0",
    "kinoticOsApiVersion": "^4.0.0",
    "kinoticPersistenceVersion": "^4.0.0"
  }
}
```

```jsonc
// package.json.liquid
{
  "devDependencies": {"@kinotic-ai/kinotic-cli": "{{ kinoticCliVersion }}"},
  "catalog": {"@kinotic-ai/core": "{{ kinoticCoreVersion }}"}
}
```

The name drops the `@kinotic-ai` scope and a `kinotic-` prefix on the package
name, so `@kinotic-ai/os-api` is `kinoticOsApiVersion` and
`@kinotic-ai/kinotic-cli` is `kinoticCliVersion`.

Both hosts supply these globals themselves, and a context value beats a global —
so a generated project pins the versions the host ships with, whatever the
spawn's own `globals` say:

- the server, from a build-time projection of this repo's `package.json` files
- the CLI, from its own `@kinotic-ai` dependency ranges

Keep declaring them in `spawn.json` anyway. `lint` reports an undeclared global
whatever a host supplies at render time, and the declared value is the fallback:
a spawn adding a global for a package its host predates renders with the
`spawn.json` value rather than failing.

## Examples in this repo

Two working spawns ship with the CLI under
`kinotic-cli/src/templates/spawns/`:

- `library/spawn.json` — a single `propertySchema` property, no globals
- `project/spawn.json` — `globals` plus several `propertySchema` properties
