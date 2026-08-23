# Image for the workloads that deploy and run a customer project on a node.
#
# The sync workload runs `bun src/sync.ts` (set as the workload entrypoint) and the
# runtime workload runs the default supervisor entrypoint. The package has no runtime
# dependencies, so the sources are copied in as-is and bun runs them directly.
#
# Build from this directory: docker build -t ghcr.io/kinotic-ai/workload-runner .
FROM oven/bun:1-slim

# git for the checkout, ca-certificates for https to github.com and the Kinotic server
RUN apt-get update \
    && apt-get install -y --no-install-recommends git ca-certificates \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /opt/workload-runner
COPY package.json tsconfig.json ./
COPY src ./src

ENTRYPOINT ["bun", "src/supervise.ts"]
