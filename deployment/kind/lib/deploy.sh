#!/usr/bin/env bash
#
# Deployment Functions for kinotic-server and dependencies
# Handles Helm deployments, health checks, and configuration
#

# Prevent re-sourcing
[[ -n "${_KIND_DEPLOY_LOADED:-}" ]] && return 0
readonly _KIND_DEPLOY_LOADED=1

# Source dependencies
LIB_SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=deployment/kind/lib/logging.sh
source "${LIB_SCRIPT_DIR}/logging.sh"
# shellcheck source=deployment/kind/lib/config.sh
source "${LIB_SCRIPT_DIR}/config.sh"

#
# Add required Helm repositories
# Args:
#   None
# Returns:
#   0 on success, 1 on failure
# Example:
#   add_helm_repos
#
add_helm_repos() {
    info "Adding Helm repositories..."
    
    local repos=(
        "bitnami:https://charts.bitnami.com/bitnami"
        "elastic:https://helm.elastic.co"
        "prometheus-community:https://prometheus-community.github.io/helm-charts"
        "grafana:https://grafana.github.io/helm-charts"
        "open-telemetry:https://open-telemetry.github.io/opentelemetry-helm-charts"
        "ingress-nginx:https://kubernetes.github.io/ingress-nginx"
        "jetstack:https://charts.jetstack.io"
    )
    
    for repo in "${repos[@]}"; do
        local name="${repo%%:*}"
        local url="${repo#*:}"
        
        if helm repo list 2>/dev/null | grep -q "^${name}"; then
            verbose "Repository '${name}' already added"
        else
            progress "Adding ${name}..."
            if ! execute helm repo add "${name}" "${url}" > /dev/null 2>&1; then
                error "Failed to add Helm repository: ${name}"
                return 1
            fi
        fi
    done
    
    progress "Updating Helm repositories..."
    if ! execute helm repo update > /dev/null 2>&1; then
        error "Failed to update Helm repositories"
        return 1
    fi
    
    success "Helm repositories ready"
    return 0
}

#
# Deploy PostgreSQL for Keycloak using Bitnami Helm chart
# Args:
#   $1: Cluster name
# Returns:
#   0 on success, 1 on failure
# Example:
#   deploy_postgresql "kinotic-cluster"
#
deploy_postgresql() {
    local cluster_name="$1"
    local context="kind-${cluster_name}"
    
    progress "Deploying PostgreSQL for Keycloak..."
    
    # Get PostgreSQL values file from config directory
    local values_flags
    values_flags=$(get_service_helm_flags "postgresql") || return 1
    
    progress "Using PostgreSQL configuration from: $(get_service_values_path postgresql)"
    
    # Deploy using Bitnami Helm chart
    local helm_output
    # shellcheck disable=SC2086
    helm_output=$(helm upgrade --install keycloak-db bitnami/postgresql \
        --kube-context "${context}" \
        ${values_flags} \
        --wait --timeout 5m 2>&1)
    
    local exit_code=$?
    
    if [[ ${exit_code} -ne 0 ]]; then
        error "Failed to deploy PostgreSQL"
        echo ""
        echo "Helm output:"
        echo "${helm_output}"
        echo ""
        echo "Check pod status:"
        echo "  kubectl get pods -l app.kubernetes.io/name=postgresql --context ${context}"
        echo "Check pod logs:"
        echo "  kubectl logs -l app.kubernetes.io/name=postgresql --context ${context}"
        echo ""
        return 1
    fi
    
    success "PostgreSQL deployed (1/1 pods ready)"
    return 0
}

#
# Create Keycloak realm ConfigMap
# Args:
#   $1: Cluster name
# Returns:
#   0 on success, 1 on failure
# Example:
#   create_keycloak_realm_configmap "kinotic-cluster"
#
create_keycloak_realm_configmap() {
    local cluster_name="$1"
    local context="kind-${cluster_name}"
    local realm_file="docker-compose/keycloak-test-realm.json"
    
    if [[ ! -f "${realm_file}" ]]; then
        error "Keycloak realm file not found: ${realm_file}"
        return 1
    fi
    
    progress "Creating Keycloak realm ConfigMap..."
    
    # Check if ConfigMap already exists
    if kubectl --context "${context}" get configmap keycloak-realm &>/dev/null; then
        verbose "ConfigMap 'keycloak-realm' already exists, recreating..."
        execute kubectl --context "${context}" delete configmap keycloak-realm > /dev/null 2>&1
    fi
    
    if ! execute kubectl --context "${context}" create configmap keycloak-realm \
        --from-file=test-realm.json="${realm_file}" > /dev/null 2>&1; then
        error "Failed to create Keycloak realm ConfigMap"
        return 1
    fi
    
    success "Keycloak realm ConfigMap created"
    return 0
}

