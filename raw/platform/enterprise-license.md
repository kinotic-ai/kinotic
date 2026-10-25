---
title: "Enterprise License"
description: "Which parts of Kinotic are Enterprise Features, what running them in production requires, and how the license treats substitute implementations."
canonical_url: "https://kinotic.ai/platform/enterprise-license"
---
# Enterprise License

> Which parts of Kinotic are Enterprise Features, what running them in production requires, and how the license treats substitute implementations.

## Overview

Kinotic is source-available under two licenses. Most of the platform is licensed under the
Elastic License 2.0 with the Kinotic Additional Limitations. The Enterprise Features are licensed
under the Kinotic Enterprise License, published by Minds Ignited LLC. The root
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
      <code>
        kinotic-ee/
      </code>
    </td>
    
    <td>
      <a href="https://github.com/kinotic-ai/kinotic/blob/develop/kinotic-ee/LICENSE" rel="nofollow">
        Kinotic Enterprise License
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

The Enterprise Features are the contents of the `kinotic-ee` module and the capability of every
Enterprise Extension Point.

<table>
<thead>
  <tr>
    <th>
      Enterprise Feature
    </th>
    
    <th>
      Enterprise Extension Point
    </th>
    
    <th>
      Included without an enterprise license
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
      <code>
        org.kinotic.domain.api.ee.services.SecretStorageBackend
      </code>
    </td>
    
    <td>
      <code>
        InMemoryBackend
      </code>
      
       (<code>
        backend: IN_MEMORY
      </code>
      
      , the default), which keeps secrets in process memory and loses them on restart
    </td>
  </tr>
</tbody>
</table>

The org, system and app servers include `kinotic-ee`, so the published server images contain the
Enterprise Features. Having them in an image does not license their production use.

## Enterprise Extension Points

Any interface, abstract class, or annotation in a Java package with a segment named `ee`, such as
`org.kinotic.domain.api.ee.services`, or in a directory named `ee`, outside `kinotic-ee/`, is an
Enterprise Extension Point. The extension point itself is Elastic-licensed, so you can read it
and build against it, but the capability it provides is an Enterprise Feature.

For production use, the license does not permit you to:

- implement or extend an Enterprise Extension Point, or use such an implementation, other than
one in `kinotic-ee/` or one included in the Elastic-licensed code and used unmodified
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
licenses production use for those purposes alone. The Enterprise Features may not be
redistributed or offered to third parties as a hosted or managed service, and the Elastic License
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
