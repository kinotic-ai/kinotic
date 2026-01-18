#!/usr/bin/env bash
#
# Deployment Functions for structures-server and dependencies
# Handles Helm deployments, health checks, and configuration
#

# Prevent re-sourcing
[[ -n "${_KIND_DEPLOY_LOADED:-}" ]] && return 0
readonly _KIND_DEPLOY_LOADED=1

# Source dependencies
LIB_SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=dev-tools/kind/lib/logging.sh
source "${LIB_SCRIPT_DIR}/logging.sh"
# shellcheck source=dev-tools/kind/lib/config.sh
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
# Deploy PostgreSQL for Keycloak
# Args:
#   $1: Cluster name
# Returns:
#   0 on success, 1 on failure
# Example:
#   deploy_postgresql "structures-cluster"
#
deploy_postgresql() {
    local cluster_name="$1"
    local context="kind-${cluster_name}"
    
    progress "Deploying PostgreSQL for Keycloak..."
    
    # PostgreSQL version (using official postgres alpine image)
    local pg_version="15-alpine"
    local pg_image="postgres:${pg_version}"
    local local_tag="localhost/postgres:${pg_version}"
    
    # Detect host platform for image pulling
    local platform="linux/amd64"
    if [[ "$(uname -m)" == "arm64" ]] || [[ "$(uname -m)" == "aarch64" ]]; then
        platform="linux/arm64"
    fi
    
    # Pre-pull for specific platform
    progress "Pre-loading PostgreSQL image into cluster..."
    if ! docker image inspect "${pg_image}" &>/dev/null; then
        progress "Pulling ${pg_image} for ${platform}..."
        if ! docker pull --platform "${platform}" "${pg_image}"; then
            error "Failed to pull PostgreSQL image"
            return 1
        fi
    fi
    
    # Use tarball method to avoid multi-platform manifest issues
    progress "Loading image into KinD cluster (tarball method)..."
    local temp_tar="/tmp/postgres-${RANDOM}.tar"
    
    # Save image to tarball (this flattens multi-platform references)
    if ! docker save "${pg_image}" -o "${temp_tar}"; then
        error "Failed to save PostgreSQL image to tarball"
        return 1
    fi
    
    # Verify tarball exists
    if [[ ! -f "${temp_tar}" ]]; then
        error "Tarball not created: ${temp_tar}"
        return 1
    fi
    
    progress "Created tarball: ${temp_tar} ($(du -h "${temp_tar}" | cut -f1))"
    
    # Import tarball into each KinD node by piping to ctr stdin
    local all_loaded=true
    for node in $(kind get nodes --name "${cluster_name}"); do
        progress "Loading into node ${node}..."
        
        # Pipe tarball directly to ctr via docker exec stdin
        local import_output
        import_output=$(cat "${temp_tar}" | docker exec -i "${node}" ctr -n k8s.io images import - 2>&1)
        local import_result=$?
        
        if [[ ${import_result} -eq 0 ]]; then
            success "Loaded into ${node}"
        else
            error "Failed to import image on ${node}"
            echo "Import output: ${import_output}"
            all_loaded=false
        fi
    done
    
    # Clean up tarball
    rm -f "${temp_tar}"
    
    if [[ "${all_loaded}" == "false" ]]; then
        error "Failed to load PostgreSQL image into some cluster nodes"
        return 1
    fi
    
    # Deploy using plain Kubernetes manifest (avoids Bitnami chart restrictions)
    progress "Deploying PostgreSQL StatefulSet..."
    
    cat <<EOF | kubectl apply --context "${context}" -f - 2>&1
---
apiVersion: v1
kind: Service
metadata:
  name: keycloak-db-postgresql
  labels:
    app: postgresql
spec:
  type: NodePort
  ports:
  - port: 5432
    targetPort: 5432
    nodePort: 30555  # Maps to hostPort 5555 → localhost:5555
    name: postgres
  selector:
    app: postgresql
---
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: keycloak-db-postgresql
spec:
  serviceName: keycloak-db-postgresql
  replicas: 1
  selector:
    matchLabels:
      app: postgresql
  template:
    metadata:
      labels:
        app: postgresql
    spec:
      containers:
      - name: postgres
        image: postgres:${pg_version}
        imagePullPolicy: IfNotPresent
        ports:
        - containerPort: 5432
          name: postgres
        env:
        - name: POSTGRES_DB
          value: keycloak
        - name: POSTGRES_USER
          value: keycloak
        - name: POSTGRES_PASSWORD
          value: keycloak
        - name: PGDATA
          value: /var/lib/postgresql/data/pgdata
        volumeMounts:
        - name: postgres-storage
          mountPath: /var/lib/postgresql/data
        - name: run
          mountPath: /var/run/postgresql
        resources:
          requests:
            cpu: 100m
            memory: 256Mi
          limits:
            cpu: 500m
            memory: 512Mi
        readinessProbe:
          exec:
            command:
            - sh
            - -c
            - pg_isready -U keycloak
          initialDelaySeconds: 10
          periodSeconds: 5
        livenessProbe:
          exec:
            command:
            - sh
            - -c
            - pg_isready -U keycloak
          initialDelaySeconds: 30
          periodSeconds: 10
      volumes:
      - name: postgres-storage
        emptyDir: {}
      - name: run
        emptyDir: {}
EOF
    
    if [[ $? -ne 0 ]]; then
        error "Failed to create PostgreSQL StatefulSet"
        return 1
    fi
    
    # Wait for PostgreSQL to be ready
    progress "Waiting for PostgreSQL to be ready..."
    if ! kubectl wait --context "${context}" \
        --for=condition=ready pod \
        -l app=postgresql \
        --timeout=5m 2>&1; then
        error "PostgreSQL pod did not become ready"
        kubectl get pods --context "${context}" -l app=postgresql 2>&1 || true
        kubectl describe pod --context "${context}" -l app=postgresql 2>&1 || true
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
#   create_keycloak_realm_configmap "structures-cluster"
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
# Deploy Keycloak with PostgreSQL backend using custom StatefulSet
# Args:
#   $1: Cluster name
# Returns:
#   0 on success, 1 on failure
# Example:
#   deploy_keycloak "structures-cluster"
#
deploy_keycloak() {
    local cluster_name="$1"
    local context="kind-${cluster_name}"
    
    progress "Deploying Keycloak..."
    
    # Keycloak version (matching docker-compose/compose.keycloak.yml)
    local kc_version="26.0.2"
    local kc_image="quay.io/keycloak/keycloak:${kc_version}"
    
    # Deploy using plain Kubernetes manifest
    progress "Deploying Keycloak StatefulSet..."
    
    if ! kubectl apply --context "${context}" -f - <<EOF 2>&1 | grep -v "Warning:"; then
---
apiVersion: v1
kind: Service
metadata:
  name: keycloak
  labels:
    app: keycloak
spec:
  type: NodePort
  ports:
  - port: 8888
    targetPort: 8888
    nodePort: 30888
    name: http
  selector:
    app: keycloak
---
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: keycloak
spec:
  serviceName: keycloak
  replicas: 1
  selector:
    matchLabels:
      app: keycloak
  template:
    metadata:
      labels:
        app: keycloak
    spec:
      containers:
      - name: keycloak
        image: ${kc_image}
        imagePullPolicy: IfNotPresent
        command: ["/opt/keycloak/bin/kc.sh"]
        args:
        - start-dev
        - --import-realm
        env:
        - name: KC_DB
          value: postgres
        - name: KC_DB_URL
          value: jdbc:postgresql://keycloak-db-postgresql:5432/keycloak
        - name: KC_DB_USERNAME
          value: keycloak
        - name: KC_DB_PASSWORD
          value: keycloak
        - name: KC_HOSTNAME
          value: https://structures.local/auth
        - name: KC_PROXY_HEADERS
          value: xforwarded
        - name: KC_HOSTNAME_STRICT
          value: "false"
        - name: KC_HTTP_ENABLED
          value: "true"
        - name: KC_HTTP_PORT
          value: "8888"
        - name: KC_HTTP_RELATIVE_PATH
          value: /auth
        - name: KC_METRICS_ENABLED
          value: "true"
        - name: KC_HEALTH_ENABLED
          value: "true"
        - name: KC_LOG_LEVEL
          value: INFO
        - name: KEYCLOAK_ADMIN
          value: admin
        - name: KEYCLOAK_ADMIN_PASSWORD
          value: admin
        ports:
        - containerPort: 8888
          name: http
        volumeMounts:
        - name: realm-config
          mountPath: /opt/keycloak/data/import
          readOnly: true
        resources:
          requests:
            cpu: 500m
            memory: 512Mi
          limits:
            cpu: 1000m
            memory: 1Gi
        readinessProbe:
          httpGet:
            path: /auth/realms/master
            port: 8888
          initialDelaySeconds: 90
          periodSeconds: 10
          timeoutSeconds: 10
          failureThreshold: 10
        livenessProbe:
          httpGet:
            path: /auth/realms/master
            port: 8888
          initialDelaySeconds: 180
          periodSeconds: 10
          timeoutSeconds: 10
          failureThreshold: 6
      volumes:
      - name: realm-config
        configMap:
          name: keycloak-realm
EOF
        error "Failed to create Keycloak StatefulSet"
        return 1
    fi
    
    # Wait for Keycloak to be ready
    progress "Waiting for Keycloak to be ready (this may take 2-3 minutes)..."
    if ! kubectl wait --context "${context}" \
        --for=condition=ready pod \
        -l app=keycloak \
        --timeout=10m 2>&1; then
        error "Keycloak pod did not become ready"
        progress "Pod status:"
        kubectl get pods --context "${context}" -l app=keycloak 2>&1 || true
        progress "Pod logs:"
        kubectl logs --context "${context}" -l app=keycloak --tail=50 2>&1 || true
        return 1
    fi
    
    success "Keycloak deployed (1/1 pods ready)"
    progress "Keycloak Admin Console: http://localhost:30888/auth/admin (admin/admin)"
    return 0
}

#
# Deploy NGINX Ingress Controller for KinD
# Args:
#   $1: Cluster name
# Returns:
#   0 on success, 1 on failure
# Example:
#   deploy_nginx_ingress "structures-cluster"
#
deploy_nginx_ingress() {
    local cluster_name="$1"
    local context="kind-${cluster_name}"
    
    progress "Deploying NGINX Ingress Controller..."
    
    # Label the control-plane node for ingress scheduling
    # This is required because KinD extraPortMappings are only on the control-plane
    progress "Labeling control-plane node for ingress..."
    kubectl label node "${cluster_name}-control-plane" ingress-ready=true --overwrite --context "${context}" 2>/dev/null || true
    
    # Check if already deployed
    if kubectl get deployment ingress-nginx-controller -n ingress-nginx --context "${context}" &>/dev/null; then
        success "NGINX Ingress Controller already deployed"
        # Ensure it's scheduled on control-plane and snippets are enabled
        ensure_ingress_on_control_plane "${context}"
        enable_nginx_snippets "${context}"
        return 0
    fi
    
    # Apply the official KinD ingress-nginx manifest
    progress "Applying ingress-nginx manifest..."
    if ! kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/main/deploy/static/provider/kind/deploy.yaml --context "${context}"; then
        error "Failed to apply ingress-nginx manifest"
        return 1
    fi
    
    # Ensure ingress controller runs on control-plane (where port mappings are)
    ensure_ingress_on_control_plane "${context}"
    
    # Wait for ingress-nginx to be ready
    progress "Waiting for ingress-nginx pods to be ready (up to 300s)..."
    if ! kubectl wait --namespace ingress-nginx \
        --for=condition=ready pod \
        --selector=app.kubernetes.io/component=controller \
        --timeout=300s \
        --context "${context}"; then
        warning "Ingress controller pods did not become ready in time"
        progress "Checking pod status:"
        kubectl get pods -n ingress-nginx --context "${context}"
        return 1
    fi
    
    # Enable snippet annotations for development (required by structures ingress)
    enable_nginx_snippets "${context}"
    
    success "NGINX Ingress Controller deployed successfully"
    return 0
}

#
# Ensure ingress controller runs on control-plane node
# This is required for KinD because extraPortMappings (80, 443) are only on control-plane
# Args:
#   $1: kubectl context
# Returns:
#   0 on success
# Example:
#   ensure_ingress_on_control_plane "kind-structures-cluster"
#
ensure_ingress_on_control_plane() {
    local context="$1"
    
    progress "Ensuring ingress controller runs on control-plane node..."
    
    # Patch deployment to require ingress-ready label (which is on control-plane)
    kubectl patch deployment ingress-nginx-controller -n ingress-nginx \
        --context "${context}" \
        -p '{"spec":{"template":{"spec":{"nodeSelector":{"ingress-ready":"true","kubernetes.io/os":"linux"}}}}}' \
        2>/dev/null || true
    
    return 0
}

#
# Enable snippet annotations in NGINX Ingress Controller
# This is required for structures-server ingress which uses configuration-snippet
# for WebSocket handling and path rewrites.
#
# ⚠️ SECURITY WARNING - DEVELOPMENT ONLY ⚠️
# This function configures nginx-ingress with relaxed security settings:
#   - allow-snippet-annotations: true (enables configuration-snippet annotation)
#   - annotations-risk-level: Critical (allows proxy_set_header, rewrite, if directives)
#
# These settings should NOT be used in production environments as they can allow
# configuration injection attacks. For production, refactor the ingress to avoid
# configuration-snippet or use more restrictive settings.
#
# See: https://kubernetes.github.io/ingress-nginx/user-guide/nginx-configuration/configmap/
#
# Args:
#   $1: kubectl context
# Returns:
#   0 on success, 1 on failure
# Example:
#   enable_nginx_snippets "kind-structures-cluster"
#
enable_nginx_snippets() {
    local context="$1"
    
    warning "Configuring nginx-ingress with relaxed security settings (DEVELOPMENT ONLY)"
    progress "  → allow-snippet-annotations: true"
    progress "  → annotations-risk-level: Critical"
    
    # Patch the ingress-nginx-controller ConfigMap to allow snippet annotations
    # This is necessary because the structures Helm chart uses configuration-snippet
    # for WebSocket handling and path rewrites
    # 
    # Settings:
    #   - allow-snippet-annotations: enables configuration-snippet annotation
    #   - annotations-risk-level: set to Critical to allow "risky" directives like
    #     proxy_set_header, rewrite, etc. (acceptable for dev, not for production)
    if ! kubectl patch configmap ingress-nginx-controller \
        -n ingress-nginx \
        --context "${context}" \
        --type merge \
        -p '{"data":{"allow-snippet-annotations":"true","annotations-risk-level":"Critical"}}' 2>/dev/null; then
        warning "Could not patch ingress-nginx ConfigMap (may not exist yet)"
        # Try creating the ConfigMap if it doesn't exist
        kubectl create configmap ingress-nginx-controller \
            -n ingress-nginx \
            --context "${context}" \
            --from-literal=allow-snippet-annotations=true \
            --from-literal=annotations-risk-level=Critical 2>/dev/null || true
    fi
    
    # Restart the ingress controller to pick up the configuration change
    progress "Restarting ingress controller to apply configuration..."
    kubectl rollout restart deployment ingress-nginx-controller \
        -n ingress-nginx \
        --context "${context}" 2>/dev/null || true
    
    # Wait for the controller to be ready again
    progress "Waiting for ingress controller to restart..."
    kubectl wait --namespace ingress-nginx \
        --for=condition=ready pod \
        --selector=app.kubernetes.io/component=controller \
        --timeout=60s \
        --context "${context}" 2>/dev/null || true
    
    success "NGINX snippet annotations enabled (development mode)"
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
#   configure_coredns_custom_hosts "structures-cluster" "structures.local"
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
    
    # Update CoreDNS ConfigMap by replacing "ready" with hosts block + ready
    progress "Patching CoreDNS ConfigMap..."
    
    # Create a temporary file for the patch
    local tmpfile
    tmpfile=$(mktemp)
    cat > "${tmpfile}" << COREDNS_EOF
apiVersion: v1
kind: ConfigMap
metadata:
  name: coredns
  namespace: kube-system
data:
  Corefile: |
    .:53 {
      errors
      health {
          lameduck 5s
      }
      ready
      hosts {
        ${ingress_ip} ${hostname}
        fallthrough
      }
      kubernetes cluster.local in-addr.arpa ip6.arpa {
          pods insecure
          fallthrough in-addr.arpa ip6.arpa
          ttl 30
      }
      prometheus :9153
      forward . /etc/resolv.conf {
          max_concurrent 1000
      }
      cache 30 {
          disable success cluster.local
          disable denial cluster.local
      }
      loop
      reload
      loadbalance
    }
COREDNS_EOF
    
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
# Install cert-manager for TLS certificate management
# Args:
#   $1: kubectl context
# Returns:
#   0 on success, 1 on failure
# Example:
#   install_cert_manager "kind-structures-cluster"
#
install_cert_manager() {
    local context="$1"
    
    # Check if cert-manager is already installed
    if kubectl get namespace cert-manager --context "${context}" &>/dev/null; then
        verbose "cert-manager already installed"
        return 0
    fi
    
    progress "Installing cert-manager..."
    
    # Apply cert-manager CRDs and components
    if ! kubectl apply -f https://github.com/cert-manager/cert-manager/releases/download/v1.14.0/cert-manager.yaml \
        --context "${context}" 2>&1 | grep -v "Warning:"; then
        error "Failed to apply cert-manager manifest"
        return 1
    fi
    
    # Wait for cert-manager to be ready
    progress "Waiting for cert-manager to be ready (up to 120s)..."
    if ! kubectl wait --for=condition=Available deployment --all \
        -n cert-manager \
        --timeout=120s \
        --context "${context}" 2>&1; then
        warning "cert-manager deployments did not become ready in time"
        progress "Checking pod status:"
        kubectl get pods -n cert-manager --context "${context}"
        return 1
    fi
    
    # Wait for webhook to be ready (important for Certificate resources)
    progress "Waiting for cert-manager webhook to be ready..."
    local retries=30
    while [[ ${retries} -gt 0 ]]; do
        if kubectl get validatingwebhookconfigurations cert-manager-webhook --context "${context}" &>/dev/null; then
            break
        fi
        sleep 2
        retries=$((retries - 1))
    done
    
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
#   setup_tls "kind-structures-cluster" "default"
#
setup_tls() {
    local context="$1"
    local namespace="${2:-default}"
    local secret_name="structures-tls-secret"
    
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
# Deploy Elasticsearch using official Elastic image
# Args:
#   $1: Cluster name
# Returns:
#   0 on success, 1 on failure
# Example:
#   deploy_elasticsearch "structures-cluster"
#
deploy_elasticsearch() {
    local cluster_name="$1"
    local context="kind-${cluster_name}"
    
    progress "Deploying Elasticsearch..."
    
    # Elasticsearch version (matching docker-compose compose.ek-stack.yml)
    local es_version="8.18.1"
    local es_image="docker.elastic.co/elasticsearch/elasticsearch:${es_version}"
    local local_tag="localhost/elasticsearch:${es_version}"
    
    # Detect host platform for image pulling
    local platform="linux/amd64"
    if [[ "$(uname -m)" == "arm64" ]] || [[ "$(uname -m)" == "aarch64" ]]; then
        platform="linux/arm64"
    fi
    
    # Pre-pull image for current platform and re-tag to avoid multi-platform issues
    progress "Pre-loading Elasticsearch image into cluster..."
    if ! docker image inspect "${local_tag}" &>/dev/null; then
        progress "Pulling ${es_image} for ${platform}..."
        # Pull for specific platform
        if ! docker pull --platform "${platform}" "${es_image}"; then
            error "Failed to pull Elasticsearch image"
            return 1
        fi
        
        # Re-tag to local name to create clean single-platform reference
        progress "Re-tagging image to local reference..."
        if ! docker tag "${es_image}" "${local_tag}"; then
            error "Failed to tag Elasticsearch image"
            return 1
        fi
    fi
    
    # Load the locally-tagged image into KinD (avoids multi-platform issues)
    progress "Loading image into KinD cluster..."
    if ! kind load docker-image "${local_tag}" --name "${cluster_name}"; then
        error "Failed to load Elasticsearch image into cluster"
        return 1
    fi
    
    # Also tag in the cluster as the original name so pods can find it
    progress "Tagging image in cluster nodes..."
    for node in $(kind get nodes --name "${cluster_name}"); do
        docker exec "${node}" ctr -n k8s.io images tag "${local_tag}" "${es_image}" || true
    done
    
    # Create custom values file for single-node Elasticsearch
    progress "Creating Elasticsearch configuration..."
    local values_file="/tmp/elasticsearch-values-${RANDOM}.yaml"
    cat > "${values_file}" <<EOF
# Single-node Elasticsearch configuration for KinD
# Matches docker-compose/compose.ek-stack.yml

replicas: 2
minimumMasterNodes: 1

image: "docker.elastic.co/elasticsearch/elasticsearch"
imageTag: "${es_version}"
imagePullPolicy: IfNotPresent

# Disable all security for local dev (matching docker-compose)
# Disable X-Pack security for local development
# Must be disabled at protocol AND security level
protocol: http
httpPort: 9200
transportPort: 9300

# Disable security completely
createCert: false
secret:
  enabled: false

# Disable X-Pack security for local development
# This must be at the top level AND in esConfig
security:
  enabled: false

# Set ELASTIC_PASSWORD - required by the readiness probe script even when security is disabled
extraEnvs:
  - name: ELASTIC_PASSWORD
    value: "-"

# Elasticsearch configuration
esConfig:
  elasticsearch.yml: |
    xpack:
      security:
        enabled: false
        http.ssl.enabled: false
        transport.ssl.enabled: false
    discovery:
      # type: single-node
      # seed_providers: ""
      seed_hosts: ["elasticsearch-master-headless"]
    cluster:
      initial_master_nodes: ["elasticsearch-master-0"]
    # node:
    #   roles: ["master", "data", "data_content", "data_hot", "data_warm", "data_cold", "ingest", "ml", "remote_cluster_client", "transform"]
  # log4j2.properties: |
  #   logger.discovery.name = org.elasticsearch.discovery
  #   logger.discovery.level = debug

# Elasticsearch roles that will be applied to this nodeGroup
# These will be set as environment variables. E.g. node.roles=master
# https://www.elastic.co/guide/en/elasticsearch/reference/current/modules-node.html#node-roles
roles: []

# Expose Elasticsearch via NodePort for external access
service:
  type: NodePort
  nodePort: 30920  # Maps to hostPort 9200 → localhost:9200

# Resource limits (matching structures needs)
esJavaOpts: "-Xms1024m -Xmx1024m"
resources:
  requests:
    cpu: "500m"
    memory: "2Gi"
  limits:
    memory: "2Gi"

# Volume config - use emptyDir for local dev
volumeClaimTemplate:
  accessModes: ["ReadWriteOnce"]
  resources:
    requests:
      storage: 15Gi

persistence:
  enabled: false

# Elasticsearch doesn't need ingress - accessed internally or via NodePort
ingress:
  enabled: false

# Health check
clusterHealthCheckParams: "wait_for_status=yellow&timeout=1s"

# Soft anti-affinity
antiAffinity: "soft"

# Single-node doesn't need service mesh
sysctlVmMaxMapCount: 262144
EOF
    
    # Deploy using values file
    local helm_output
    helm_output=$(helm upgrade --install elasticsearch elastic/elasticsearch \
        --kube-context "${context}" \
        --version 8.5.1 \
        --values "${values_file}" \
        --wait --timeout 5m 2>&1)
    
    local exit_code=$?
    rm -f "${values_file}"
    
    if [[ ${exit_code} -ne 0 ]]; then
        error "Failed to deploy Elasticsearch"
        echo ""
        echo "Helm output:"
        echo "${helm_output}"
        echo ""
        echo "Check pod status:"
        echo "  kubectl get pods -l app=elasticsearch-master --context ${context}"
        echo "Check pod logs:"
        echo "  kubectl logs -l app=elasticsearch-master --context ${context}"
        echo "Check events:"
        echo "  kubectl get events --context ${context} --sort-by='.lastTimestamp' | tail -20"
        echo ""
        return 1
    fi
    
    success "Elasticsearch deployed (1/1 pods ready)"
    return 0
}

#
# Deploy structures-server via Helm
# Args:
#   $1: Cluster name
#   $2: Additional Helm set flags (optional)
# Returns:
#   0 on success, 1 on failure
# Example:
#   deploy_structures_server "structures-cluster" "--set replicaCount=3"
#
deploy_structures_server() {
    local cluster_name="$1"
    local context="kind-${cluster_name}"
    local additional_sets="${2:-}"
    
    progress "Deploying structures-server..."
    
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
        helm upgrade --install structures-server "${HELM_CHART_PATH}"
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
    helm_cmd+=(--wait --timeout "${DEPLOY_TIMEOUT}" --atomic)
    
    # Show the helm command being executed (for debugging)
    verbose "Executing: ${helm_cmd[*]}"
    
    # Execute helm command with detailed output
    progress "Installing/upgrading structures-server Helm release..."
    local helm_output
    helm_output=$("${helm_cmd[@]}" 2>&1)
    local helm_result=$?
    
    # Always show helm output for transparency
    echo "${helm_output}" | while IFS= read -r line; do
        progress "  ${line}"
    done
    
    if [[ ${helm_result} -ne 0 ]]; then
        error "Failed to deploy structures-server"
        echo ""
        
        # Show detailed diagnostics
        progress "Troubleshooting information:"
        echo ""
        
        progress "1. Checking pod status..."
        kubectl get pods --context "${context}" -l app.kubernetes.io/name=structures-server 2>&1 || true
        echo ""
        
        progress "2. Checking pod details..."
        kubectl describe pods --context "${context}" -l app.kubernetes.io/name=structures-server 2>&1 | tail -50 || true
        echo ""
        
        progress "3. Checking recent events..."
        kubectl get events --context "${context}" \
            --field-selector type!=Normal \
            --sort-by='.lastTimestamp' 2>&1 | tail -20 || true
        echo ""
        
        progress "4. Checking service status..."
        kubectl get svc --context "${context}" -l app.kubernetes.io/name=structures-server 2>&1 || true
        echo ""
        
        # Check if there are any existing pods with logs
        local pod_count
        pod_count=$(kubectl get pods --context "${context}" \
            -l app.kubernetes.io/name=structures-server \
            -o name 2>/dev/null | wc -l | tr -d ' ')
        
        if [[ ${pod_count} -gt 0 ]]; then
            progress "5. Recent logs from structures-server pod(s):"
            kubectl logs --context "${context}" \
                -l app.kubernetes.io/name=structures-server \
                --tail=50 \
                --all-containers=true 2>&1 || true
        else
            warning "No structures-server pods found to retrieve logs from"
        fi
        
        return 1
    fi
    
    # Verify deployment
    progress "Verifying deployment..."
    local ready_pods
    ready_pods=$(kubectl get pods --context "${context}" \
        -l app.kubernetes.io/name=structures-server \
        -o jsonpath='{.items[*].status.conditions[?(@.type=="Ready")].status}' 2>/dev/null | grep -c "True" || echo "0")

    echo "Ready pods: ${ready_pods}"
    
    if [[ ${ready_pods} -lt 1 ]]; then
        warning "Deployment completed but pods are not ready yet"
        progress "Current pod status:"
        kubectl get pods --context "${context}" -l app.kubernetes.io/name=structures-server 2>&1 || true
    else
        success "structures-server deployed successfully (${ready_pods} pod(s) ready)"
        
        # Show service information
        progress "Service endpoints:"
        kubectl get svc --context "${context}" -l app.kubernetes.io/name=structures-server 2>&1 || true
    fi
    
    return 0
}

#
# Wait for pods to be ready
# Args:
#   $1: Cluster name
#   $2: Selector (e.g., "app=structures-server")
#   $3: Expected count
#   $4: Timeout in seconds (default: 300)
# Returns:
#   0 if ready, 1 on timeout
# Example:
#   wait_for_pods_ready "structures-cluster" "app=structures-server" 2 300
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
#   display_deployment_status "structures-cluster"
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
    
    progress "Via NodePort (direct, no TLS):"
    progress "  http://localhost:9090      - Static UI"
    progress "  http://localhost:8080      - OpenAPI REST"
    progress "  http://localhost:4000      - GraphQL"
    progress "  ws://localhost:58503       - STOMP WebSocket"
    blank_line
    
    progress "Health check: http://localhost:9090/health"

    if [[ "${OIDC_ENABLED:-false}" == "true" ]]; then
        blank_line
        progress "Keycloak: "
        progress "  Test Structures User: testuser@example.com/password123"
        progress "  http://localhost:8888/auth"
        progress "  Admin: http://localhost:8888/auth/admin (admin/admin)"
    fi
    
    blank_line
}