#
# Deploy Keycloak with PostgreSQL backend using local Helm chart
# Note: We use a local chart instead of the Bitnami chart because
# the Bitnami chart has Bitnami-specific components that conflict
# with the official Keycloak image.
# Args:
#   $1: Cluster name
# Returns:
#   0 on success, 1 on failure
# Example:
#   deploy_keycloak "kinotic-cluster"
#
deploy_keycloak() {
    local cluster_name="$1"
    local context="kind-${cluster_name}"
    
    progress "Deploying Keycloak..."
    
    # Local Keycloak chart path
    local chart_path="${LIB_SCRIPT_DIR}/../charts/keycloak"
    
    if [[ ! -d "${chart_path}" ]]; then
        error "Keycloak chart not found: ${chart_path}"
        return 1
    fi
    
    # Get Keycloak values file from config directory
    local values_flags
    values_flags=$(get_service_helm_flags "keycloak") || return 1
    
    progress "Using Keycloak chart from: ${chart_path}"
    progress "Using Keycloak values from: $(get_service_values_path keycloak)"
    
    # Deploy using local Helm chart
    local helm_output
    # shellcheck disable=SC2086
    helm_output=$(helm upgrade --install keycloak "${chart_path}" \
        --kube-context "${context}" \
        ${values_flags} \
        --wait --timeout 10m 2>&1)
    
    local exit_code=$?
    
    if [[ ${exit_code} -ne 0 ]]; then
        error "Failed to deploy Keycloak"
        echo ""
        echo "Helm output:"
        echo "${helm_output}"
        echo ""
        echo "Check pod status:"
        echo "  kubectl get pods -l app=keycloak --context ${context}"
        echo "Check pod logs:"
        echo "  kubectl logs -l app=keycloak --context ${context}"
        echo ""
        return 1
    fi
    
    success "Keycloak deployed (1/1 pods ready)"
    progress "Keycloak Admin Console: http://localhost:8888/auth/admin (admin/admin)"
    return 0
}

#
# Deploy NGINX Ingress Controller for KinD using Helm chart
# Args:
#   $1: Cluster name
# Returns:
#   0 on success, 1 on failure
# Example:
#   deploy_nginx_ingress "kinotic-cluster"
#
deploy_nginx_ingress() {
    local cluster_name="$1"
    local context="kind-${cluster_name}"
    
    progress "Deploying NGINX Ingress Controller..."
    
    # Label the control-plane node for ingress scheduling
    # This is required because KinD extraPortMappings are only on the control-plane
    progress "Labeling control-plane node for ingress..."
    kubectl label node "${cluster_name}-control-plane" ingress-ready=true --overwrite --context "${context}" 2>/dev/null || true
    
    # Get ingress-nginx values file from config directory
    local values_flags
    values_flags=$(get_service_helm_flags "ingress-nginx") || return 1
    
    progress "Using ingress-nginx configuration from: $(get_service_values_path ingress-nginx)"
    
    # Deploy using Helm chart
    local helm_output
    # shellcheck disable=SC2086
    helm_output=$(helm upgrade --install ingress-nginx ingress-nginx/ingress-nginx \
        --kube-context "${context}" \
        --namespace ingress-nginx \
        --create-namespace \
        ${values_flags} \
        --wait --timeout 8m 2>&1)
    
    local exit_code=$?
    
    if [[ ${exit_code} -ne 0 ]]; then
        error "Failed to deploy NGINX Ingress Controller"
        echo ""
        echo "Helm output:"
        echo "${helm_output}"
        echo ""
        echo "Check pod status:"
        echo "  kubectl get pods -n ingress-nginx --context ${context}"
        echo ""
        return 1
    fi
    
    success "NGINX Ingress Controller deployed successfully"
    return 0
}

