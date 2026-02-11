#!/usr/bin/env bash
# =============================================================================
# Firecracker Host Networking Setup (nft-based NAT)
# Configures host networking infrastructure for Firecracker VMs
# Sets up nft table and chains for NAT-based routing
# Based on: https://github.com/firecracker-microvm/firecracker/blob/main/docs/network-setup.md
# =============================================================================

set -euo pipefail

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

log()   { echo -e "${GREEN}[INFO]${NC} $*"; }
warn()  { echo -e "${YELLOW}[WARN]${NC} $*"; }
error() { echo -e "${RED}[ERROR]${NC} $*"; }

NFT_TABLE="firecracker"
VM_SUBNET="${VM_SUBNET:-172.16.0.0/16}"
ACTION="${1:-setup}"

# Auto-detect host interface
detect_host_interface() {
    HOST_IFACE=$(ip route | grep default | awk '{print $5}' | head -n1)
    if [[ -z "$HOST_IFACE" ]]; then
        error "Could not detect default network interface. Please set HOST_IFACE environment variable."
        exit 1
    fi
    log "Auto-detected host interface: $HOST_IFACE"
}

# Setup networking infrastructure
setup_networking() {
    log "Setting up Firecracker host networking infrastructure (nft-based NAT)..."

    detect_host_interface

    # Validate that nft is available
    if ! command -v nft >/dev/null 2>&1; then
        error "nft is not installed. Please install nftables: sudo apt-get install nftables"
        exit 1
    fi

    # Enable IPv4 forwarding
    if [[ $(cat /proc/sys/net/ipv4/ip_forward) != "1" ]]; then
        log "Enabling IPv4 forwarding..."
        echo 1 | sudo tee /proc/sys/net/ipv4/ip_forward >/dev/null
    else
        log "IPv4 forwarding already enabled"
    fi

    # Create nft table (idempotent)
    if ! sudo nft list table "$NFT_TABLE" >/dev/null 2>&1; then
        log "Creating nft table: $NFT_TABLE"
        sudo nft add table "$NFT_TABLE"
    else
        log "Nft table '$NFT_TABLE' already exists"
    fi

    # Create postrouting chain for NAT (idempotent)
    if ! sudo nft list chain "$NFT_TABLE" postrouting >/dev/null 2>&1; then
        log "Creating postrouting chain for NAT..."
        sudo nft 'add chain '"$NFT_TABLE"' postrouting { type nat hook postrouting priority srcnat; policy accept; }'
    else
        log "Postrouting chain already exists"
    fi

    # Create filter chain for forwarding (idempotent)
    if ! sudo nft list chain "$NFT_TABLE" filter >/dev/null 2>&1; then
        log "Creating filter chain for forwarding..."
        sudo nft 'add chain '"$NFT_TABLE"' filter { type filter hook forward priority filter; policy accept; }'
    else
        log "Filter chain already exists"
    fi

    # Add masquerade rule for VM subnet (idempotent)
    # This rule masquerades ALL traffic from the VM subnet (e.g., 172.16.0.0/16)
    # when leaving the host interface, so any VM IP in that range will be NAT'd
    if sudo nft list chain "$NFT_TABLE" postrouting 2>/dev/null | grep -q "masquerade"; then
        log "Masquerade rule already exists"
    else
        log "Adding masquerade rule for VM subnet $VM_SUBNET on $HOST_IFACE..."
        sudo nft add rule "$NFT_TABLE" postrouting ip saddr "$VM_SUBNET" oifname "$HOST_IFACE" counter masquerade
    fi

    # Add forward rule for ALL tap devices (idempotent)
    # This rule accepts traffic from any tap device (tap0, tap1, tap2, etc.) going to the host interface
    # Using wildcard "tap*" so we don't need individual rules for each tap device
    if sudo nft list chain "$NFT_TABLE" filter 2>/dev/null | grep -q 'iifname "tap'; then
        log "Forward rule for tap devices already exists"
    else
        log "Adding forward rule for all tap devices (tap*) to $HOST_IFACE..."
        sudo nft add rule "$NFT_TABLE" filter iifname "tap*" oifname "$HOST_IFACE" accept
    fi

    log "Host networking infrastructure setup complete!"
    echo
    log "Configuration summary:"
    echo "  Nft table:     $NFT_TABLE"
    echo "  VM subnet:     $VM_SUBNET"
    echo "  Host iface:    $HOST_IFACE"
    echo "  IPv4 forward:  enabled"
    echo
    log "Current nft rules:"
    sudo nft list table "$NFT_TABLE"
    echo
    warn "Note: These changes are NOT persistent across reboots."
    warn "Run '$0 persist' to make them persistent, or run '$0 setup' again after reboot."
}

