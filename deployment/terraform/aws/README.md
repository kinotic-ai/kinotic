# Atlas Forge - Terraform Infrastructure

This Terraform configuration deploys a bare metal AWS EC2 instance configured to run Firecracker microVMs with multi-tenant network isolation using VXLAN overlay networking.

## Overview

The infrastructure provides:
- **Bare metal EC2 instance** for running Firecracker microVMs with KVM support
- **Multi-tenant network isolation** using VXLAN overlay networking
- **Open vSwitch (OVS)** for virtual switching and network programmability
- **Automated provisioning** of customer networks and VM configurations
- **Scalable architecture** supporting up to 65,536 customers with 253 VMs each

## Architecture

### Network Topology

```
┌─────────────────────────────────────────────────────────────┐
│                    AWS VPC (10.0.0.0/16)                    │
│                                                             │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐       │
│  │  Mgmt Subnet │  │Ingress Subnet│  │ Egress Subnet│       │
│  │  10.0.1.0/24 │  │ 10.0.2.0/24  │  │ 10.0.3.0/24  │       │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘       │
│         │                 │                 │               │
│         └─────────────────┴─────────────────┘               │
│                            │                                │
│                    ┌───────▼────────┐                       │
│                    │ Bare Metal EC2 │                       │
│                    │   Instance     │                       │
│                    └───────┬────────┘                       │
│                            │                                │
│         ┌──────────────────┴──────────────────┐             │
│         │      OVS Bridge (ovs-br0)           │             │
│         │  ┌───────────┐      ┌──────────┐    │             │
│         │  │   eth1    │      │  eth2    │    │             │
│         │  │ (ingress) │      │ (egress) │    │             │
│         │  └───────────┘      └──────────┘    │             │
│         │                                     │             │
│         │  ┌─────────┐      ┌───────────┐     │             │
│         │  │  vxlan* │      │ tap-cust* │     │             │
│         │  │ (VXLAN) │      │ (VM NICs) │     │             │
│         │  └─────────┘      └───────────┘     │             │
│         └─────────────────────────────────────┘             │
│                            │                                │
│                    ┌───────▼────────┐                       │
│                    │ Firecracker    │                       │
│                    │   microVMs     │                       │
│                    └────────────────┘                       │
└─────────────────────────────────────────────────────────────┘
```

### Customer Network Isolation

Each customer gets an isolated network segment:
- **VXLAN Interface**: Creates a VXLAN tunnel (vxlan<VNI>) for overlay networking
- **TAP Interface**: Virtual network interface (tap-cust<ID>) for VM connectivity
- **OVS Bridge**: Connects TAP and VXLAN interfaces via OpenFlow rules
- **Customer Subnet**: `/24` subnet (253 usable IPs) mapped deterministically from customer ID

## Design Decisions

### 1. Separate Ingress and Egress Interfaces

**Why three network interfaces?**

- **Management (eth0)**: SSH access, system administration, Elastic IP for stable access
- **Ingress (eth1)**: Handles incoming customer traffic, VXLAN tunnel endpoints
- **Egress (eth2)**: Handles outbound traffic, future NAT/proxy services

**Benefits:**
- **Traffic Separation**: Different security policies, QoS, and monitoring per interface
- **Performance**: Distribute bandwidth across multiple interfaces
- **Scalability**: Handle asymmetric traffic patterns (high ingress, moderate egress)
- **Future Flexibility**: Add DDoS protection on ingress, NAT on egress
- **Compliance**: Separate tracking for ingress/egress traffic

**Current Implementation:**
Both ingress and egress interfaces are added to the same OVS bridge, acting as a single logical switch. The separation provides architectural flexibility for future policy differentiation.

### 2. VXLAN Overlay Networking

**Why VXLAN instead of VLAN?**

- **No VLAN Tagging Required**: VXLAN runs over IP/UDP, doesn't require 802.1Q support on physical infrastructure
- **Massive Scale**: Supports 16+ million network segments (24-bit VNI) vs 4,094 VLANs
- **Cloud-Friendly**: Works over any IP network, ideal for cloud deployments
- **Encapsulation**: Encapsulates Layer 2 frames in UDP, enabling Layer 3 routing