#
# Configure CoreDNS to resolve custom hostnames to the nginx ingress
# This allows pods within the cluster to reach services via ingress hostnames
# like structures.local without external DNS configuration.
#
# Args:
#   $1: Cluster name
#   $2: hostname to resolve (e.g., "structures.local")
# Returns:
#   0 on success, 1 on failure
# Example:
#   configure_coredns_custom_hosts "kinotic-cluster" "structures.local"
#
configure_coredns_custom_hosts() {
    local cluster_name="$1"
    local hostname="$2"
    local context="kind-${cluster_name}"
    
    progress "Configuring CoreDNS to resolve ${hostname} to nginx ingress..."
    
    # Get the ingress controller's cluster IP
    local ingress_ip
    ingress_ip=$(kubectl get svc ingress-nginx-controller -n ingress-nginx \
        --context "${context}" -o jsonpath='{.spec.clusterIP}' 2>/dev/null)
    
    if [[ -z "${ingress_ip}" ]]; then
        warning "Could not get ingress-nginx-controller ClusterIP, skipping CoreDNS configuration"
        return 1
    fi
    
    verbose "Ingress controller ClusterIP: ${ingress_ip}"
    
    # Check if hosts block already exists in CoreDNS config
    local current_config
    current_config=$(kubectl get configmap coredns-custom -n kube-system \
        --context "${context}" -o jsonpath='{.data.Corefile}' 2>/dev/null)
    
    if echo "${current_config}" | grep -q "${hostname}"; then
        verbose "CoreDNS already configured for ${hostname}"
        return 0
    fi
    
    # Get the CoreDNS template file
    local template_file
    template_file=$(get_coredns_template_path)
    
    if [[ ! -f "${template_file}" ]]; then
        error "CoreDNS template not found: ${template_file}"
        return 1
    fi
    
    progress "Using CoreDNS template from: ${template_file}"
    
    # Create a temporary file with substituted variables
    local tmpfile
    tmpfile=$(mktemp)
    
    # Substitute template variables
    sed -e "s/\${INGRESS_IP}/${ingress_ip}/g" \
        -e "s/\${HOSTNAME}/${hostname}/g" \
        "${template_file}" > "${tmpfile}"
    
    verbose "Generated CoreDNS ConfigMap:"
    cat "${tmpfile}"

    if ! kubectl apply -f "${tmpfile}" --context "${context}" 2>/dev/null; then
        rm -f "${tmpfile}"
        warning "Failed to patch CoreDNS ConfigMap"
        return 1
    fi
    rm -f "${tmpfile}"
    
    # Restart CoreDNS to pick up the changes
    progress "Restarting CoreDNS..."
    kubectl rollout restart deployment coredns -n kube-system --context "${context}" 2>/dev/null || true
    
    # Wait for CoreDNS to be ready
    kubectl wait --for=condition=ready pod \
        -l k8s-app=kube-dns \
        -n kube-system \
        --timeout=60s \
        --context "${context}" 2>/dev/null || true
    
    success "CoreDNS configured: ${hostname} → ${ingress_ip}"
    progress "Note: Add '127.0.0.1 ${hostname}' to your /etc/hosts for browser access"
    return 0
}

# =============================================================================
# TLS Setup with mkcert (for local dev) or cert-manager fallback
# =============================================================================

#
# Install cert-manager for TLS certificate management using Helm chart
# Args:
#   $1: kubectl context
# Returns:
#   0 on success, 1 on failure
# Example:
#   install_cert_manager "kind-kinotic-cluster"
#
install_cert_manager() {
    local context="$1"
    
    # Check if cert-manager is already installed
    if kubectl get namespace cert-manager --context "${context}" &>/dev/null; then
        verbose "cert-manager already installed"
        return 0
    fi
    
    progress "Installing cert-manager..."
    
    # Get cert-manager values file from config directory
    local values_flags
    values_flags=$(get_service_helm_flags "cert-manager") || return 1
    
    progress "Using cert-manager configuration from: $(get_service_values_path cert-manager)"
    
    # Deploy using Helm chart from jetstack repo
    local helm_output
    # shellcheck disable=SC2086
    helm_output=$(helm upgrade --install cert-manager jetstack/cert-manager \
        --kube-context "${context}" \
        --namespace cert-manager \
        --create-namespace \
        ${values_flags} \
        --wait --timeout 5m 2>&1)
    
    local exit_code=$?
    
    if [[ ${exit_code} -ne 0 ]]; then
        error "Failed to install cert-manager"
        echo ""
        echo "Helm output:"
        echo "${helm_output}"
        echo ""
        echo "Check pod status:"
        echo "  kubectl get pods -n cert-manager --context ${context}"
        echo ""
        return 1
    fi
    
    success "cert-manager installed"
    return 0
}

