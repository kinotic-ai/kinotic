# Contributing

> How to contribute to the Kinotic OS project.

## Overview

We welcome contributions to Kinotic OS. This guide covers the repository structure, build process, and contribution workflow.

## Repository Structure

<table>
<thead>
  <tr>
    <th>
      Directory
    </th>
    
    <th>
      Description
    </th>
  </tr>
</thead>

<tbody>
  <tr>
    <td>
      <code>
        kinotic-core/
      </code>
    </td>
    
    <td>
      Java/Kotlin backend (Spring Boot) — RPC gateway, service registry, authentication
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        kinotic-management-api/
      </code>
    </td>
    
    <td>
      Domain model and management API services — application, project, and cluster management
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        kinotic-js/
      </code>
    </td>
    
    <td>
      TypeScript SDK workspace (Bun) — <code>
        @kinotic-ai/core
      </code>
      
      , <code>
        @kinotic-ai/persistence
      </code>
      
      , and related packages
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        kinotic-frontend/
      </code>
    </td>
    
    <td>
      Vue.js UI workspace (pnpm) — <code>
        apps/portal
      </code>
      
       (the Kinotic OS dashboard), <code>
        apps/system
      </code>
      
       (the platform-operator console), <code>
        packages/common
      </code>
      
       (shared UI code)
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        website/
      </code>
    </td>
    
    <td>
      Documentation site (Docus/Nuxt)
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        deployment/
      </code>
    </td>
    
    <td>
      Helm charts, Docker Compose, Terraform configurations
    </td>
  </tr>
</tbody>
</table>

## Building

### Java/Kotlin Backend

```bash
./gradlew build
```

### TypeScript SDK

```bash
cd kinotic-js
bun install
bun run build
```

### Website

```bash
cd website
bun install
bun run dev
```

## Testing

### Java/Kotlin

```bash
./gradlew test
```

### TypeScript (Vitest)

```bash
cd kinotic-js
bun test
```

## Submitting Changes

1. Fork the repository and create a feature branch from `develop`
2. Make your changes with clear, descriptive commit messages
3. Ensure all tests pass before submitting
4. Submit a pull request against the `develop` branch
