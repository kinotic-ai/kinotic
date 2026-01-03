# Environment Variable Setup Guide

This document explains how to configure the Continuum WebSocket connection using environment variables.

## Quick Commands

| Command | Environment | Use Case |
|---------|-------------|----------|
| `npm run dev` | `.env.development` | Local development with default settings |
| `npm run dev:kind` | `.env.kind` | Development against Kind cluster |
| `npm run build` | `.env` | Production build |
| `npm run build:kind` | `.env.kind` | Build for Kind cluster deployment |
| `npm run preview` | Production build | Preview production build locally |
| `npm run preview:kind` | Kind build | Preview kind build locally |

## Quick Reference

### Default Ports
- **Pod Internal Port**: 58503 (Continuum Stomp/WebSocket)
- **Kind NodePort**: 30503 (Kubernetes NodePort range: 30000-32767)
- **Kind Host Port**: 58503 (Mapped via kind-config.yaml)

### Port Mapping Flow (Kind Cluster)
```
Development (npm run dev:kind):
  Browser → localhost:58503 → kind control-plane:30503 → Service:58503 → Pod:58503
  
Production (served from structures-server in kind):
  Browser → localhost:9090/index.html → WebSocket to localhost:58503 → NodePort:30503 → Service:58503 → Pod:58503
```

### Environment File Loading Order
```
Lowest Priority                                    Highest Priority
    .env  →  .env.[mode]  →  .env.local  →  Command-line env vars
    
Examples:
  npm run dev        → .env + .env.development + .env.local
  npm run dev:kind   → .env + .env.kind + .env.local
  npm run build      → .env + .env.production + .env.local (if exists)
  npm run build:kind → .env + .env.kind + .env.local (if exists)
```

## Environment Files

### `.env` (Production/Default)
Used when the app is built and served from the structures-server.
```bash
VITE_CONTINUUM_HOST=
VITE_CONTINUUM_PORT=58503
VITE_CONTINUUM_USE_SSL=
```

### `.env.development` (Development)
Used automatically when running `npm run dev`.
```bash
VITE_CONTINUUM_HOST=127.0.0.1
VITE_CONTINUUM_PORT=58503
VITE_CONTINUUM_USE_SSL=false
```

### `.env.kind` (Kind Cluster)
Used when running `npm run dev:kind`.
```bash
VITE_CONTINUUM_HOST=127.0.0.1
VITE_CONTINUUM_PORT=58503
VITE_CONTINUUM_USE_SSL=false
```

### `.env.local` (Local Overrides)
Create this file for personal/machine-specific settings. **This file is gitignored.**

Copy from `.env.local.example`:
```bash
cp .env.local.example .env.local
```

## Common Scenarios

### 1. Development with Kind Cluster (Default Setup)
Use the host port mapping:
```bash
npm run dev
# Connects to localhost:58503 (mapped to NodePort 30503)
```

Build for kind cluster:
```bash
npm run build:kind
# Builds with .env.kind configuration
```

Preview the kind build:
```bash
npm run preview:kind
# Previews the kind build locally
```

### 2. Direct NodePort Access (Testing)
Connect directly to the NodePort:
```bash
# Create .env.local
echo "VITE_CONTINUUM_PORT=30503" > .env.local
npm run dev
```

### 3. Custom Port Configuration
Override for different deployment:
```bash
# .env.local
VITE_CONTINUUM_HOST=192.168.1.100
VITE_CONTINUUM_PORT=8080
VITE_CONTINUUM_USE_SSL=true
```

### 4. Production Deployment
When served from structures-server, the app auto-detects the host and protocol:
- Host: Detected from `window.location.hostname`
- Port: Uses `VITE_CONTINUUM_PORT` or defaults to 58503
- SSL: Auto-detected from `window.location.protocol`

Build for production:
```bash
npm run build
# Uses .env (production defaults)
```

## Why NodePort Can't Match Pod Port

Kubernetes NodePorts must be in the range **30000-32767** by default. Since the pod uses port 58503 (outside this range), we need:
- **NodePort**: 30503 (within allowed range)
- **Pod Port**: 58503 (application default)
- **Kind Host Mapping**: 58503 → 30503 (for convenience)

## Troubleshooting

### Connection Refused
1. Check if the kind cluster is running: `kind get clusters`
2. Verify port mappings: `kubectl get svc structures`
3. Check logs: `kubectl logs -l app=structures-server`

### Wrong Port
1. Check which `.env` file is active
2. Verify environment variables: Add `console.log(import.meta.env)` to your code
3. Restart dev server after changing `.env` files

### SSL Issues
1. If using HTTPS in browser, you may need `VITE_CONTINUUM_USE_SSL=true`
2. Check browser console for mixed content warnings

## Vite Environment Variable Rules

1. **Prefix**: Only variables starting with `VITE_` are exposed to client code
2. **Access**: Use `import.meta.env.VITE_VARIABLE_NAME`
3. **Priority**: `.env.local` > `.env.[mode]` > `.env`
4. **String Values**: All env vars are strings (parse as needed)
5. **Restart Required**: Dev server must be restarted after changing `.env` files

### How Vite Modes Work

When you specify `--mode kind`:
1. Vite loads `.env` (base configuration)
2. Then loads `.env.kind` (overwrites any duplicate keys)
3. Then loads `.env.local` if it exists (highest priority, overwrites all)

**Examples:**
- `vite` → loads `.env` + `.env.development` (default mode for dev)
- `vite --mode kind` → loads `.env` + `.env.kind`
- `vite build` → loads `.env` + `.env.production` (default mode for build)
- `vite build --mode kind` → loads `.env` + `.env.kind`

**Note:** `.env.local` always takes precedence and is gitignored for machine-specific overrides.

## Related Configuration Files

- `kind-config.yaml`: Kind cluster port mappings
- `helm-values.yaml`: Kubernetes service NodePort configuration
- `vite.config.ts`: Vite build configuration
- `src/vite-env.d.ts`: TypeScript type definitions for env vars