#
# Setup TLS certificates using mkcert (if available) or cert-manager fallback
# Args:
#   $1: kubectl context
#   $2: Namespace (default: default)
# Returns:
#   0 on success, sets MKCERT_USED=true/false
# Example:
#   setup_tls "kind-kinotic-cluster" "default"
#
setup_tls() {
    local context="$1"
    local namespace="${2:-default}"
    local secret_name="kinotic-tls-secret"
    
    section "TLS Certificate Setup"
    
    # Always install cert-manager (used as fallback or for other certs)
    if ! install_cert_manager "${context}"; then
        warning "cert-manager installation failed, continuing without it"
    fi
    
    # Check if mkcert is available for locally-trusted certificates
    if command -v mkcert &> /dev/null; then
        progress "mkcert found - generating locally-trusted certificates"
        
        # Ensure mkcert CA is installed (idempotent, may require sudo on first run)
        progress "Ensuring mkcert CA is installed..."
        mkcert -install 2>/dev/null || {
            warning "mkcert -install failed (may need sudo on first run)"
            warning "Run 'mkcert -install' manually if you see certificate errors"
        }
        
        # Generate certificates in temp directory
        local cert_dir
        cert_dir=$(mktemp -d)
        pushd "${cert_dir}" > /dev/null || return 1
        
        progress "Generating certificates for localhost, structures.local, 127.0.0.1, ::1"
        if ! mkcert localhost structures.local 127.0.0.1 ::1 2>&1; then
            error "Failed to generate certificates with mkcert"
            popd > /dev/null || true
            rm -rf "${cert_dir}"
            return 1
        fi
        
        # Find the generated files (mkcert names them based on first hostname)
        local cert_file key_file
        cert_file=$(ls localhost+*.pem 2>/dev/null | grep -v '\-key' | head -1)
        key_file=$(ls localhost+*-key.pem 2>/dev/null | head -1)
        
        if [[ -z "${cert_file}" || -z "${key_file}" ]]; then
            error "Could not find generated certificate files"
            ls -la
            popd > /dev/null || true
            rm -rf "${cert_dir}"
            return 1
        fi
        
        progress "Creating Kubernetes TLS secret from mkcert certificates..."
        
        # Delete existing secret if it exists (to update it)
        kubectl delete secret "${secret_name}" \
            --namespace="${namespace}" \
            --context="${context}" 2>/dev/null || true
        
        # Create the TLS secret
        if ! kubectl create secret tls "${secret_name}" \
            --cert="${cert_file}" \
            --key="${key_file}" \
            --namespace="${namespace}" \
            --context="${context}" 2>&1; then
            error "Failed to create TLS secret"
            popd > /dev/null || true
            rm -rf "${cert_dir}"
            return 1
        fi
        
        popd > /dev/null || true
        rm -rf "${cert_dir}"
        
        success "mkcert certificates created and loaded into cluster"
        progress "Browsers will trust https://localhost without warnings"
        
        # Export for use in helm values
        export MKCERT_USED=true
        return 0
    else
        warning "mkcert not found - will use cert-manager self-signed certificates"
        progress "Browsers will show security warnings for https://localhost"
        blank_line
        progress "To enable browser-trusted HTTPS, install mkcert:"
        progress "  macOS:  brew install mkcert && mkcert -install"
        progress "  Linux:  See https://github.com/FiloSottile/mkcert#installation"
        blank_line
        
        # Export for use in helm values
        export MKCERT_USED=false
        return 0
    fi
}

#
# Export CA certificate for CLI tools
# Creates ~/.structures/kind/ca.crt for use with NODE_EXTRA_CA_CERTS
# 
# If mkcert was used: exports the mkcert root CA (required for Node.js)
# If cert-manager was used: exports the self-signed certificate
#
# Args:
#   $1: Cluster name
# Returns:
#   0 on success, 1 on failure (non-fatal, just logs warning)
# Example:
#   export_tls_certificate "kinotic-cluster"
#
export_tls_certificate() {
    local cluster_name="$1"
    local context="kind-${cluster_name}"
    local cert_dir="${HOME}/.structures/kind"
    local cert_file="${cert_dir}/ca.crt"
    
    section "Exporting CA Certificate for CLI Tools"
    
    # Create directory if it doesn't exist
    if ! mkdir -p "${cert_dir}" 2>/dev/null; then
        warning "Could not create certificate directory: ${cert_dir}"
        return 1
    fi
    
    # Check if mkcert was used (check if the TLS secret contains mkcert-signed cert)
    if command -v mkcert &>/dev/null; then
        local mkcert_ca_root
        mkcert_ca_root="$(mkcert -CAROOT 2>/dev/null)"
        
        if [[ -n "${mkcert_ca_root}" && -f "${mkcert_ca_root}/rootCA.pem" ]]; then
            # Check if the deployed cert was signed by mkcert
            local server_issuer
            server_issuer=$(kubectl get secret kinotic-tls-secret -n default \
                --context "${context}" \
                -o jsonpath='{.data.tls\.crt}' 2>/dev/null | base64 -d | \
                openssl x509 -noout -issuer 2>/dev/null || echo "")
            
            if [[ "${server_issuer}" == *"mkcert"* ]]; then
                progress "Detected mkcert certificates - exporting mkcert root CA..."
                
                if cp "${mkcert_ca_root}/rootCA.pem" "${cert_file}" 2>/dev/null; then
                    success "mkcert root CA exported to: ${cert_file}"
                    progress "Node.js will now trust certificates signed by your local mkcert CA"
                    return 0
                else
                    warning "Failed to copy mkcert root CA"
                    # Fall through to try cert-manager approach
                fi
            fi
        fi
    fi
    
    # Fallback: Export the self-signed certificate from cert-manager
    progress "Exporting self-signed certificate from cluster..."
    
    # Wait a moment for the secret to be ready (cert-manager might still be provisioning)
    local max_attempts=10
    local attempt=0
    
    while [[ ${attempt} -lt ${max_attempts} ]]; do
        if kubectl get secret kinotic-tls-secret -n default --context "${context}" &>/dev/null; then
            break
        fi
        ((attempt++))
        if [[ ${attempt} -lt ${max_attempts} ]]; then
            verbose "Waiting for TLS secret to be ready (attempt ${attempt}/${max_attempts})..."
            sleep 2
        fi
    done
    
    if [[ ${attempt} -ge ${max_attempts} ]]; then
        warning "TLS secret not ready after ${max_attempts} attempts"
        progress "Certificate can be exported manually once available"
        return 1
    fi
    
    # Export the certificate
    if kubectl get secret kinotic-tls-secret -n default \
        --context "${context}" \
        -o jsonpath='{.data.tls\.crt}' 2>/dev/null | base64 -d > "${cert_file}" 2>/dev/null; then
        
        # Verify the file was written and has content
        if [[ -s "${cert_file}" ]]; then
            success "Self-signed certificate exported to: ${cert_file}"
            return 0
        else
            warning "Certificate file is empty"
            rm -f "${cert_file}"
            return 1
        fi
    else
        warning "Failed to export TLS certificate"
        return 1
    fi
}

