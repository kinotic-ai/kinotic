#!/usr/bin/env bash
#
# Configuration Loading Functions for KinD Cluster Tools
# Handles environment variables, CLI flags, and configuration files
#

# Prevent re-sourcing
[[ -n "${_KIND_CONFIG_LOADED:-}" ]] && return 0
readonly _KIND_CONFIG_LOADED=1

# Source logging functions
LIB_SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=dev-tools/kind/lib/logging.sh
source "${LIB_SCRIPT_DIR}/logging.sh"

# Configuration defaults
readonly DEFAULT_CLUSTER_NAME="structures-cluster"
readonly DEFAULT_CONFIG_DIR="${LIB_SCRIPT_DIR}/../config"
readonly DEFAULT_KIND_CONFIG="${DEFAULT_CONFIG_DIR}/kind-config.yaml"
readonly DEFAULT_HELM_CHART_PATH="./helm/structures"
readonly DEFAULT_K8S_VERSION="latest"
readonly DEFAULT_DEPLOY_TIMEOUT="5m"

# Service-specific config directories (Helm values files)
readonly CONFIG_ELASTICSEARCH_DIR="${DEFAULT_CONFIG_DIR}/elasticsearch"
readonly CONFIG_POSTGRESQL_DIR="${DEFAULT_CONFIG_DIR}/postgresql"
readonly CONFIG_KEYCLOAK_DIR="${DEFAULT_CONFIG_DIR}/keycloak"
readonly CONFIG_INGRESS_NGINX_DIR="${DEFAULT_CONFIG_DIR}/ingress-nginx"
readonly CONFIG_CERT_MANAGER_DIR="${DEFAULT_CONFIG_DIR}/cert-manager"
readonly CONFIG_COREDNS_DIR="${DEFAULT_CONFIG_DIR}/coredns"
readonly CONFIG_STRUCTURES_SERVER_DIR="${DEFAULT_CONFIG_DIR}/structures-server"

# Structures-server Helm values (moved from helm-values.yaml)
readonly DEFAULT_HELM_VALUES="${CONFIG_STRUCTURES_SERVER_DIR}/values.yaml"
readonly DEFAULT_HELM_VALUES_LOCAL="${CONFIG_STRUCTURES_SERVER_DIR}/values.local.yaml"

# Global configuration variables (set by load_config)
CLUSTER_NAME=""
KIND_CONFIG_PATH=""
HELM_VALUES_PATH=""
HELM_CHART_PATH=""
K8S_VERSION=""
SKIP_CHECKS=""
DEPLOY_DEPS="1"
DEPLOY_KEYCLOAK="0"       # Deploy Keycloak + PostgreSQL (default: disabled)
DEPLOY_OBSERVABILITY="0"
DEPLOY_TIMEOUT=""
OIDC_ENABLED="false"      # Enable OIDC in structures-server (set automatically when DEPLOY_KEYCLOAK=1)

#
# Load configuration from environment variables, files, and defaults
# Priority: CLI flags > environment variables > config files > defaults
# Args:
#   None (reads from environment)
# Example:
#   load_config
#
load_config() {
    # Cluster name
    CLUSTER_NAME="${KIND_CLUSTER_NAME:-${DEFAULT_CLUSTER_NAME}}"
    
    # Configuration file paths
    KIND_CONFIG_PATH="${KIND_CONFIG_PATH:-${DEFAULT_KIND_CONFIG}}"
    HELM_VALUES_PATH="${HELM_VALUES_PATH:-${DEFAULT_HELM_VALUES}}"
    HELM_CHART_PATH="${HELM_CHART_PATH:-${DEFAULT_HELM_CHART_PATH}}"
    
    # Kubernetes version
    K8S_VERSION="${K8S_VERSION:-${DEFAULT_K8S_VERSION}}"
    
    # Feature flags
    SKIP_CHECKS="${SKIP_CHECKS:-0}"
    DEPLOY_DEPS="${DEPLOY_DEPS:-1}"
    DEPLOY_KEYCLOAK="${DEPLOY_KEYCLOAK:-0}"
    DEPLOY_OBSERVABILITY="${DEPLOY_OBSERVABILITY:-0}"
    
    # OIDC is enabled when Keycloak is deployed
    if [[ "${DEPLOY_KEYCLOAK}" == "1" ]]; then
        OIDC_ENABLED="true"
    else
        OIDC_ENABLED="false"
    fi
    export OIDC_ENABLED
    
    # Timeouts
    DEPLOY_TIMEOUT="${DEPLOY_TIMEOUT:-${DEFAULT_DEPLOY_TIMEOUT}}"
    
    verbose "Configuration loaded:"
    verbose "  CLUSTER_NAME=${CLUSTER_NAME}"
    verbose "  KIND_CONFIG_PATH=${KIND_CONFIG_PATH}"
    verbose "  HELM_VALUES_PATH=${HELM_VALUES_PATH}"
    verbose "  HELM_CHART_PATH=${HELM_CHART_PATH}"
    verbose "  K8S_VERSION=${K8S_VERSION}"
    verbose "  SKIP_CHECKS=${SKIP_CHECKS}"
    verbose "  DEPLOY_DEPS=${DEPLOY_DEPS}"
    verbose "  DEPLOY_KEYCLOAK=${DEPLOY_KEYCLOAK}"
    verbose "  DEPLOY_OBSERVABILITY=${DEPLOY_OBSERVABILITY}"
    verbose "  OIDC_ENABLED=${OIDC_ENABLED}"
    verbose "  DEPLOY_TIMEOUT=${DEPLOY_TIMEOUT}"
}

