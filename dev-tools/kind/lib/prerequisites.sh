#!/usr/bin/env bash
#
# Prerequisite Checking Functions for KinD Cluster Tools
# Validates required tools are installed and provides installation guidance
#

# Prevent re-sourcing
[[ -n "${_KIND_PREREQUISITES_LOADED:-}" ]] && return 0
readonly _KIND_PREREQUISITES_LOADED=1

# Source logging functions
LIB_SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=dev-tools/kind/lib/logging.sh
source "${LIB_SCRIPT_DIR}/logging.sh"

#
# Detect operating system
# Returns:
#   "macos", "linux", or "unknown"
# Example:
#   os=$(detect_os)
#
detect_os() {
    case "$(uname -s)" in
        Darwin*)
            echo "macos"
            ;;
        Linux*)
            echo "linux"
            ;;
        *)
            echo "unknown"
            ;;
    esac
}

#
# Detect Linux distribution
# Returns:
#   "ubuntu", "debian", "rhel", "centos", "fedora", or "unknown"
# Example:
#   distro=$(detect_linux_distro)
#
detect_linux_distro() {
    if [[ -f /etc/os-release ]]; then
        # shellcheck source=/dev/null
        source /etc/os-release
        case "${ID}" in
            ubuntu)
                echo "ubuntu"
                ;;
            debian)
                echo "debian"
                ;;
            rhel|centos)
                echo "rhel"
                ;;
            fedora)
                echo "fedora"
                ;;
            *)
                echo "unknown"
                ;;
        esac
    else
        echo "unknown"
    fi
}

#
# Get installation instructions for a tool
# Args:
#   $1: Tool name (docker, kind, kubectl, helm)
# Returns:
#   Installation instructions for current OS
# Example:
#   get_installation_instructions docker
#
get_installation_instructions() {
    local tool="$1"
    local os
    os=$(detect_os)
    
    case "${tool}" in
        docker)
            case "${os}" in
                macos)
                    echo "Install Docker Desktop for Mac:"
                    echo "  https://docs.docker.com/desktop/install/mac-install/"
                    echo "  Or via Homebrew:"
                    echo "  brew install --cask docker"
                    ;;
                linux)
                    local distro
                    distro=$(detect_linux_distro)
                    case "${distro}" in
                        ubuntu|debian)
                            echo "Install Docker Engine:"
                            echo "  sudo apt-get update"
                            echo "  sudo apt-get install docker.io"
                            echo "  sudo systemctl start docker"
                            echo "  sudo usermod -aG docker \$USER"
                            ;;
                        rhel|centos|fedora)
                            echo "Install Docker Engine:"
                            echo "  sudo yum install docker"
                            echo "  sudo systemctl start docker"
                            echo "  sudo usermod -aG docker \$USER"
                            ;;
                        *)
                            echo "Install Docker Engine for your distribution"
                            echo "  https://docs.docker.com/engine/install/"
                            ;;
                    esac
                    ;;
                *)
                    echo "Install Docker from: https://docs.docker.com/get-docker/"
                    ;;
            esac
            ;;
        kind)
            case "${os}" in
                macos)
                    echo "Install KinD via Homebrew:"
                    echo "  brew install kind"
                    echo "Or download binary:"
                    echo "  https://kind.sigs.k8s.io/docs/user/quick-start/#installation"
                    ;;
                linux)
                    echo "Install KinD:"
                    echo "  curl -Lo ./kind https://kind.sigs.k8s.io/dl/v0.20.0/kind-linux-amd64"
                    echo "  chmod +x ./kind"
                    echo "  sudo mv ./kind /usr/local/bin/kind"
                    ;;
                *)
                    echo "Install KinD from: https://kind.sigs.k8s.io/docs/user/quick-start/#installation"
                    ;;
            esac
            ;;
        kubectl)
            case "${os}" in
                macos)
                    echo "Install kubectl via Homebrew:"
                    echo "  brew install kubectl"
                    echo "Or download binary:"
                    echo "  https://kubernetes.io/docs/tasks/tools/install-kubectl-macos/"
                    ;;
                linux)
                    echo "Install kubectl:"
                    echo "  curl -LO \"https://dl.k8s.io/release/\$(curl -L -s https://dl.k8s.io/release/stable.txt)/bin/linux/amd64/kubectl\""
                    echo "  chmod +x kubectl"
                    echo "  sudo mv kubectl /usr/local/bin/"
                    ;;
                *)
                    echo "Install kubectl from: https://kubernetes.io/docs/tasks/tools/"
                    ;;
            esac
            ;;
        helm)
            case "${os}" in
                macos)
                    echo "Install Helm via Homebrew:"
                    echo "  brew install helm"
                    echo "Or download binary:"
                    echo "  https://helm.sh/docs/intro/install/"
                    ;;
                linux)
                    echo "Install Helm:"
                    echo "  curl https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 | bash"
                    ;;
                *)
                    echo "Install Helm from: https://helm.sh/docs/intro/install/"
                    ;;
            esac
            ;;
        *)
            echo "No installation instructions available for: ${tool}"
            ;;
    esac
}

