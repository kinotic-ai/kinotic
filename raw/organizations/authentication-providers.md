---
title: "Authentication Providers"
description: "Let your organization's members sign in to the Kinotic portal with your own identity provider."
canonical_url: "https://kinotic.ai/organizations/authentication-providers"
---
# Authentication Providers

> Let your organization's members sign in to the Kinotic portal with your own identity provider.

An **authentication provider** is an identity provider, such as Okta or a Microsoft Entra ID directory, that your organization's members use to sign in to the Kinotic portal.

You add providers in the Kinotic portal under **Organization settings > Authentication providers**. Each provider is offered on your organization's login page once its **callback URL** is registered at the provider.

## Setup order

Every provider follows the same order:

1. Create an application (client) registration at the provider and note its client ID and secret.
2. Add the provider in Kinotic. Kinotic creates the configuration and shows its exact callback URL.
3. Register that callback URL as a redirect URI at the provider.

The callback URL contains the configuration's id, which is only created when you first save the provider in Kinotic. That is why the provider registration is created first with a placeholder redirect URI and updated afterwards.

## Guides

- [Okta SSO setup](https://kinotic.ai/organizations/okta-sso-setup)


## Sitemap

See the full [sitemap](https://kinotic.ai/sitemap.md) for all pages.
