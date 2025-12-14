#!/usr/bin/env bash
#
# KinD Cluster Management Tool for Structures Testing
# Provides commands to create, manage, and clean up KinD clusters for local development
#

set -euo pipefail

# Script directory and library path
readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly LIB_DIR="${SCRIPT_DIR}/lib"

# Source library functions
# shellcheck source=dev-tools/kind/lib/logging.sh
source "${LIB_DIR}/logging.sh"
# shellcheck source=dev-tools/kind/lib/config.sh
source "${LIB_DIR}/config.sh"
# shellcheck source=dev-tools/kind/lib/prerequisites.sh
source "${LIB_DIR}/prerequisites.sh"
# shellcheck source=dev-tools/kind/lib/cluster.sh
source "${LIB_DIR}/cluster.sh"
# shellcheck source=dev-tools/kind/lib/deploy.sh
source "${LIB_DIR}/deploy.sh"
# shellcheck source=dev-tools/kind/lib/images.sh
source "${LIB_DIR}/images.sh"

# Exit codes
readonly EXIT_SUCCESS=0
readonly EXIT_GENERAL_ERROR=1
readonly EXIT_PREREQUISITES_NOT_MET=2
readonly EXIT_CLUSTER_OPERATION_FAILED=3
readonly EXIT_DEPLOYMENT_FAILED=4
readonly EXIT_UNSAFE_OPERATION_BLOCKED=5
readonly EXIT_USER_CANCELLED=130

# Global flags
DRY_RUN="${DRY_RUN:-0}"
VERBOSE="${VERBOSE:-0}"

#
# Print usage information
#
usage() {
    cat <<EOF
KinD Cluster Management Tool for Structures Testing

Usage: $(basename "$0") <subcommand> [options]

Subcommands:
  create    Create a new KinD cluster
  deploy    Deploy structures-server and dependencies
  build     Build structures-server Docker image
  load      Load Docker image into cluster
  status    Display cluster and deployment status
  delete    Delete the KinD cluster
  logs      Display logs from structures-server
  help      Show this help message

Global Options:
  --verbose, -v       Enable verbose logging
  --dry-run           Print commands without executing
  --help, -h          Show this help message

Environment Variables:
  KIND_CLUSTER_NAME        Cluster name (default: structures-cluster)
  KIND_CONFIG_PATH         Path to kind-config.yaml
  HELM_VALUES_PATH         Path to helm-values.yaml
  HELM_CHART_PATH          Path to structures Helm chart
  VERBOSE                  Enable verbose output (0/1)
  DRY_RUN                  Dry run mode (0/1)
  SKIP_CHECKS              Skip prerequisite checks (0/1)
  DEPLOY_DEPS              Deploy dependencies (0/1, default: 1)
  DEPLOY_OBSERVABILITY     Deploy observability stack (0/1, default: 0)

Examples:
  # Create a cluster with defaults
  $(basename "$0") create

  # Deploy structures-server with dependencies
  $(basename "$0") deploy --with-deps

  # Build and load local image
  $(basename "$0") build --load

  # Check cluster status
  $(basename "$0") status

  # Delete cluster
  $(basename "$0") delete

For subcommand-specific help:
  $(basename "$0") <subcommand> --help

EOF
}

#
# Parse global options
# Args:
#   $@: All command-line arguments
# Sets:
#   VERBOSE, DRY_RUN global variables
#   Remaining arguments in ARGS array
#
parse_global_options() {
    ARGS=()
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --verbose|-v)
                VERBOSE=1
                export VERBOSE
                shift
                ;;
            --dry-run)
                DRY_RUN=1
                export DRY_RUN
                shift
                ;;
            --help|-h)
                usage
                exit "${EXIT_SUCCESS}"
                ;;
            *)
                ARGS+=("$1")
                shift
                ;;
        esac
    done
}

#
# Execute command (or print if dry-run)
# Args:
#   $@: Command and arguments
# Returns:
#   Command exit code (or 0 if dry-run)
#
execute() {
    log_command "$@"
    
    if [[ "${DRY_RUN}" == "1" ]]; then
        info "[DRY RUN] Would execute: $*"
        return 0
    fi
    
    "$@"
}

