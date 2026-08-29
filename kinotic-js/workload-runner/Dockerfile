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
COPY package.json bun.lock bunfig.toml tsconfig.json ./
RUN bun install --production --frozen-lockfile
COPY src ./src

ENTRYPOINT ["bun", "src/supervise.ts"]
