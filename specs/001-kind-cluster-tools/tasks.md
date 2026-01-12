---

description: "Task list for KinD Cluster Developer Tools implementation"
---

# Tasks: KinD Cluster Developer Tools

**Input**: Design documents from `/specs/001-kind-cluster-tools/`
**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/

**Tests**: Integration tests with real KinD clusters are included per constitution principle VI (Integration-First TDD).

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3, US4)
- Include exact file paths in descriptions

## Path Conventions

- **Scripts**: `dev-tools/kind/` at repository root
- **Library functions**: `dev-tools/kind/lib/`
- **Configuration**: `dev-tools/kind/config/`
- **Tests**: `tests/integration/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and basic structure

- [x] T001 Create dev-tools/kind directory structure per implementation plan
- [x] T002 [P] Create config directory at dev-tools/kind/config/
- [x] T003 [P] Create lib directory at dev-tools/kind/lib/
- [x] T004 [P] Create tests/integration directory for integration tests
- [x] T005 Create kind-config.yaml default configuration in dev-tools/kind/config/kind-config.yaml
- [x] T006 Create helm-values.yaml default configuration in dev-tools/kind/config/helm-values.yaml
- [x] T007 [P] Create .gitignore entry for helm-values.local.yaml in dev-tools/kind/config/

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure that MUST be complete before ANY user story can be implemented

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [x] T008 Implement logging functions (info, error, success, verbose) in dev-tools/kind/lib/logging.sh
- [x] T009 Implement configuration loading function (reads env vars, CLI flags, config files) in dev-tools/kind/lib/config.sh
- [x] T010 Implement prerequisite checking for Docker in dev-tools/kind/lib/prerequisites.sh
- [x] T011 Implement prerequisite checking for kind CLI in dev-tools/kind/lib/prerequisites.sh
- [x] T012 Implement prerequisite checking for kubectl in dev-tools/kind/lib/prerequisites.sh
- [x] T013 Implement prerequisite checking for helm in dev-tools/kind/lib/prerequisites.sh
- [x] T014 Implement OS detection (macOS, Linux distributions) in dev-tools/kind/lib/prerequisites.sh
- [x] T015 Implement installation guidance messages per OS in dev-tools/kind/lib/prerequisites.sh
- [x] T016 Implement Docker daemon status check in dev-tools/kind/lib/prerequisites.sh
- [x] T017 Create main script entrypoint with subcommand routing in dev-tools/kind/kind-cluster.sh
- [x] T018 Implement global option parsing (--verbose, --dry-run, --help) in dev-tools/kind/kind-cluster.sh
- [x] T019 Implement exit code constants and error handling in dev-tools/kind/kind-cluster.sh

**Checkpoint**: Foundation ready - user story implementation can now begin in parallel

---

## Phase 3: User Story 1 - Bootstrap KinD Cluster (Priority: P1) 🎯 MVP

**Goal**: Create a KinD cluster with default or custom configuration

**Independent Test**: Run create command on machine without existing cluster, verify cluster accessible via kubectl cluster-info

### Implementation for User Story 1

- [x] T020 [US1] Implement cluster existence check function in dev-tools/kind/lib/cluster.sh
- [x] T021 [US1] Implement kubectl context safety check function in dev-tools/kind/lib/cluster.sh
- [x] T022 [US1] Implement KinD cluster creation function (calls kind create cluster) in dev-tools/kind/lib/cluster.sh
- [x] T023 [US1] Implement cluster readiness wait function (polls kubectl get nodes) in dev-tools/kind/lib/cluster.sh
- [x] T024 [US1] Implement cluster info display function (shows nodes, API server, context) in dev-tools/kind/lib/cluster.sh
- [x] T025 [US1] Implement 'create' subcommand with flag parsing in dev-tools/kind/kind-cluster.sh
- [x] T026 [US1] Implement --force flag logic (delete existing cluster if present) in dev-tools/kind/kind-cluster.sh
- [x] T027 [US1] Implement --config flag to use custom kind-config.yaml in dev-tools/kind/kind-cluster.sh
- [x] T028 [US1] Implement --k8s-version flag to specify Kubernetes version in dev-tools/kind/kind-cluster.sh
- [x] T029 [US1] Add error handling for port conflicts during cluster creation in dev-tools/kind/lib/cluster.sh
- [x] T030 [US1] Add error handling for insufficient resources in dev-tools/kind/lib/cluster.sh

### Integration Tests for User Story 1

- [ ] T031 [US1] Write integration test: create cluster with defaults in tests/integration/kind-cluster-test.sh
- [ ] T032 [US1] Write integration test: create cluster with custom name in tests/integration/kind-cluster-test.sh
- [ ] T033 [US1] Write integration test: create cluster with --force flag in tests/integration/kind-cluster-test.sh
- [ ] T034 [US1] Write integration test: prerequisite check fails gracefully in tests/integration/kind-cluster-test.sh

**Checkpoint**: At this point, User Story 1 should be fully functional and testable independently

---

## Phase 4: User Story 2 - Deploy structures-server (Priority: P2)

**Goal**: Deploy structures-server, Keycloak, Elasticsearch to KinD cluster using Helm

**Independent Test**: Deploy to existing cluster, verify all pods running and health checks pass

### Implementation for User Story 2

- [x] T035 [US2] Implement Helm repository addition function in dev-tools/kind/lib/deploy.sh
- [x] T036 [US2] Implement PostgreSQL deployment for Keycloak in dev-tools/kind/lib/deploy.sh
- [x] T037 [US2] Implement Keycloak realm ConfigMap creation from docker-compose/keycloak-test-realm.json in dev-tools/kind/lib/deploy.sh
- [x] T038 [US2] Implement Keycloak deployment with custom values (matching docker-compose) in dev-tools/kind/lib/deploy.sh
- [x] T039 [US2] Implement Elasticsearch deployment via Bitnami chart in dev-tools/kind/lib/deploy.sh
- [x] T040 [US2] Implement structures-server Helm deployment with OIDC configuration in dev-tools/kind/lib/deploy.sh
- [x] T041 [US2] Implement pod readiness wait function (kubectl wait --for=condition=ready) in dev-tools/kind/lib/deploy.sh
- [x] T042 [US2] Implement health check verification for deployed services in dev-tools/kind/lib/deploy.sh
- [x] T043 [US2] Implement deployment status display (pods, services, endpoints) in dev-tools/kind/lib/deploy.sh
- [ ] T044 [US2] Implement OpenTelemetry Collector deployment (optional) in dev-tools/kind/lib/deploy.sh
- [ ] T045 [US2] Implement Prometheus deployment (optional) in dev-tools/kind/lib/deploy.sh
- [ ] T046 [US2] Implement Grafana deployment with dashboards (optional) in dev-tools/kind/lib/deploy.sh
- [ ] T047 [US2] Implement Jaeger deployment (optional) in dev-tools/kind/lib/deploy.sh
- [ ] T048 [US2] Implement Loki deployment (optional) in dev-tools/kind/lib/deploy.sh
- [x] T049 [US2] Implement 'deploy' subcommand with flag parsing in dev-tools/kind/kind-cluster.sh
- [x] T050 [US2] Implement --with-deps flag (default true) to deploy dependencies in dev-tools/kind/kind-cluster.sh
- [x] T051 [US2] Implement --with-observability flag to deploy observability stack in dev-tools/kind/kind-cluster.sh
- [x] T052 [US2] Implement --values flag to use custom helm-values.yaml in dev-tools/kind/kind-cluster.sh
- [x] T053 [US2] Implement --set flag for inline Helm value overrides in dev-tools/kind/kind-cluster.sh
- [x] T054 [US2] Add helm upgrade --install --atomic logic for idempotent deployments in dev-tools/kind/lib/deploy.sh
- [x] T055 [US2] Add deployment failure diagnostic messages (which pods failed, why) in dev-tools/kind/lib/deploy.sh

### Integration Tests for User Story 2

- [ ] T056 [US2] Write integration test: deploy with dependencies to existing cluster in tests/integration/kind-cluster-test.sh
- [ ] T057 [US2] Write integration test: deploy with custom Helm values in tests/integration/kind-cluster-test.sh
- [ ] T058 [US2] Write integration test: deploy with observability stack in tests/integration/kind-cluster-test.sh
- [ ] T059 [US2] Write integration test: verify Keycloak realm imported correctly in tests/integration/kind-cluster-test.sh
- [ ] T060 [US2] Write integration test: verify OIDC configuration in structures-server pods in tests/integration/kind-cluster-test.sh
- [ ] T061 [US2] Write integration test: helm upgrade on existing deployment in tests/integration/kind-cluster-test.sh

**Checkpoint**: At this point, User Stories 1 AND 2 should both work independently

---

## Phase 5: User Story 3 - Build and Load Local Images (Priority: P3)

**Goal**: Build structures-server image using bootBuildImage and load into KinD cluster

**Independent Test**: Make code change, build image, load into cluster, deploy with new image, verify changes reflected

### Implementation for User Story 3

- [x] T062 [US3] Implement gradle.properties version reader in dev-tools/kind/lib/images.sh
- [x] T063 [US3] Implement bootBuildImage invocation (./gradlew :structures-server:bootBuildImage) in dev-tools/kind/lib/images.sh
- [x] T064 [US3] Implement Docker image existence check in dev-tools/kind/lib/images.sh
- [x] T065 [US3] Implement kind load docker-image function in dev-tools/kind/lib/images.sh
- [x] T066 [US3] Implement crictl images verification on cluster nodes in dev-tools/kind/lib/images.sh
- [x] T067 [US3] Implement image info display (name, tag, size) in dev-tools/kind/lib/images.sh
- [x] T068 [US3] Implement 'build' subcommand with flag parsing in dev-tools/kind/kind-cluster.sh
- [x] T069 [US3] Implement --load flag to automatically load after build in dev-tools/kind/kind-cluster.sh
- [x] T070 [US3] Implement --module flag to specify which module to build in dev-tools/kind/kind-cluster.sh
- [x] T071 [US3] Implement 'load' subcommand with flag parsing in dev-tools/kind/kind-cluster.sh
- [x] T072 [US3] Implement --image flag to specify custom image name in dev-tools/kind/kind-cluster.sh
- [x] T073 [US3] Add error handling for bootBuildImage failures in dev-tools/kind/lib/images.sh
- [x] T074 [US3] Add error handling for image not found locally in dev-tools/kind/lib/images.sh

### Integration Tests for User Story 3

- [ ] T075 [US3] Write integration test: build image with bootBuildImage in tests/integration/kind-cluster-test.sh
- [ ] T076 [US3] Write integration test: load image into cluster nodes in tests/integration/kind-cluster-test.sh
- [ ] T077 [US3] Write integration test: build with --load flag in tests/integration/kind-cluster-test.sh
- [ ] T078 [US3] Write integration test: deploy with locally-built image in tests/integration/kind-cluster-test.sh

**Checkpoint**: All user stories US1, US2, and US3 should now be independently functional

---

## Phase 6: User Story 4 - Clean Up Test Environment (Priority: P4)

**Goal**: Delete KinD cluster and clean up all related Docker containers

**Independent Test**: Create cluster with deployments, run delete, verify cluster and containers removed

### Implementation for User Story 4

- [x] T079 [US4] Implement cluster deletion function (kind delete cluster) in dev-tools/kind/lib/cluster.sh
- [x] T080 [US4] Implement Docker container cleanup verification in dev-tools/kind/lib/cluster.sh
- [x] T081 [US4] Implement kubectl context cleanup logic in dev-tools/kind/lib/cluster.sh
- [x] T082 [US4] Implement confirmation prompt for destructive operations in dev-tools/kind/lib/cluster.sh
- [x] T083 [US4] Implement 'delete' subcommand with flag parsing in dev-tools/kind/kind-cluster.sh
- [x] T084 [US4] Implement --force flag to skip confirmation prompt in dev-tools/kind/kind-cluster.sh
- [x] T085 [US4] Add kubectl context safety check (ensure pointing at kind cluster) in dev-tools/kind/lib/cluster.sh
- [x] T086 [US4] Add graceful handling when cluster doesn't exist in dev-tools/kind/lib/cluster.sh

### Integration Tests for User Story 4

- [ ] T087 [US4] Write integration test: delete cluster with confirmation in tests/integration/kind-cluster-test.sh
- [ ] T088 [US4] Write integration test: delete with --force flag in tests/integration/kind-cluster-test.sh
- [ ] T089 [US4] Write integration test: delete when cluster doesn't exist in tests/integration/kind-cluster-test.sh
- [ ] T090 [US4] Write integration test: verify all Docker containers removed in tests/integration/kind-cluster-test.sh

**Checkpoint**: All user stories should now be independently functional

---

## Phase 7: Cross-Cutting Features

**Purpose**: Features that span multiple user stories

- [x] T091 [P] Implement cluster status query function in dev-tools/kind/lib/cluster.sh
- [ ] T092 [P] Implement Helm releases listing function in dev-tools/kind/lib/deploy.sh
- [ ] T093 [P] Implement pod status display function in dev-tools/kind/lib/deploy.sh
- [ ] T094 [P] Implement service endpoints display function in dev-tools/kind/lib/deploy.sh
- [ ] T095 [P] Implement OIDC configuration display in dev-tools/kind/lib/deploy.sh
- [ ] T096 [P] Implement observability stack status display in dev-tools/kind/lib/deploy.sh
- [x] T097 Implement 'status' subcommand with --watch flag in dev-tools/kind/kind-cluster.sh
- [x] T098 [P] Implement log streaming function (kubectl logs) in dev-tools/kind/lib/cluster.sh
- [x] T099 [P] Implement log filtering by pod selector in dev-tools/kind/lib/cluster.sh
- [x] T100 Implement 'logs' subcommand with --follow and --tail flags in dev-tools/kind/kind-cluster.sh

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: Improvements that affect multiple user stories

- [x] T101 [P] Create README.md with usage examples in dev-tools/kind/README.md
- [x] T102 [P] Document all subcommands and flags in dev-tools/kind/README.md
- [x] T103 [P] Add troubleshooting section to README.md in dev-tools/kind/README.md
- [x] T104 [P] Add quickstart workflow examples in dev-tools/kind/README.md
- [x] T105 Make kind-cluster.sh executable (chmod +x) at dev-tools/kind/kind-cluster.sh
- [x] T106 Add shebang and set -euo pipefail to all shell scripts in dev-tools/kind/
- [ ] T107 Add help text for main command in dev-tools/kind/kind-cluster.sh
- [ ] T108 Add help text for each subcommand in dev-tools/kind/kind-cluster.sh
- [ ] T109 [P] Run shellcheck on all bash scripts and fix warnings
- [ ] T110 [P] Add comprehensive error handling to all library functions
- [ ] T111 [P] Add bash completion script (optional) in dev-tools/kind/completions/kind-cluster.bash
- [ ] T112 Run full integration test suite against fresh cluster in tests/integration/kind-cluster-test.sh
- [ ] T113 Test on macOS platform and document any platform-specific issues
- [ ] T114 Test on Ubuntu platform and document any platform-specific issues
- [ ] T115 Test on RHEL/CentOS platform and document any platform-specific issues
- [ ] T116 Validate all success criteria from spec.md (cluster <5min, deploy <3min, cleanup <2min)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion - BLOCKS all user stories
- **User Stories (Phase 3-6)**: All depend on Foundational phase completion
  - User stories can then proceed in parallel (if staffed)
  - Or sequentially in priority order (P1 → P2 → P3 → P4)
- **Cross-Cutting (Phase 7)**: Depends on US1 (cluster) and US2 (deploy) completion
- **Polish (Phase 8)**: Depends on all desired user stories being complete

### User Story Dependencies

- **User Story 1 (P1)**: Can start after Foundational (Phase 2) - No dependencies on other stories
- **User Story 2 (P2)**: Can start after Foundational (Phase 2) - Integrates with US1 but independently testable
- **User Story 3 (P3)**: Can start after Foundational (Phase 2) - Integrates with US1/US2 but independently testable
- **User Story 4 (P4)**: Can start after Foundational (Phase 2) - Integrates with US1 but independently testable

### Within Each User Story

- Implementation tasks before integration tests
- Integration tests verify the story works as a whole with real KinD clusters
- No mocks used - all tests against real Docker, kubectl, helm, kind CLI
- Story complete before moving to next priority

### Parallel Opportunities

- All Setup tasks marked [P] can run in parallel (T002, T003, T004, T007)
- Once Foundational phase completes, all user stories can start in parallel (if team capacity allows)
- Within US2: Dependencies deployment tasks (T036-T039) can run in parallel after T035
- Within US2: Observability deployment tasks (T044-T048) can run in parallel
- Cross-cutting tasks (T091-T096, T098-T099) can run in parallel
- Polish tasks marked [P] can run in parallel (T101-T104, T109-T110)

---

## Parallel Example: User Story 2

```bash
# After US1 complete, these can proceed together:
Task T035: Add Helm repos (blocking for rest)
# Then in parallel:
Task T036: Deploy PostgreSQL
Task T037: Create Keycloak ConfigMap
Task T039: Deploy Elasticsearch
# After T036 complete:
Task T038: Deploy Keycloak (depends on PostgreSQL)
# After T036-T039 complete:
Task T040: Deploy structures-server (depends on Elasticsearch and Keycloak)
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational (CRITICAL - blocks all stories)
3. Complete Phase 3: User Story 1
4. **STOP and VALIDATE**: Test User Story 1 independently
5. Verify: Can create cluster, cluster accessible, no errors