#
# Deploy the ECK (Elastic Cloud on Kubernetes) operator
# Installs CRDs and the operator into the elastic-system namespace.
# Args:
#   $1: Cluster name
# Returns:
#   0 on success, 1 on failure
# Example:
#   deploy_eck_operator "kinotic-cluster"
#
deploy_eck_operator() {
    local cluster_name="$1"
    local context="kind-${cluster_name}"

    progress "Installing ECK operator..."

    # Check if CRDs are already installed
    if kubectl --context "${context}" get crd elasticsearches.elasticsearch.k8s.elastic.co &>/dev/null; then
        verbose "ECK CRDs already installed"
    else
        progress "Installing ECK CRDs..."
        local crd_output
        crd_output=$(helm upgrade --install elastic-operator-crds elastic/eck-operator-crds \
            --kube-context "${context}" \
            --namespace elastic-system \
            --create-namespace 2>&1)
        local crd_exit=$?
        if [[ ${crd_exit} -ne 0 ]]; then
            error "Failed to install ECK CRDs"
            echo "${crd_output}"
            return 1
        fi
    fi

    # Get ECK operator values if available
    local eck_values_flags=""
    local eck_values_file
    eck_values_file=$(get_service_values_path "eck-operator") 2>/dev/null
    if [[ -f "${eck_values_file}" ]]; then
        eck_values_flags="-f ${eck_values_file}"
        progress "Using ECK operator config from: ${eck_values_file}"
    fi

    local helm_output
    # shellcheck disable=SC2086
    helm_output=$(helm upgrade --install elastic-operator elastic/eck-operator \
        --kube-context "${context}" \
        --namespace elastic-system \
        --create-namespace \
        --set managedNamespaces='{default}' \
        ${eck_values_flags} \
        --wait --timeout 5m 2>&1)

    local exit_code=$?

    if [[ ${exit_code} -ne 0 ]]; then
        error "Failed to install ECK operator"
        echo ""
        echo "Helm output:"
        echo "${helm_output}"
        echo ""
        echo "Check pod status:"
        echo "  kubectl get pods -n elastic-system --context ${context}"
        echo ""
        return 1
    fi

    success "ECK operator installed"
    return 0
}

