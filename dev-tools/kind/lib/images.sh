#!/usr/bin/env bash
#
# Image Build and Load Functions for structures-server
# Handles building Docker images with bootBuildImage and loading into KinD
#

# Prevent re-sourcing
[[ -n "${_KIND_IMAGES_LOADED:-}" ]] && return 0
readonly _KIND_IMAGES_LOADED=1

# Source dependencies
LIB_SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=dev-tools/kind/lib/logging.sh
source "${LIB_SCRIPT_DIR}/logging.sh"
# shellcheck source=dev-tools/kind/lib/config.sh
source "${LIB_SCRIPT_DIR}/config.sh"

#
# Build Docker image using Gradle bootBuildImage
# Args:
#   $1: Module name (e.g., "structures-server")
# Returns:
#   0 on success, 1 on failure
# Example:
#   build_image "structures-server"
#
build_image() {
    local module="$1"
    
    info "Building ${module} with Gradle..."
    
    # Check if Gradle wrapper exists
    if [[ ! -f "./gradlew" ]]; then
        error "Gradle wrapper not found. Are you in the project root?"
        return 1
    fi
    
    # Clean the project
    progress "Running: ./gradlew clean"
    execute ./gradlew "clean"
    if [[ $? -ne 0 ]]; then
        error "Structures Server: Gradle clean failed"
        return 1
    fi
    success "Gradle clean completed"
    blank_line
    
    # Run bootBuildImage
    progress "Running: ./gradlew :${module}:bootBuildImage"
    progress "This may take a few minutes..."
    blank_line

    export RUNNING_KIND_CLUSTER="true"
    if ! execute ./gradlew ":${module}:bootBuildImage" 2>&1 | while IFS= read -r line; do
        # Filter out excessive Gradle output, show important lines
        if [[ "${line}" == *"BUILD"* ]] || \
           [[ "${line}" == *"Running in"* ]] || \
           [[ "${line}" == *"Successfully built"* ]] || \
           [[ "${line}" == *"Paketo"* ]] || \
           [[ "${line}" == *"Error"* ]] || \
           [[ "${line}" == *"FAIL"* ]] || \
           [[ "${VERBOSE}" == "1" ]]; then
            echo "  ${line}"
        fi
    done; then
        error "Gradle bootBuildImage failed"
        echo ""
        echo "Troubleshooting:"
        echo "  - Check Java version (requires Java 21+)"
        echo "  - Check Docker daemon is running"
        echo "  - Try with --verbose flag for full output"
        echo ""
        return 1
    fi
    
    blank_line
    success "Image built successfully!"
    return 0
}

#
# Check if Docker image exists locally
# Args:
#   $1: Image name (e.g., "kinotic/structures-server:0.5.0-SNAPSHOT")
# Returns:
#   0 if exists, 1 otherwise
# Example:
#   image_exists "kinotic/structures-server:0.5.0-SNAPSHOT"
#
image_exists() {
    local image_name="$1"
    
    if docker images --format "{{.Repository}}:{{.Tag}}" | grep -q "^${image_name}$"; then
        return 0
    else
        return 1
    fi
}

#
# Get image information
# Args:
#   $1: Image name
# Example:
#   get_image_info "kinotic/structures-server:0.5.0-SNAPSHOT"
#
get_image_info() {
    local image_name="$1"
    
    if ! image_exists "${image_name}"; then
        error "Image not found: ${image_name}"
        return 1
    fi
    
    blank_line
    section "Image Information"
    
    # Get image details
    local image_id
    image_id=$(docker images --format "{{.ID}}" "${image_name}" | head -1)
    local created
    created=$(docker images --format "{{.CreatedSince}}" "${image_name}" | head -1)
    local size
    size=$(docker images --format "{{.Size}}" "${image_name}" | head -1)
    
    progress "Image: ${image_name}"
    progress "ID: ${image_id}"
    progress "Created: ${created}"
    progress "Size: ${size}"
    
    # Show build info if available
    if docker inspect "${image_name}" &>/dev/null; then
        local labels
        labels=$(docker inspect -f '{{range $k, $v := .Config.Labels}}{{$k}}={{$v}}{{"\n"}}{{end}}' "${image_name}" 2>/dev/null | grep -E "(io.buildpacks|org.opencontainers)" || true)
        
        if [[ -n "${labels}" ]]; then
            blank_line
            progress "Buildpack Information:"
            echo "${labels}" | while IFS= read -r label; do
                verbose "  ${label}"
            done
        fi
    fi
    
    blank_line
    return 0
}

#
# Load Docker image into KinD cluster
# Args:
#   $1: Cluster name
#   $2: Image name
# Returns:
#   0 on success, 1 on failure
# Example:
#   load_image_into_cluster "structures-cluster" "kinotic/structures-server:0.5.0-SNAPSHOT"
#
load_image_into_cluster() {
    local cluster_name="$1"
    local image_name="$2"
    
    # Verify cluster exists
    if ! kind get clusters 2>/dev/null | grep -q "^${cluster_name}$"; then
        error "Cluster '${cluster_name}' does not exist"
        echo ""
        echo "Create cluster first: $(basename "$0") create"
        echo ""
        return 1
    fi
    
    # Verify image exists
    if ! image_exists "${image_name}"; then
        error "Image '${image_name}' not found locally"
        echo ""
        echo "Build image first: $(basename "$0") build"
        echo ""
        return 1
    fi
    
    info "Loading image into cluster '${cluster_name}'..."
    progress "Image: ${image_name}"
    blank_line
    
    # Load image into KinD
    if ! execute kind load docker-image "${image_name}" --name "${cluster_name}" 2>&1 | while IFS= read -r line; do
        progress "${line}"
    done; then
        error "Failed to load image into cluster"
        return 1
    fi
    
    blank_line
    success "Image loaded successfully!"
    
    # Verify image in cluster nodes
    progress "Verifying image on cluster nodes..."
    local control_plane_node="${cluster_name}-control-plane"
    
    if docker exec "${control_plane_node}" crictl images 2>/dev/null | grep -q "${image_name%%:*}"; then
        success "Image verified on cluster nodes"
    else
        warning "Could not verify image on cluster nodes (may still be available)"
    fi
    
    blank_line
    return 0
}

#
# Display image deployment instructions
# Args:
#   $1: Image name
# Example:
#   display_deployment_instructions "kinotic/structures-server:0.5.0-SNAPSHOT"
#
display_deployment_instructions() {
    local image_name="$1"
    local repository="${image_name%%:*}"
    local tag="${image_name##*:}"
    
    section "Next Steps"
    progress "Deploy with this image:"
    echo ""
    echo "  $(basename "$0") deploy \\"
    echo "    --set image.repository=${repository} \\"
    echo "    --set image.tag=${tag} \\"
    echo "    --set image.pullPolicy=Never"
    echo ""
    
    progress "Or update existing deployment:"
    echo ""
    echo "  kubectl set image deployment/structures-server \\"
    echo "    structures-server=${image_name}"
    echo ""
    
    blank_line
}