#
# Check if Docker daemon is running
# Returns:
#   0 if running, 1 otherwise
# Example:
#   check_docker_running || exit 1
#
check_docker_running() {
    if ! docker ps &> /dev/null; then
        error "Docker daemon is not running"
        echo ""
        echo "Details:"
        echo "  The Docker daemon must be running to create KinD clusters."
        echo ""
        echo "Remediation:"
        local os
        os=$(detect_os)
        case "${os}" in
            macos)
                echo "  - Start Docker Desktop application"
                echo "  - Wait for Docker to be ready (check menu bar icon)"
                ;;
            linux)
                echo "  - Start Docker service: sudo systemctl start docker"
                echo "  - Enable Docker on boot: sudo systemctl enable docker"
                ;;
            *)
                echo "  - Start Docker daemon for your system"
                ;;
        esac
        echo "  - Verify Docker is running: docker ps"
        echo ""
        return 1
    fi
    return 0
}

#
# Check if a tool is installed
# Args:
#   $1: Tool name
#   $2: Minimum version (optional)
# Returns:
#   0 if installed, 1 otherwise
# Example:
#   check_tool_installed kubectl || exit 1
#
check_tool_installed() {
    local tool="$1"
    local min_version="${2:-}"
    
    if ! command -v "${tool}" &> /dev/null; then
        error "${tool} is not installed"
        echo ""
        get_installation_instructions "${tool}"
        echo ""
        return 1
    fi
    
    # Get version for informational purposes
    local version=""
    case "${tool}" in
        docker)
            version=$(docker --version 2>&1 | grep -oE '[0-9]+\.[0-9]+\.[0-9]+' | head -1)
            ;;
        kind)
            version=$(kind version 2>&1 | grep -oE 'v[0-9]+\.[0-9]+\.[0-9]+' | head -1)
            ;;
        kubectl)
            version=$(kubectl version --client --short 2>&1 | grep -oE 'v[0-9]+\.[0-9]+\.[0-9]+' | head -1)
            ;;
        helm)
            version=$(helm version --short 2>&1 | grep -oE 'v[0-9]+\.[0-9]+\.[0-9]+' | head -1)
            ;;
    esac
    
    if [[ -n "${version}" ]]; then
        verbose "${tool}: ${version}"
    else
        verbose "${tool}: installed"
    fi
    
    return 0
}

#
# Check all prerequisites
# Returns:
#   0 if all prerequisites met, EXIT_PREREQUISITES_NOT_MET otherwise
# Example:
#   check_prerequisites || exit $?
#
check_prerequisites() {
    local all_ok=0
    
    section "Checking Prerequisites"
    
    # Check Docker
    if check_tool_installed docker; then
        success "Docker: installed"
    else
        all_ok="${EXIT_PREREQUISITES_NOT_MET}"
    fi
    
    # Check Docker daemon
    if [[ ${all_ok} -eq 0 ]]; then
        if check_docker_running; then
            success "Docker daemon: running"
        else
            all_ok="${EXIT_PREREQUISITES_NOT_MET}"
        fi
    fi
    
    # Check KinD
    if check_tool_installed kind; then
        success "KinD: installed"
    else
        all_ok="${EXIT_PREREQUISITES_NOT_MET}"
    fi
    
    # Check kubectl
    if check_tool_installed kubectl; then
        success "kubectl: installed"
    else
        all_ok="${EXIT_PREREQUISITES_NOT_MET}"
    fi
    
    # Check Helm
    if check_tool_installed helm; then
        success "Helm: installed"
    else
        all_ok="${EXIT_PREREQUISITES_NOT_MET}"
    fi
    
    blank_line
    
    if [[ ${all_ok} -eq 0 ]]; then
        success "All prerequisites met!"
        return 0
    else
        error "Some prerequisites are missing. Please install them and try again."
        return "${EXIT_PREREQUISITES_NOT_MET}"
    fi
}