**Trade-offs:**
- Slightly higher overhead (50 bytes per packet) vs VLAN (4 bytes)
- Requires UDP port 4789 in security groups
- More complex than VLAN but provides better scalability

### 3. Deterministic Network Mapping

**Customer ID → Network Address Algorithm:**
```
X = (customer_id / 256) % 256  → Third octet
Y = customer_id % 256          → Fourth octet base
VNI = 1000 + customer_id       → VXLAN Network Identifier
CIDR = 10.X.Y.0/24
Gateway = 10.X.Y.1
```

**Benefits:**
- Predictable: Same customer ID always maps to same network
- No database needed: Calculate network from customer ID
- Easy debugging: Customer ID directly maps to network address
- Scalable: Supports 65,536 customers (256 × 256)

**Example:**
- Customer ID 1001 → Network 10.3.232.0/24, Gateway 10.3.232.1, VNI 2001
- Customer ID 256 → Network 10.1.0.0/24, Gateway 10.1.0.1, VNI 1256

### 4. Open vSwitch (OVS) Bridge

**Why OVS instead of Linux bridge?**

- **OpenFlow Support**: Programmable forwarding rules for traffic control
- **VXLAN Native Support**: Built-in VXLAN interface management
- **Performance**: Optimized for virtualized environments
- **Flexibility**: Easy to add flow rules, QoS, monitoring
- **Industry Standard**: Widely used in cloud and SDN environments

### 5. Bare Metal Instance Type

**Why bare metal (metal instance types)?**

- **KVM Support**: Required for Firecracker microVMs
- **Performance**: Direct hardware access, no virtualization overhead
- **Nested Virtualization**: Can run VMs inside the instance
- **Network Performance**: Better throughput for high-bandwidth scenarios

**Supported Instance Types:**
- `a1.metal` - ARM-based, cost-effective
- `i3en.metal` - High I/O, NVMe storage
- `c6gn.metal` - Compute-optimized, Graviton2
- `m6g.metal` - General purpose, Graviton2

## Components

### Terraform Resources

1. **VPC & Networking**
   - VPC with DNS support
   - Three subnets: management, ingress, egress
   - Internet Gateway for public access
   - Route tables for all subnets

2. **Security**
   - Security group allowing SSH (22), VXLAN (4789/UDP), ICMP
   - Auto-generated SSH key pair
   - Elastic IP for stable management access

3. **EC2 Instance**
   - Bare metal instance with three ENIs
   - User data script for automated setup
   - Firecracker and OVS installation

### Installed Scripts

1. **`create_customers.sh`** (`/usr/local/bin/create_customers.sh`)
   - Provisions customer network infrastructure
   - Creates VXLAN and TAP interfaces
   - Configures OVS bridge and OpenFlow rules
   - Sets up routing for customer subnet

2. **`generate-vm-config.sh`** (`/usr/local/bin/generate-vm-config.sh`)
   - Generates Firecracker VM configuration JSON
   - Calculates network parameters from customer ID
   - Generates unique MAC addresses
   - Creates VM config ready for Firecracker

### Installed Software

- **Firecracker**: MicroVM runtime (v1.13.1)
- **Open vSwitch**: Virtual switch with OpenFlow support
- **KVM**: Kernel modules for virtualization

## Capacity Limits

### Network Capacity

- **Maximum Customers**: 65,536 (customer_id 0-65,535)
- **IPs per Customer**: 253 usable IPs (10.X.Y.2 - 10.X.Y.254)
- **Total IPs**: 16,580,608 (65,536 × 253)
- **VXLAN VNIs**: 65,536 (VNI 1000-66535)

### MAC Address Capacity

- **Unique MACs**: 16,580,608 (65,536 customers × 253 VMs)
- **MAC Format**: `02:FC:XX:YY:ZZ:WW`
  - `XX:YY` = Customer ID (2 bytes)
  - `ZZ:WW` = VM IP offset (2 bytes)

## Usage

### Initial Deployment

