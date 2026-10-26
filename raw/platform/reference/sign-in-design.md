---
title: "Sign-In Design"
description: "How OIDC configurations, scoped logins and the one-user-per-scope model fit together, in seven figures."
canonical_url: "https://kinotic.ai/platform/reference/sign-in-design"
---
# Sign-In Design

> How OIDC configurations, scoped logins and the one-user-per-scope model fit together, in seven figures.

## Overview

Seven pictures of how sign-up and sign-in fit together: who owns a provider configuration and where its secret lives, the configuration classes, how a configuration's kind decides which login offers it, the two routes a browser can take through the app server, the identity and participant classes, how one person is a separate user of each organization, and the doors on the management server. [Organization Management](https://kinotic.ai/platform/organization-management) walks the same flows endpoint by endpoint and [System Security](https://kinotic.ai/platform/system-security) states the rules they enforce; this page is the map.

## Two owners, two keys into one vault

A system user and an organization administrator each write their own kind of configuration. Only the system user's kind can name a secret in the vault. An organization's kind has no such field at all: its secret is handed to the service at save time and stored under the organization's own scope, so nothing an organization writes can reach a system secret.

<sign-in-flows-diagram figure="owners">



</sign-in-flows-diagram>

Each owner's kind reaches the vault by its own key. The red path is the point of the design: an organization's row has nowhere to write a system secret's name.

- `OidcConfiguration` is sealed: `ManagedOidcConfiguration` (`ORG_SIGNUP` for GitHub sign-up, `APP_SOCIAL` for Google and Microsoft) and `OwnedOidcConfiguration`, whose concrete kind is the scope it is offered at: `ORGANIZATION`, `APPLICATION`, `TENANT`. All are documents in one index, `kinotic_oidc_configuration`, read back by their `type`.
- An organization names its account at a provider, never a URL. The issuer is derived from the provider's template on every save, so the server only ever connects to the provider's own hosts. The organization's configurations are managed on the Authentication providers tab of its settings page, an application's and a tenant's on the application's settings page, or through `OwnedOidcConfigurationService`.
- Deleting an organization's configuration deletes its secret. A save without a secret keeps the one held; `deleteClientSecret` removes it, which makes the configuration a public client whose sign-in never reads the vault. A copy to another scope (`copy`) gets its own callback URL and its own copy of the secret.

## The configuration model

One sealed hierarchy. The abstract root carries what every provider needs; the two abstract branches carry what distinguishes the system's registrations from an organization's own; and each leaf adds only the ids that name the scope it is offered at. The leaf type is the `type` discriminator the index stores.

<sign-in-flows-diagram figure="model">



</sign-in-flows-diagram>

The leaf a configuration is saved as is its scope: a leaf with no scope id is the organization's own login, `applicationId` names an application's, `applicationId` plus `tenantId` a tenant's. An `ApplicationOidcConfiguration` whose `clientRegistrationType` is `MANAGED` is the only reference to a managed registration an organization ever holds, and it names it by provider kind alone.

- Only a managed registration can name a secret (`secretNameRef`) or set the explicit endpoint fields GitHub needs instead of discovery. An owned configuration derives its authority from `provider` and `providerAccount` on every save.
- The hierarchy is sealed on both levels, so every `switch` over a configuration's kind is exhaustive, and a new scope is a new leaf plus a new `OidcConfigurationType` constant.

## Which login offers a configuration

Nothing is embedded in an organization or an application, and there is no join row. An owned configuration is offered at exactly the scope its kind names; an organization that wants the same Okta tenant at its own login and at an application's copies the configuration into the application's scope. The system's GitHub registration is offered at every organization's login by right. The system's social registrations are never offered on their own: an application's configuration of their provider kind signs in through one when its `clientRegistrationType` is `MANAGED`, and is offered at the application's login like any other of its configurations. A tenant's login offers the tenant's configurations first, then everything the application's login offers.

<sign-in-flows-diagram figure="offering">



</sign-in-flows-diagram>

What reaches each login and why: an org sign-up registration by right, an owned configuration at the one scope its kind names, and a social registration through an application's `MANAGED` configuration only, never at the organization's own login.

- Every read and write of an owned configuration is held to the caller's organization, and a copy is refused for an application or tenant the organization does not have.
- A `createUserOnSignIn` flag on an application's or a tenant's configuration creates the identity its provider verifies as a user of that scope on the first sign-in: a user of the application, with a tenant of its own while the system manages the application's tenants, or a user of the tenant for a `TENANT` kind. Email and password is a provider of the same lists, a `PasswordConfiguration` of an organization's, an application's or a tenant's login, which takes a password only while one of its scope is enabled; an application whose tenants the system manages may let people sign up with it, with the configuration's own `createUserOnSignIn`, verified by an emailed link. `GET /api/auth/app/login/methods` tells a page whether its login takes a password, whether the password sign-up is open, and which of its providers sign people up. An organization's configuration refuses the flag: its members are added by administrators, and its provider then signs them in.

## Where the provider sends the browser back

