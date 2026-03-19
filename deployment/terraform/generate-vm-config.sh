#!/bin/bash
# Generate Firecracker VM config for a customer
# Usage: ./generate-vm-config.sh <customer_id> [vm_ip_offset]
# Example: ./generate-vm-config.sh 1001 2  (creates config for customer 1001 with IP 10.3.232.2)

CUST_ID=$1
VM_IP_OFFSET=${2:-2}  # Default to .2 (first usable IP after gateway)

if [ -z "$CUST_ID" ]; then
  echo "Usage: $0 <customer_id> [vm_ip_offset]"
  echo "Example: $0 1001 2"
  echo "Note: vm_ip_offset must be between 2-254 (253 usable IPs per customer)"
  exit 1
fi

# Validate VM IP offset (2-254, since .1 is gateway and .255 is broadcast)
if [ "$VM_IP_OFFSET" -lt 2 ] || [ "$VM_IP_OFFSET" -gt 254 ]; then
  echo "Error: vm_ip_offset must be between 2 and 254 (253 usable IPs per customer)"
  exit 1
fi

# Calculate network parameters (same as create_customers.sh)
X=$(( (CUST_ID / 256) % 256 ))
Y=$(( CUST_ID % 256 ))
VM_IP="10.${X}.${Y}.${VM_IP_OFFSET}"
GATEWAY="10.${X}.${Y}.1"
TAP_IFACE="tap-cust${CUST_ID}"

# Generate MAC address: 02:FC:XX:YY:ZZ:WW
# XX:YY = customer_id (2 bytes, supports 0-65535 customers)
# ZZ:WW = vm_ip_offset (2 bytes, supports 0-65535, but we only use 2-254 = 253 VMs per customer)
# Total capacity: 65,536 customers × 253 VMs = 16,580,608 unique MAC addresses
MAC_BYTE3=$(( (CUST_ID / 256) % 256 ))
MAC_BYTE4=$(( CUST_ID % 256 ))
MAC_BYTE5=$(( (VM_IP_OFFSET / 256) % 256 ))
MAC_BYTE6=$(( VM_IP_OFFSET % 256 ))
GUEST_MAC=$(printf "02:FC:%02x:%02x:%02x:%02x" $MAC_BYTE3 $MAC_BYTE4 $MAC_BYTE5 $MAC_BYTE6)

# Generate config
cat > "vm-config-cust${CUST_ID}.json" <<EOF
{
  "boot-source": {
    "kernel_image_path": "./bin/vmlinux",
    "boot_args": "console=ttyS0 reboot=k panic=1 pci=off ip=${VM_IP}::${GATEWAY}:255.255.255.0::eth0:off loglevel=7 earlyprintk=serial"
  },
  "drives": [{
    "drive_id": "rootfs",
    "path_on_host": "./bin/firecracker-vm-alpine-rootfs.ext4",
    "is_root_device": true,
    "is_read_only": false
  }],
  "network-interfaces": [{
    "iface_id": "eth0",
    "guest_mac": "${GUEST_MAC}",
    "host_dev_name": "${TAP_IFACE}"
  }],
  "machine-config": {
    "vcpu_count": 1,
    "mem_size_mib": 128
  }
}
EOF

echo "Generated vm-config-cust${CUST_ID}.json"
echo "  Customer ID: ${CUST_ID}"
echo "  Network: 10.${X}.${Y}.0/24"
echo "  VM IP: ${VM_IP}"
echo "  Gateway: ${GATEWAY}"
echo "  TAP Interface: ${TAP_IFACE}"
echo "  MAC Address: ${GUEST_MAC}"
echo ""
echo "Make sure to run: sudo /usr/local/bin/create_customers.sh ${CUST_ID}"
echo "Then launch VM: sudo firecracker --config-file vm-config-cust${CUST_ID}.json"

