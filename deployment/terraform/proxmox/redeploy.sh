#!/usr/bin/env bash
# Deploys what changed since the last run, and nothing else:
#
#   - the kinotic-server and kinotic-migration images, when the tag at kinotic_version has a
#     new digest on Docker Hub: both containers are replaced (a republished SNAPSHOT keeps its
#     tag, so a plain apply never notices), the migration runs again, and the applier restores
#     the settings the provider drops
#   - the portal and the system console, when kinotic-frontend differs from what the sites
#     account serves (deploy-ui.sh records the tree it built in each site's version.json)
#   - the vm-manager on each node in vm_nodes, when the version vm_manager_version resolves
#     to on npm is not the one installed; the node kit is synced first so the installer is current
#
#   redeploy.sh [--dry-run]     --dry-run prints the decisions and changes nothing
#
# Runs from the machine that applies this root: terraform, az (signed in) and ssh to the host
# and the nodes. A config change in main.tf is still a terraform apply; this is for new builds.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
AZURE="$HERE/../azure/dev-server"
REPO="$(cd "$HERE/../../.." && pwd)"
DRY_RUN=false
[[ "${1:-}" == "--dry-run" ]] && DRY_RUN=true

out() { terraform -chdir="$HERE" output -raw "$1"; }
HOST="$(out proxmox_host)"
VERSION="$(out kinotic_version)"
API_HOSTNAME="$(out api_hostname)"
STATE="${STATE_DIR:-/var/lib/kinotic/state}"
SNIPPETS="${SNIPPETS_DIR:-/var/lib/vz/snippets}"
MARKER="$STATE/images.deployed"

hub_digest() {
  curl -fsS "https://hub.docker.com/v2/repositories/kinoticai/$1/tags/$VERSION" \
    | python3 -c 'import sys, json; print(json.load(sys.stdin)["digest"])'
}

# ---- kinotic-server and kinotic-migration -------------------------------------------------
wanted_images="server=$(hub_digest kinotic-server) migration=$(hub_digest kinotic-migration)"
deployed_images="$(ssh "root@$HOST" "cat '$MARKER' 2>/dev/null || true")"
if [[ "$wanted_images" == "$deployed_images" ]]; then
  echo "==> Images: kinotic-server and kinotic-migration $VERSION are what the host runs"
elif $DRY_RUN; then
  echo "==> Images: $VERSION has a new digest on Docker Hub; would replace both containers and re-run the migration"
else
  echo "==> Images: $VERSION has a new digest on Docker Hub; replacing both containers"
  migration_vmid="$(terraform -chdir="$HERE" output -json containers | python3 -c 'import sys, json; print(json.load(sys.stdin)["kinotic-migration"])')"
  plan="$(mktemp)"
  terraform -chdir="$HERE" plan -input=false -out="$plan" \
    -replace=proxmox_oci_image.kinotic_server -replace='proxmox_virtual_environment_container.fleet["kinotic-server"]' \
    -replace=proxmox_oci_image.kinotic_migration -replace='proxmox_virtual_environment_container.fleet["kinotic-migration"]' >/dev/null
  # the marker is per vmid, so a replaced migration container would otherwise not run
  ssh "root@$HOST" "rm -f '$STATE/$migration_vmid.ran'"
  terraform -chdir="$HERE" apply -input=false "$plan"
  rm -f "$plan"
  # A replaced container keeps its vmid, which the apply step does not treat as a change
  ssh "root@$HOST" "python3 '$SNIPPETS/kinotic-apply-container.py' '$SNIPPETS'/kinotic-*.manifest.json"
  echo "==> Waiting for https://$API_HOSTNAME"
  for _ in $(seq 1 60); do
    if [[ "$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 "https://$API_HOSTNAME/v1")" == "400" ]]; then
      break
    fi
    sleep 5
  done
  ssh "root@$HOST" "echo '$wanted_images' > '$MARKER'"
fi

# ---- the portal and the system console ----------------------------------------------------
frontend_tree="$(git -C "$REPO" rev-parse HEAD:kinotic-frontend)"
if [[ -n "$(git -C "$REPO" status --porcelain -- kinotic-frontend)" ]]; then
  frontend_tree="$frontend_tree-dirty"
fi
account="$(terraform -chdir="$AZURE" output -raw sites_storage_blob_endpoint | sed -E 's#https://([^.]+)\..*#\1#')"
portal_hostname="$(terraform -chdir="$AZURE" output -raw portal_hostname)"
version_file="$(mktemp)"
deployed_tree=""
if az storage blob download --auth-mode login --account-name "$account" --container-name sites \
     --name "$portal_hostname/version.json" --file "$version_file" --only-show-errors >/dev/null 2>&1; then
  deployed_tree="$(python3 -c 'import sys, json; print(json.load(open(sys.argv[1])).get("commitSha", ""))' "$version_file")"
fi
rm -f "$version_file"
if [[ "$frontend_tree" == "$deployed_tree" && "$frontend_tree" != *-dirty ]]; then
  echo "==> UIs: the sites account serves kinotic-frontend $frontend_tree"
elif $DRY_RUN; then
  echo "==> UIs: kinotic-frontend is $frontend_tree, the sites account serves ${deployed_tree:-nothing}; would build and upload"
else
  echo "==> UIs: kinotic-frontend is $frontend_tree, the sites account serves ${deployed_tree:-nothing}"
  "$AZURE/deploy-ui.sh"
fi

# ---- the vm-manager on each node ----------------------------------------------------------
wanted_vm_manager="$(npm view "@kinotic-ai/vm-manager@$(out vm_manager_version)" version 2>/dev/null | tail -1 | tr -d "'\"")"
terraform -chdir="$HERE" output -json vm_nodes | python3 -c 'import sys, json; print("\n".join(json.load(sys.stdin)))' | while read -r node; do
  [[ -z "$node" ]] && continue
  installed="$(ssh "$node" "bun -e \"console.log(require('/opt/kinotic/vm-manager/node_modules/@kinotic-ai/vm-manager/package.json').version)\" 2>/dev/null" || true)"
  if [[ "$installed" == "$wanted_vm_manager" ]]; then
    echo "==> Node $node: vm-manager $installed"
  elif $DRY_RUN; then
    echo "==> Node $node: vm-manager ${installed:-absent}; would install $wanted_vm_manager"
  else
    echo "==> Node $node: vm-manager ${installed:-absent}; installing $wanted_vm_manager"
    rsync -rlt "$REPO/deployment/vm-node/" "$node:vm-node/"
    ssh "$node" "cd vm-node && sudo -n VM_MANAGER_VERSION='$wanted_vm_manager' ./install-vm-manager.sh"
  fi
done
