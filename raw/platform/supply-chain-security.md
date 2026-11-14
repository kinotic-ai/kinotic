---
title: "Supply Chain Security"
description: "How Kinotic inventories, scans and gates the container images it publishes, and where the evidence for each control is kept."
canonical_url: "https://kinotic.ai/platform/supply-chain-security"
---
# Supply Chain Security

> How Kinotic inventories, scans and gates the container images it publishes, and where the evidence for each control is kept.

## Overview

Every Kinotic service image is inventoried and scanned for known vulnerabilities before it is published. An image with a Critical vulnerability that has a published fix is never promoted to Docker Hub. The image controls on this page run automatically in the `Build and Publish` workflow (`.github/workflows/gradle-build.yml`), and every control leaves evidence in GitHub.

### Scope

<table>
<thead>
  <tr>
    <th>
      Image
    </th>
    
    <th>
      Published as
    </th>
  </tr>
</thead>

<tbody>
  <tr>
    <td>
      Kinotic management server
    </td>
    
    <td>
      <code>
        kinoticai/kinotic-server-management
      </code>
    </td>
  </tr>
  
  <tr>
    <td>
      Kinotic system server
    </td>
    
    <td>
      <code>
        kinoticai/kinotic-server-system
      </code>
    </td>
  </tr>
  
  <tr>
    <td>
      Kinotic app server
    </td>
    
    <td>
      <code>
        kinoticai/kinotic-server-app
      </code>
    </td>
  </tr>
  
  <tr>
    <td>
      Kinotic migration
    </td>
    
    <td>
      <code>
        kinoticai/kinotic-migration
      </code>
    </td>
  </tr>
</tbody>
</table>

## Controls

### Software inventory (SBOM)

<table>
<thead>
  <tr>
    <th>
      
    </th>
    
    <th>
      
    </th>
  </tr>
</thead>

<tbody>
  <tr>
    <td>
      <strong>
        Control
      </strong>
    </td>
    
    <td>
      Each build produces a software bill of materials for each image, one for its amd64 build and one for its arm64 build, the two platforms its published tag holds. It lists every operating-system package and application library in the image, with its version.
    </td>
  </tr>
  
  <tr>
    <td>
      <strong>
        Format
      </strong>
    </td>
    
    <td>
      CycloneDX JSON, generated with <a href="https://github.com/anchore/syft" rel="nofollow">
        Syft
      </a>
      
       from the built image, not from source manifests. The inventory therefore matches what runs.
    </td>
  </tr>
  
  <tr>
    <td>
      <strong>
        Frequency
      </strong>
    </td>
    
    <td>
      Every build of the images: each pull request into <code>
        develop
      </code>
      
       or <code>
        main
      </code>
      
      , each push to them that changes application code, and nightly.
    </td>
  </tr>
  
  <tr>
    <td>
      <strong>
        Evidence
      </strong>
    </td>
    
    <td>
      Workflow run artifacts named <code>
        sbom-kinotic-server-management.cdx.json
      </code>
      
      , <code>
        sbom-kinotic-server-system.cdx.json
      </code>
      
      , <code>
        sbom-kinotic-server-app.cdx.json
      </code>
      
       and <code>
        sbom-kinotic-migration.cdx.json
      </code>
      
       for the amd64 builds, and the same names with <code>
        -arm64
      </code>
      
       before <code>
        .cdx.json
      </code>
      
       for the arm64 builds.
    </td>
  </tr>
  
  <tr>
    <td>
      <strong>
        Retention
      </strong>
    </td>
    
    <td>
      90 days.
    </td>
  </tr>
</tbody>
</table>

### Vulnerability scanning

<table>
<thead>
  <tr>
    <th>
      
    </th>
    
    <th>
      
    </th>
  </tr>
</thead>

