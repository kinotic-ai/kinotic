# Supply Chain Security

> How Kinotic inventories, scans and gates the container images it publishes, and where the evidence for each control is kept.

## Overview

Every Kinotic service image is inventoried and scanned for known vulnerabilities before it is published. An image with a Critical vulnerability that has a published fix is never promoted to Docker Hub. Every control on this page runs automatically in the `Build and Publish` workflow (`.github/workflows/gradle-build.yml`), and each one leaves evidence in GitHub.

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
      Kinotic server
    </td>
    
    <td>
      <code>
        kinoticai/kinotic-server
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
      Each build produces a software bill of materials for each image. It lists every operating-system package and application library in the image, with its version.
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
      Every build of the images: each push to <code>
        develop
      </code>
      
       or <code>
        main
      </code>
      
       that changes application code, and nightly.
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
        sbom-kinotic-server.cdx.json
      </code>
      
       and <code>
        sbom-kinotic-migration.cdx.json
      </code>
      
      .
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
      Every build and nightly. The nightly run checks newly published advisories against the current images.
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
        image-kinotic-server
      </code>
      
       and <code>
        image-kinotic-migration
      </code>
      
      , with open and fixed history per finding. Each workflow run's summary lists the fixable findings for each image.
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
      
       vulnerability with a published fix. Only an image from a fully passing build is promoted from the staging registry (ghcr.io) to Docker Hub.
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
