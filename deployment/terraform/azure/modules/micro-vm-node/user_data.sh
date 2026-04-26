#!/bin/bash
# User data script for Azure VM with nested virtualization
# Installs and configures KVM and Firecracker to enable running Ubuntu guest VMs

set -x
exec > >(tee /var/log/user-data.log|logger -t user-data -s 2>/dev/console) 2>&1

# Get admin username from Terraform template variable
ADMIN_USER="${admin_username}"

echo "Starting user data script at $(date)"
echo "Admin user: $ADMIN_USER"

# Update system packages
export DEBIAN_FRONTEND=noninteractive
apt-get update -y
apt-get upgrade -y

# Install KVM and related packages (including Firecracker dependencies)
apt-get install -y \
    qemu-kvm \
    qemu-utils \
    libvirt-daemon-system \
    libvirt-clients \
    bridge-utils \
    virt-install \
    cpu-checker \
    cloud-utils \
    cloud-initramfs-growroot \
    curl \
    wget \
    jq \
    screen \
    tmux

# Check if virtualization extensions are available
echo "Checking for virtualization extensions..."
if grep -q vmx /proc/cpuinfo || grep -q svm /proc/cpuinfo; then
    echo "Virtualization extensions detected:"
    grep -E "vmx|svm" /proc/cpuinfo | head -1
else
    echo "WARNING: Virtualization extensions not detected in /proc/cpuinfo"
    echo "Nested virtualization may not work properly"
fi

# Load KVM modules
modprobe kvm
if grep -q vmx /proc/cpuinfo; then
    modprobe kvm_intel
elif grep -q svm /proc/cpuinfo; then
    modprobe kvm_amd
fi

# Ensure modules are loaded on boot
if ! grep -q "kvm" /etc/modules; then
    echo "kvm" >> /etc/modules
    if grep -q vmx /proc/cpuinfo; then
        echo "kvm_intel" >> /etc/modules
    elif grep -q svm /proc/cpuinfo; then
        echo "kvm_amd" >> /etc/modules
    fi
fi

# Add admin user to libvirt and kvm groups
if [ -n "$ADMIN_USER" ] && getent passwd "$ADMIN_USER" > /dev/null 2>&1; then
    usermod -aG libvirt "$ADMIN_USER"
    usermod -aG kvm "$ADMIN_USER"
    echo "Added user $ADMIN_USER to libvirt and kvm groups"
else
    echo "WARNING: Admin user $ADMIN_USER not found, trying to find admin user..."
    # Fallback: Get the first non-root user (should be the admin user)
    FALLBACK_USER=$(getent passwd | grep -v "^root:" | grep "/home" | cut -d: -f1 | head -1)
    if [ -n "$FALLBACK_USER" ]; then
        usermod -aG libvirt "$FALLBACK_USER"
        usermod -aG kvm "$FALLBACK_USER"
        echo "Added user $FALLBACK_USER to libvirt and kvm groups (fallback)"
    else
        echo "WARNING: Could not determine admin user to add to groups"
    fi
fi

# Enable and start libvirt service
systemctl enable libvirtd
systemctl start libvirtd

# Wait for libvirt to start
sleep 5

# Check if libvirt is running
if systemctl is-active --quiet libvirtd; then
    echo "libvirtd service is running"
else
    echo "WARNING: libvirtd service is not running"
fi

# Verify KVM installation
echo "Verifying KVM installation..."
if command -v kvm-ok &> /dev/null; then
    kvm-ok
else
    echo "kvm-ok command not found, but KVM packages are installed"
    # Manual check
    if [ -e /dev/kvm ]; then
        echo "KVM device node exists at /dev/kvm"
        ls -l /dev/kvm
    else
        echo "WARNING: /dev/kvm does not exist"
    fi
fi

# Configure default libvirt network (NAT)
if command -v virsh &> /dev/null; then
    # Check if default network exists
    if ! virsh net-list --all | grep -q default; then
        echo "Creating default libvirt network..."
        virsh net-define /usr/share/libvirt/networks/default.xml || true
        virsh net-autostart default || true
        virsh net-start default || true
    else
        echo "Default libvirt network already exists"
        virsh net-start default || true
    fi
fi

# Set permissions for KVM device
chmod 666 /dev/kvm || true
if ! grep -q "kvm:x:" /etc/group; then
    # Add kvm group if it doesn't exist
    groupadd kvm || true
fi
chown root:kvm /dev/kvm || true
chmod 0660 /dev/kvm || true


echo ""
echo "User data script completed at $(date)"
echo "KVM setup complete"
echo ""
echo "To verify nested virtualization is working:"
echo "  grep -c vmx /proc/cpuinfo  # Should return > 0"
echo "  kvm-ok                      # Should indicate KVM acceleration can be used"
echo "  virsh list --all            # Should show default network"
echo ""