#
# Placeholder subcommand implementations
# These will be implemented in subsequent phases
#

cmd_create() {
    local force=false
    local config_override=""
    local k8s_version_override=""
    
    # Parse subcommand options
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --name)
                CLUSTER_NAME="$2"
                shift 2
                ;;
            --config)
                config_override="$2"
                shift 2
                ;;
            --k8s-version)
                k8s_version_override="$2"
                shift 2
                ;;
            --force)
                force=true
                shift
                ;;
            --skip-checks)
                SKIP_CHECKS=1
                shift
                ;;
            --help|-h)
                cat <<EOF
Create a new KinD cluster for structures testing

Usage: $(basename "$0") create [options]

Options:
  --name <name>            Cluster name (default: structures-cluster)
  --config <path>          Path to kind-config.yaml (default: config/kind-config.yaml)
  --k8s-version <version>  Kubernetes version (default: latest)
  --force                  Recreate if cluster exists
  --skip-checks            Skip prerequisite checks
  --help, -h               Show this help message

Examples:
  # Create cluster with defaults
  $(basename "$0") create

  # Create with custom name
  $(basename "$0") create --name test-cluster

  # Recreate existing cluster
  $(basename "$0") create --force

  # Use specific Kubernetes version
  $(basename "$0") create --k8s-version v1.28.0

EOF
                return "${EXIT_SUCCESS}"
                ;;
            *)
                error "Unknown option: $1"
                echo "Use --help for usage information"
                return "${EXIT_GENERAL_ERROR}"
                ;;
        esac
    done
    
    # Use overrides if provided
    [[ -n "${config_override}" ]] && KIND_CONFIG_PATH="${config_override}"
    [[ -n "${k8s_version_override}" ]] && K8S_VERSION="${k8s_version_override}"
    
    # Check prerequisites (unless skipped)
    if [[ "${SKIP_CHECKS}" != "1" ]]; then
        check_prerequisites || return $?
    fi
    
    # Validate configuration files exist
    if ! validate_config; then
        return "${EXIT_GENERAL_ERROR}"
    fi
    
    # Check if cluster already exists
    if cluster_exists "${CLUSTER_NAME}"; then
        if [[ "${force}" == "true" ]]; then
            warning "Cluster '${CLUSTER_NAME}' already exists. Deleting..."
            delete_cluster "${CLUSTER_NAME}" "true" || return $?
            blank_line
        else
            error "Cluster '${CLUSTER_NAME}' already exists"
            echo ""
            echo "Options:"
            echo "  - Use --force to recreate: $(basename "$0") create --force"
            echo "  - Use different name: $(basename "$0") create --name other-cluster"
            echo "  - Delete first: $(basename "$0") delete"
            echo ""
            return "${EXIT_UNSAFE_OPERATION_BLOCKED}"
        fi
    fi
    
    # Create the cluster
    section "Creating KinD Cluster"
    print_config_summary
    blank_line
    
    if ! create_cluster "${CLUSTER_NAME}" "${KIND_CONFIG_PATH}" "${K8S_VERSION}"; then
        error "Failed to create cluster"
        return "${EXIT_CLUSTER_OPERATION_FAILED}"
    fi
    
    # Wait for cluster to be ready
    if ! wait_for_cluster_ready "${CLUSTER_NAME}"; then
        error "Cluster creation timed out"
        return "${EXIT_CLUSTER_OPERATION_FAILED}"
    fi
    
    # Display cluster information
    success "Cluster created successfully!"
    display_cluster_info "${CLUSTER_NAME}"
    
    # Show next steps
    section "Next Steps"
    progress "Deploy structures-server: $(basename "$0") deploy"
    progress "Check status: $(basename "$0") status"
    progress "View logs: $(basename "$0") logs"
    blank_line
    
    return "${EXIT_SUCCESS}"
}