#
# Deploy Elasticsearch via ECK operator using the reusable Helm chart.
# Requires the ECK operator to be installed first (deploy_eck_operator).
# Args:
#   $1: Cluster name
# Returns:
#   0 on success, 1 on failure
# Example:
#   deploy_elasticsearch "kinotic-cluster"
#
deploy_elasticsearch() {
    local cluster_name="$1"
    local context="kind-${cluster_name}"

    progress "Deploying Elasticsearch via ECK operator..."

    # Ensure the ECK operator is running
    if ! deploy_eck_operator "${cluster_name}"; then
        return 1
    fi

    blank_line

    # The reusable Helm chart lives in deployment/helm/elasticsearch.
    # KinD-specific overrides are in the config directory.
    local es_chart_path="./deployment/helm/elasticsearch"
    if [[ ! -d "${es_chart_path}" ]]; then
        error "Elasticsearch Helm chart not found at: ${es_chart_path}"
        return 1
    fi

    # Build values flags: base chart defaults + KinD overrides
    local values_flags=""
    local kind_values
    kind_values=$(get_service_values_path "elasticsearch") 2>/dev/null
    if [[ -f "${kind_values}" ]]; then
        values_flags="-f ${kind_values}"
        progress "Using KinD Elasticsearch overrides from: ${kind_values}"
    fi

    local helm_output
    # shellcheck disable=SC2086
    helm_output=$(helm upgrade --install elasticsearch "${es_chart_path}" \
        --kube-context "${context}" \
        ${values_flags} \
        --timeout 5m 2>&1)

    local exit_code=$?

    if [[ ${exit_code} -ne 0 ]]; then
        error "Failed to deploy Elasticsearch"
        echo ""
        echo "Helm output:"
        echo "${helm_output}"
        echo ""
        echo "Check ECK operator logs:"
        echo "  kubectl logs -n elastic-system sts/elastic-operator --context ${context}"
        echo "Check Elasticsearch resource:"
        echo "  kubectl get elasticsearch --context ${context}"
        echo "Check pods:"
        echo "  kubectl get pods -l elasticsearch.k8s.elastic.co/cluster-name=kinotic-es --context ${context}"
        echo "Check events:"
        echo "  kubectl get events --context ${context} --sort-by='.lastTimestamp' | tail -20"
        echo ""
        return 1
    fi

    # Wait for Elasticsearch health to turn green or yellow
    progress "Waiting for Elasticsearch cluster to become ready..."
    local max_wait=300
    local elapsed=0
    local interval=5

    while [[ ${elapsed} -lt ${max_wait} ]]; do
        local health
        health=$(kubectl --context "${context}" get elasticsearch kinotic-es \
            -o jsonpath='{.status.health}' 2>/dev/null)

        if [[ "${health}" == "green" || "${health}" == "yellow" ]]; then
            success "Elasticsearch cluster is ${health} ($(kubectl --context "${context}" get elasticsearch kinotic-es -o jsonpath='{.status.availableNodes}' 2>/dev/null) nodes available)"
            return 0
        fi

        local phase
        phase=$(kubectl --context "${context}" get elasticsearch kinotic-es \
            -o jsonpath='{.status.phase}' 2>/dev/null)
        verbose "Elasticsearch phase: ${phase:-unknown}, health: ${health:-unknown} (${elapsed}s/${max_wait}s)"

        sleep ${interval}
        elapsed=$((elapsed + interval))
    done

    warning "Elasticsearch did not reach green/yellow health within ${max_wait}s"
    progress "Current status:"
    kubectl --context "${context}" get elasticsearch kinotic-es 2>&1 || true
    kubectl --context "${context}" get pods -l elasticsearch.k8s.elastic.co/cluster-name=kinotic-es 2>&1 || true
    return 0
}