An application's login page offers every method up front: the password form and a button per offered configuration. No endpoint takes an email before the user has chosen. An application's configuration then signs in through one of two registrations with the provider, and they differ in exactly one thing: where the provider returns the browser. The organization's own client (`OWN`) returns to the application's host. Kinotic's registration (`MANAGED`) is shared by every application and can only return to one place, so it returns to the app server's shared host, which finds the user and hands the sign-in to the application's host under a single-use code.

<sign-in-flows-diagram figure="paths">



</sign-in-flows-diagram>

Same start, same ending. Path B adds the shared host between the provider and the application's host, and nothing else. A sign-in from a tenant's site takes the same paths with the tenant stashed on the flow. Black arrows are the browser calling a host; green, the application's host answering; violet, the shared host; amber, the provider.

- The start is `POST /api/auth/app/login/oidc/start/:configId` on the application's host. It refuses a page that is not one of the application's UIs, a page that is a site of another tenant, and a configuration the login does not offer, then answers `{redirect}`.
- In path B the shared host runs the provider round-trip through the system registration on its own session, holding the application's configuration the flow started with. The application's host accepts the code once, only if it was staged for that very application and presented by the browser that started the sign-in, and then establishes the session exactly as path A does.
- Either callback finds the application's user through the binding of the application's configuration and the identifier the provider gives the person under every client (`sub`, or `<tid>/<oid>` for Microsoft), so a configuration switched between `MANAGED` and `OWN` finds the same users; an identity no user is bound to claims the pending user an administrator added to the scope under its email, and otherwise is created as a user where the configuration signs people up (`ApplicationSignUpService.findOrCreateOidcUser`); then the callback checks the user belongs to the login's scope: a tenant's login admits that tenant's users only. The code is random, dies on first use or after a minute, and carries only the user's id.
- The password form posts to `POST /api/auth/app/login` and answers 204 with the cookie, or a generic 401.

## The identity and participant model

Three kinds of identity share one sealed root, and a person is one of three kinds of user, each the user of one scope: a system user, an organization user and an application user. An organization user, an application user, a delegate and a machine are each a `ScopedParticipantIdentity`, which carries the scope it acts in: `organizationId` for an organization user, `organizationId` and `applicationId` for the others. A system user acts in the system scope and has neither. A user's sign-in methods are rows of their own that point back at the user. What a request runs as is a `Participant`: the identity acting in its scope.

<sign-in-flows-diagram figure="identity">



</sign-in-flows-diagram>

An identity row says who; a participant says who, acting where. Every identity carries its own scope, so `createParticipant(identity)` makes its participant, and `resolveParticipant(identityId, organizationId, applicationId)` admits it only in the scope it carries, a delegate only while its owner may act there, and a disabled identity nowhere.

- The index mappings follow the classes one to one: `kinotic_participant_identity` for the sealed root with its subclass fields, `kinotic_identity_credential` and `kinotic_oidc_binding`.
- `ParticipantIdentityType` {SYSTEM_USER, ORGANIZATION_USER, APPLICATION_USER, DELEGATE, MACHINE} is the discriminator the index stores for the root, as `OidcConfigurationType` is for configurations. A delegate and a machine authenticate as their type says; a user's methods are its credential and binding rows, and `AuthType` {PASSWORD, OIDC} names one of them on the Profile page.
- Each login authenticates the kind of user its scope holds, so one email may be a user of every scope, each with its own password: the system console finds system users, an organization's login organization users, an application's login that application's users.

## One user per organization

A person who uses the portal holds a separate organization user in each organization they belong to, unique by email within that organization, each with its own password, bindings and `enabled` flag. Every login is addressed to one organization and signs in that organization's user, and a session acts in that organization alone; using another is a sign-in at that organization's login.

<sign-in-flows-diagram figure="organization-user">



</sign-in-flows-diagram>

Two rows, one per organization: the methods that sign the user in, the user, and the organization it belongs to. One GitHub account is bound separately in each organization. Each login below reads one binding or credential, so nothing in the login path waits on an index refresh.

- Disabling or removing a member touches that organization's user alone. Removing a member deletes the user with its credential, bindings and delegates, and the person's users of other organizations stay as they are.
- A delegate (the CLI, an MCP host) is unique per owner and client, carries its owner's organization, and authenticates only while its owner may still act there.
- The system console signs in system users, a separate row for the same person with a password of its own.

## The doors on the management server

Organization users sign up and in on the management server. Every door produces or finds a user of one organization; none of them asks about an email before a method is chosen, and a signed-in organization user manages its own methods from the Profile page. An administrator adds a member from the Members page: the organization's user exists at once, pending, until an invitation or the organization's own provider claims it.

<sign-in-flows-diagram figure="doors">



</sign-in-flows-diagram>

Four doors on one host. A new organization user appears through the first three: founded with a new organization at sign-up, added by an administrator, or created by an invitation. The fourth is where a signed-in organization user adds a method; the only other ways a provider identity joins a user that already exists are an invitation or the organization's own provider claiming a pending member.


## Sitemap

See the full [sitemap](https://kinotic.ai/sitemap.md) for all pages.
