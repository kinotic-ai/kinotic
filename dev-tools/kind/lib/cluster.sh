#!/usr/bin/env bash
#
# Cluster Lifecycle Management Functions for KinD
# Handles cluster creation, deletion, and status checking
#

# Prevent re-sourcing
[[ -n "${_KIND_CLUSTER_LOADED:-}" ]] && return 0
readonly _KIND_CLUSTER_LOADED=1

# Source dependencies
LIB_SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=dev-tools/kind/lib/logging.sh
source "${LIB_SCRIPT_DIR}/logging.sh"
# shellcheck source=dev-tools/kind/lib/config.sh
source "${LIB_SCRIPT_DIR}/config.sh"

#
# Check if cluster exists
# Args:
#   $1: Cluster name
# Returns:
#   0 if exists, 1 otherwise
# Example:
#   cluster_exists "structures-cluster" && echo "exists"
#
cluster_exists() {
    local cluster_name="$1"
    kind get clusters 2>/dev/null | grep -q "^${cluster_name}$"
}

#
# Verify kubectl context is safe (points to KinD cluster)
# Args:
#   $1: Cluster name
# Returns:
#   0 if safe, EXIT_UNSAFE_OPERATION_BLOCKED otherwise
# Example:
#   verify_safe_context "structures-cluster" || exit $?
#
verify_safe_context() {
    local cluster_name="$1"
    local expected_context="kind-${cluster_name}"
    local current_context
    
    current_context=$(kubectl config current-context 2>/dev/null || echo "")
    
    if [[ -z "${current_context}" ]]; then
        error "No kubectl context is set"
        return "${EXIT_UNSAFE_OPERATION_BLOCKED:-5}"
    fi
    
    if [[ "${current_context}" != "${expected_context}" ]]; then
        error "Current kubectl context '${current_context}' does not match expected context '${expected_context}'"
        echo ""
        echo "Details:"
        echo "  This operation requires the kubectl context to be set to a KinD cluster."
        echo "  Current context: ${current_context}"
        echo "  Expected context: ${expected_context}"
        echo ""
        echo "Remediation:"
        echo "  - Switch to KinD context: kubectl config use-context ${expected_context}"
        echo "  - Or recreate the cluster: $0 create --force"
        echo ""
        return "${EXIT_UNSAFE_OPERATION_BLOCKED:-5}"
    fi
    
    verbose "Context verified: ${current_context}"
    return 0
}

#
# Create KinD cluster
# Args:
#   $1: Cluster name
#   $2: Config file path
#   $3: K8s version (optional)
# Returns:
#   0 on success, EXIT_CLUSTER_OPERATION_FAILED otherwise
# Example:
#   create_cluster "structures-cluster" "config/kind-config.yaml" "v1.28.0"
#
create_cluster() {
    local cluster_name="$1"
    local config_file="$2"
    local k8s_version="${3:-}"
    
    info "Creating KinD cluster '${cluster_name}'..."
    
    local cmd=(kind create cluster --name "${cluster_name}" --config "${config_file}")
    
    if [[ -n "${k8s_version}" && "${k8s_version}" != "latest" ]]; then
        cmd+=(--image "kindest/node:${k8s_version}")
        progress "Kubernetes version: ${k8s_version}"
    fi
    
    # Execute kind create cluster
    if ! execute "${cmd[@]}" 2>&1 | while IFS= read -r line; do
        progress "${line}"
    done; then
        error "Failed to create KinD cluster"
        return "${EXIT_CLUSTER_OPERATION_FAILED:-3}"
    fi
    
    return 0
}

#
# Wait for cluster to be ready
# Args:
#   $1: Cluster name
#   $2: Timeout in seconds (default: 600)
# Returns:
#   0 if ready, EXIT_CLUSTER_OPERATION_FAILED otherwise
# Example:
#   wait_for_cluster_ready "structures-cluster" 600
#
wait_for_cluster_ready() {
    local cluster_name="$1"
    local timeout="${2:-600}"
    local elapsed=0
    local interval=5
    
    info "Waiting for cluster to be ready..."
    
    while [[ ${elapsed} -lt ${timeout} ]]; do
        # Check if all nodes are Ready
        if kubectl get nodes --context "kind-${cluster_name}" 2>/dev/null | grep -q "Ready"; then
            # Count ready nodes - use awk to avoid whitespace issues
            local ready_nodes
            ready_nodes=$(kubectl get nodes --context "kind-${cluster_name}" --no-headers 2>/dev/null | awk '$2 == "Ready" {count++} END {print count+0}')
            
            if [[ ${ready_nodes} -gt 0 ]]; then
                local total_nodes
                total_nodes=$(kubectl get nodes --context "kind-${cluster_name}" --no-headers 2>/dev/null | wc -l | tr -d ' ' | tr -d '\n')
                
                if [[ ${ready_nodes} -eq ${total_nodes} ]]; then
                    success "All nodes ready (${ready_nodes}/${total_nodes})"
                    return 0
                else
                    progress "Nodes ready: ${ready_nodes}/${total_nodes}"
                fi
            fi
        fi
        
        sleep ${interval}
        elapsed=$((elapsed + interval))
    done
    
    error "Cluster did not become ready within ${timeout} seconds"
    return "${EXIT_CLUSTER_OPERATION_FAILED:-3}"
}