cmd_deploy() {
    local deploy_deps_override=""
    local deploy_observability_override=""
    local helm_values_override=""
    local helm_sets=()
    local wait_timeout_override=""
    
    # Parse subcommand options
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --name)
                CLUSTER_NAME="$2"
                shift 2
                ;;
            --chart)
                HELM_CHART_PATH="$2"
                shift 2
                ;;
            --values)
                helm_values_override="$2"
                shift 2
                ;;
            --set)
                helm_sets+=("--set" "$2")
                shift 2
                ;;
            --with-deps)
                deploy_deps_override="1"
                shift
                ;;
            --no-deps)
                deploy_deps_override="0"
                shift
                ;;
            --with-observability)
                deploy_observability_override="1"
                shift
                ;;
            --wait-timeout)
                wait_timeout_override="$2"
                shift 2
                ;;
            --help|-h)
                cat <<EOF
Deploy structures-server and dependencies to the KinD cluster

Usage: $(basename "$0") deploy [options]

Options:
  --name <name>            Cluster name (default: structures-cluster)
  --chart <path>           Path to Helm chart (default: ./helm/structures)
  --values <path>          Path to values file (default: config/helm-values.yaml)
  --set <key=value>        Override Helm values (can be used multiple times)
  --with-deps              Deploy dependencies (Elasticsearch, Keycloak) - default
  --no-deps                Skip dependencies (deploy only structures-server)
  --with-observability     Deploy observability stack (OTEL, Prometheus, Grafana)
  --wait-timeout <duration> Deployment timeout (default: 5m)
  --help, -h               Show this help message

Examples:
  # Deploy with all dependencies
  $(basename "$0") deploy

  # Deploy without dependencies (assumes already deployed)
  $(basename "$0") deploy --no-deps

  # Deploy with observability stack
  $(basename "$0") deploy --with-observability

  # Deploy with custom values
  $(basename "$0") deploy --values my-values.yaml

  # Deploy with inline override
  $(basename "$0") deploy --set replicaCount=3

