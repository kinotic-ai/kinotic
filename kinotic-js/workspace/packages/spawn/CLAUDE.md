# @kinotic-ai/spawn

`kinotic-management-api/build.gradle` extracts `dist/graal-spawn-renderer.js` from the **published**
tarball, pinned by an exact `kinoticSpawnVersion` in the root `gradle.properties` — it cannot
float like a semver range.

**Whenever this package is published at a new version, set `kinoticSpawnVersion` to it.**
Otherwise the server silently keeps rendering with the old engine while the CLI moves on.
