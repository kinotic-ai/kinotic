# The development server on Proxmox

One Proxmox host runs the platform's services: a container per service — the org, system and
app servers, the one-shot migration, three Elasticsearch nodes on a physical disk each, Loki,
Tempo, Mimir, Grafana — created from the same images the compose stack pulls, and the edge, an
HAProxy container that passes each TLS connection on the router's forwarded 443 to the server
its SNI names. The portal and the system console are served by Front Door from the Azure root's
sites account (`deployment/terraform/azure/dev-server/deploy-ui.sh`), so the host exposes the
edge alone. The workload nodes are separate machines provisioned with `deployment/vm-node`,
configured from this root's `vm_manager_env` output. The design and the reasons are on the
[Development Server](https://kinotic.ai/platform/development-server) page; this is the
runbook.

Terraform owns what the Proxmox API exposes: the private network the Elasticsearch nodes
live on, the images, the containers with their mounts, and the files it uploads to the
host. The API validates a container's environment as word-keyed, which Elasticsearch's
dotted settings are not, and writes no resolv.conf into an OCI image, so terraform uploads
a manifest per container and `host/kinotic-apply-container.py` applies it on the host: the
environment, the resolvers, the console log, the config files each store reads, and the
ownership of the directories each container mounts. The applier merges in the secrets the
operator placed on the host, so nothing secret goes through terraform or its state.

Each container's state lives in a host directory and survives the container: the
Elasticsearch data on ZFS datasets `/es1/data`, `/es2/data`, `/es3/data`, one pool per
disk; everything else under `/var/lib/kinotic/data/<service>`; the config files under
`/var/lib/kinotic/config/<service>`; secrets under `/etc/kinotic/secrets`.

## Before the first apply

On the host, once. Proxmox VE 9.1 or later, installed by hand on the first disk; the
enterprise repository the installer enables answers 401 without a subscription, so disable
it and enable `pve-no-subscription` under Node → Repositories.

```bash
# The three Elasticsearch drives, whole, by stable id
ls -l /dev/disk/by-id/ | grep -v part
```

Terraform authenticates as `root@pam` with its password: bind mounts into containers are
allowed for that user alone, and an API token, even one without privilege separation,
authenticates as `root@pam!name` and fails the check.

Then `host/prepare-host.sh` with the three Elasticsearch disks: the ZFS pools, the
directories, the sysctl Elasticsearch needs, the datastore content types, the timer that
restarts a container whose entrypoint exited (Proxmox does not), the timer that keeps the
servers' DNS records on the router's public address, which the ISP changes, and the host's own
exposure: SSH by key only, rpcbind off, and the Proxmox firewall admitting SSH and the web UI
from the LAN alone. The key of whoever runs it must already be in root's `authorized_keys`:

```bash
scp host/prepare-host.sh host/kinotic-dyndns.py root@<host>:
ssh root@<host> ./prepare-host.sh /dev/disk/by-id/nvme-A /dev/disk/by-id/nvme-B /dev/disk/by-id/nvme-C
```

Uploads and the applier run over SSH as root, so the key of whoever runs terraform must be
in root's `authorized_keys` on the host and loaded in their agent.

The Azure side comes first: `deployment/terraform/azure/dev-server` (its README), applied
from the same checkout, because this root reads its outputs from that state file.

The `kinotic-server-org`, `kinotic-server-system`, `kinotic-server-app` and `kinotic-migration`
images at `kinotic_version` must carry the `dev-server` profile (`application-dev-server.yml`),
which imports the secrets file: without it a server starts with no master key. The nightly `gradle-build.yml` run promotes the
`-SNAPSHOT` tags from `develop`; `gh workflow run gradle-build.yml --ref develop` does it now.

## Secrets and the certificates

Placed on the host before the first apply, so the servers start with everything they need:

```bash
./generate-secrets.sh ./dev-server-secrets
(cd ../azure/dev-server && terraform output -raw secrets_env)   # → dev-server-secrets/kinotic-servers.env
# The shared GitHub App's private key and webhook secret → dev-server-secrets/kinotic-server-org/secrets.yml
# and dev-server-secrets/kinotic-server-system/secrets.yml, the servers that load the GitHub module
./sync-secrets.sh ./dev-server-secrets <host>
```

Each server has a directory of its own, mounted at `/etc/kinotic` in its container: its
`secrets.yml`, its own JWT key set, and its certificate. The master key in the three
`secrets.yml` files is one key, since the servers store and read the same secrets. The generated
directory is the only copy of the JWT signing keys and the master key. Keep it somewhere safe and
out of the repository; they are carried to the cloud at migration. The two
roots' state files hold the principal's secret and the storage keys, so keep them at mode 0600.

Each server's certificate is issued on the host by certbot with the DNS-01 plugin, as the
servers' principal (the `dev-server` root granted it DNS Zone Contributor), and installed into
the directory that server's container mounts — `cnb`, uid 1002 and gid 1001 in the container,
is 101002:101001 on the host. The app server's carries its own name and every application's
host under it:

```bash
ssh root@<host>
# pyOpenSSL 26 drops X509Req, which the josepy 1.x certbot pins still imports; azure-mgmt-dns 9
# changes the client constructor certbot-dns-azure calls
python3 -m venv /opt/certbot && /opt/certbot/bin/pip install certbot certbot-dns-azure "pyOpenSSL>=25,<26" "azure-mgmt-dns<9"
install -m 0600 /dev/stdin /etc/kinotic/certbot-azure.ini <<EOT
dns_azure_sp_client_id = <AZURE_CLIENT_ID>
dns_azure_sp_client_secret = <AZURE_CLIENT_SECRET>
dns_azure_tenant_id = <AZURE_TENANT_ID>
dns_azure_environment = AzurePublicCloud
dns_azure_zone1 = kinotic.ai:/subscriptions/<subscription>/resourceGroups/<global rg>
EOT
issue() {   # <server> <vmid> <name>...
  local server=$1 vmid=$2 name domains=(); shift 2
  for name in "$@"; do domains+=(-d "$name"); done
  /opt/certbot/bin/certbot certonly --non-interactive --agree-tos --email <you> --cert-name "$server" \
    --authenticator dns-azure --dns-azure-config /etc/kinotic/certbot-azure.ini \
    --deploy-hook "install -m 0640 -o 101002 -g 101001 \"\$RENEWED_LINEAGE\"/fullchain.pem \"\$RENEWED_LINEAGE\"/privkey.pem /etc/kinotic/secrets/$server/certs/ && pct reboot $vmid 2>/dev/null || true" \
    "${domains[@]}"
}
issue kinotic-server-org 121 dev-api.kinotic.ai
issue kinotic-server-system 122 dev-system-api.kinotic.ai
issue kinotic-server-app 123 dev-apps-api.kinotic.ai '*.dev-apps-api.kinotic.ai'
echo '0 3 * * * root /opt/certbot/bin/certbot renew -q' > /etc/cron.d/certbot
```

The deploy hook runs on every renewal too, which is all the certificate rotation there is.
`kinotic-dyndns.timer` reads the names to keep current from these certificates and its Azure
credentials from the same ini, so it starts working with the first issuance.

## Applying

```hcl
# local.auto.tfvars (gitignored)
proxmox_host      = "192.168.1.10"
proxmox_password  = "..."                # or PROXMOX_VE_PASSWORD in the environment
edge_ip           = "192.168.1.20/24"    # the address the router reserves for edge_mac; the interface takes it by DHCP
edge_mac          = "BC:24:11:00:00:01"
loki_ip           = "192.168.1.21/24"
tempo_ip          = "192.168.1.22/24"
mimir_ip          = "192.168.1.23/24"
grafana_ip        = "192.168.1.24/24"
gateway           = "192.168.1.1"
dns_servers       = ["192.168.1.1"]
```

```bash
cd deployment/terraform/proxmox
terraform init
terraform apply
```

The apply creates the private network, pulls the images, creates every container stopped,
uploads the manifests, and runs the applier over them in startup order: the three
Elasticsearch nodes, then Loki, Tempo, Mimir and Grafana, then the migration, which waits for
the cluster to be healthy, runs to completion, and is verified against the
`migration_history` index, then the three servers, which form one Ignite cluster over their
private addresses, then the edge.

The servers are on `https://dev-api.kinotic.ai` (org), `https://dev-system-api.kinotic.ai`
(system) and `https://dev-apps-api.kinotic.ai` with every application's
`https://<organizationId>--<applicationId>.dev-apps-api.kinotic.ai` (app) once the router
forwards 443 to the edge. The edge listens on 443 itself, because a consumer router forwards a
port only to the same port: an autodev hook the applier installs lowers the container's
unprivileged port floor, so HAProxy's own user binds it. It reads the SNI of each connection
and passes the connection through unopened to that server on the private network, where the
server terminates TLS with its own certificate; a connection naming no server's hostname is
closed. The router picks the target by device and reserves an address for it when the forward
is saved, so the edge's LAN interface takes its address by DHCP under a fixed MAC
(`edge_mac`), and `edge_ip` is the address the router reserved, which the nodes dial for every
server; the portal and the system console are on Front Door as soon as `deploy-ui.sh` in the
Azure root has uploaded them.

## After the first apply

1. **The GitHub App's webhook** → `https://dev-api.kinotic.ai/api/github/webhook`.

2. **The nodes.** Each is Ubuntu 22.04 on its own machine with the kit from
   `deployment/vm-node` (its README: the two XFS `prjquota` partitions, then `setup-node.sh`,
   egress default-deny, `install-vm-manager.sh`, `verify-node.sh`). Its configuration is this
   root's output plus the node's own id; the machine credentials come from a SYSTEM-scope
   machine created in the system console:

   ```bash
   # Every server by its certificate's name, at the edge on the LAN; dnsmasq reads /etc/hosts on start and reload only
   terraform output -raw hosts_entry | ssh kinotic@<node ip> 'sudo tee -a /etc/hosts >/dev/null && sudo systemctl reload dnsmasq'
   { terraform output -raw vm_manager_env; echo KINOTIC_NODE_ID=dev-node-1; } | ssh kinotic@<node ip> 'sudo tee /etc/kinotic/vm-manager.env >/dev/null'
   ssh kinotic@<node ip> 'sudo tee /etc/kinotic/vm-manager.secrets.env >/dev/null && sudo chmod 0600 /etc/kinotic/vm-manager.secrets.env && sudo systemctl start kinotic-vm-manager' <<EOT
   KINOTIC_CLIENT_ID=<machine id>
   KINOTIC_CLIENT_SECRET=<machine secret>
   EOT
   ```

   The node appears `ONLINE` in the console with no health message. A first deployment lands
   on the first `ONLINE` node with room for it.

3. **Snapshots.** The storage account key (`terraform output -raw snapshots_storage_account_key`
   in the Azure root) goes into each node's keystore, then the repository and a daily policy
   are registered once, from the host, which reaches the private network directly:

   ```bash
   # As the elasticsearch user, which owns the keystore and the tool refuses to change; pct exec
   # runs as root and starts in /root, and the image has no su or runuser
   for id in 101 102 103; do
     lxc-attach -n $id --uid 1000 --gid 0 -- bash -c 'cd /usr/share/elasticsearch && bin/elasticsearch-keystore add -x azure.client.default.account <<<"stkinoticdevsnapshots" && bin/elasticsearch-keystore add -x azure.client.default.key <<<"<key>"'
   done
   curl -X POST http://10.10.0.11:9200/_nodes/reload_secure_settings
   curl -X PUT http://10.10.0.11:9200/_snapshot/azure -H 'Content-Type: application/json' -d '{"type":"azure","settings":{"container":"elasticsearch-snapshots"}}'
   curl -X PUT http://10.10.0.11:9200/_slm/policy/daily -H 'Content-Type: application/json' -d '{"schedule":"0 30 2 * * ?","name":"<daily-{now/d}>","repository":"azure","config":{"indices":"*"},"retention":{"expire_after":"30d"}}'
   ```

   The keystore lives in each node's root filesystem, so this is repeated after a node's
   container is replaced.

## Day 2

- **A new build** is `./redeploy.sh`, which deploys only what changed: the servers' and the
  migration's containers when the images at `kinotic_version` have a new digest on Docker Hub
  (the migration runs again), the portal and the console when `kinotic-frontend` differs from
  what the sites account serves, and the vm-manager on each node in `vm_nodes` when the
  version `vm_manager_version` resolves to on npm is not the one installed. `--dry-run` prints
  the decisions. It needs `az` signed in for the sites account and ssh to the host and the
  nodes; `vm_nodes` is a list of `user@host` in `local.auto.tfvars`.
- **Logs.** Each container's stdout and stderr go to `/var/log/kinotic/<name>.log` on the
  host (16 MB, one rotation); the servers' logs are in Loki too, through Grafana, and the
  edge's log records each connection's client address and the server it went to.
- **A config or environment change** — `tempo.yml` in `deployment/docker-compose`, a value
  in `main.tf` — is a `terraform apply`: the applier restarts only the containers whose
  manifest or files changed.
- **A newer image** (a republished SNAPSHOT included) is a replacement of the image and the
  containers built from it, which is what `redeploy.sh` does; every container's state is in
  host directories, so it comes back with its data. By hand:

  ```bash
  terraform apply -replace='proxmox_oci_image.server["kinotic-server-org"]' -replace='proxmox_virtual_environment_container.fleet["kinotic-server-org"]'
  ```

  A container replaced this way keeps its vmid, so run the applier yourself afterwards:
  `ssh root@<host> python3 /var/lib/vz/snippets/kinotic-apply-container.py /var/lib/vz/snippets/kinotic-*.manifest.json`.
  To re-run the migration on the same image, `rm /var/lib/kinotic/state/120.ran` first.
- **New secrets** are another `./sync-secrets.sh`, which re-applies the servers and Grafana.
- **Stopping a container** for more than a minute: remove its marker first,
  `rm /var/lib/kinotic/state/keepalive/<vmid>`, or `kinotic-keepalive.timer` starts it
  again; the next apply or applier run puts the marker back.
- **The node kit** is idempotent: `ssh kinotic@<node ip> sudo /opt/kinotic/vm-node/verify-node.sh`
  after a reboot, `setup-node.sh` again to pick up a new Kata release.
- **`terraform destroy`** removes the containers, the images, and the private network. The
  host directories are not touched: a new apply mounts the same Elasticsearch data, the same
  store data, and the same secrets. The nodes are not this root's and keep running.

## Moving from the single kinotic-server

A host built before the split runs one `kinotic-server` container, vmid 121, behind the
router's forward. The org server takes that vmid and the edge takes the forward:

1. **The router's forward.** In `local.auto.tfvars`, `server_ip` and `server_mac` become
   `edge_ip` and `edge_mac` with the same values. The router forwards 443 to that MAC, which is
   now the edge's, so the router needs no change.
2. **The secrets.** The master key stays, since every stored secret is named from it, and so
   does the JWT key set the CLI's tokens are signed with, which moves to the org server, where
   the CLI connects. The system and app servers get key sets of their own:

   ```bash
   cd dev-server-secrets     # the directory generate-secrets.sh wrote for kinotic-server
   mv kinotic-server.env kinotic-servers.env
   for server in kinotic-server-org kinotic-server-system kinotic-server-app; do
     mkdir -p $server/platform-secrets $server/certs
   done
   cp kinotic-server/secrets.yml kinotic-server-org/
   cp kinotic-server/secrets.yml kinotic-server-system/
   # The app server loads no GitHub module: its copy ends before the managementApi block
   awk '/^  managementApi:/ { exit } { print }' kinotic-server/secrets.yml > kinotic-server-app/secrets.yml
   mv kinotic-server/platform-secrets/jwt-signing-keys kinotic-server-org/platform-secrets/
   for server in kinotic-server-system kinotic-server-app; do
     printf '{"activeKeyId":"v1","keys":[{"id":"v1","key":"%s"}]}\n' "$(openssl rand -base64 32)" \
       > $server/platform-secrets/jwt-signing-keys
   done
   rm -r kinotic-server
   cd .. && ./sync-secrets.sh ./dev-server-secrets <host>
   ssh root@<host> rm -r /etc/kinotic/secrets/kinotic-server /etc/kinotic/secrets/kinotic-server.env
   ```
3. **The certificates.** On the host, `/opt/certbot/bin/certbot delete --cert-name dev-api.kinotic.ai`,
   whose deploy hook installs into the old directory, then the three `issue` lines above.
4. **The Azure root** first, for the new names' records (its README), then `terraform apply`
   here. The apply replaces container 121 with the org server and creates the system server,
   the app server, and the edge; the nodes take the new `hosts_entry` and `vm_manager_env`,
   whose server is now the system server's name.
