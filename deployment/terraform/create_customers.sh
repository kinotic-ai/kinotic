#!/bin/bash
#
# Customer Network Provisioning Script
# =====================================
#
# This script provisions isolated network infrastructure for a customer using a
# VXLAN overlay network architecture. It creates the necessary network interfaces,
# connects them via an Open vSwitch (OVS) bridge, and configures routing to enable
# customer VM connectivity with network isolation.
#
# Architecture Overview:
# ---------------------
# The script implements a multi-tenant network architecture where each customer
# gets an isolated network segment:
#
#   - VXLAN Interface: Creates a VXLAN tunnel interface (vxlan<VNI>) that provides
#     Layer 2 overlay networking. VXLAN encapsulates customer traffic and enables
#     network isolation across the physical infrastructure. The VNI (VXLAN Network
#     Identifier) uniquely identifies each customer's network segment.
#
#   - TAP Interface: Creates a TAP interface (tap-cust<ID>) that serves as the
#     virtual network interface for customer VMs. TAP interfaces operate at Layer 2
#     and allow VMs to connect to the virtual network.
#
#   - OVS Bridge: Both interfaces are added to the OVS bridge (ovs-br0), which acts
#     as a virtual switch connecting the TAP interface (local VM traffic) with the
#     VXLAN interface (overlay network traffic).
#
#   - OpenFlow Rules: Bidirectional flow rules are configured to forward traffic
#     between the TAP and VXLAN interfaces, enabling VMs to communicate over the
#     overlay network.
#
# Customer ID to Network Mapping:
# --------------------------------
# The script uses a deterministic algorithm to map customer IDs to network addresses:
#
#   - X = (customer_id / 256) % 256  (third octet of IP address)
#   - Y = customer_id % 256          (fourth octet base of IP address)
#   - VNI = 1000 + customer_id       (VXLAN Network Identifier)
#
# Example: customer_id 1001
#   - X = (1001 / 256) % 256 = 3
#   - Y = 1001 % 256 = 232
#   - VNI = 1000 + 1001 = 2001
#   - CIDR: 10.3.232.0/24
#   - Gateway: 10.3.232.1
#
# Script Execution Flow:
# ---------------------
# 1. VXLAN Interface Creation:
#    - Creates a VXLAN interface with the calculated VNI
#    - Uses UDP port 4789 (standard VXLAN port)
#    - Binds to the local IP address from eth1 interface
#    - Adds the interface to the OVS bridge
#
# 2. TAP Interface Creation:
#    - Creates a TAP interface for VM connectivity
#    - Brings the interface up
#    - Adds the interface to the OVS bridge
#
# 3. OpenFlow Flow Rules:
#    - Configures bidirectional forwarding rules:
#      * Traffic from TAP interface → forwarded to VXLAN interface
#      * Traffic from VXLAN interface → forwarded to TAP interface
#    - Uses --strict flag to update existing flows if present
#
# 4. Routing Configuration:
#    - Assigns the gateway IP address (10.X.Y.1) to the VXLAN interface
#    - Adds a route for the customer CIDR pointing to the VXLAN interface
#    - Enables routing of customer traffic through the overlay network
#
# Prerequisites:
# --------------
# - The script must be run with sudo/root privileges
# - OVS bridge 'ovs-br0' must already exist and be configured
# - Network interface 'eth1' must exist and have an IP address assigned
#   (used as the local endpoint for VXLAN tunnels)
# - Required tools: ip, ovs-vsctl, ovs-ofctl
#
# Rate Limiting:
# --------------
# Rate limiting functionality is currently commented out. When enabled, it would
# use Linux Traffic Control (tc) with Token Bucket Filter (TBF) to limit outgoing
# traffic on the TAP interface to 100 Mbps with a 32 kbit burst and 50ms latency.
#
# Usage: sudo ./create_customers.sh <customer_id>
# Example: sudo ./create_customers.sh 1001 → 10.3.232.0/24

set -e

CUST_ID=$1
if [ -z "$CUST_ID" ]; then
  echo "Usage: $0 <customer_id>"
  exit 1
fi

# Validate customer ID range (0-65535, supports 65,536 customers)
if ! [[ "$CUST_ID" =~ ^[0-9]+$ ]]; then
  echo "Error: customer_id must be a number"
  exit 1
fi

if [ "$CUST_ID" -lt 0 ] || [ "$CUST_ID" -gt 65535 ]; then
  echo "Error: customer_id must be between 0 and 65535 (supports 65,536 customers)"
  exit 1
fi

# Map ID → X.Y (e.g., 1001 → X=3, Y=232)
X=$(( (CUST_ID / 256) % 256 ))
Y=$(( CUST_ID % 256 ))
VNI=$(( 1000 + CUST_ID ))

CIDR="10.${X}.${Y}.0/24"
GATEWAY="10.${X}.${Y}.1"

echo "Onboarding customer $CUST_ID → VNI $VNI → CIDR $CIDR"

# Use ingress ENI for VXLAN local IP
LOCAL_IP=$(ip -4 addr show eth1 | grep inet | awk '{print $2}' | cut -d/ -f1)

# VXLAN
ip link add vxlan$VNI type vxlan id $VNI dstport 4789 local $LOCAL_IP 2>/dev/null || true
ip link set vxlan$VNI up
ovs-vsctl add-port ovs-br0 vxlan$VNI 2>/dev/null || true

# Tap
ip tuntap add tap-cust$CUST_ID mode tap 2>/dev/null || true
ip link set tap-cust$CUST_ID up
ovs-vsctl add-port ovs-br0 tap-cust$CUST_ID 2>/dev/null || true

# OpenFlow
ovs-ofctl add-flow ovs-br0 "in_port=tap-cust$CUST_ID actions=output:vxlan$VNI" --strict 2>/dev/null || true
ovs-ofctl add-flow ovs-br0 "in_port=vxlan$VNI actions=output:tap-cust$CUST_ID" --strict 2>/dev/null || true

# Routing
ip addr add $GATEWAY/24 dev vxlan$VNI 2>/dev/null || true
ip route add $CIDR dev vxlan$VNI 2>/dev/null || true

# TODO: Figure out proper Rate limit, below is supported by the linux kernel
# In summary, this command ensures that the outgoing traffic on the specified network interface (tap-cust$CUST_ID) is rate-limited to a maximum of 100 Mbps,
# with an allowed burst of 32 kilobits, and packets that are queued for longer than 50 milliseconds are dropped.
# The error messages are suppressed and the script continues running regardless of success or failure
#tc qdisc replace dev tap-cust$CUST_ID root tbf rate 100mbit burst 32kbit latency 50ms 2>/dev/null || true

echo "Customer $CUST_ID ready: $CIDR (gateway: $GATEWAY)"