#
# Deploy kinotic-server via Helm
# Args:
#   $1: Cluster name
#   $2: Additional Helm set flags (optional)
# Returns:
#   0 on success, 1 on failure
# Example:
#   deploy_structures_server "kinotic-cluster" "--set replicaCount=3"
#
deploy_structures_server() {
    local cluster_name="$1"
    local context="kind-${cluster_name}"
    local additional_sets="${2:-}"
    
    progress "Deploying kinotic-server..."
    
    # Setup TLS certificates (mkcert or cert-manager fallback)
    setup_tls "${context}" "default"
    
    # Verify Helm chart exists
    if [[ ! -d "${HELM_CHART_PATH}" ]]; then
        error "Helm chart not found at: ${HELM_CHART_PATH}"
        return 1
    fi
    
    progress "Using Helm chart: ${HELM_CHART_PATH}"
    
    # Show current values files being used
    if [[ -f "${HELM_VALUES_PATH}" ]]; then
        progress "Using values file: ${HELM_VALUES_PATH}"
    else
        warning "Default values file not found: ${HELM_VALUES_PATH}"
    fi
    
    # Build helm command
    local helm_cmd=(
        helm upgrade --install kinotic-server "${HELM_CHART_PATH}"
        --kube-context "${context}"
    )
    
    # If mkcert was used, tell helm to use the existing secret
    if [[ "${MKCERT_USED:-false}" == "true" ]]; then
        progress "Using mkcert-generated TLS secret"
        helm_cmd+=(--set "ingress.tls.existingSecret=true")
    else
        progress "Using cert-manager for TLS certificate generation"
        helm_cmd+=(--set "ingress.tls.existingSecret=false")
    fi
    
    # Add values files
    local values_flags
    values_flags=$(get_helm_values_flags)
    for flag in ${values_flags}; do
        helm_cmd+=("${flag}")
    done
    
    # Add additional sets if provided
    if [[ -n "${additional_sets}" ]]; then
        progress "Applying custom Helm values overrides"
        # Split additional_sets into array
        read -ra sets <<< "${additional_sets}"
        helm_cmd+=("${sets[@]}")
    fi
    
    # Add wait and timeout
    helm_cmd+=(--wait --timeout "${DEPLOY_TIMEOUT}" --rollback-on-failure)
    
    # Show the helm command being executed (for debugging)
    verbose "Executing: ${helm_cmd[*]}"
    
    # Execute helm command with detailed output
    progress "Installing/upgrading kinotic-server Helm release..."
    local helm_output
    helm_output=$("${helm_cmd[@]}" 2>&1)
    local helm_result=$?
    
    # Always show helm output for transparency
    echo "${helm_output}" | while IFS= read -r line; do
        progress "  ${line}"
    done
    
    if [[ ${helm_result} -ne 0 ]]; then
        error "Failed to deploy kinotic-server"
        echo ""
        
        # Show detailed diagnostics
        progress "Troubleshooting information:"
        echo ""
        
        progress "1. Checking pod status..."
        kubectl get pods --context "${context}" -l app.kubernetes.io/name=kinotic-server 2>&1 || true
        echo ""
        
        progress "2. Checking pod details..."
        kubectl describe pods --context "${context}" -l app.kubernetes.io/name=kinotic-server 2>&1 | tail -50 || true
        echo ""
        
        progress "3. Checking recent events..."
        kubectl get events --context "${context}" \
            --field-selector type!=Normal \
            --sort-by='.lastTimestamp' 2>&1 | tail -20 || true
        echo ""
        
        progress "4. Checking service status..."
        kubectl get svc --context "${context}" -l app.kubernetes.io/name=kinotic-server 2>&1 || true
        echo ""
        
        # Check if there are any existing pods with logs
        local pod_count
        pod_count=$(kubectl get pods --context "${context}" \
            -l app.kubernetes.io/name=kinotic-server \
            -o name 2>/dev/null | wc -l | tr -d ' ')
        
        if [[ ${pod_count} -gt 0 ]]; then
            progress "5. Recent logs from kinotic-server pod(s):"
            kubectl logs --context "${context}" \
                -l app.kubernetes.io/name=kinotic-server \
                --tail=50 \
                --all-containers=true 2>&1 || true
        else
            warning "No kinotic-server pods found to retrieve logs from"
        fi
        
        return 1
    fi
    
    # Verify deployment
    progress "Verifying deployment..."
    local ready_pods
    ready_pods=$(kubectl get pods --context "${context}" \
        -l app.kubernetes.io/name=kinotic-server \
        -o jsonpath='{.items[*].status.conditions[?(@.type=="Ready")].status}' 2>/dev/null | grep -c "True" || echo "0")

    echo "Ready pods: ${ready_pods}"
    
    if [[ ${ready_pods} -lt 1 ]]; then
        warning "Deployment completed but pods are not ready yet"
        progress "Current pod status:"
        kubectl get pods --context "${context}" -l app.kubernetes.io/name=kinotic-server 2>&1 || true
    else
        success "kinotic-server deployed successfully (${ready_pods} pod(s) ready)"
        
        # Show service information
        progress "Service endpoints:"
        kubectl get svc --context "${context}" -l app.kubernetes.io/name=kinotic-server 2>&1 || true
    fi
    
    return 0
}

#
# Deploy load generator via standalone Helm chart
# Runs as a Job that generates schemas and test data against kinotic-server
# Args:
#   $1: Cluster name
# Returns:
#   0 on success, 1 on failure
# Example:
#   deploy_load_generator "kinotic-cluster"
#
deploy_load_generator() {
    local cluster_name="$1"
    local context="kind-${cluster_name}"
    local chart_path="./helm/load-generator"
    
    progress "Running load generator (generateComplexStructures)..."
    
    if [[ ! -d "${chart_path}" ]]; then
        error "Load generator chart not found: ${chart_path}"
        return 1
    fi
    
    # Get load-generator values file from config directory
    local values_flags
    values_flags=$(get_service_helm_flags "load-generator") || return 1
    
    progress "Using load-generator chart from: ${chart_path}"
    progress "Using load-generator values from: $(get_service_values_path load-generator)"
    
    # Deploy using standalone Helm chart
    local helm_output
    # shellcheck disable=SC2086
    helm_output=$(helm upgrade --install load-generator "${chart_path}" \
        --kube-context "${context}" \
        ${values_flags} \
        --wait --timeout 10m 2>&1)
    
    local exit_code=$?
    
    if [[ ${exit_code} -ne 0 ]]; then
        error "Failed to run load generator"
        echo ""
        echo "Helm output:"
        echo "${helm_output}"
        echo ""
        echo "Check job status:"
        echo "  kubectl get jobs --context ${context}"
        echo "Check pod logs:"
        echo "  kubectl logs -l app.kubernetes.io/name=structures-load-generator --context ${context}"
        echo ""
        return 1
    fi
    
    success "Load generator completed successfully"
    return 0
}

