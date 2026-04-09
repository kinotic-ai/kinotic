# Three-Layer IAM Authentication — Technical Design

## Status

**Implemented** — Authentication only. Authorization (roles, policies, RBAC) and sign-up flows are deferred to separate efforts.

## Problem

Kinotic OS had a flat authentication model: a single SecurityService interface with two conditional implementations — one for OIDC (configured statically via YAML) and one for development (hardcoded credentials). There was no concept of managed users, no way to configure OIDC providers at runtime, no organization entity, and no separation between platform operators, development teams, and end-users.

This created several problems:

- **No user management.** The platform had no stored users — authentication was either "you have a valid JWT" or "you know the hardcoded password." There was no way to control who could access what.
- **Static OIDC configuration.** Adding or changing an OIDC provider required editing YAML and redeploying. Customers couldn't self-service their own identity providers.
- **No multi-tenancy in authentication.** A valid JWT granted access to the entire platform. There was no way to scope a user to a specific organization or application.
- **No path to self-service.** Without stored users or scoped authentication, there was no foundation for features like sign-up, invitation, or user management UIs.

## Design Goals

1. **Hierarchical scope isolation** — Platform operators, organization developers, and application end-users must be completely separate identity pools.
2. **Runtime OIDC management** — OIDC providers must be manageable as data entities, not static configuration.
3. **Shared provider registration** — Kinotic OS should register once with Google/Microsoft, and every customer should benefit without touching any provider console.
4. **Authentication only** — Ship the identity layer first. Authorization (roles, policies) and sign-up flows come later, but the data model must support them without schema changes.
5. **No auto-provisioning** — Having a valid Google account must not automatically grant access to any scope. Administrators control who gets in.

## Three-Layer Model

The system has three authentication scopes, each maintaining completely independent user pools:

| Layer | Who | Managed By |
|-------|-----|------------|
| **System** | Platform operators | System administrators |
| **Organization** | Development teams | Organization administrators |
| **Application** | End-users, machines | Organization or application administrators |

A user with email `jane@example.com` in Organization A is a fundamentally different identity from `jane@example.com` in Application B. They have separate records, separate credentials, and separate authentication paths. This is intentional — it prevents accidental cross-scope access and allows the same person to have different authentication methods at different scopes (e.g., email/password for testing an app, federated SSO at the org level).

### Why Three Layers

Two layers (platform vs. tenant) would force organizations and their applications to share a user pool. This doesn't work because:

- An organization's developers should not automatically be end-users of every application they manage
- Application end-users should not have access to organization-level tooling
- Different applications under the same organization may serve completely different user populations

Three layers map directly to the real-world trust boundaries: the platform operator trusts the infrastructure, the organization trusts its developers, and the application trusts its users.

## OIDC Configuration as Standalone Entities

OIDC configurations are stored as independent, reusable entities with no embedded scope or ownership. The association between a config and the scopes that use it is stored on the consuming entity (System, Organization, or Application) as a list of configuration IDs.

### Why Standalone

The alternative was to embed OIDC configs within each scope entity or to create a sharing/inheritance model. Both were rejected:

- **Embedded configs** would require duplication when the same provider is used across multiple scopes. Updating a provider's settings would require updating every copy.
- **Sharing/inheritance models** add complexity (who owns the config? what happens when it's modified? do children inherit changes?) without clear benefit.

The standalone model is simpler: an OIDC config exists once, and any number of scopes can reference it by ID. To "share" a config, you just add the same ID to another scope. To stop using it, you remove the ID. No ownership, no inheritance, no cascading updates.

### Built-In Configurations

A key workflow for Kinotic OS is: the platform registers once with Google and once with Microsoft, and every customer benefits without any provider console interaction. Built-in configurations make this explicit.

A system administrator creates an OIDC configuration and marks it as built-in. This means:

- Organization and application administrators can see it and enable it for their scope
- They cannot modify or delete it
- End users see "Kinotic OS" on the provider's consent screen, which is actually a feature — their users are approving a known platform, not an unknown app

Non-built-in configurations are created by organization administrators for their own providers (e.g., a corporate Okta instance). These follow the same standalone model but without the immutability constraint.

## User Pre-Creation

Users must be created by an administrator before they can authenticate. The platform does not auto-provision users on first login.

### Why No Auto-Provisioning

If Application A and Application B both enable Google login, and the platform auto-provisioned users on first OIDC login, then any Google user who authenticates against Application A would automatically get access. This breaks scope isolation — the whole point of having separate user pools is that administrators decide who gets in.

The pre-creation model works as follows:

1. An administrator creates a user record with their email and target scope
2. For OIDC users, only the email is needed — no OIDC-specific information required at creation time
3. On first OIDC login, the platform matches the JWT's email to the pre-created user and links the OIDC identity automatically
4. Subsequent logins match on the linked identity

This gives administrators full control over who can access each scope while keeping the onboarding experience simple — they only need an email address.

### Future: Self-Service Sign-Up

The current data model supports future sign-up flows without schema changes. Planned approaches include:

- **Registration policies** on Organization and Application entities (open, closed, domain-restricted)
- **Invitation-based onboarding** via email
- **Admin approval workflows** for self-registration

These are additive features that build on the pre-creation model rather than replacing it.

## Credential Separation

Password hashes are stored in a separate internal entity, keyed by user ID. This entity is not exposed through any published service interface.

### Why Separate

The user entity is part of the public API — it's returned by CRUD operations, displayed in UIs, and passed around in service calls. If password hashes were stored on the user entity, every read operation would need to strip them, every serialization would risk leaking them, and every developer working with user objects would need to be aware of the sensitive field.

By storing credentials separately in an internal-only entity, password hashes are architecturally invisible to the rest of the system. The user CRUD service has no way to accidentally return them.

## Replacing Legacy Authentication

The new IAM service is the sole authentication implementation, replacing both the OIDC service (JWT validation from YAML config) and the temporary development service (hardcoded credentials).

### What Was Kept

- **JWT validation logic** — The JWKS-based token validation, email claim extraction, and roles claim path parsing were ported from the existing OIDC service. The validation mechanics are proven and unchanged.
- **JwksService** — The existing JWKS key fetching and caching service is reused directly.
- **Participant model** — The existing Participant interface and DefaultParticipant implementation are preserved. The IAM service populates them with scope-aware metadata.

### What Changed

- **OIDC config source** — Provider configurations come from Elasticsearch entities instead of YAML. This enables runtime management.
- **User lookup** — Authentication now requires a matching user record in the target scope, not just a valid JWT.
- **Scope awareness** — Every authentication request includes scope headers that determine which user pool and which OIDC configurations to check against.

## Data Model Decisions

### Scope as String Fields, Not Foreign Keys

The user entity stores scope type and scope ID as plain string fields rather than typed references. This avoids coupling the user entity to the Organization and Application entities, keeps queries simple (term filters on keyword fields), and allows the scope model to evolve without migrating user records.

### Single User Entity for All Scopes

All three scope layers share the same user entity type, distinguished by the scope fields. The alternative — separate user types per scope — was rejected because the authentication logic is identical across scopes, the fields are identical, and separate types would triple the service interfaces and implementations with no behavioral difference.

### Organization Slug

Organizations have an auto-generated URL-safe slug derived from their name. This supports subdomain-based tenant identification (e.g., `customer.kinotic.ai`) without exposing UUIDs in URLs.

## Scope of This Design

This design intentionally covers **authentication only**:

- Proving identity (email/password, OIDC)
- Managing users and OIDC configurations
- Scoping authentication to the correct layer

It does **not** cover:

- **Authorization** — Roles, policies, RBAC, and the ABAC policy engine integration will be addressed separately
- **Groups** — Group entities and membership management are deferred
- **Sign-up** — Self-registration, invitations, and approval workflows are deferred
- **Secret management** — OIDC client secrets will be handled by a dedicated secret store (not yet built)

The Participant returned by authentication currently has an empty roles list. Future authorization work will populate it from the policy system.
