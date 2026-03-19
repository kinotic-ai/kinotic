variable "region" {
  description = "AWS region"
  type        = string
  default     = "us-east-1"
}

variable "availability_zone" {
  description = "AZ for all resources"
  type        = string
  default     = "us-east-1c"
}

variable "instance_name" {
  description = "Name tag for the bare metal instance"
  type        = string
  default     = "kinotic"
}

variable "instance_type" {
  description = "EC2 bare metal instance type (e.g., a1.metal, i3en.metal, c6gn.metal, m6g.metal, etc.)"
  type        = string
  default     = "a1.metal"
}

variable "ami_id" {
  description = "AMI ID for the bare metal instance"
  type        = string
  default     = "ami-0bd040bf2a1a73ba6"
}