EOF
                return "${EXIT_SUCCESS}"
                ;;
            *)
                error "Unknown option: $1"
                echo "Use --help for usage information"
                return "${EXIT_GENERAL_ERROR}"
                ;;
        esac
    done
    
    # Apply overrides
    [[ -n "${helm_values_override}" ]] && HELM_VALUES_PATH="${helm_values_override}"
    [[ -n "${deploy_deps_override}" ]] && DEPLOY_DEPS="${deploy_deps_override}"
    [[ -n "${deploy_observability_override}" ]] && DEPLOY_OBSERVABILITY="${deploy_observability_override}"
    [[ -n "${wait_timeout_override}" ]] && DEPLOY_TIMEOUT="${wait_timeout_override}"
    
    # Verify cluster exists
    if ! cluster_exists "${CLUSTER_NAME}"; then
        error "Cluster '${CLUSTER_NAME}' does not exist"
        echo ""
        echo "Create cluster first: $(basename "$0") create"
        echo ""
        return "${EXIT_CLUSTER_OPERATION_FAILED}"
    fi
    
    # Set kubectl context to the cluster
    local context="kind-${CLUSTER_NAME}"
    if ! kubectl config use-context "${context}" &>/dev/null; then
        error "Failed to set kubectl context to '${context}'"
        return "${EXIT_CLUSTER_OPERATION_FAILED}"
    fi
    
    # Verify cluster is accessible
    if ! kubectl cluster-info --context "${context}" &>/dev/null; then
        error "Cluster '${CLUSTER_NAME}' is not accessible"
        return "${EXIT_CLUSTER_OPERATION_FAILED}"
    fi
    
    section "Verifying Cluster"
    success "Cluster: ${CLUSTER_NAME}"
    success "Context: ${context}"
    blank_line
    
    # Add Helm repositories
    if ! add_helm_repos; then
        return "${EXIT_DEPLOYMENT_FAILED}"
    fi
    blank_line
    
    # Deploy NGINX Ingress Controller
    section "Deploying NGINX Ingress Controller"
    if ! deploy_nginx_ingress "${CLUSTER_NAME}"; then
        return "${EXIT_DEPLOYMENT_FAILED}"
    fi
    blank_line
    
    # Deploy dependencies if requested
    if [[ "${DEPLOY_DEPS}" == "1" ]]; then
        section "Deploying Dependencies"
        
        # Deploy Elasticsearch
        if ! deploy_elasticsearch "${CLUSTER_NAME}"; then
            return "${EXIT_DEPLOYMENT_FAILED}"
        fi
        
        # Deploy PostgreSQL
        if ! deploy_postgresql "${CLUSTER_NAME}"; then
            return "${EXIT_DEPLOYMENT_FAILED}"
        fi
        
        # Create Keycloak realm ConfigMap
        if ! create_keycloak_realm_configmap "${CLUSTER_NAME}"; then
            return "${EXIT_DEPLOYMENT_FAILED}"
        fi
        
        # Deploy Keycloak
        if ! deploy_keycloak "${CLUSTER_NAME}"; then
            return "${EXIT_DEPLOYMENT_FAILED}"
        fi
        
        blank_line
    fi
    
    # Deploy observability stack if requested
    if [[ "${DEPLOY_OBSERVABILITY}" == "1" ]]; then
        section "Deploying Observability Stack"
        warning "Observability stack deployment not yet fully implemented"
        warning "This will be completed in a future update"
        blank_line
    fi
    
    # Deploy structures-server
    section "Deploying structures-server"
    
    # Build additional sets string
    local additional_sets=""
    if [[ ${#helm_sets[@]} -gt 0 ]]; then
        additional_sets="${helm_sets[*]}"
    fi

    # Build and loadstructures-server 
    cmd_build "--load"
    
    if ! deploy_structures_server "${CLUSTER_NAME}" "${additional_sets}"; then
        error "Deployment failed"
        echo ""
        echo "Troubleshooting:"
        echo "  - Check pod status: kubectl --context ${context} get pods"
        echo "  - Check logs: kubectl --context ${context} logs -l app=structures-server"
        echo "  - Check events: kubectl --context ${context} get events --sort-by='.lastTimestamp'"
        echo ""
        return "${EXIT_DEPLOYMENT_FAILED}"
    fi
    
    # Display deployment status
    success "Deployment successful!"
    display_deployment_status "${CLUSTER_NAME}"
    
    # Show next steps
    section "Next Steps"
    progress "Login to Structures: http://localhost:9090/login"
    progress "Check logs: $(basename "$0") logs --follow"
    progress "Check status: $(basename "$0") status"
    progress "Run integration tests: ./gradlew :structures-core:integrationTest"
    blank_line
    
    return "${EXIT_SUCCESS}"
}

cmd_build() {
    local module="structures-server"
    local auto_load=false
    
    # Parse subcommand options
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --module)
                module="$2"
                shift 2
                ;;
            --load)
                auto_load=true
                shift
                ;;
            --help|-h)
                cat <<EOF
Build Docker image for structures-server using Gradle bootBuildImage

Usage: $(basename "$0") build [options]

Options:
  --module <name>      Gradle module to build (default: structures-server)
  --load               Load image into cluster after build
  --help, -h           Show this help message

Examples:
  # Build image
  $(basename "$0") build

  # Build and load into cluster
  $(basename "$0") build --load

  # Build specific module
  $(basename "$0") build --module structures-server

EOF
                return "${EXIT_SUCCESS}"
                ;;
            *)
                error "Unknown option: $1"
                echo "Use --help for usage information"
                return "${EXIT_GENERAL_ERROR}"
                ;;
        esac
    done
    
    section "Building ${module}"
    
    # Build the image
    if ! build_image "${module}"; then
        return "${EXIT_GENERAL_ERROR}"
    fi
    
    # Get image name
    local image_name
    image_name=$(get_image_name) || return "${EXIT_GENERAL_ERROR}"
    
    # Display image info
    get_image_info "${image_name}"
    
    # Auto-load if requested
    if [[ "${auto_load}" == "true" ]]; then
        blank_line
        section "Loading Image into Cluster"
        if ! load_image_into_cluster "${CLUSTER_NAME}" "${image_name}"; then
            return "${EXIT_GENERAL_ERROR}"
        fi
    else
        display_deployment_instructions "${image_name}"
    fi
    
    return "${EXIT_SUCCESS}"
}