### Incremental Delivery

1. Complete Setup + Foundational → Foundation ready
2. Add User Story 1 → Test independently (MVP!)
3. Add User Story 2 → Test independently
4. Add User Story 3 → Test independently
5. Add User Story 4 → Test independently
6. Add Cross-Cutting Features → Complete experience
7. Polish → Production ready

### Parallel Team Strategy

With multiple developers:

1. Team completes Setup + Foundational together
2. Once Foundational is done:
   - Developer A: User Story 1 (bootstrap cluster)
   - Developer B: User Story 2 (deploy structures-server)
   - Developer C: User Story 3 (build/load images)
3. Stories complete and integrate independently

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- Each user story should be independently completable and testable
- Integration tests use real KinD clusters, Docker, kubectl, helm (no mocks per constitution)
- Commit after each task or logical group
- Stop at any checkpoint to validate story independently
- All bash scripts must include error handling and verbose logging support
- Use absolute paths for all file operations
- Test on multiple unix/linux platforms (macOS, Ubuntu, RHEL/CentOS)

---

## Task Summary

**Total Tasks**: 116

**Tasks per User Story**:
- Setup: 7 tasks
- Foundational: 12 tasks (BLOCKS all user stories)
- User Story 1 (P1): 15 tasks (11 implementation + 4 integration tests)
- User Story 2 (P2): 27 tasks (21 implementation + 6 integration tests)
- User Story 3 (P3): 17 tasks (13 implementation + 4 integration tests)
- User Story 4 (P4): 12 tasks (8 implementation + 4 integration tests)
- Cross-Cutting: 10 tasks
- Polish: 16 tasks

