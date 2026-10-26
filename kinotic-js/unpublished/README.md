# Unpublished packages

Packed `@kinotic-ai` packages whose version is not on the registry yet, installed from here by the
consumers that need them until they are published: `kinotic-frontend/pnpm-workspace.yaml`'s catalog and
`e2e-tests/package.json` (with the override in its `pnpm-workspace.yaml`) point at
`kinotic-ai-management-api-5.0.0-beta.70.tgz`, and `deployment/docker-compose/kinotic-ui.Dockerfile`
copies this directory into the UI image's build.

Each tarball is `bun pm pack` of its package in `../workspace` after `bun run build` there, so its
`workspace:` dependencies are written as the versions `bun.lock` records.

Once the packages are published (`../CLAUDE.md`, "Bumping the published packages"), delete this
directory, point every consumer back at the registry versions, refresh the lockfiles, and remove the
`COPY` of it from the Dockerfile.
