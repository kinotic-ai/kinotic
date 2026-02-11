#!/usr/bin/env bash
#
# Logging Functions for KinD Cluster Tools
# Provides consistent, formatted output with color support
#

# Prevent re-sourcing
[[ -n "${_KIND_LOGGING_LOADED:-}" ]] && return 0
readonly _KIND_LOGGING_LOADED=1

# Color codes for terminal output
readonly COLOR_RED='\033[0;31m'
readonly COLOR_GREEN='\033[0;32m'
readonly COLOR_YELLOW='\033[1;33m'
readonly COLOR_BLUE='\033[0;34m'
readonly COLOR_CYAN='\033[0;36m'
readonly COLOR_RESET='\033[0m'

# Unicode symbols for status indicators
readonly SYMBOL_SUCCESS="✓"
readonly SYMBOL_ERROR="✗"
readonly SYMBOL_WARNING="⚠️"
readonly SYMBOL_INFO="ℹ"
readonly SYMBOL_PROGRESS="→"

# Global verbose flag (set by main script)
VERBOSE="${VERBOSE:-0}"

#
# Print informational message
# Args:
#   $1: Message to print
# Example:
#   info "Starting cluster creation..."
#
info() {
    local message="$1"
    echo -e "${COLOR_BLUE}${SYMBOL_INFO}${COLOR_RESET} ${message}"
}

#
# Print success message
# Args:
#   $1: Message to print
# Example:
#   success "Cluster created successfully!"
#
success() {
    local message="$1"
    echo -e "${COLOR_GREEN}${SYMBOL_SUCCESS}${COLOR_RESET} ${message}"
}

#
# Print error message to stderr
# Args:
#   $1: Message to print
# Example:
#   error "Failed to create cluster"
#
error() {
    local message="$1"
    echo -e "${COLOR_RED}${SYMBOL_ERROR} ERROR:${COLOR_RESET} ${message}" >&2
}

#
# Print warning message
# Args:
#   $1: Message to print
# Example:
#   warning "Cluster already exists, use --force to recreate"
#
warning() {
    local message="$1"
    echo -e "${COLOR_YELLOW}${SYMBOL_WARNING}${COLOR_RESET} ${message}"
}

#
# Print verbose/debug message (only if VERBOSE=1)
# Args:
#   $1: Message to print
# Example:
#   verbose "Executing: kind create cluster..."
#
verbose() {
    local message="$1"
    if [[ "${VERBOSE}" == "1" ]]; then
        echo -e "${COLOR_CYAN}${SYMBOL_PROGRESS}${COLOR_RESET} ${message}"
    fi
}

#
# Print progress message with indentation
# Args:
#   $1: Message to print
# Example:
#   progress "Control plane node: ready"
#
progress() {
    local message="$1"
    echo -e "  ${COLOR_CYAN}${SYMBOL_PROGRESS}${COLOR_RESET} ${message}"
}

#
# Print section header
# Args:
#   $1: Section title
# Example:
#   section "Deploying Dependencies"
#
section() {
    local title="$1"
    echo ""
    echo -e "${COLOR_GREEN}${SYMBOL_SUCCESS}${COLOR_RESET} ${title}"
}

#
# Print command being executed (if verbose mode)
# Args:
#   $@: Command and arguments
# Example:
#   log_command kubectl get nodes
#
log_command() {
    if [[ "${VERBOSE}" == "1" ]]; then
        echo -e "${COLOR_CYAN}  $ ${*}${COLOR_RESET}"
    fi
}

#
# Print fatal error and exit
# Args:
#   $1: Error message
#   $2: Exit code (optional, defaults to 1)
# Example:
#   fatal "Docker daemon not running" 2
#
fatal() {
    local message="$1"
    local exit_code="${2:-1}"
    error "${message}"
    exit "${exit_code}"
}

#
# Print blank line
#
blank_line() {
    echo ""
}

