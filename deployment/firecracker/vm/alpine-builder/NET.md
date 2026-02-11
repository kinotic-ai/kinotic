# Firecracker Bridge Networking

## Setup Bridge on Host and Guest
* See (Github issue)[https://github.com/firecracker-microvm/firecracker/issues/2618]

### Host 
sudo ip link add name br0 type bridge
sudo ip tuntap add tap0 mode tap
sudo ip link set tap0 up
sudo ip link set dev tap0 master br0
sudo ip address add 192.168.0.150/24 dev br0

#### Setup forwarding
``` shell Enable forwarding
sudo sysctl -w net.ipv4.ip_forward=1
```

# NAT only traffic that is leaving the real uplink (eth0)
sudo iptables -t nat -A POSTROUTING -o eth0 -j MASQUERADE

# Allow forwarding between br0 (guest) and eth0 (internet)
sudo iptables -A FORWARD -i br0 -o eth0 -j ACCEPT
sudo iptables -A FORWARD -i eth0 -o br0 -m state --state RELATED,ESTABLISHED -j ACCEPT

### Guest
ip addr add 192.168.0.169/24 dev eth0
ip link set eth0 up
ip r add 192.168.0.1 via 192.168.0.150 dev eth0
ip r add default via 192.168.0.150 dev eth0
