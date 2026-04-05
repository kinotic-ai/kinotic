#!/usr/bin/env bash
# ── Bootstrap Terraform Remote State ──────────────────────
#
# Run once before the first `terraform init`. Creates the Azure
# Storage Account that holds terraform.tfstate.
#
# Prerequisites: az login

set -euo pipefail

RG_NAME="rg-kinotic-tfstate"
LOCATION="westus2"
SA_NAME="stkinotictfstate"
CONTAINER="tfstate"

echo "==> Creating resource group ${RG_NAME}"
az group create --name "$RG_NAME" --location "$LOCATION" --output none

echo "==> Creating storage account ${SA_NAME}"
az storage account create \
  --name "$SA_NAME" \
  --resource-group "$RG_NAME" \
  --location "$LOCATION" \
  --sku Standard_LRS \
  --min-tls-version TLS1_2 \
  --allow-blob-public-access false \
  --output none

echo "==> Creating blob container ${CONTAINER}"
az storage container create \
  --name "$CONTAINER" \
  --account-name "$SA_NAME" \
  --output none

echo "==> Done. Run: terraform init"