#
# Validate required configuration files exist
# Args:
#   None
# Returns:
#   0 if valid, 1 otherwise
# Example:
#   validate_config || exit 1
#
validate_config() {
    local valid=0
    
    # Check KinD config file
    if [[ ! -f "${KIND_CONFIG_PATH}" ]]; then
        error "KinD configuration file not found: ${KIND_CONFIG_PATH}"
        valid=1
    fi
    
    # Check Helm chart path
    if [[ ! -d "${HELM_CHART_PATH}" ]]; then
        error "Helm chart directory not found: ${HELM_CHART_PATH}"
        valid=1
    fi
    
    # Check Helm values file
    if [[ ! -f "${HELM_VALUES_PATH}" ]]; then
        error "Helm values file not found: ${HELM_VALUES_PATH}"
        valid=1
    fi
    
    return ${valid}
}

#
# Get Helm values file path (with local override if exists)
# Returns:
#   Path to Helm values file (may be multiple via -f flags)
# Example:
#   helm install myrelease mychart $(get_helm_values_flags)
#
get_helm_values_flags() {
    local flags="-f ${HELM_VALUES_PATH}"
    
    # Add local overrides if file exists
    if [[ -f "${DEFAULT_HELM_VALUES_LOCAL}" ]]; then
        flags="${flags} -f ${DEFAULT_HELM_VALUES_LOCAL}"
    fi
    
    echo "${flags}"
}

#
# Get service-specific values file path
# Args:
#   $1: Service name (elasticsearch, postgresql, keycloak, ingress-nginx, cert-manager, coredns, structures-server)
# Returns:
#   Path to the service's values.yaml file
# Example:
#   values_file=$(get_service_values_path "elasticsearch")
#
get_service_values_path() {
    local service_name="$1"
    local config_dir=""
    
    case "${service_name}" in
        elasticsearch)
            config_dir="${CONFIG_ELASTICSEARCH_DIR}"
            ;;
        postgresql)
            config_dir="${CONFIG_POSTGRESQL_DIR}"
            ;;
        keycloak)
            config_dir="${CONFIG_KEYCLOAK_DIR}"
            ;;
        ingress-nginx)
            config_dir="${CONFIG_INGRESS_NGINX_DIR}"
            ;;
        cert-manager)
            config_dir="${CONFIG_CERT_MANAGER_DIR}"
            ;;
        coredns)
            config_dir="${CONFIG_COREDNS_DIR}"
            ;;
        structures-server)
            config_dir="${CONFIG_STRUCTURES_SERVER_DIR}"
            ;;
        *)
            error "Unknown service: ${service_name}"
            return 1
            ;;
    esac
    
    echo "${config_dir}/values.yaml"
}

#
# Get service-specific Helm values flags (with local override if exists)
# Args:
#   $1: Service name
# Returns:
#   Helm -f flags for the service's values files
# Example:
#   helm upgrade --install myrelease mychart $(get_service_helm_flags "elasticsearch")
#
get_service_helm_flags() {
    local service_name="$1"
    local values_file
    values_file=$(get_service_values_path "${service_name}") || return 1
    
    local config_dir
    config_dir=$(dirname "${values_file}")
    local local_values="${config_dir}/values.local.yaml"
    
    local flags="-f ${values_file}"
    
    # Add local overrides if file exists
    if [[ -f "${local_values}" ]]; then
        flags="${flags} -f ${local_values}"
    fi
    
    echo "${flags}"
}

#
# Get CoreDNS custom hosts template path
# Returns:
#   Path to the CoreDNS custom-hosts.yaml template
# Example:
#   template=$(get_coredns_template_path)
#
get_coredns_template_path() {
    echo "${CONFIG_COREDNS_DIR}/custom-hosts.yaml"
}

#
# Read version from gradle.properties
# Returns:
#   Version string (e.g., "0.5.0-SNAPSHOT")
# Example:
#   version=$(get_structures_version)
#
get_structures_version() {
    local gradle_props="./gradle.properties"
    
    if [[ ! -f "${gradle_props}" ]]; then
        error "gradle.properties not found"
        return 1
    fi
    
    local version
    version=$(grep '^structuresVersion=' "${gradle_props}" | cut -d'=' -f2)
    
    if [[ -z "${version}" ]]; then
        error "structuresVersion not found in gradle.properties"
        return 1
    fi
    
    echo "${version}"
}

#
# Get image name for structures-server
# Returns:
#   Full image name (e.g., "mindignited/structures-server:0.5.0-SNAPSHOT")
# Example:
#   image=$(get_image_name)
#
get_image_name() {
    local version
    version=$(get_structures_version) || return 1
    
    local image_name="${IMAGE_NAME:-mindignited/structures-server}"
    echo "${image_name}:${version}"
}

#
# Print configuration summary
# Args:
#   None
# Example:
#   print_config_summary
#
print_config_summary() {
    info "Configuration:"
    progress "Cluster Name: ${CLUSTER_NAME}"
    progress "KinD Config: ${KIND_CONFIG_PATH}"
    progress "Helm Chart: ${HELM_CHART_PATH}"
    progress "Helm Values: ${HELM_VALUES_PATH}"
    if [[ -f "${DEFAULT_HELM_VALUES_LOCAL}" ]]; then
        progress "Local Overrides: ${DEFAULT_HELM_VALUES_LOCAL}"
    fi
}