<tbody>
  <tr>
    <td>
      <strong>
        Control
      </strong>
    </td>
    
    <td>
      <a href="https://github.com/anchore/grype" rel="nofollow">
        Grype
      </a>
      
       scans each image's SBOM against current vulnerability advisories: GitHub Security Advisories, the National Vulnerability Database, and operating-system vendor feeds. The scan covers the same artifact the inventory records.
    </td>
  </tr>
  
  <tr>
    <td>
      <strong>
        Frequency
      </strong>
    </td>
    
    <td>
      Every build, including each pull request, and nightly. The nightly run checks newly published advisories against the current images.
    </td>
  </tr>
  
  <tr>
    <td>
      <strong>
        Evidence
      </strong>
    </td>
    
    <td>
      Findings of every severity are recorded as GitHub code scanning alerts in the categories <code>
        image-kinotic-server-management
      </code>
      
      , <code>
        image-kinotic-server-system
      </code>
      
      , <code>
        image-kinotic-server-app
      </code>
      
       and <code>
        image-kinotic-migration
      </code>
      
       for the amd64 builds, and the same categories with <code>
        -arm64
      </code>
      
       appended for the arm64 builds, with open and fixed history per finding. Each workflow run's summary lists the fixable findings for each image and platform.
    </td>
  </tr>
</tbody>
</table>

### Runtime vulnerability monitoring

<table>
<thead>
  <tr>
    <th>
      
    </th>
    
    <th>
      
    </th>
  </tr>
</thead>

<tbody>
  <tr>
    <td>
      <strong>
        Control
      </strong>
    </td>
    
    <td>
      Each server jar lists the libraries it was built with and the OpenTelemetry agent it runs with (<code>
        META-INF/kinotic/components.txt
      </code>
      
      ), and each node stores that list as its SBOM when it starts. The running cluster checks the libraries of every server build its nodes run against the Maven advisories it reads from <a href="https://osv.dev" rel="nofollow">
        OSV
      </a>
      
      , the GitHub Advisory Database's among them.
    </td>
  </tr>
  
  <tr>
    <td>
      <strong>
        Frequency
      </strong>
    </td>
    
    <td>
      Whenever the system console's <strong>
        Cluster
      </strong>
      
       page is opened. The advisories are read from OSV hourly, so an advisory published after an image was built shows up within the hour.
    </td>
  </tr>
  
  <tr>
    <td>
      <strong>
        Evidence
      </strong>
    </td>
    
    <td>
      The <strong>
        Vulnerabilities
      </strong>
      
       section of the system console's Cluster page: a summary of each server build running (the nodes of one server kind and version that list the same libraries) with its node count and how many advisories affect it, and one row per advisory with its severity, CVEs, the builds it affects and the version that fixes each affected library.
    </td>
  </tr>
</tbody>
</table>

### Release gate

<table>
<thead>
  <tr>
    <th>
      
    </th>
    
    <th>
      
    </th>
  </tr>
</thead>

<tbody>
  <tr>
    <td>
      <strong>
        Control
      </strong>
    </td>
    
    <td>
      A build fails when either image has a <strong>
        Critical
      </strong>
      
       vulnerability with a published fix. On a pull request, the failure is reported on the change before it merges. Only an image from a fully passing build of <code>
        develop
      </code>
      
       or <code>
        main
      </code>
      
       is promoted from the staging registry (ghcr.io) to Docker Hub.
    </td>
  </tr>
  
  <tr>
    <td>
      <strong>
        Effect
      </strong>
    </td>
    
    <td>
      An image is published only when, at build time, it has no known Critical vulnerability with an available fix.
    </td>
  </tr>
  
  <tr>
    <td>
      <strong>
        Evidence
      </strong>
    </td>
    
    <td>
      The <code>
        SBOM and Vulnerability Scan
      </code>
      
       jobs and the <code>
        Build Result
      </code>
      
       job of each workflow run. <code>
        Build Result
      </code>
      
       performs the promotion and records which job withheld it.
    </td>
  </tr>
</tbody>
</table>

### Integrity of the scanning tools

<table>
<thead>
  <tr>
    <th>
      
    </th>
    
    <th>
      
    </th>
  </tr>
</thead>

<tbody>
  <tr>
    <td>
      <strong>
        Control
      </strong>
    </td>
    
    <td>
      Third-party actions in the scan job are pinned to full commit SHAs rather than version tags, so a repointed tag cannot change the code that runs. The scan job has read-only access to repository contents and holds no publishing, signing or deployment credentials.
    </td>
  </tr>
  
  <tr>
    <td>
      <strong>
        Maintenance
      </strong>
    </td>
    
    <td>
      Dependabot proposes updates to every workflow action weekly, including the pinned SHAs (<code>
        .github/dependabot.yml
      </code>
      
      ).
    </td>
  </tr>
</tbody>
</table>

### Dependency vulnerability alerts

<table>
<thead>
  <tr>
    <th>
      
    </th>
    
    <th>
      
    </th>
  </tr>
</thead>

