# @kinotic-ai/spawn-node

Filesystem adapter for [`@kinotic-ai/spawn`](../spawn). Renders a Spawn from a
directory on disk into another directory, reusing the host-agnostic engine for
the rendering and adding the Node-only pieces:

- loading a spawn directory into an in-memory tree and writing the rendered tree
  back out
- confining all reads (inheritance refs) to the source and all writes to the
  destination, so a template can't escape its root via `..`

The core `@kinotic-ai/spawn` package stays IO-free so it can run embedded in the
JVM (GraalJS); this package is for node/bun callers (e.g. the Kinotic CLI) that
render to disk and shouldn't have to reimplement the disk handling or the
directory-traversal guards.

```ts
import { NodeSpawnRenderer } from '@kinotic-ai/spawn-node'

await new NodeSpawnRenderer().render(spawnDir, destination, {
  context: { projectName: 'acme' },
})
```