# Make configuration persistent across reboots
persist_config() {
    log "Making Firecracker networking configuration persistent..."

    detect_host_interface

    # Make IPv4 forwarding persistent via sysctl
    SYSCTL_FILE="/etc/sysctl.d/99-firecracker.conf"
    if [[ -f "$SYSCTL_FILE" ]] && grep -q "net.ipv4.ip_forward" "$SYSCTL_FILE"; then
        log "IPv4 forwarding already configured in $SYSCTL_FILE"
    else
        log "Adding IPv4 forwarding to $SYSCTL_FILE..."
        echo "net.ipv4.ip_forward = 1" | sudo tee -a "$SYSCTL_FILE" >/dev/null
    fi

    # Make nftables rules persistent
    # Save current rules to a file that nftables can load on boot
    NFT_RULES_FILE="/etc/nftables/firecracker.nft"
    log "Saving nftables rules to $NFT_RULES_FILE..."

    # Create directory if it doesn't exist
    sudo mkdir -p /etc/nftables

    # Generate the rules file
    sudo tee "$NFT_RULES_FILE" >/dev/null <<EOF
#!/usr/sbin/nft -f
# Firecracker networking rules - auto-generated
# Do not edit manually, regenerate with: $0 persist

table ip $NFT_TABLE {
    chain postrouting {
        type nat hook postrouting priority srcnat; policy accept;
        ip saddr $VM_SUBNET oifname "$HOST_IFACE" counter masquerade
    }

    chain filter {
        type filter hook forward priority filter; policy accept;
        iifname "tap*" oifname "$HOST_IFACE" accept
    }
}
EOF

    # Make sure nftables service loads this file on boot
    # Check if nftables.conf includes our file
    NFTABLES_CONF="/etc/nftables.conf"
    if [[ -f "$NFTABLES_CONF" ]] && grep -q "firecracker.nft" "$NFTABLES_CONF"; then
        log "Nftables config already includes firecracker rules"
    else
        log "Adding include to $NFTABLES_CONF..."
        if ! grep -q "^include" "$NFTABLES_CONF" 2>/dev/null; then
            # File might not exist or be empty, create it
            echo "include \"$NFT_RULES_FILE\"" | sudo tee "$NFTABLES_CONF" >/dev/null
        else
            # Add include at the end
            echo "include \"$NFT_RULES_FILE\"" | sudo tee -a "$NFTABLES_CONF" >/dev/null
        fi
    fi

    # Enable nftables service if systemd is available
    if command -v systemctl >/dev/null 2>&1; then
        if systemctl is-enabled nftables >/dev/null 2>&1; then
            log "Nftables service already enabled"
        else
            log "Enabling nftables service..."
            sudo systemctl enable nftables >/dev/null 2>&1 || warn "Could not enable nftables service (may not be installed)"
        fi
    fi

    log "Configuration is now persistent!"
    echo
    log "Changes made:"
    echo "  - IPv4 forwarding: $SYSCTL_FILE"
    echo "  - Nftables rules:  $NFT_RULES_FILE"
    echo "  - Nftables config: $NFTABLES_CONF"
    echo
    log "To test persistence, you can reload nftables:"
    log "  sudo nft -f $NFT_RULES_FILE"
}

