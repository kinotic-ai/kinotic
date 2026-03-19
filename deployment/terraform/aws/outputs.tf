output "vpc_id" {
  value = aws_vpc.main.id
}

output "subnet_ids" {
  value = {
    mgmt    = aws_subnet.mgmt.id
    ingress = aws_subnet.ingress.id
    egress  = aws_subnet.egress.id
  }
}

output "instance_public_ip" {
  value = aws_eip.mgmt.public_ip
}

output "key_pair_name" {
  value = aws_key_pair.firecracker.key_name
}

output "private_key_file" {
  value = local_file.private_key.filename
}

output "ssh_command" {
  value = "ssh -i ${local_file.private_key.filename} ec2-user@${aws_eip.mgmt.public_ip}"
}

output "onboard_customer_command" {
  value = <<EOT
# Onboard a new customer:
ssh -i ${local_file.private_key.filename} ec2-user@${aws_eip.mgmt.public_ip} <<'EOF'
sudo /usr/local/bin/create_customers.sh 1001   # Gets 10.3.232.0/24
EOF
EOT
}