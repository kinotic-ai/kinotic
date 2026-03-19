#!/usr/bin/env bash
#
# Node Maintenance / Update Simulation for KinD clusters
# Simulates node lifecycle events: cordon → drain → restart → wait → uncordon
#

# Prevent re-sourcing
[[ -n "${_KIND_NODE_UPDATE_LOADED:-}" ]] && return 0
readonly _KIND_NODE_UPDATE_LOADED=1

# Source dependencies
LIB_SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=dev-tools/kind/lib/logging.sh
source "${LIB_SCRIPT_DIR}/logging.sh"
# shellcheck source=dev-tools/kind/lib/cluster.sh
source "${LIB_SCRIPT_DIR}/cluster.sh"

#
# Get target node names for simulation.
# Defaults to worker nodes only unless include_control_plane=true or explicit nodes provided.
#
# Args:
#   $1: kubectl context (e.g., kind-structures-cluster)
#   $2: include_control_plane ("true"/"false")
#   $@: explicit node names (optional, starting at $3)
# Outputs:
#   newline-delimited list of node names
#
get_target_nodes() {
    local context="$1"
    local include_control_plane="${2:-false}"
    shift 2 || true

    # If explicit nodes provided, return them as-is
    if [[ $# -gt 0 ]]; then
        for n in "$@"; do
            echo "${n}"
        done
        return 0
    fi

    # Otherwise derive from cluster nodes
    kubectl get nodes --context "${context}" --no-headers 2>/dev/null | while IFS= read -r line; do
        # NAME STATUS ROLES AGE VERSION
        local node_name
        node_name="$(echo "${line}" | awk '{print $1}')"
        local roles
        roles="$(echo "${line}" | awk '{print $3}')"

        # If roles column is missing/shifted, fall back to grep for control-plane keywords
        if [[ -z "${roles}" ]]; then
            roles="$(echo "${line}" | awk '{print $0}')"
        fi

        if echo "${roles}" | grep -Eq '(control-plane|master)'; then
            if [[ "${include_control_plane}" == "true" ]]; then
                echo "${node_name}"
            fi
        else
            echo "${node_name}"
        fi
    done
}

cordon_node() {
    local context="$1"
    local node="$2"
    progress "Cordoning node: ${node}"
    execute kubectl cordon "${node}" --context "${context}" >/dev/null
}

uncordon_node() {
    local context="$1"
    local node="$2"
    progress "Uncordoning node: ${node}"
    execute kubectl uncordon "${node}" --context "${context}" >/dev/null
}

drain_node() {
    local context="$1"
    local node="$2"
    local drain_timeout="${3:-5m}"
    local grace_period="${4:-30}"
    local delete_emptydir_data="${5:-true}"

    progress "Draining node: ${node}"

    local cmd=(kubectl drain "${node}" --context "${context}"
        --ignore-daemonsets
        --force
        --grace-period="${grace_period}"
        --timeout="${drain_timeout}"
    )

    if [[ "${delete_emptydir_data}" == "true" ]]; then
        cmd+=(--delete-emptydir-data)
    fi

    # Drain can be noisy; stream output through progress for readability
    if ! execute "${cmd[@]}" 2>&1 | while IFS= read -r line; do
        progress "  ${line}"
    done; then
        error "Failed to drain node: ${node}"
        return 1
    fi

    return 0
}

restart_node_container() {
    local node_container="$1"
    progress "Restarting KinD node container: ${node_container}"
    execute docker restart "${node_container}" >/dev/null
}

wait_for_node_ready() {
    local context="$1"
    local node="$2"
    local timeout="${3:-5m}"

    progress "Waiting for node to become Ready: ${node} (timeout: ${timeout})"
    if ! kubectl wait --context "${context}" --for=condition=Ready "node/${node}" --timeout="${timeout}" >/dev/null 2>&1; then
        warning "Node did not become Ready in time: ${node}"
        kubectl get node "${node}" --context "${context}" 2>/dev/null || true
        return 1
    fi
    return 0
}

#
# Basic validation: ensure Structures deployment/pods are healthy after disruption.
# If Structures isn't deployed, this degrades to a no-op warning.
#
basic_validate_structures() {
    local context="$1"
    local timeout="${2:-5m}"
    local namespace="${3:-default}"

    # Deployment name is Helm release name when using kind tooling: structures-server
    local deploy_name="structures-server"

    if ! kubectl get deployment "${deploy_name}" -n "${namespace}" --context "${context}" >/dev/null 2>&1; then
        warning "Deployment '${deploy_name}' not found in namespace '${namespace}' (skipping app validation)"
        return 0
    fi

    progress "Validating deployment rollout: ${deploy_name}"
    if ! kubectl rollout status deployment/"${deploy_name}" -n "${namespace}" --context "${context}" --timeout="${timeout}" >/dev/null 2>&1; then
        warning "Deployment rollout not healthy: ${deploy_name}"
        kubectl get deployment "${deploy_name}" -n "${namespace}" --context "${context}" 2>/dev/null || true
        kubectl get pods -n "${namespace}" --context "${context}" -l "release=${deploy_name}" 2>/dev/null || true
        return 1
    fi

    # Prefer the release label (present in chart templates)
    progress "Validating pods Ready (label: release=${deploy_name})"
    if ! kubectl wait --for=condition=Ready pod -n "${namespace}" --context "${context}" -l "release=${deploy_name}" --timeout="${timeout}" >/dev/null 2>&1; then
        # Fall back to app label (chart name defaults to 'structures')
        verbose "release label wait failed, falling back to app=structures selector"
        kubectl wait --for=condition=Ready pod -n "${namespace}" --context "${context}" -l "app=structures" --timeout="${timeout}" >/dev/null 2>&1 || true
    fi

    return 0
}

#
# Simulate a node update cycle on a single node.
# Args:
#   $1: cluster name (e.g., structures-cluster)
#   $2: node name (Kubernetes node name; also KinD container name)
#   $3: drain timeout
#   $4: grace period
#   $5: delete emptyDir data ("true"/"false")
#   $6: node-ready timeout
#   $7: validation timeout
#   $8: namespace
#
simulate_node_update_once() {
    local cluster_name="$1"
    local node="$2"
    local drain_timeout="${3:-5m}"
    local grace_period="${4:-30}"
    local delete_emptydir_data="${5:-true}"
    local node_ready_timeout="${6:-5m}"
    local validate_timeout="${7:-5m}"
    local namespace="${8:-default}"

    local context="kind-${cluster_name}"

    section "Node update simulation: ${node}"

    cordon_node "${context}" "${node}"
    drain_node "${context}" "${node}" "${drain_timeout}" "${grace_period}" "${delete_emptydir_data}"

    restart_node_container "${node}"

    # In dry-run mode, skip waits/validation since nothing actually happened.
    if [[ "${DRY_RUN:-0}" == "1" ]]; then
        info "[DRY RUN] Skipping wait/validation for ${node}"
        return 0
    fi

    # Node will come back as SchedulingDisabled after restart; wait for Ready then uncordon.
    wait_for_node_ready "${context}" "${node}" "${node_ready_timeout}"
    uncordon_node "${context}" "${node}"

    basic_validate_structures "${context}" "${validate_timeout}" "${namespace}"
}

