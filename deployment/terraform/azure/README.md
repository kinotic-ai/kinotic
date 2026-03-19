# Azure VM with Nested Virtualization - Terraform Configuration

This Terraform configuration deploys an Azure VM with nested virtualization enabled, configured to run Ubuntu guest VMs using KVM.

## Prerequisites

- [Terraform](https://www.terraform.io/downloads) >= 1.5.0
- Azure CLI installed (recommended) or Azure credentials configured
- Appropriate Azure subscription and permissions

## Azure Authentication

The Azure Terraform provider supports multiple authentication methods. Choose the one that best fits your use case:

### Option 1: Azure CLI Authentication (Recommended for Local Development)

This is the simplest method for local development:

1. **Install Azure CLI** (if not already installed):
   ```bash
   # On Ubuntu/Debian
   curl -sL https://aka.ms/InstallAzureCLIDeb | sudo bash
   
   # On macOS
   brew install azure-cli
   
   # On Windows
   # Download from https://aka.ms/installazurecliwindows
   ```

2. **Login to Azure**:
   ```bash
   az login
   ```
   
   This will open a browser window for you to authenticate. After successful login, you'll see your subscription information.

3. **Set your subscription** (if you have multiple subscriptions):
   ```bash
   # List available subscriptions
   az account list --output table
   
   # Set the active subscription
   az account set --subscription "Your-Subscription-Name-or-ID"
   ```

4. **Verify authentication**:
   ```bash
   az account show
   ```

The Terraform Azure provider will automatically use your Azure CLI credentials when you run `terraform plan` or `terraform apply`.

### Option 2: Service Principal with Client ID and Secret

This is recommended for CI/CD pipelines and automation:

1. **Create a Service Principal**:
   ```bash
   az ad sp create-for-rbac --role="Contributor" --scopes="/subscriptions/YOUR_SUBSCRIPTION_ID"
   ```

   This command outputs JSON with:
   - `appId` (Client ID)
   - `password` (Client Secret)
   - `tenant` (Tenant ID)

2. **Set Environment Variables**:
   ```bash
   export ARM_CLIENT_ID="your-app-id"
   export ARM_CLIENT_SECRET="your-client-secret"
   export ARM_SUBSCRIPTION_ID="your-subscription-id"
   export ARM_TENANT_ID="your-tenant-id"
   ```

   Or create a `.env` file and source it:
   ```bash
   # .env file
   export ARM_CLIENT_ID="your-app-id"
   export ARM_CLIENT_SECRET="your-client-secret"
   export ARM_SUBSCRIPTION_ID="your-subscription-id"
   export ARM_TENANT_ID="your-tenant-id"
   
   # Source it
   source .env
   ```

### Option 3: Service Principal with Client Certificate

For certificate-based authentication:

1. **Create a Service Principal with certificate**:
   ```bash
   az ad sp create-for-rbac --name "terraform-sp" --create-cert
   ```

2. **Set Environment Variables**:
   ```bash
   export ARM_CLIENT_ID="your-app-id"
   export ARM_CLIENT_CERTIFICATE_PATH="/path/to/certificate.pfx"
   export ARM_CLIENT_CERTIFICATE_PASSWORD="certificate-password"
   export ARM_SUBSCRIPTION_ID="your-subscription-id"
   export ARM_TENANT_ID="your-tenant-id"
   ```

### Option 4: Configure in Terraform Provider Block

You can also configure credentials directly in the provider block (not recommended for production):

```hcl
provider "azurerm" {
  features {}
  
  subscription_id = "your-subscription-id"
  tenant_id       = "your-tenant-id"
  client_id       = "your-client-id"
  client_secret   = "your-client-secret"
}
```

**Note**: Hardcoding credentials in Terraform files is a security risk. Use environment variables or Azure CLI instead.

### Required Permissions

Your Azure account or Service Principal needs the following permissions:
- **Contributor** role (recommended) - Allows creating, updating, and deleting resources
- Or at minimum:
  - Virtual Machine Contributor
  - Network Contributor
  - Storage Account Contributor

### Verify Authentication

Test your authentication setup:

```bash
# Using Azure CLI
az account show

# Using Terraform (should not error)
cd terraform/azure
terraform init
terraform plan
```

## Usage

1. **Navigate to the Azure directory**:
   ```bash
   cd terraform/azure
   ```

2. **Initialize Terraform**:
   ```bash
   terraform init
   ```

3. **Review and customize variables** (optional):
   ```bash
   # Edit variables.tf or use terraform.tfvars
   cat > terraform.tfvars <<EOF
   location            = "eastus"
   resource_group_name = "my-kinotic-rg"
   vm_name             = "my-vm"
   vm_size             = "Standard_D4s_v3"
   admin_username      = "azureuser"
   EOF
   ```

4. **Plan the deployment**:
   ```bash
   terraform plan
   ```

5. **Apply the configuration**:
   ```bash
   terraform apply
   ```

6. **Get connection information**:
   ```bash
   terraform output ssh_command
   terraform output vm_public_ip
   ```

## Verify Nested Virtualization

After the VM is deployed, connect via SSH and verify nested virtualization:

```bash
# Connect to the VM
ssh -i azure-vm-key-*.pem azureuser@<public-ip>

# Check for virtualization extensions
grep -c vmx /proc/cpuinfo
# Should return a number > 0

# For AMD processors
grep -c svm /proc/cpuinfo

# Check KVM status
kvm-ok
# Should indicate "KVM acceleration can be used"

# Check libvirt
sudo systemctl status libvirtd
virsh list --all
```

## Creating Ubuntu Guest VMs

Once nested virtualization is verified, you can create Ubuntu guest VMs using:

```bash
# Using virt-install
sudo virt-install \
  --name ubuntu-guest \
  --ram 2048 \
  --disk path=/var/lib/libvirt/images/ubuntu-guest.qcow2,size=20 \
  --vcpus 2 \
  --os-type linux \
  --os-variant ubuntu22.04 \
  --network network=default \
  --graphics none \
  --console pty,target_type=serial \
  --location 'http://archive.ubuntu.com/ubuntu/dists/jammy/main/installer-amd64/' \
  --extra-args 'console=ttyS0,115200n8 serial'
```

Or use `virt-manager` GUI (via X11 forwarding) for a graphical interface.

## Troubleshooting Authentication Issues

### Issue: "Azure CLI not found"
**Solution**: Install Azure CLI or use environment variables for Service Principal authentication.

### Issue: "No subscription found"
**Solution**: 
```bash
az login
az account list --output table
az account set --subscription "Your-Subscription-ID"
```

### Issue: "Insufficient permissions"
**Solution**: Ensure your account has Contributor role or equivalent permissions:
```bash
az role assignment list --assignee $(az account show --query user.name -o tsv) --scope /subscriptions/YOUR_SUBSCRIPTION_ID
```

### Issue: "Invalid client secret"
**Solution**: Regenerate the Service Principal secret:
```bash
az ad sp credential reset --name "your-service-principal-name"
```

## Cleanup

To destroy all resources:

```bash
terraform destroy
```
