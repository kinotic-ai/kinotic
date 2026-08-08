# @kinotic-ai/spawn

This is the one workspace package the Java server embeds rather than consumes over the
wire, which gives it a release requirement no other package has.

## Publishing: bump `kinoticSpawnVersion` in the same change

**IMPORTANT:** Publishing a new version of this package does not reach the Kinotic server
on its own. `kinotic-github/build.gradle` resolves the published tarball from the npm
registry through a Gradle ivy repository and extracts `dist/graal-spawn-renderer.js` onto
the server classpath, so `GraalJsSpawnRenderer` executes the *published* engine, not the
source in this directory:

```groovy
spawnRenderer "@kinotic-ai:spawn:${kinoticSpawnVersion}@tgz"
```

`kinoticSpawnVersion` lives in the root `gradle.properties` and is an **exact** version.
It cannot be a range the way `kinotic-cli`'s `^5.0.0-beta.1` dependency is: the ivy
repository resolves the tarball by literal filename, so nothing about a publish moves it.

Whenever this package's `version` changes and is published, set `kinoticSpawnVersion` to
that same version in the root `gradle.properties`.

Forgetting it fails silently and in a way tests do not catch. The CLI's caret range picks
up the new engine immediately while the server stays on the pinned one, so the two render
templates with different liquid versions — contradicting what `GraalJsSpawnRenderer`
documents about them being the same engine, and making a template that works under
`kinotic spawn lint` capable of rendering differently during project provisioning.

The pin sat on `0.5.0` while this package moved onto the `5.0.0` release train. Nothing
surfaced it, because the `5.0.0-beta.0` renumber produced a byte-identical bundle; the
drift only became observable once `5.0.0-beta.1` changed the bundled liquidjs.

## The published bundle

`files: ["dist"]` ships `dist/graal-spawn-renderer.js` — an iife built by the
`build:graal-spawn-renderer` script, which the workspace root `build` runs after `bunup`.
It defines a `KinoticSpawn` global whose `renderSpawn(json)` the server calls.

`scripts/publish.ts` rebuilds it from a clean lockfile install and smoke-tests the built
iife before publishing, so it never needs to be built by hand for a release. That check
guards the bundle's contents; it does not know about `kinoticSpawnVersion`, which remains
a manual step.
