#!/bin/bash
# ========================================================
# AWS EC2 4GB Swap & Kernel Optimization Script
# ========================================================
set -euo pipefail

echo "==> 1. Checking existing swap..."
sudo swapoff -a || true
sudo rm -f /swapfile

echo "==> 2. Creating 4GB swap file at /swapfile..."
sudo fallocate -l 4G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile

echo "==> 3. Configuring permanent mount in /etc/fstab..."
if ! grep -q '/swapfile none swap sw 0 0' /etc/fstab; then
    echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
fi

echo "==> 4. Optimizing kernel vm.swappiness to 10..."
sudo sysctl vm.swappiness=10
if ! grep -q 'vm.swappiness=10' /etc/sysctl.conf; then
    echo 'vm.swappiness=10' | sudo tee -a /etc/sysctl.conf
fi

echo "==> Swap & Memory Optimization Complete!"
free -h
