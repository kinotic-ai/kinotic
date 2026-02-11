#!/bin/bash
# Bootstrap script for Firecracker bare metal instance
# This script sets up the environment for running Firecracker VMs with OpenVSwitch

set -x
exec > >(tee /var/log/user-data.log|logger -t user-data -s 2>/dev/console) 2>&1

# Variables from Terraform template
ENI_MGMT="${eni_mgmt}"
ENI_INGRESS="${eni_ingress}"
ENI_EGRESS="${eni_egress}"

# Firecracker version - use latest stable
FC_VERSION="v1.13.1"

echo "Starting bootstrap script at $(date)"

# Update system packages
yum update -y || true
yum install -y epel-release || true
yum install -y curl wget git jq || true

# Install OpenVSwitch
yum install -y openvswitch || true

# Enable and start OpenVSwitch
systemctl enable openvswitch || true
systemctl start openvswitch || true

# Wait for OVS to be ready
sleep 5

# Create OVS bridge if it doesn't exist
if command -v ovs-vsctl &> /dev/null; then
    ovs-vsctl --may-exist add-br ovs-br0 || true
    ovs-vsctl set bridge ovs-br0 protocols=OpenFlow13 || true
    
    # Add network interfaces to OVS bridge (defer if interfaces not ready)
    if ip link show $ENI_INGRESS &> /dev/null; then
        ovs-vsctl --may-exist add-port ovs-br0 $ENI_INGRESS || true
    fi
    
    if ip link show $ENI_EGRESS &> /dev/null; then
        ovs-vsctl --may-exist add-port ovs-br0 $ENI_EGRESS || true
    fi
fi

# Install Firecracker
ARCH=$$(uname -m)
if [ "$$ARCH" = "aarch64" ] || [ "$$ARCH" = "arm64" ]; then
    FC_ARCH="aarch64"
else
    FC_ARCH="x86_64"
fi

FC_RELEASE_URL="https://github.com/firecracker-microvm/firecracker/releases/download/$${FC_VERSION}/firecracker-$${FC_VERSION}-$${FC_ARCH}.tgz"
curl -fsSL $${FC_RELEASE_URL} -o /tmp/firecracker.tar.gz || true

if [ -f /tmp/firecracker.tar.gz ]; then
    tar -xzf /tmp/firecracker.tar.gz -C /tmp || true
    mv /tmp/release-$${FC_VERSION}-$${FC_ARCH}/firecracker-$${FC_VERSION}-$${FC_ARCH} /usr/local/bin/firecracker || true
    mv /tmp/release-$${FC_VERSION}-$${FC_ARCH}/jailer-$${FC_VERSION}-$${FC_ARCH} /usr/local/bin/jailer || true
    chmod +x /usr/local/bin/firecracker /usr/local/bin/jailer || true
    rm -rf /tmp/release-$${FC_VERSION}-$${FC_ARCH} || true
    rm -f /tmp/firecracker.tar.gz || true
fi

# Enable KVM support
modprobe kvm || true
if [ "$$ARCH" = "aarch64" ] || [ "$$ARCH" = "arm64" ]; then
    modprobe kvm-arm || true
else
    modprobe kvm_intel || true
fi

echo "kvm" >> /etc/modules-load.d/kvm.conf || true
if [ "$$ARCH" = "aarch64" ] || [ "$$ARCH" = "arm64" ]; then
    echo "kvm-arm" >> /etc/modules-load.d/kvm.conf || true
else
    echo "kvm_intel" >> /etc/modules-load.d/kvm.conf || true
fi

# Save create_customers.sh script
mkdir -p /usr/local/bin
cat > /usr/local/bin/create_customers.sh << CREATE_CUSTOMERS_EOF
${create_customers_file}
CREATE_CUSTOMERS_EOF
chmod +x /usr/local/bin/create_customers.sh

# Save generate-vm-config.sh script
cat > /usr/local/bin/generate-vm-config.sh << GENERATE_VM_CONFIG_EOF
${generate_vm_config_file}
GENERATE_VM_CONFIG_EOF
chmod +x /usr/local/bin/generate-vm-config.sh

echo "Bootstrap script completed at $(date)"
echo "Firecracker and OpenVSwitch setup complete"