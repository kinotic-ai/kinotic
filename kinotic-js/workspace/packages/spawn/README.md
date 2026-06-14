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
  context derived entirely from the `Project`