# Remove persistent configuration
remove_persistence() {
    log "Removing persistent Firecracker networking configuration..."

    SYSCTL_FILE="/etc/sysctl.d/99-firecracker.conf"
    NFT_RULES_FILE="/etc/nftables/firecracker.nft"
    NFTABLES_CONF="/etc/nftables.conf"

    if [[ -f "$SYSCTL_FILE" ]]; then
        log "Removing $SYSCTL_FILE..."
        sudo rm -f "$SYSCTL_FILE"
    fi

    if [[ -f "$NFT_RULES_FILE" ]]; then
        log "Removing $NFT_RULES_FILE..."
        sudo rm -f "$NFT_RULES_FILE"
    fi

    if [[ -f "$NFTABLES_CONF" ]] && grep -q "firecracker.nft" "$NFTABLES_CONF"; then
        log "Removing firecracker include from $NFTABLES_CONF..."
        sudo sed -i '/firecracker\.nft/d' "$NFTABLES_CONF"
        # If file is now empty or only has comments, we could remove it, but safer to leave it
    fi

    log "Persistent configuration removed!"
    warn "Note: Runtime configuration (if active) is not affected."
    warn "Run '$0 cleanup' to remove runtime rules."
}

# Cleanup networking infrastructure
cleanup_networking() {
    log "Cleaning up Firecracker host networking infrastructure..."

    detect_host_interface

    # Remove rules if they exist
    if sudo nft list table "$NFT_TABLE" >/dev/null 2>&1; then
        if sudo nft list chain "$NFT_TABLE" postrouting 2>/dev/null | grep -q "masquerade"; then
            log "Removing masquerade rule..."
            sudo nft delete rule "$NFT_TABLE" postrouting ip saddr "$VM_SUBNET" oifname "$HOST_IFACE" masquerade 2>/dev/null || true
        fi
        if sudo nft list chain "$NFT_TABLE" filter 2>/dev/null | grep -q 'iifname "tap'; then
            log "Removing forward rule for tap devices..."
            sudo nft delete rule "$NFT_TABLE" filter iifname "tap*" oifname "$HOST_IFACE" accept 2>/dev/null || true
        fi

        # Remove nft table (this will remove all chains and remaining rules)
        log "Removing nft table '$NFT_TABLE' (this removes all chains and rules)..."
        sudo nft delete table "$NFT_TABLE" 2>/dev/null || true
        log "Nft table removed"
    else
        log "Nft table '$NFT_TABLE' does not exist, nothing to clean up"
    fi

    # Note: IPv4 forwarding is left enabled as other processes might need it
    # To disable manually: echo 0 | sudo tee /proc/sys/net/ipv4/ip_forward

    log "Cleanup complete!"
}

# Show current status
show_status() {
    log "Firecracker host networking status:"
    echo
    echo "IPv4 Forwarding: $(cat /proc/sys/net/ipv4/ip_forward)"
    echo
    if sudo nft list table "$NFT_TABLE" >/dev/null 2>&1; then
        echo "Nft table '$NFT_TABLE' rules:"
        sudo nft list table "$NFT_TABLE"
    else
        echo "Nft table '$NFT_TABLE' does not exist"
    fi
}

# Show usage
show_usage() {
    cat <<EOF
Usage: $0 [setup|cleanup|status|persist|remove-persist]

Actions:
  setup            Set up host networking infrastructure for Firecracker (default)
  cleanup           Remove host networking infrastructure (removes nft table and all rules)
  status            Show current host networking status
  persist           Make configuration persistent across reboots
  remove-persist    Remove persistent configuration files

This script sets up the host networking infrastructure (nft table, chains, and
masquerade rule) for Firecracker VMs. The masquerade rule is configured for
the VM subnet (default: 172.16.0.0/16) on the default host interface.

By default, changes are NOT persistent across reboots. Use 'persist' to make
them permanent.

Options (via environment variables):
  VM_SUBNET     VM subnet for masquerade rule (default: 172.16.0.0/16)
  HOST_IFACE    Host network interface (auto-detected if not set)

Examples:
  # Setup host infrastructure (runtime only)
  $0 setup

  # Make configuration persistent across reboots
  $0 persist

  # Cleanup (removes runtime rules only)
  $0 cleanup

  # Remove persistent configuration
  $0 remove-persist

  # Show status
  $0 status

EOF
}

# Main
case "${ACTION}" in
    setup)
        setup_networking
        ;;
    cleanup)
        cleanup_networking
        ;;
    persist)
        persist_config
        ;;
    remove-persist)
        remove_persistence
        ;;
    status)
        show_status
        ;;
    help|--help|-h)
        show_usage
        ;;
    *)
        error "Unknown action: $ACTION"
        show_usage
        exit 1
        ;;
esac
