# Implementation Plan: KinD Cluster Developer Tools

**Branch**: `001-kind-cluster-tools` | **Date**: 2025-11-26 | **Spec**: [spec.md](spec.md)  
**Input**: Feature specification from `/specs/001-kind-cluster-tools/spec.md`

**Note**: This template is filled in by the `/speckit.plan` command. See `.specify/templates/commands/plan.md` for the execution workflow.

## Summary

Create developer tools that automate the creation and management of KinD (Kubernetes in Docker) clusters for testing cluster coordination features in structures-server. The tools will bootstrap a local Kubernetes environment, deploy structures-server via Helm with properly configured Keycloak (matching docker-compose setup) and optional observability stack (OpenTelemetry, Prometheus, Grafana), support local image builds, and provide lifecycle management (create/deploy/cleanup). Implementation will use Bash scripts following convention-over-configuration principles with sensible defaults and optional customization.

## Technical Context

**Language/Version**: Bash 4.0+ (universally available on unix/linux)  
**Primary Dependencies**: KinD CLI, kubectl, helm, Docker  
**Storage**: File-based (KinD configuration YAML, Helm values files, cluster state)  
**Testing**: Integration tests using real KinD clusters (TestContainers not applicable for Bash scripts)  
**Target Platform**: unix/linux (macOS, Ubuntu, RHEL/CentOS, other Linux distributions)  
**Project Type**: CLI tooling (shell scripts)  
**Performance Goals**: Cluster creation <5min, deployment <3min, cleanup <2min  
**Constraints**: Must work without network for pre-downloaded dependencies, idempotent operations, safe context switching  
**Scale/Scope**: Single-node or multi-node KinD clusters (1-5 nodes typical), local development only

**Build Integration**: Uses existing Gradle `bootBuildImage` task from `buildSrc/src/main/groovy/org.mindignited.java-application-conventions.gradle` for image builds. **Note**: The `publish` flag (line 37) should be updated to be conditional on CI environment to prevent accidental publishes during local development:
```groovy
publish = System.getenv("CI") == "true" || System.getenv("GITHUB_ACTIONS") == "true"
```

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

### Principle I: Library-First Architecture
**Status**: ⚠️ **NEEDS CONSIDERATION** - These are development tools, not library code  
**Assessment**: The tools are standalone scripts for developer workflows, not library modules consumed by applications. However, scripts can follow modular design with reusable functions.  
**Action**: Structure scripts with reusable functions that could later be extracted if needed. No violation - tools are inherently standalone.

### Principle II: Multi-Module Build Conventions
**Status**: ✅ **COMPLIANT** - Not applicable  
**Assessment**: Bash scripts don't require Gradle builds. Scripts will be placed in appropriate directory structure.  
**Action**: None required.

### Principle III: Convention over Configuration with MVP Focus
**Status**: ✅ **COMPLIANT**  
**Assessment**: Tools will provide sensible defaults (cluster name, node count, K8s version, Helm values) with optional overrides via CLI flags or environment variables. MVP focuses on P1 (bootstrap) and P2 (deploy) first.  
**Action**: Implement with zero-config defaults, extensible configuration.

### Principle IV: Reactive Programming
**Status**: ✅ **COMPLIANT** - Not applicable  
**Assessment**: Bash scripts are synchronous by nature. Not applicable to CLI tooling.  
**Action**: None required.

### Principle V: Stop and Ask When Uncertain
**Status**: ✅ **COMPLIANT**  
**Assessment**: Scripts will validate prerequisites and halt with clear error messages when uncertain (missing tools, unclear cluster state, unsafe contexts).  
**Action**: Implement comprehensive prerequisite checking and safe-mode prompts.

### Principle VI: Integration-First Test-Driven Development
**Status**: ✅ **COMPLIANT**  
**Assessment**: Scripts will be tested by actually creating KinD clusters and deploying structures-server (real integration tests, no mocks). Test with real Docker, kubectl, helm, kind CLI.  
**Action**: Create integration test suite that runs scripts against real environments.

### Principle VII: Documentation Last
**Status**: ✅ **COMPLIANT**  
**Assessment**: README and usage documentation will be written after scripts are implemented and tested.  
**Action**: Document after implementation validated.

### Principle VIII: API Stability & Backward Compatibility
**Status**: ✅ **COMPLIANT**  
**Assessment**: CLI interfaces (flags, environment variables) should remain stable. Internal script functions can evolve.  
**Action**: Design clean CLI interface from start, mark as v1.0 when stable.

### Principle IX: Security-First Approach
**Status**: ✅ **COMPLIANT**  
**Assessment**: Scripts must verify kubectl context before destructive operations. No credentials hardcoded. Safe defaults only.  
**Action**: Implement context safety checks, especially for delete operations.

### Principle X: Multi-Tenant by Design
**Status**: ✅ **COMPLIANT** - Not directly applicable  
**Assessment**: Tools create isolated KinD clusters (inherently single-tenant for local dev). No cross-tenant concerns.  
**Action**: None required.

### Principle XI: Observability & Monitoring
**Status**: ✅ **COMPLIANT**  
**Assessment**: Scripts will provide verbose logging of all operations, status checking commands, and diagnostic output.  
**Action**: Implement comprehensive logging and status reporting.

### Principle XII: Performance Optimization
**Status**: ✅ **COMPLIANT**  
**Assessment**: Scripts will cache tool downloads, reuse existing clusters when safe, and optimize image loading.  
**Action**: Implement caching strategies for downloaded binaries and images.

### Principle XIII: Schema Evolution Support
**Status**: ✅ **COMPLIANT** - Not applicable  
**Assessment**: No data schemas involved in tooling scripts.  
**Action**: None required.

### Principle XIV: TypeScript-First Client APIs
**Status**: ✅ **COMPLIANT** - Not applicable  
**Assessment**: This is Bash tooling, not client API code. TypeScript not relevant.  
**Action**: None required.

**Overall Assessment**: ✅ **PASSES** - All applicable principles compliant. Tools are developer workflow automation, not application code, so some principles naturally don't apply. No violations require justification.

## Project Structure

### Documentation (this feature)

```text
specs/001-kind-cluster-tools/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan command)
├── data-model.md        # Phase 1 output (configuration model)
├── quickstart.md        # Phase 1 output (usage guide)
├── contracts/           # Phase 1 output (CLI contracts)
│   └── cli-interface.md # Command specifications
└── tasks.md             # Phase 2 output (/speckit.tasks command)
```

### Source Code (repository root)

```text
dev-tools/
├── kind/
│   ├── kind-cluster.sh         # Main orchestration script
│   ├── lib/
│   │   ├── prerequisites.sh    # Prerequisite checking functions
│   │   ├── cluster.sh          # Cluster lifecycle functions
│   │   ├── deploy.sh           # Deployment functions
│   │   ├── images.sh           # Image build/load functions
│   │   ├── logging.sh          # Logging and output functions
│   │   └── config.sh           # Configuration loading functions
│   ├── config/
│   │   ├── kind-config.yaml    # Default KinD cluster configuration
│   │   └── helm-values.yaml    # Default Helm values for structures-server
│   └── README.md               # Usage documentation (created after implementation)
│
tests/
└── integration/
    └── kind-cluster-test.sh    # Integration tests for KinD tooling
```

**Structure Decision**: Single CLI tooling project using modular Bash scripts. The main entry point (`kind-cluster.sh`) orchestrates workflows by calling functions from library modules in `lib/`. Configuration files provide sensible defaults. This structure follows convention-over-configuration, allowing easy extension without changing core scripts.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

*No violations identified. Table not needed.*
