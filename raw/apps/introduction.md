# Introduction

> Learn what Kinotic is, why it exists, and how it helps developers and AI agents build enterprise-grade applications at scale.

Kinotic is a next-generation cloud operating system that abstracts away infrastructure complexity so developers and AI agents can focus on what matters: building great applications.

## Humane Representation of Thought

The rise of AI-assisted development has unlocked a new paradigm -- rapid iteration driven by natural-language intent and intelligent code generation. Kinotic is purpose-built for this paradigm. Whether a human developer or an AI agent is writing the code, Kinotic provides the guardrails, services, and runtime that turn quick prototypes into production-grade systems.

## Core Components

- **Kinotic Apps** -- Applications that run on Kinotic OS. Today, Kinotic Apps are built with TypeScript running on Bun -- you write standard TypeScript for domain models, business logic, and UI, and the platform handles infrastructure, deployment, and scaling. Support for Go, Java, and Python backends is coming soon.
- **Kinotic OS** -- The cloud operating system that runs your apps. It provides authentication, authorization, persistence, service mesh, observability, and deployment infrastructure out of the box.
- **Kinotic OS Cloud** -- A fully managed SaaS offering of Kinotic OS. No servers to provision, no infrastructure to maintain.
- **Kinotic CLI** -- The developer tool that ties it all together. Use it to initialize projects, define entities, generate service code, and deploy applications.

## Why Kinotic?

<table>
<thead>
  <tr>
    <th>
      Concern
    </th>
    
    <th>
      Without Kinotic
    </th>
    
    <th>
      With Kinotic
    </th>
  </tr>
</thead>

<tbody>
  <tr>
    <td>
      Authentication & Authorization
    </td>
    
    <td>
      Wire up OIDC, RBAC, and session management yourself
    </td>
    
    <td>
      Built in -- configure once, enforce everywhere
    </td>
  </tr>
  
  <tr>
    <td>
      Persistence
    </td>
    
    <td>
      Choose a database, write migrations, build CRUD endpoints
    </td>
    
    <td>
      Define entities, run <code>
        kinotic sync
      </code>
      
      , get a full service layer
    </td>
  </tr>
  
  <tr>
    <td>
      Service Mesh
    </td>
    
    <td>
      Deploy sidecars, configure routing, handle retries
    </td>
    
    <td>
      Automatic service discovery and RPC across the application
    </td>
  </tr>
  
  <tr>
    <td>
      CI/CD
    </td>
    
    <td>
      Maintain pipelines, container registries, rollout strategies
    </td>
    
    <td>
      Handled by the platform
    </td>
  </tr>
  
  <tr>
    <td>
      Observability
    </td>
    
    <td>
      Instrument code, deploy collectors, build dashboards
    </td>
    
    <td>
      SBOM generation, metrics, and tracing are included
    </td>
  </tr>
</tbody>
</table>

**Rapid prototyping.** Go from an idea to a running application in minutes with the CLI and generated services.

**Enterprise-ready.** Role-based access control, software bill of materials, and full observability mean you never have to rewrite for production.

**Internet scale.** The platform is designed to scale horizontally from day one.

## What These Docs Cover

This documentation walks through everything you need to build and ship a Kinotic App:

1. **Quick Start** -- Scaffold a project and persist your first entity in under five minutes.
2. **Application Structure** -- Understand how organizations, applications, projects, and artifacts fit together.
3. **Services** -- Publish and consume services across your application.
4. **Persistence** -- Define entities, configure indexes, and run queries.
5. **Security** -- Set up authentication and authorization.
6. **Deployment** -- Ship your application to Kinotic OS or Kinotic OS Cloud.

Ready to build something? Head to the [Quick Start](/apps/quick-start) to create your first Kinotic App.
