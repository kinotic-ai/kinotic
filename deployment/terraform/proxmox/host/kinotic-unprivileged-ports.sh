#!/bin/sh
# An LXC autodev hook: runs in the container's namespaces before pivot_root, so the write
# lands in the container's network namespace and the host's floor stays at 1024. It lets the
# container's unprivileged user bind ports below 1024.
echo 0 > /proc/sys/net/ipv4/ip_unprivileged_port_start
