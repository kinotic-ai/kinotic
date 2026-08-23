# Image for the workloads that deploy and run a customer project on a node.
#
# The sync workload runs `bun src/sync.ts` (set as the workload entrypoint) and the
# runtime workload runs the default supervisor entrypoint.
#
# Build from this directory: docker build -t ghcr.io/kinotic-ai/workload-runner .
FROM oven/bun:1-slim

# git for the checkout, ca-certificates for https to github.com and the Kinotic server
RUN apt-get update \
    && apt-get install -y --no-install-recommends git ca-certificates \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /opt/workload-runner
COPY package.json tsconfig.json ./
# The kinotic CLI is what the sync entrypoint runs (generate + entity sync). It is an
# image-level install only until this version is on npm: a package.json dependency on an
# unpublished version fails the whole workspace's bun install (the CLI is not a workspace
# member, so bun resolves it from the registry). Once published, move this into
# package.json dependencies and change this line back to `bun install --production`.
RUN bun add @kinotic-ai/kinotic-cli@^5.2.0-beta.9
COPY src ./src

ENTRYPOINT ["bun", "src/supervise.ts"]
