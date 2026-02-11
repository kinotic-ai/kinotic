#!/bin/bash

# Debug script for Keycloak startup issues

echo "=== Keycloak Debug Script ==="

# Check if containers are running
echo "1. Checking container status..."
docker ps | grep keycloak

# Check Keycloak logs
echo ""
echo "2. Checking Keycloak logs..."
docker logs keycloak --tail 20

# Check if port is accessible
echo ""
echo "3. Checking if port 8888 is accessible..."
curl -v http://localhost:8888/auth/health/ready 2>&1 | head -10

# Check if the health endpoint exists
echo ""
echo "4. Checking if health endpoint exists..."
curl -v http://localhost:8888/auth/health/ 2>&1 | head -10

# Check if Keycloak is responding at all
echo ""
echo "5. Checking if Keycloak responds..."
curl -v http://localhost:8888/ 2>&1 | head -10

# Check container network
echo ""
echo "6. Checking container network..."
docker exec keycloak netstat -tlnp 2>/dev/null || echo "netstat not available in container"

# Check if curl is available in container
echo ""
echo "7. Checking if curl is available in container..."
docker exec keycloak which curl 2>/dev/null || echo "curl not found in container"

# Check if wget is available in container
echo ""
echo "8. Checking if wget is available in container..."
docker exec keycloak which wget 2>/dev/null || echo "wget not found in container"

echo ""
echo "=== Debug Complete ===" 