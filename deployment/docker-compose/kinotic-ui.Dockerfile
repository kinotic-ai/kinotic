# The compose stack's UIs: the portal and the system console, built from kinotic-frontend, served on 9090 and
# 9091 by kinotic-ui.ts.
#
# Build with the repository root as the context, which holds kinotic-frontend, the server script and the Worker
# whose rules it serves by:
#   docker build -f deployment/docker-compose/kinotic-ui.Dockerfile -t kinoticai/kinotic-ui .
# Branch-only: builds the first-party packages kinotic-frontend's pnpm overrides link to, at the place the links
# name, so this branch needs no npm publish. Never merge into develop.
FROM oven/bun:1-slim AS js
WORKDIR /kinotic-js/workspace
COPY kinotic-js/workspace/ ./
RUN bun install --frozen-lockfile && bunx bunup

FROM node:24.17.0-slim AS build
# Branch-only, with the stage above. Never merge into develop.
COPY --from=js /kinotic-js/workspace /kinotic-js/workspace
WORKDIR /kinotic-frontend
# corepack runs the pnpm release package.json pins as its packageManager, downloading it without asking
ENV COREPACK_ENABLE_DOWNLOAD_PROMPT=0
RUN corepack enable
# The dependencies are fetched from the lockfile alone, so a change to the sources reuses this layer
COPY kinotic-frontend/package.json kinotic-frontend/pnpm-lock.yaml kinotic-frontend/pnpm-workspace.yaml ./
RUN pnpm fetch
COPY kinotic-frontend/ ./
RUN pnpm install --offline --frozen-lockfile && pnpm build

FROM oven/bun:1-slim
WORKDIR /deployment
# kinotic-ui.ts imports the Worker by its path in the repository, so both keep their places under /deployment
COPY deployment/cloudflare/sites-worker/worker.js cloudflare/sites-worker/worker.js
COPY deployment/docker-compose/kinotic-ui.ts docker-compose/kinotic-ui.ts
COPY --from=build /kinotic-frontend/apps/portal/dist docker-compose/portal
COPY --from=build /kinotic-frontend/apps/system/dist docker-compose/console
EXPOSE 9090 9091
CMD ["bun", "docker-compose/kinotic-ui.ts"]