#
# Display cluster information
# Args:
#   $1: Cluster name
# Example:
#   display_cluster_info "structures-cluster"
#
display_cluster_info() {
    local cluster_name="$1"
    local context="kind-${cluster_name}"
    
    blank_line
    section "Cluster Information"
    
    # Cluster name and context
    progress "Cluster Name: ${cluster_name}"
    progress "Context: ${context}"
    
    # Kubernetes version
    local k8s_version
    k8s_version=$(kubectl version --context "${context}" --short 2>/dev/null | grep "Server Version" | awk '{print $3}' || echo "unknown")
    progress "Kubernetes: ${k8s_version}"
    
    # API server
    local api_server
    api_server=$(kubectl cluster-info --context "${context}" 2>/dev/null | grep "Kubernetes control plane" | grep -oE 'https://[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+:[0-9]+' || echo "unknown")
    progress "API Server: ${api_server}"
    
    # Nodes
    blank_line
    section "Nodes"
    kubectl get nodes --context "${context}" 2>/dev/null | while IFS= read -r line; do
        if [[ "${line}" != "NAME"* ]]; then
            local node_name
            node_name=$(echo "${line}" | awk '{print $1}')
            local status
            status=$(echo "${line}" | awk '{print $2}')
            local roles
            roles=$(echo "${line}" | awk '{print $3}')
            
            if [[ "${status}" == "Ready" ]]; then
                success "${node_name} (${roles})"
            else
                warning "${node_name} (${roles}) - ${status}"
            fi
        fi
    done
    
    blank_line
}

#
# Delete KinD cluster
# Args:
#   $1: Cluster name
#   $2: Force flag (skip confirmation)
# Returns:
#   0 on success, EXIT_CLUSTER_OPERATION_FAILED otherwise, EXIT_USER_CANCELLED if user cancels
# Example:
#   delete_cluster "structures-cluster" "true"
#
delete_cluster() {
    local cluster_name="$1"
    local force="${2:-false}"
    
    if ! cluster_exists "${cluster_name}"; then
        warning "Cluster '${cluster_name}' does not exist"
        return 0
    fi
    
    # Confirm deletion unless --force
    if [[ "${force}" != "true" ]]; then
        warning "About to delete cluster '${cluster_name}'"
        echo "   This will remove all pods, deployments, and data."
        echo ""
        read -rp "   Continue? [y/N]: " response
        echo ""
        
        if [[ ! "${response}" =~ ^[Yy]$ ]]; then
            info "Deletion cancelled by user"
            return "${EXIT_USER_CANCELLED:-130}"
        fi
    fi
    
    info "Deleting cluster '${cluster_name}'..."
    
    if ! execute kind delete cluster --name "${cluster_name}" 2>&1 | while IFS= read -r line; do
        progress "${line}"
    done; then
        error "Failed to delete cluster"
        return "${EXIT_CLUSTER_OPERATION_FAILED:-3}"
    fi
    
    success "Cluster '${cluster_name}' deleted successfully!"
    return 0
}

#
# Get cluster status
# Args:
#   $1: Cluster name
# Returns:
#   0 if cluster running, 1 otherwise
# Example:
#   get_cluster_status "structures-cluster"
#
get_cluster_status() {
    local cluster_name="$1"
    local context="kind-${cluster_name}"
    
    if ! cluster_exists "${cluster_name}"; then
        error "Cluster '${cluster_name}' does not exist"
        return 1
    fi
    
    section "Cluster: ${cluster_name}"
    
    # Check if cluster is accessible
    if kubectl cluster-info --context "${context}" &>/dev/null; then
        success "Status: Running"
        progress "Context: ${context}"
        
        # API server
        local api_server
        api_server=$(kubectl cluster-info --context "${context}" 2>/dev/null | grep "Kubernetes control plane" | grep -oE 'https://[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+:[0-9]+' || echo "unknown")
        progress "API Server: ${api_server}"
        
        blank_line
        section "Nodes"
        kubectl get nodes --context "${context}" --no-headers 2>/dev/null | while IFS= read -r line; do
            local node_name
            node_name=$(echo "${line}" | awk '{print $1}')
            local status
            status=$(echo "${line}" | awk '{print $2}')
            local roles
            roles=$(echo "${line}" | awk '{print $3}')
            
            if [[ "${status}" == "Ready" ]]; then
                success "${node_name} (${roles}) - Ready"
            else
                warning "${node_name} (${roles}) - ${status}"
            fi
        done
    else
        error "Status: Not accessible"
        return 1
    fi
    
    return 0
}

#
# Stream logs from pods
# Args:
#   $1: Cluster name
#   $2: Pod selector (e.g., "app=structures-server")
#   $3: Follow flag (true/false)
#   $4: Tail lines (number)
# Example:
#   stream_logs "structures-cluster" "app=structures-server" "true" "100"
#
stream_logs() {
    local cluster_name="$1"
    local selector="$2"
    local follow="${3:-false}"
    local tail="${4:-100}"
    local context="kind-${cluster_name}"
    
    local cmd=(kubectl logs --context "${context}" -l "${selector}" --tail="${tail}")
    
    if [[ "${follow}" == "true" ]]; then
        cmd+=(--follow)
    fi
    
    execute "${cmd[@]}"
}

