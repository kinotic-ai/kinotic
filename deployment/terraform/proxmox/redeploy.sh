#!/usr/bin/env bash
# Deploys what changed since the last run, and nothing else:
#
#   - each of the servers' images and kinotic-migration's, Summit 42's among them when it runs,
#     whose tag has a new digest on Docker Hub: only those containers are replaced (a republished
#     SNAPSHOT keeps its tag, so a plain apply never notices), a replaced migration runs again, and
#     the applier restores the settings the provider drops
#   - the portal and the system console, when kinotic-frontend differs from what the sites
#     Worker serves (deploy-ui.sh records the tree it built in each app's version.json)
#   - Summit 42's UI, when it runs and the summit-42 repository's main differs from the commit its
#     version.json records: built from a fresh clone with bun and uploaded beside the portal
#   - the vm-manager on each node in vm_nodes, when the version vm_manager_version resolves
#     to on npm is not the one installed; the node kit is synced first so the installer is current
#
#   redeploy.sh [--dry-run]     --dry-run prints the decisions and changes nothing
#
# Run it under ../with-dev-server-secrets.sh, whose root@pam password its plan and apply take.
#
# Runs from the machine that applies this root: terraform, az (signed in), bun, and ssh to the host
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
# Each server's name, hostname and image, one "name hostname image" line each
SERVERS="$(python3 -c 'import sys, json; hosts, images = json.loads(sys.argv[1]), json.loads(sys.argv[2]); print("\n".join(f"{name} {host} {images[name]}" for name, host in hosts.items()))' \
  "$(terraform -chdir="$HERE" output -json server_hostnames)" "$(terraform -chdir="$HERE" output -json server_images)")"
STATE="${STATE_DIR:-/var/lib/kinotic/state}"
SNIPPETS="${SNIPPETS_DIR:-/var/lib/vz/snippets}"
MARKER="$STATE/images.deployed"

hub_digest() {   # <repository>:<tag>
  curl -fsS "https://hub.docker.com/v2/repositories/${1%:*}/tags/${1##*:}" \
    | python3 -c 'import sys, json; print(json.load(sys.stdin)["digest"])'
}

# ---- the servers and kinotic-migration ----------------------------------------------------
# The marker holds one "name=digest" word per image; only a container whose digest differs is replaced
deployed_images="$(ssh "root@$HOST" "cat '$MARKER' 2>/dev/null || true")"
deployed_digest() { tr ' ' '\n' <<<"$deployed_images" | sed -n "s/^$1=//p"; }
migration_digest="$(hub_digest "kinoticai/kinotic-migration:$VERSION")"
wanted_images="migration=$migration_digest"
changed=() changed_hosts=() replace=()
# the migration goes first, so the applier runs it before the servers it migrates for
if [[ "$migration_digest" != "$(deployed_digest migration)" ]]; then
  changed+=(kinotic-migration)
  replace+=(-replace=proxmox_oci_image.kinotic_migration -replace='proxmox_virtual_environment_container.fleet["kinotic-migration"]')
fi
while read -r name hostname image; do
  digest="$(hub_digest "$image")"
  wanted_images="$wanted_images $name=$digest"
  if [[ "$digest" != "$(deployed_digest "$name")" ]]; then
    changed+=("$name")
    changed_hosts+=("$hostname")
    replace+=(-replace="proxmox_oci_image.server[\"$name\"]" -replace="proxmox_virtual_environment_container.fleet[\"$name\"]")
  fi
