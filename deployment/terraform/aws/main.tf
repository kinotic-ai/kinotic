provider "aws" {
  region = var.region
}

# ========================================
# 1. Generate SSH Key Pair (TLS)
# ========================================

resource "tls_private_key" "firecracker" {
  algorithm = "RSA"
  rsa_bits  = 4096
}

resource "random_id" "key_suffix" {
  byte_length = 4
}

resource "aws_key_pair" "firecracker" {
  key_name   = "firecracker-${random_id.key_suffix.hex}"
  public_key = tls_private_key.firecracker.public_key_openssh

  tags = {
    Name = "firecracker-auto-key"
  }
}

# Save private key to local file
resource "local_file" "private_key" {
  content         = tls_private_key.firecracker.private_key_pem
  filename        = "${path.module}/firecracker-key-${random_id.key_suffix.hex}.pem"
  file_permission = "0400"
}

# ========================================
# 2. VPC + Subnets + IGW + Route Tables
# ========================================

resource "aws_vpc" "main" {
  cidr_block           = "10.0.0.0/16"
  enable_dns_hostnames = true
  enable_dns_support   = true

  tags = {
    Name = "${var.instance_name}-vpc"
  }
}

resource "aws_internet_gateway" "igw" {
  vpc_id = aws_vpc.main.id

  tags = {
    Name = "${var.instance_name}-igw"
  }
}

resource "aws_subnet" "mgmt" {
  vpc_id                  = aws_vpc.main.id
  cidr_block              = "10.0.1.0/24"
  availability_zone       = var.availability_zone
  map_public_ip_on_launch = true

  tags = {
    Name = "${var.instance_name}-subnet-mgmt"
  }
}

resource "aws_subnet" "ingress" {
  vpc_id                  = aws_vpc.main.id
  cidr_block              = "10.0.2.0/24"
  availability_zone       = var.availability_zone
  map_public_ip_on_launch = true

  tags = {
    Name = "${var.instance_name}-subnet-ingress"
  }
}

resource "aws_subnet" "egress" {
  vpc_id                  = aws_vpc.main.id
  cidr_block              = "10.0.3.0/24"
  availability_zone       = var.availability_zone
  map_public_ip_on_launch = true

  tags = {
    Name = "${var.instance_name}-subnet-egress"
  }
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.main.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.igw.id
  }

  tags = {
    Name = "${var.instance_name}-rt-public"
  }
}

resource "aws_route_table_association" "mgmt" {
  subnet_id      = aws_subnet.mgmt.id
  route_table_id = aws_route_table.public.id
}

resource "aws_route_table_association" "ingress" {
  subnet_id      = aws_subnet.ingress.id
  route_table_id = aws_route_table.public.id
}

resource "aws_route_table_association" "egress" {
  subnet_id      = aws_subnet.egress.id
  route_table_id = aws_route_table.public.id
}

# ========================================
# 3. Security Group
# ========================================

resource "aws_security_group" "fc_sg" {
  name        = "${var.instance_name}-sg"
  description = "Allow SSH, VXLAN, ICMP"
  vpc_id      = aws_vpc.main.id

  ingress {
    description = "SSH"
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    description = "VXLAN"
    from_port   = 4789
    to_port     = 4789
    protocol    = "udp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    description = "ICMP"
    from_port   = -1
    to_port     = -1
    protocol    = "icmp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "${var.instance_name}-sg"
  }
}

# ========================================
# 4. Network Interfaces (ENIs)
# ========================================

resource "aws_network_interface" "mgmt" {
  subnet_id       = aws_subnet.mgmt.id
  security_groups = [aws_security_group.fc_sg.id]
  private_ips     = ["10.0.1.10"]

  tags = {
    Name = "${var.instance_name}-eni-mgmt"
  }
}

resource "aws_network_interface" "ingress" {
  subnet_id       = aws_subnet.ingress.id
  security_groups = [aws_security_group.fc_sg.id]
  private_ips     = ["10.0.2.10"]

  tags = {
    Name = "${var.instance_name}-eni-ingress"
  }
}

resource "aws_network_interface" "egress" {
  subnet_id       = aws_subnet.egress.id
  security_groups = [aws_security_group.fc_sg.id]
  private_ips     = ["10.0.3.10"]

  tags = {
    Name = "${var.instance_name}-eni-egress"
  }
}

# ========================================
# 5. Elastic IP
# ========================================

resource "aws_eip" "mgmt" {
  network_interface = aws_network_interface.mgmt.id
  domain            = "vpc"

  tags = {
    Name = "${var.instance_name}-eip-mgmt"
  }
}

# ========================================
# 6. Bare Metal Instance
# ========================================

resource "aws_instance" "firecracker_baremetal" {
  ami           = var.ami_id
  instance_type = var.instance_type
  key_name      = aws_key_pair.firecracker.key_name

  network_interface {
    network_interface_id = aws_network_interface.mgmt.id
    device_index         = 0
  }

  network_interface {
    network_interface_id = aws_network_interface.ingress.id
    device_index         = 1
  }

  network_interface {
    network_interface_id = aws_network_interface.egress.id
    device_index         = 2
  }

  user_data = base64encode(templatefile("${path.module}/user_data.sh", {
    eni_mgmt                = "eth0"
    eni_ingress             = "eth1"
    eni_egress               = "eth2"
    create_customers_file    = file("${path.module}/create_customers.sh")
    generate_vm_config_file  = file("${path.module}/generate-vm-config.sh")
  }))

  tags = {
    Name = var.instance_name
  }

  lifecycle {
    prevent_destroy = false
  }
}