cmd_load() {
    local image_override=""
    
    # Parse subcommand options
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --name)
                CLUSTER_NAME="$2"
                shift 2
                ;;
            --image)
                image_override="$2"
                shift 2
                ;;
            --help|-h)
                cat <<EOF
Load Docker image into KinD cluster nodes

Usage: $(basename "$0") load [options]

Options:
  --name <name>        Cluster name (default: structures-cluster)
  --image <name>       Full image name (default: from gradle.properties)
  --help, -h           Show this help message

Examples:
  # Load default image
  $(basename "$0") load

  # Load specific image
  $(basename "$0") load --image kinotic/structures-server:0.5.0

  # Load into specific cluster
  $(basename "$0") load --name test-cluster

EOF
                return "${EXIT_SUCCESS}"
                ;;
            *)
                error "Unknown option: $1"
                echo "Use --help for usage information"
                return "${EXIT_GENERAL_ERROR}"
                ;;
        esac
    done
    
    # Get image name
    local image_name
    if [[ -n "${image_override}" ]]; then
        image_name="${image_override}"
    else
        image_name=$(get_image_name) || return "${EXIT_GENERAL_ERROR}"
    fi
    
    section "Loading Image"
    
    # Load the image
    if ! load_image_into_cluster "${CLUSTER_NAME}" "${image_name}"; then
        return "${EXIT_GENERAL_ERROR}"
    fi
    
    display_deployment_instructions "${image_name}"
    
    return "${EXIT_SUCCESS}"
}

cmd_status() {
    local watch=false
    
    # Parse subcommand options
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --name)
                CLUSTER_NAME="$2"
                shift 2
                ;;
            --watch|-w)
                watch=true
                shift
                ;;
            --help|-h)
                cat <<EOF
Display cluster and deployment status

Usage: $(basename "$0") status [options]

Options:
  --name <name>     Cluster name (default: structures-cluster)
  --watch, -w       Watch status updates (refresh every 2 seconds)
  --help, -h        Show this help message

Examples:
  # Show status
  $(basename "$0") status

  # Watch status updates
  $(basename "$0") status --watch

EOF
                return "${EXIT_SUCCESS}"
                ;;
            *)
                error "Unknown option: $1"
                return "${EXIT_GENERAL_ERROR}"
                ;;
        esac
    done
    
    if [[ "${watch}" == "true" ]]; then
        while true; do
            clear
            get_cluster_status "${CLUSTER_NAME}" || true
            echo ""
            echo "Refreshing every 2 seconds... (Ctrl+C to stop)"
            sleep 2
        done
    else
        get_cluster_status "${CLUSTER_NAME}"
    fi
}

cmd_delete() {
    local force=false
    
    # Parse subcommand options
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --name)
                CLUSTER_NAME="$2"
                shift 2
                ;;
            --force|-f)
                force=true
                shift
                ;;
            --help|-h)
                cat <<EOF
Delete the KinD cluster and all resources

Usage: $(basename "$0") delete [options]

Options:
  --name <name>     Cluster name (default: structures-cluster)
  --force, -f       Skip confirmation prompt
  --help, -h        Show this help message

Examples:
  # Delete with confirmation
  $(basename "$0") delete

  # Delete without confirmation
  $(basename "$0") delete --force

