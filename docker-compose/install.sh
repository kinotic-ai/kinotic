#!/bin/bash
set -euo pipefail

REPO="${STRUCTURES_REPO:-kinotic-ai/kinotic}"
BRANCH="${STRUCTURES_BRANCH:-main}"
BASE_URL="https://raw.githubusercontent.com/${REPO}/${BRANCH}/docker-compose"
INSTALL_DIR="${STRUCTURES_INSTALL:-$HOME/.structures}"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${BLUE}╔════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║   Structures Docker Compose Installer  ║${NC}"
echo -e "${BLUE}╚════════════════════════════════════════╝${NC}"
echo ""

# Check Docker
if ! command -v docker &> /dev/null; then
    echo -e "${RED}✗${NC} Docker is not installed. Please install Docker first."
    echo "Visit: https://docs.docker.com/get-docker/"
    exit 1
fi

if ! docker info &> /dev/null; then
    echo -e "${RED}✗${NC} Docker is not running. Please start Docker first."
    exit 1
fi

# Check Docker Compose
if docker compose version &> /dev/null 2>&1; then
    COMPOSE_CMD="docker compose"
elif command -v docker-compose &> /dev/null; then
    COMPOSE_CMD="docker-compose"
else
    echo -e "${RED}✗${NC} Docker Compose is not installed. Please install Docker Compose first."
    exit 1
fi

# Download files
echo -e "${BLUE}ℹ${NC} Downloading Docker Compose files..."
mkdir -p "$INSTALL_DIR"
cd "$INSTALL_DIR"

FILES=(
    "compose.yml"
    "compose.ek-stack.yml"
    "compose-otel.yml"
    "compose.gen-schemas.yml"
    "otel-collector-config.yaml"
    "tempo.yml"
    "mimir.yml"
    "grafana-datasource.yaml"
    "kibana.yml"
)

for file in "${FILES[@]}"; do
    if ! curl -fsSL "${BASE_URL}/${file}" -o "$file" 2>/dev/null; then
        echo -e "${RED}✗${NC} Failed to download ${file}"
        exit 1
    fi
done

echo -e "${GREEN}✓${NC} Files downloaded to ${INSTALL_DIR}"

# Stop existing services if any
if $COMPOSE_CMD ps 2>/dev/null | grep -q "Up"; then
    echo -e "${YELLOW}⚠${NC} Stopping existing services..."
    $COMPOSE_CMD down 2>/dev/null || true
fi

# Start services
echo -e "${BLUE}ℹ${NC} Pulling images and starting services..."
$COMPOSE_CMD pull --quiet 2>/dev/null || true
$COMPOSE_CMD up -d

echo -e "${GREEN}✓${NC} Services started!"
sleep 3

echo ""
echo -e "${GREEN}✓${NC} Structures is running!"
echo ""
echo -e "${BLUE}ℹ${NC} Service URLs:"
echo "  • Structures Server UI:     http://localhost:9090"
echo "  • Structures GraphQL:       http://localhost:4000/graphql/"
echo "  • Structures OpenAPI:       http://localhost:8080/api/"
echo "  • Grafana (Observability):  http://localhost:3000"
echo "  • Kibana:                   http://localhost:5601"
echo "  • Elasticsearch:            http://localhost:9200"
echo ""
echo -e "${BLUE}ℹ${NC} Manage services:"
echo "  cd $INSTALL_DIR"
echo "  $COMPOSE_CMD logs -f    # View logs"
echo "  $COMPOSE_CMD down       # Stop services"
echo "  $COMPOSE_CMD up -d      # Start services"
echo ""
echo -e "${YELLOW}⚠${NC} Services may take a few minutes to fully start."
