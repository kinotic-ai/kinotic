---
title: "Enterprise License"
description: "Which parts of Kinotic are Enterprise Features, what running them in production requires, and how the license treats substitute implementations."
canonical_url: "https://kinotic.ai/platform/enterprise-license"
---
# Enterprise License

> Which parts of Kinotic are Enterprise Features, what running them in production requires, and how the license treats substitute implementations.

## Overview

Kinotic is licensed under three licenses. Kinotic RPC, the service and event layer every server
is built on, is open source under the Apache License 2.0, so it can be used without Kinotic OS.
The rest of the platform is source-available under the Elastic License 2.0 with the Kinotic
Additional Limitations, and the Enterprise Features under the Kinotic Enterprise License, published
by Minds Ignited LLC. The root
[`LICENSE.md`](https://github.com/kinotic-ai/kinotic/blob/develop/LICENSE.md) routes each part of
the repository to its license, and the license texts govern wherever this page summarizes them.

<table>
<thead>
  <tr>
    <th>
      Part of the repository
    </th>
    
    <th>
      License
    </th>
  </tr>
</thead>

<tbody>
  <tr>
    <td>
      Any <code>
        ee
      </code>
      
       package, other than its Enterprise Extension Points
    </td>
    
    <td>
      <a href="https://github.com/kinotic-ai/kinotic/blob/develop/LICENSE-ENTERPRISE" rel="nofollow">
        Kinotic Enterprise License
      </a>
    </td>
  </tr>
  
  <tr>
    <td>
      Kinotic RPC: <code>
        kinotic-core/
      </code>
      
      , <code>
        kinotic-api-gateway/
      </code>
      
      , <code>
        kinotic-client/
      </code>
      
      , <code>
        kinotic-idl/
      </code>
      
      , and the <code>
        @kinotic-ai/core
      </code>
      
       and <code>
        @kinotic-ai/idl
      </code>
      
       packages in <code>
        kinotic-js/workspace/packages/
      </code>
    </td>
    
    <td>
      <a href="https://github.com/kinotic-ai/kinotic/blob/develop/LICENSE-APACHE" rel="nofollow">
        Apache License 2.0
      </a>
    </td>
  </tr>
  
  <tr>
    <td>
      Third-party components
    </td>
    
    <td>
      The license of each component's owner
    </td>
  </tr>
  
  <tr>
    <td>
      Everything else
    </td>
    
    <td>
      Elastic License 2.0 with the Kinotic Additional Limitations
    </td>
  </tr>
</tbody>
</table>

## Enterprise Features

The Enterprise Features are every Enterprise Implementation, which is any file in a Java package
with a segment named `ee` other than an Enterprise Extension Point, and the capability of every
Enterprise Extension Point. They live in the module each one belongs to, such as
`org.kinotic.domain.internal.api.ee` in `kinotic-domain`.

<table>
<thead>
  <tr>
    <th>
      Enterprise Feature
    </th>
    
    <th>
      Free alternative
    </th>
  </tr>
</thead>

<tbody>
  <tr>
    <td>
      Azure Key Vault secret storage (<code>
        kinotic.domain.secretStorage.backend: AZURE
      </code>
      
      )
    </td>
    
    <td>
      In-memory secret storage (<code>
        backend: IN_MEMORY
      </code>
      
      , the default), which keeps secrets in process memory and loses them on restart
    </td>
  </tr>
  
  <tr>
    <td>
      Okta and Microsoft Entra ID sign-in through an organization's own client, at the organization's login, an application's login or a tenant's login
    </td>
    
    <td>
      Google sign-in through the organization's own client. Microsoft sign-in through Kinotic's managed registration is not an Enterprise Feature
    </td>
  </tr>
</tbody>
</table>

The org, system and app servers include the Enterprise Features, so the published server images
contain them. An Enterprise Feature that is present but not turned on is not in production use, and
having one in an image does not license its production use.

## License key

A server turns an Enterprise Feature on only while the cluster's installed License Key from Minds Ignited lists the
feature, or `*` for every feature, and has not ended its grace period. A key keeps its features on for 14 days
after it expires, while the server logs a warning, and turns them off when the grace period ends. Every server
decides at each use, so installing a new key, or a key expiring, takes effect without a restart, except where the
table below says a feature keeps working after its key lapses.

The whole cluster runs on one installed key, held in Elasticsearch. A system user installs it by pasting it on
the system console's **License** page, or with the system API's `LicenseService.install`, which verifies it and
installs it in place of the installed key. The call completes once the key is stored and the server that took it
runs on it; every other server runs on it shortly after. A server that missed the notice, or could not read the key
then, reads it at its next reload, which it tries every ten minutes. A new
cluster starts with no key installed, and a cluster that stores secrets in Azure Key Vault refuses a key that does
not turn `AZURE_KEY_VAULT` on. The License page shows the licensee, status, expiry, grace period end and features
of the installed key, which `LicenseService.findCurrent` returns.

Keys are verified offline, and each server logs the licensee, expiry and features a key turns on, or why the key is
not valid.

The migrations of the local Docker Compose stack in `deployment/docker-compose/`, the integration tests and the e2e
suites install the Kinotic evaluation license key, which turns on every Enterprise Feature until the end of 2036 for
development and testing only. The published server images carry no key.

<table>
<thead>
  <tr>
    <th>
      Feature named in the key
    </th>
    
    <th>
      Without it
    </th>
  </tr>
</thead>

<tbody>
  <tr>
    <td>
      <code>
        AZURE_KEY_VAULT
      </code>
    </td>
    
    <td>
      A server set to <code>
        kinotic.domain.secretStorage.backend: AZURE
      </code>
      
       starts, reads and deletes the secrets already in Azure Key Vault, but refuses to store a secret until a key that turns the feature on is installed, and after that key's grace period ends
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        ENTERPRISE_IDENTITY_PROVIDERS
      </code>
    </td>
    
    <td>
      Saving or editing an Okta or Microsoft Entra ID configuration that uses an organization's own client is refused. Configurations already saved keep signing people in
    </td>
  </tr>
</tbody>
</table>

## Enterprise Extension Points

Any interface, abstract class, or annotation in a Java package with a segment named `ee`, such as
`org.kinotic.domain.api.ee.services`, or in a directory named `ee`, is an Enterprise Extension
Point. The extension point itself is Elastic-licensed, so you can read it
and build against it, but the capability it provides is an Enterprise Feature.

For production use, the license does not permit you to:

- implement or extend an Enterprise Extension Point, or use such an implementation, other than
an Enterprise Implementation or one included in the Elastic-licensed code and used unmodified
- modify Kinotic to move a type out of an `ee` package, or to replace, bypass or wrap an
Enterprise Extension Point or the code that uses it, so that other software provides its
capability
- provide the capability of an Enterprise Extension Point in any other way designed to avoid the
need for an enterprise license

## What each use requires

<table>
<thead>
  <tr>
    <th>
      Use
    </th>
    
    <th>
      Requires
    </th>
  </tr>
</thead>

<tbody>
  <tr>
    <td>
      Reading, building, modifying and testing the Enterprise Features, locally or in a non-production environment that serves no end users, customers or business operations
    </td>
    
    <td>
      Accepting the Kinotic Enterprise License, which you do by using them
    </td>
  </tr>
  
  <tr>
    <td>
      Writing your own implementation of an Enterprise Extension Point for development and testing
    </td>
    
    <td>
      Nothing beyond the Elastic License 2.0 and the Kinotic Additional Limitations
    </td>
  </tr>
  
  <tr>
    <td>
      Running an Enterprise Feature in production
    </td>
    
    <td>
      A written agreement with Minds Ignited that permits that use, and a valid License Key wherever Minds Ignited has issued one for that feature
    </td>
  </tr>
  
  <tr>
    <td>
      Running Kinotic in production without the Enterprise Features
    </td>
    
    <td>
      The Elastic License 2.0 and the Kinotic Additional Limitations: no agreement, provided each Enterprise Extension Point runs only the implementation included in the Elastic-licensed code, unmodified
    </td>
  </tr>
</tbody>
</table>

A written agreement that permits only limited purposes, such as evaluation or a proof of concept,
licenses production use for those purposes alone. The Enterprise Features may be redistributed
only unmodified, with the Kinotic Enterprise License, as part of a copy of Kinotic distributed
under the Elastic License 2.0, and may not be offered to third parties as a hosted or managed
service, and the Elastic License
2.0 forbids offering Kinotic itself as a hosted or managed service that gives users access to a
substantial set of its features.

To arrange an enterprise agreement, contact Minds Ignited through [kinotic.ai](https://kinotic.ai).

## Contributions

Contributors accept the
[Kinotic Contributor License Agreement](https://github.com/kinotic-ai/kinotic/blob/develop/CLA.md),
which lets Minds Ignited license a contribution under either license. See
[Contributing](https://kinotic.ai/platform/contributing#licensing).


## Sitemap

See the full [sitemap](https://kinotic.ai/sitemap.md) for all pages.