<tbody>
  <tr>
    <td>
      <strong>
        Control
      </strong>
    </td>
    
    <td>
      GitHub's dependency graph reads the npm, pnpm and Bun lockfiles, and Dependabot raises an alert for each dependency with a known vulnerability. For each JavaScript project, Dependabot opens one security update pull request that batches its available fixes (<code>
        .github/dependabot.yml
      </code>
      
      ). The <code>
        Dependabot Merge
      </code>
      
       workflow (<code>
        .github/workflows/dependabot-merge.yml
      </code>
      
      ) merges each such pull request once at least one of the repository's GitHub Actions checks has succeeded on it, none of its checks has failed and none is still running, which closes its alerts. A failure from any check blocks the merge, as does a GitHub Actions run that was cancelled, timed out, went stale or needs action; another app's cancelled or skipped check does not. A pull request with a blocking check, with no successful or running GitHub Actions check a day after it was opened, or that GitHub refuses to merge is assigned to a maintainer. The Java libraries are covered by the image vulnerability scanning above.
    </td>
  </tr>
  
  <tr>
    <td>
      <strong>
        Frequency
      </strong>
    </td>
    
    <td>
      Alerts are raised as advisories are published. Security updates are checked weekly, and passing pull requests are merged daily.
    </td>
  </tr>
  
  <tr>
    <td>
      <strong>
        Evidence
      </strong>
    </td>
    
    <td>
      Dependabot alerts on the repository's Security tab, Dependabot's update runs under Insights > Dependency graph > Dependabot, and the runs of the <code>
        Dependabot Merge
      </code>
      
       workflow.
    </td>
  </tr>
</tbody>
</table>

### Source code analysis

<table>
<thead>
  <tr>
    <th>
      
    </th>
    
    <th>
      
    </th>
  </tr>
</thead>

<tbody>
  <tr>
    <td>
      <strong>
        Control
      </strong>
    </td>
    
    <td>
      CodeQL static analysis of the Java and TypeScript source.
    </td>
  </tr>
  
  <tr>
    <td>
      <strong>
        Frequency
      </strong>
    </td>
    
    <td>
      Every push to <code>
        develop
      </code>
      
      , and weekly.
    </td>
  </tr>
  
  <tr>
    <td>
      <strong>
        Evidence
      </strong>
    </td>
    
    <td>
      GitHub code scanning alerts from the <code>
        CodeQL
      </code>
      
       workflow.
    </td>
  </tr>
</tbody>
</table>

## Control mapping

<table>
<thead>
  <tr>
    <th>
      Control
    </th>
    
    <th>
      NIST SP 800-171 Rev 2
    </th>
    
    <th>
      SOC 2
    </th>
    
    <th>
      ISO/IEC 27001:2022
    </th>
  </tr>
</thead>

<tbody>
  <tr>
    <td>
      Software inventory (SBOM)
    </td>
    
    <td>
      3.4.1
    </td>
    
    <td>
      CC7.1
    </td>
    
    <td>
      A.5.9, A.8.9
    </td>
  </tr>
  
  <tr>
    <td>
      Vulnerability scanning
    </td>
    
    <td>
      3.11.2
    </td>
    
    <td>
      CC7.1
    </td>
    
    <td>
      A.8.8
    </td>
  </tr>
  
  <tr>
    <td>
      Release gate
    </td>
    
    <td>
      3.11.3, 3.14.1
    </td>
    
    <td>
      CC7.1, CC8.1
    </td>
    
    <td>
      A.8.8, A.8.32
    </td>
  </tr>
  
  <tr>
    <td>
      Integrity of the scanning tools
    </td>
    
    <td>
      3.4.1, 3.14.1
    </td>
    
    <td>
      CC8.1
    </td>
    
    <td>
      A.8.25, A.8.28
    </td>
  </tr>
  
  <tr>
    <td>
      Dependency vulnerability alerts
    </td>
    
    <td>
      3.11.2
    </td>
    
    <td>
      CC7.1
    </td>
    
    <td>
      A.8.8
    </td>
  </tr>
  
  <tr>
    <td>
      Source code analysis
    </td>
    
    <td>
      3.11.2
    </td>
    
    <td>
      CC7.1
    </td>
    
    <td>
      A.8.28
    </td>
  </tr>
</tbody>
</table>


## Sitemap

See the full [sitemap](https://kinotic.ai/sitemap.md) for all pages.