**Parallel Opportunities**: 24 tasks marked [P] can run in parallel

**Independent Test Criteria**:
- US1: Create cluster, verify accessible via kubectl cluster-info
- US2: Deploy to cluster, verify all pods running and health checks pass
- US3: Build and load image, deploy with new image, verify changes reflected
- US4: Delete cluster, verify all resources removed

**Suggested MVP Scope**: Phase 1 + Phase 2 + Phase 3 (User Story 1 only) = 34 tasks to deliver basic cluster creation capability

---

## Phase 9: Kubernetes Cache Eviction Testing

**Purpose**: Add automated tests for cache eviction in Kubernetes clusters

### Implementation

- [x] T117 [K8s] Create TestKubernetesProperties configuration class in structures-core/src/test/java/org/mindignited/structures/kubernetes/TestKubernetesProperties.java
- [x] T118 [K8s] Create application-k8s-test.yml configuration in structures-core/src/test/resources/application-k8s-test.yml
- [x] T119 [K8s] Create K8sTestBase with kubectl utilities and port-forward management in structures-core/src/test/java/org/mindignited/structures/kubernetes/K8sTestBase.java
- [x] T120 [K8s] Create KubectlPortForwardManager for managing background port-forward processes in structures-core/src/test/java/org/mindignited/structures/kubernetes/KubectlPortForwardManager.java
- [x] T121 [K8s] Create K8sClusterCacheEvictionTest with cache propagation test in structures-core/src/test/java/org/mindignited/structures/kubernetes/K8sClusterCacheEvictionTest.java

### Documentation

- [x] T122 [K8s] Add Kubernetes testing section to docker-compose/CLUSTER_TESTING.md
- [x] T123 [K8s] Document manual testing procedures via kubectl port-forward in docker-compose/CLUSTER_TESTING.md
- [x] T124 [K8s] Document troubleshooting for K8s tests in docker-compose/CLUSTER_TESTING.md
- [x] T125 [K8s] Update tasks.md with K8s cache eviction test tasks

**Checkpoint**: Kubernetes cache eviction tests are now available alongside Docker Compose tests

---

