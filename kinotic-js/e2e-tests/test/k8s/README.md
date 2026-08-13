# Kubernetes Cache Eviction Tests

Verifies that a cache eviction on one kinotic-server pod propagates to the rest of the
cluster. The test connects to every pod through `kubectl port-forward` over STOMP, mutates
an entity definition on one pod, and checks the eviction CSVs the other pods write.

## Status: disabled

The suite file is `k8s-cache-eviction.testx.ts`. Vitest's default `include` only matches
`*.test.ts` / `*.spec.ts`, so `.testx.ts` is never collected — `pnpm test` skips it
entirely. Rename it to `.test.ts` to run it, and expect to fix the eviction-data plumbing
described under "Reading the eviction CSVs" first.

## Prerequisites

1. A Kubernetes cluster with kinotic-server at 3 replicas (`deployment/kind/`).
2. `kubectl` configured for that cluster.
3. `evictionTracking.enabled: true` in the Helm values, plus the `eviction-tracking` Spring
   profile on the server — `deployment/kind/config/kinotic-server/values.yaml` and
   `deployment/kind/terraform/kinotic.tf` both set these for KinD.

## Configuration

`K8sTestHelper` (`k8s-helper.ts`) reads these:

| Variable | Description | Default |
|----------|-------------|---------|
| `K8S_TEST_ENABLED` | Enable the suite | `false` |
| `K8S_CONTEXT` | Kubernetes context | `kind-structures-cluster` |
| `K8S_NAMESPACE` | Kubernetes namespace | `default` |
| `K8S_LABEL_SELECTOR` | Pod label selector | `app=structures` |
| `K8S_REPLICA_COUNT` | Expected replicas | `3` |
| `K8S_STOMP_PORT` | STOMP port in the pods | `58503` |
| `K8S_STARTING_LOCAL_PORT` | First local port for port-forwards | `58511` |
| `K8S_EVICTION_DATA_PATH` | Where the eviction CSVs are read from | `../../../../dev-tools/kind/eviction-data` |

The context, selector, and eviction path defaults predate the rename to Kinotic and the
current KinD terraform. Against `deployment/kind/` the cluster is `kind-kinotic-cluster`,
the server pods carry `app=kinotic` in namespace `kinotic`, and no host directory is
mounted for eviction data — so all four need overriding:

```bash
K8S_TEST_ENABLED=true \
K8S_CONTEXT=kind-kinotic-cluster \
K8S_NAMESPACE=kinotic \
K8S_LABEL_SELECTOR=app=kinotic \
pnpm test -- k8s-cache-eviction
```

## Port forwarding

The helper starts one `kubectl port-forward` per pod and maps them to consecutive local
ports from `K8S_STARTING_LOCAL_PORT`:

```
localhost:58511 → pod 0:58503
localhost:58512 → pod 1:58503
localhost:58513 → pod 2:58503
```

They are torn down when the helper closes. If a run dies mid-test:
`pkill -f "kubectl port-forward"`.

## Reading the eviction CSVs

`EvictionEventRecorder` (`kinotic-persistence`, gated on the `eviction-tracking` Spring
profile) appends one line per eviction to `kinotic.cache.eviction.csv.path`. The Helm chart
sets that to `<evictionTracking.mountPath>/evictions-${POD_NAME}.csv` and backs the mount
with a `hostPath` — which in KinD is a path inside the node *container*, not on your
machine. Getting the files where `K8S_EVICTION_DATA_PATH` can see them needs either a KinD
`extra_mounts` entry for `evictionTracking.hostPath` or a `kubectl cp` step; neither exists
today.

`EvictionEvent.toCsvLine()` writes `timestamp,cacheName,key,cause` with no header row:

```csv
1704384000000,entityServiceCache,myapp.vehicle,EXPLICIT
```

`cacheName` is whatever the builder was given via `.name(...)` — `entityServiceCache`,
`processedEvictions`, and so on — or `unnamed` when the builder set none.

`cause` is a Caffeine `RemovalCause`: `EXPLICIT`, `REPLACED`, `EXPIRED`, `SIZE`, or
`COLLECTED`. An entity-definition change produces `EXPLICIT`.

`eviction-utils.ts` parses and asserts over these files — `readEvictionFiles`,
`waitForEvictions`, `summarizeEvictions`, `filterByStructureId`, `clearEvictionFiles`,
`assertEvictionsOnPods`.

## Troubleshooting

**Suite does not run** — it is disabled; see "Status" above.

**No pods discovered** — the label selector and namespace defaults do not match the KinD
deployment. Check with `kubectl get pods -n kinotic -l app=kinotic`.

**No eviction files** — confirm the profile is active
(`kubectl logs <pod> | grep eviction-tracking`) and that the CSVs exist in the pod
(`kubectl exec <pod> -- ls -la /eviction-data/`) before looking at the host path.

**Evictions do not propagate** — check Ignite formed a 3-node cluster:
`kubectl logs <pod> -n kinotic | grep "Topology snapshot"`.
