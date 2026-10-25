# The compose stack's UIs: the portal and the system console, built from kinotic-frontend, served on 9090 and
# 9091 by kinotic-ui.ts.
#
# Build with the repository root as the context, which holds both kinotic-frontend and the server script:
#   docker build -f deployment/docker-compose/kinotic-ui.Dockerfile -t kinoticai/kinotic-ui .
FROM node:24.17.0-slim AS build
RUN npm install --global pnpm@11
WORKDIR /kinotic-frontend
COPY kinotic-frontend/ ./
RUN pnpm install --frozen-lockfile && pnpm build

FROM oven/bun:1-slim
WORKDIR /ui
COPY --from=build /kinotic-frontend/apps/portal/dist portal
COPY --from=build /kinotic-frontend/apps/system/dist console
COPY deployment/docker-compose/kinotic-ui.ts kinotic-ui.ts
EXPOSE 9090 9091
CMD ["bun", "kinotic-ui.ts"]