EOF
                return "${EXIT_SUCCESS}"
                ;;
            *)
                error "Unknown option: $1"
                return "${EXIT_GENERAL_ERROR}"
                ;;
        esac
    done
    
    # Verify cluster exists
    if ! cluster_exists "${CLUSTER_NAME}"; then
        warning "Cluster '${CLUSTER_NAME}' does not exist"
        return "${EXIT_SUCCESS}"
    fi
    
    # Set kubectl context to the cluster
    local context="kind-${CLUSTER_NAME}"
    if ! kubectl config use-context "${context}" &>/dev/null; then
        error "Failed to set kubectl context to '${context}'"
        return "${EXIT_CLUSTER_OPERATION_FAILED}"
    fi
    
    # Safety check: verify kubectl context
    if ! verify_safe_context "${CLUSTER_NAME}"; then
        error "Refusing to delete - context verification failed"
        return "${EXIT_UNSAFE_OPERATION_BLOCKED}"
    fi
    
    # Delete cluster
    if [[ "${force}" == "true" ]]; then
        delete_cluster "${CLUSTER_NAME}" "true"
    else
        delete_cluster "${CLUSTER_NAME}" "false"
    fi
}

cmd_logs() {
    local follow=false
    local tail="100"
    local selector="app=structures-server"
    
    # Parse subcommand options
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --name)
                CLUSTER_NAME="$2"
                shift 2
                ;;
            --follow|-f)
                follow=true
                shift
                ;;
            --tail)
                tail="$2"
                shift 2
                ;;
            --selector|-l)
                selector="$2"
                shift 2
                ;;
            --help|-h)
                cat <<EOF
Display logs from structures-server pods

Usage: $(basename "$0") logs [options]

Options:
  --name <name>         Cluster name (default: structures-cluster)
  --follow, -f          Follow log output
  --tail <n>            Show last N lines (default: 100)
  --selector, -l <sel>  Pod selector (default: app=structures-server)
  --help, -h            Show this help message

Examples:
  # Show last 100 lines
  $(basename "$0") logs

  # Follow logs
  $(basename "$0") logs --follow

  # Show last 500 lines
  $(basename "$0") logs --tail 500

EOF
                return "${EXIT_SUCCESS}"
                ;;
            *)
                error "Unknown option: $1"
                return "${EXIT_GENERAL_ERROR}"
                ;;
        esac
    done
    
    if ! cluster_exists "${CLUSTER_NAME}"; then
        error "Cluster '${CLUSTER_NAME}' does not exist"
        return "${EXIT_CLUSTER_OPERATION_FAILED}"
    fi
    
    # Set kubectl context to the cluster
    local context="kind-${CLUSTER_NAME}"
    if ! kubectl config use-context "${context}" &>/dev/null; then
        error "Failed to set kubectl context to '${context}'"
        return "${EXIT_CLUSTER_OPERATION_FAILED}"
    fi
    
    info "Streaming logs from pods matching: ${selector}"
    if [[ "${follow}" == "true" ]]; then
        progress "Following log output (Ctrl+C to stop)..."
    else
        progress "Showing last ${tail} lines..."
    fi
    blank_line
    
    stream_logs "${CLUSTER_NAME}" "${selector}" "${follow}" "${tail}"
}

#
# Main entry point
#
main() {
    # Parse global options
    parse_global_options "$@"
    set -- "${ARGS[@]}"
    
    # Require subcommand
    if [[ $# -eq 0 ]]; then
        error "No subcommand specified"
        echo ""
        usage
        exit "${EXIT_GENERAL_ERROR}"
    fi
    
    local subcommand="$1"
    shift
    
    # Load configuration
    load_config
    
    # Route to subcommand
    case "${subcommand}" in
        create)
            cmd_create "$@"
            ;;
        deploy)
            cmd_deploy "$@"
            ;;
        build)
            cmd_build "$@"
            ;;
        load)
            cmd_load "$@"
            ;;
        status)
            cmd_status "$@"
            ;;
        delete)
            cmd_delete "$@"
            ;;
        logs)
            cmd_logs "$@"
            ;;
        help|--help|-h)
            usage
            exit "${EXIT_SUCCESS}"
            ;;
        *)
            error "Unknown subcommand: ${subcommand}"
            echo ""
            usage
            exit "${EXIT_GENERAL_ERROR}"
            ;;
    esac
}

# Run main if executed directly (not sourced)
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    main "$@"
fi

