# The local edge compose.sites.yml runs: the Cloudflare sites Worker serving every published site from Azurite
# (sites-edge.ts).
#
# Build with deployment/ as the context, which holds both the Worker and the edge script:
#   docker build -f docker-compose/sites-edge.Dockerfile -t kinoticai/kinotic-sites-edge deployment
FROM oven/bun:1-slim

WORKDIR /deployment
# sites-edge.ts imports the Worker by its path in the repository, so both keep their places under /deployment
COPY cloudflare/sites-worker/worker.js cloudflare/sites-worker/worker.js
COPY docker-compose/sites-edge.ts docker-compose/sites-edge.ts

EXPOSE 8088
CMD ["bun", "docker-compose/sites-edge.ts"]