```bash
# Initialize Terraform
terraform init

# Review plan
terraform plan

# Deploy infrastructure
terraform apply

# Get SSH command
terraform output ssh_command
```

### Onboarding a Customer

```bash
# SSH into the instance
ssh -i firecracker-key-*.pem ec2-user@<public-ip>

# Create customer network (e.g., customer ID 1001)
sudo /usr/local/bin/create_customers.sh 1001

# Generate VM configuration
/usr/local/bin/generate-vm-config.sh 1001 2

# Launch Firecracker VM
sudo firecracker --config-file vm-config-cust1001.json
```

### Customer Network Details

For customer ID 1001:
- **Network**: 10.3.232.0/24
- **Gateway**: 10.3.232.1
- **VXLAN VNI**: 2001
- **TAP Interface**: tap-cust1001
- **VXLAN Interface**: vxlan2001
- **Usable IPs**: 10.3.232.2 - 10.3.232.254 (253 IPs)

### Multiple VMs per Customer

Each customer can run up to 253 VMs:

```bash
# VM 1: IP 10.3.232.2
/usr/local/bin/generate-vm-config.sh 1001 2
sudo firecracker --config-file vm-config-cust1001.json

# VM 2: IP 10.3.232.3
/usr/local/bin/generate-vm-config.sh 1001 3
sudo firecracker --config-file vm-config-cust1001.json

# ... up to VM 253: IP 10.3.232.254
```

## Network Flow

### Customer VM Communication

1. **VM → VM (same customer)**: 
   - VM sends packet → TAP interface → OVS bridge → VXLAN interface → VXLAN tunnel → Remote VXLAN endpoint → Remote TAP → Remote VM

2. **VM → Internet**:
   - VM sends packet → TAP interface → OVS bridge → VXLAN interface → Gateway (10.X.Y.1) → Routing → Egress interface (eth2) → Internet

3. **Internet → VM**:
   - Packet arrives → Ingress interface (eth1) → VXLAN tunnel → VXLAN interface → OVS bridge → TAP interface → VM

## Variables

Key variables (see `variables.tf` for full list):

- `region`: AWS region (default: `us-east-1`)
- `availability_zone`: AZ for resources (default: `us-east-1c`)
- `instance_type`: Bare metal instance type (default: `a1.metal`)
- `ami_id`: AMI ID for the instance
- `instance_name`: Name tag for resources (default: `kinotic`)

## Outputs

- `instance_public_ip`: Public IP for SSH access
- `ssh_command`: Ready-to-use SSH command
- `onboard_customer_command`: Example customer onboarding command
- `private_key_file`: Path to generated private key

## Security Considerations

1. **SSH Access**: Auto-generated key pair, private key saved locally
2. **Security Groups**: Restrict SSH and VXLAN access as needed
3. **Network Isolation**: Each customer's traffic is isolated via VXLAN
4. **Firewall**: Consider adding iptables rules for additional security

## Troubleshooting

### Check OVS Status
```bash
sudo ovs-vsctl show
sudo ovs-ofctl dump-flows ovs-br0
```

### Check Customer Network
```bash
ip addr show vxlan2001
ip addr show tap-cust1001
ip route | grep 10.3.232
```

### Check VXLAN Tunnels
```bash
ip link show type vxlan
```

### View OVS Ports
```bash
sudo ovs-vsctl list-ports ovs-br0
```

## Future Enhancements

Potential improvements:
- **Rate Limiting**: Per-customer bandwidth limits (commented in `create_customers.sh`)
- **Monitoring**: Per-customer traffic metrics
- **Automated VM Management**: API or orchestration layer
- **Multi-Region**: VXLAN tunnels across regions
- **Load Balancing**: Distribute VMs across multiple instances
- **Backup/Recovery**: Customer network state persistence

## References

- [Firecracker Documentation](https://github.com/firecracker-microvm/firecracker)
- [Open vSwitch Documentation](https://www.openvswitch.org/)
- [VXLAN RFC 7348](https://tools.ietf.org/html/rfc7348)
- [AWS Bare Metal Instances](https://aws.amazon.com/ec2/instance-types/)