done <<<"$SERVERS"
if (( ${#changed[@]} == 0 )); then
  echo "==> Images: the servers and kinotic-migration are what the host runs"
elif $DRY_RUN; then
  echo "==> Images: a new digest on Docker Hub for ${changed[*]}; would replace their containers"
else
  echo "==> Images: a new digest on Docker Hub for ${changed[*]}; replacing their containers"
  plan="$(mktemp)"
  terraform -chdir="$HERE" plan -input=false -out="$plan" "${replace[@]}" >/dev/null
  if [[ "${changed[0]}" == kinotic-migration ]]; then
    # the marker is per vmid, so a replaced migration container would otherwise not run
    migration_vmid="$(terraform -chdir="$HERE" output -json containers | python3 -c 'import sys, json; print(json.load(sys.stdin)["kinotic-migration"])')"
    ssh "root@$HOST" "rm -f '$STATE/$migration_vmid.ran'"
  fi
  terraform -chdir="$HERE" apply -input=false "$plan"
  rm -f "$plan"
  # A replaced container keeps its vmid, which the apply step does not treat as a change
  ssh "root@$HOST" "python3 '$SNIPPETS/kinotic-apply-container.py' $(printf "'$SNIPPETS/kinotic-%s.manifest.json' " "${changed[@]}")"
  for hostname in ${changed_hosts[@]+"${changed_hosts[@]}"}; do
    echo "==> Waiting for https://$hostname"
    for _ in $(seq 1 60); do
      # any HTTP status means the server's TLS listener is up behind the edge
      if [[ "$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 "https://$hostname/")" != "000" ]]; then
        break
      fi
      sleep 5
    done
  done
  ssh "root@$HOST" "echo '$wanted_images' > '$MARKER'"
fi

# ---- the portal and the system console ----------------------------------------------------
frontend_tree="$(git -C "$REPO" rev-parse HEAD:kinotic-frontend)"
if [[ -n "$(git -C "$REPO" status --porcelain -- kinotic-frontend)" ]]; then
  frontend_tree="$frontend_tree-dirty"
fi
portal_hostname="$(terraform -chdir="$AZURE" output -raw portal_hostname)"
deployed_tree="$(curl -fsS --max-time 10 "https://$portal_hostname/version.json" 2>/dev/null \
  | python3 -c 'import sys, json; print(json.load(sys.stdin).get("commitSha", ""))' 2>/dev/null || true)"
if [[ "$frontend_tree" == "$deployed_tree" && "$frontend_tree" != *-dirty ]]; then
  echo "==> UIs: the portal serves kinotic-frontend $frontend_tree"
elif $DRY_RUN; then
  echo "==> UIs: kinotic-frontend is $frontend_tree, the portal serves ${deployed_tree:-nothing}; would build and deploy"
else
  echo "==> UIs: kinotic-frontend is $frontend_tree, the portal serves ${deployed_tree:-nothing}"
  "$AZURE/../deploy-ui.sh" dev-server dev-server
fi

# ---- Summit 42's UI ------------------------------------------------------------------------
summit42_hostname="$(terraform -chdir="$AZURE" output -json summit42_hostname | python3 -c 'import sys, json; print(json.load(sys.stdin) or "")')"
if [[ -n "$summit42_hostname" ]]; then
  summit42_commit="$(git ls-remote https://github.com/kinotic-ai/summit-42 refs/heads/main | cut -f1)"
  deployed_commit="$(curl -fsS --max-time 10 "https://$summit42_hostname/version.json" 2>/dev/null \
    | python3 -c 'import sys, json; print(json.load(sys.stdin).get("commitSha", ""))' 2>/dev/null || true)"
  if [[ "$summit42_commit" == "$deployed_commit" ]]; then
    echo "==> Summit 42: $summit42_hostname serves summit-42 $summit42_commit"
  elif $DRY_RUN; then
    echo "==> Summit 42: main is $summit42_commit, $summit42_hostname serves ${deployed_commit:-nothing}; would build and deploy"
  else
    echo "==> Summit 42: main is $summit42_commit, $summit42_hostname serves ${deployed_commit:-nothing}; building"
    checkout="$(mktemp -d)"
    git clone -q --depth 1 https://github.com/kinotic-ai/summit-42 "$checkout"
    (cd "$checkout/frontend" && bun install --frozen-lockfile && \
      VITE_KINOTIC_HOST="$(terraform -chdir="$AZURE" output -raw api_hostname)" VITE_KINOTIC_PORT=443 VITE_KINOTIC_USE_SSL=true \
      VITE_SUMMIT42_API_URL="https://$(terraform -chdir="$AZURE" output -raw summit42_api_hostname)" bun run build)
    printf '{"commitSha":"%s"}\n' "$(git -C "$checkout" rev-parse HEAD)" > "$checkout/frontend/dist/version.json"
    account="$(terraform -chdir="$AZURE" output -raw sites_storage_blob_endpoint | sed -E 's#https://([^.]+)\..*#\1#')"
    az storage blob upload-batch --auth-mode login --account-name "$account" \
      --destination sites --destination-path "$summit42_hostname" \
      --source "$checkout/frontend/dist" --overwrite --only-show-errors --no-progress >/dev/null
    rm -rf "$checkout"
  fi
fi

# ---- the vm-manager on each node ----------------------------------------------------------
wanted_vm_manager="$(npm view "@kinotic-ai/vm-manager@$(out vm_manager_version)" version 2>/dev/null | tail -1 | tr -d "'\"")"
terraform -chdir="$HERE" output -json vm_nodes | python3 -c 'import sys, json; print("\n".join(json.load(sys.stdin)))' | while read -r node; do
  [[ -z "$node" ]] && continue
  installed="$(ssh -n "$node" "bun -e \"console.log(require('/opt/kinotic/vm-manager/node_modules/@kinotic-ai/vm-manager/package.json').version)\" 2>/dev/null" || true)"
  if [[ "$installed" == "$wanted_vm_manager" ]]; then
    echo "==> Node $node: vm-manager $installed"
  elif $DRY_RUN; then
    echo "==> Node $node: vm-manager ${installed:-absent}; would install $wanted_vm_manager"
  else
    echo "==> Node $node: vm-manager ${installed:-absent}; installing $wanted_vm_manager"
    rsync -rlt "$REPO/deployment/vm-node/" "$node:vm-node/"
    ssh -n "$node" "cd vm-node && sudo -n VM_MANAGER_VERSION='$wanted_vm_manager' ./install-vm-manager.sh"
  fi
done
