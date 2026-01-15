# Cluster Networking Setup - Test Instance + Docker Containers

## Problem

When running a test instance locally alongside Docker Compose containers, Ignite Shared FS discovery fails because:

1. **Test instance** binds to `127.0.0.1:47500` (localhost)
2. **Containers** bind to Docker network IPs (e.g., `172.x.x.x:47501-47503`)
3. Nodes write their addresses to shared FS, but can't reach each other:
   - Test instance can't reach container Docker IPs
   - Containers can't reach test instance at `127.0.0.1` (localhost inside container)

## Solution

Configure Ignite to use addresses reachable from both sides:

### For Containers:
- Use `host.docker.internal` (Mac/Windows) or `host-gateway` IP (Linux) to reach test instance
- Bind to `0.0.0.0` and expose ports via Docker port mapping
- Write `host.docker.internal:47501-47503` to shared FS (or use host IP)

### For Test Instance:
- Bind to `0.0.0.0:47500` (all interfaces) or `127.0.0.1:47500`
- Use `localhost:47501-47503` to reach containers (via Docker port mapping)
- Write `localhost:47500` to shared FS

## Implementation

### Option 1: Use Docker Host Gateway (Recommended)

Containers can reach host via `host.docker.internal` (already configured in docker-compose).

**Test Instance Configuration:**
- Bind Ignite to `0.0.0.0:47500` (all interfaces)
- Containers connect to `host.docker.internal:47500`

**Container Configuration:**
- Bind Ignite to `0.0.0.0:47501-47503` (already done)
- Test instance connects to `localhost:47501-47503` (via port mapping)

### Option 2: Use Host Network Mode (Linux Only)

```yaml
services:
  structures-node1:
    network_mode: host
```

**Limitations:**
- Only works on Linux
- Not portable across platforms
- Port conflicts possible

### Option 3: Configure Ignite Local Address Explicitly

Configure Ignite's `TcpDiscoverySpi` to use specific local addresses:
- Test instance: `localhost` or host IP
- Containers: `host.docker.internal` or host IP

This requires modifying Ignite configuration to set `localAddress` on `TcpDiscoverySpi`.

## Current Status

The docker-compose file already has:
- Port mappings: `0.0.0.0:47501-47503:47501-47503`
- Extra hosts: `host.testcontainers.internal:host-gateway`

**Missing:**
- Ignite configuration to use `host.docker.internal` for test instance address
- Ignite configuration to bind to `0.0.0.0` instead of `127.0.0.1`

## Configuration Applied - LOCAL Discovery with Static IP Addresses

### Discovery Type: LOCAL (Static IP)
Using LOCAL discovery type with explicit static IP addresses instead of SHAREDFS discovery. Each node receives the complete list of all node addresses.

### Test Instance (application-test.yml)
- **Discovery Type**: `LOCAL`
- **Local Address**: `0.0.0.0` (bind to all interfaces)
- **Port**: `47500`
- **Static Addresses**: `host.docker.internal:47500,localhost:47501,localhost:47502,localhost:47503`
- **Rationale**: 
  - Binds to `0.0.0.0` so containers can reach it via `host.docker.internal:47500`
  - Uses `host.docker.internal` in address list so containers know how to reach it

### Container Nodes (docker-compose)
- **Discovery Type**: `LOCAL`
- **Local Address**: `0.0.0.0` (bind to all interfaces, for all 3 nodes)
- **Ports**: `47501`, `47502`, `47503`
- **Static Addresses**: `host.docker.internal:47500,localhost:47501,localhost:47502,localhost:47503`
- **Rationale**: 
  - Binds to `0.0.0.0` so port mapping works
  - Uses `localhost:47501-47503` in address list so test instance can reach them via port mapping

### How It Works

1. **Test Instance → Containers**:
   - Test instance has explicit list: `host.docker.internal:47500,localhost:47501,localhost:47502,localhost:47503`
   - Connects to containers via `localhost:47501-47503` (Docker port mapping)

2. **Containers → Test Instance**:
   - Containers have explicit list: `host.docker.internal:47500,localhost:47501,localhost:47502,localhost:47503`
   - Connect to test instance via `host.docker.internal:47500` (Docker host gateway)

3. **Container → Container**:
   - Containers connect to each other via `localhost:47501-47503`
   - Since all containers bind to `0.0.0.0`, they can reach each other via Docker's internal networking
   - Or via host network if ports are properly mapped

### Advantages of LOCAL Discovery with Static IPs

- **No Shared FS Dependency**: Doesn't require shared file system for Ignite discovery
- **Explicit Configuration**: All addresses are explicitly configured, easier to debug
- **Faster Startup**: No need to wait for shared FS file writes/reads
- **More Reliable**: No file system race conditions

### Port Mappings

All container ports are mapped to host:
- `47501:47501` (node1)
- `47502:47502` (node2)
- `47503:47503` (node3)

This allows the test instance to reach containers via `localhost` while containers can reach the test instance via `host.docker.internal`.