#
# Wait for pods to be ready
# Args:
#   $1: Cluster name
#   $2: Selector (e.g., "app=kinotic-server")
#   $3: Expected count
#   $4: Timeout in seconds (default: 300)
# Returns:
#   0 if ready, 1 on timeout
# Example:
#   wait_for_pods_ready "kinotic-cluster" "app=kinotic-server" 2 300
#
wait_for_pods_ready() {
    local cluster_name="$1"
    local selector="$2"
    local expected_count="$3"
    local timeout="${4:-300}"
    local context="kind-${cluster_name}"
    local elapsed=0
    local interval=5
    
    while [[ ${elapsed} -lt ${timeout} ]]; do
        local ready_count
        ready_count=$(kubectl --context "${context}" get pods -l "${selector}" \
            --no-headers 2>/dev/null | grep -c "Running" || echo "0")
        
        if [[ ${ready_count} -ge ${expected_count} ]]; then
            return 0
        fi
        
        sleep ${interval}
        elapsed=$((elapsed + interval))
    done
    
    error "Pods did not become ready within ${timeout} seconds"
    return 1
}

#
# Check health endpoint
# Args:
#   $1: URL
# Returns:
#   0 if healthy, 1 otherwise
# Example:
#   check_health "http://localhost:9090/health"
#
check_health() {
    local url="$1"
    
    if curl -sf "${url}" > /dev/null 2>&1; then
        return 0
    else
        return 1
    fi
}

#
# Display deployment status
# Args:
#   $1: Cluster name
# Example:
#   display_deployment_status "kinotic-cluster"
#
display_deployment_status() {
    local cluster_name="$1"
    local context="kind-${cluster_name}"
    
    blank_line
    section "Helm Releases"
    
    helm list --kube-context "${context}" --no-headers 2>/dev/null | while IFS= read -r line; do
        local name
        name=$(echo "${line}" | awk '{print $1}')
        local status
        status=$(echo "${line}" | awk '{print $2}')
        local revision
        revision=$(echo "${line}" | awk '{print $3}')
        
        if [[ "${status}" == "deployed" ]]; then
            success "${name} (deployed, revision ${revision})"
        else
            warning "${name} (${status}, revision ${revision})"
        fi
    done
    
    blank_line
    section "Pods"
    
    kubectl --context "${context}" get pods --no-headers 2>/dev/null | while IFS= read -r line; do
        local name
        name=$(echo "${line}" | awk '{print $1}')
        local ready
        ready=$(echo "${line}" | awk '{print $2}')
        local status
        status=$(echo "${line}" | awk '{print $3}')
        local restarts
        restarts=$(echo "${line}" | awk '{print $4}')
        
        if [[ "${status}" == "Running" && "${ready}" == *"/"* ]]; then
            local ready_count="${ready%%/*}"
            local total_count="${ready##*/}"
            if [[ "${ready_count}" == "${total_count}" ]]; then
                success "${name} - Running (${ready}, ${restarts} restarts)"
            else
                warning "${name} - ${status} (${ready}, ${restarts} restarts)"
            fi
        else
            warning "${name} - ${status} (${ready}, ${restarts} restarts)"
        fi
    done
    
    blank_line
    section "Access URLs"
    
    # Check if TLS is likely enabled (mkcert was used or cert-manager)
    progress "Via Ingress (HTTPS):"
    progress "  https://localhost/         - Static UI (SPA)"
    progress "  https://localhost/api/     - OpenAPI REST"
    progress "  https://localhost/graphql/ - GraphQL"
    progress "  wss://localhost/v1         - STOMP WebSocket"
    blank_line
    
    progress "Direct access (kubectl port-forward):"
    progress "  kubectl port-forward svc/kinotic-es-es-http 9200:9200   - Elasticsearch"

    if [[ "${OIDC_ENABLED:-false}" == "true" ]]; then
        progress "  kubectl port-forward svc/keycloak-db-postgresql 5432:5432  - PostgreSQL"
        progress "  kubectl port-forward svc/keycloak 8888:8888                - Keycloak"
        blank_line
        progress "Keycloak (after port-forward):"
        progress "  Test Structures User: testuser@example.com/password123"
        progress "  Admin: http://localhost:8888/auth/admin (admin/admin)"
    fi
    
    blank_line
}

