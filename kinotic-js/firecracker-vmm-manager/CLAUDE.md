# firecracker-vmm-manager

Reference only — unused, unbuilt, unpublished, and not wired into any Gradle module or CI
workflow. Skip it in repo-wide sweeps; dependency bumps and advisory fixes here are wasted work.

**Do not run an install.** `@mindignited/continuum-client` is not on the public registry, the
`@mindignited` scope is unregistered, and no `.npmrc` or `bunfig.toml` points at a private one —
so an install would run whatever anyone chooses to publish under that name, with no lockfile to
pin against. The 404 is the safe outcome.
