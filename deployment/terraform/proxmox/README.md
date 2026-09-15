# The development server on Proxmox

One Proxmox host runs the platform's services: a container per service — kinotic-server, the
one-shot migration, three Elasticsearch nodes on a physical disk each, Loki, Tempo, Mimir,
Grafana — created from the same images the compose stack pulls. The portal and the system
console are served by Front Door from the Azure root's sites account
(`deployment/terraform/azure/dev-server/deploy-ui.sh`), so the host exposes the API alone. The workload nodes are
separate machines provisioned with `deployment/vm-node`, configured from this root's
`vm_manager_env` output. The design and the reasons are on the
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
restarts a container whose entrypoint exited (Proxmox does not), and the timer that keeps the
API's DNS record on the router's public address, which the ISP changes:

```bash
scp host/prepare-host.sh host/kinotic-dyndns.py root@<host>:
ssh root@<host> ./prepare-host.sh /dev/disk/by-id/nvme-A /dev/disk/by-id/nvme-B /dev/disk/by-id/nvme-C
```

Uploads and the applier run over SSH as root, so the key of whoever runs terraform must be
in root's `authorized_keys` on the host and loaded in their agent.

The Azure side comes first: `deployment/terraform/azure/dev-server` (its README), applied
from the same checkout, because this root reads its outputs from that state file.

The `kinotic-server` and `kinotic-migration` images at `kinotic_version` must carry the
`dev-server` profile (`application-dev-server.yml`), which imports the secrets file: without
it the server starts with no master key. The nightly `gradle-build.yml` run promotes the
`-SNAPSHOT` tags from `develop`; `gh workflow run gradle-build.yml --ref develop` does it now.

## Secrets and the certificate

Placed on the host before the first apply, so the server starts with everything it needs:

```bash
./generate-secrets.sh ./dev-server-secrets
(cd ../azure/dev-server && terraform output -raw secrets_env)   # → dev-server-secrets/kinotic-server.env
# The shared GitHub App's private key and webhook secret → dev-server-secrets/kinotic-server/secrets.yml
./sync-secrets.sh ./dev-server-secrets <host>
```

The generated directory is the only copy of the JWT signing key and the master key. Keep it
somewhere safe and out of the repository; both are carried to the cloud at migration.

The certificate for the API's hostname is issued on the host by certbot with the DNS-01
plugin, as the server's principal (the `dev-server` root granted it DNS Zone Contributor),
and installed into the directory the server's container mounts — `cnb`, uid 1002 and gid 1001 in the container, is
101002:101001 on the host:

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
/opt/certbot/bin/certbot certonly --non-interactive --agree-tos --email <you> \
  --authenticator dns-azure --dns-azure-config /etc/kinotic/certbot-azure.ini \
  --deploy-hook 'install -m 0640 -o 101002 -g 101001 "$RENEWED_LINEAGE"/fullchain.pem "$RENEWED_LINEAGE"/privkey.pem /etc/kinotic/secrets/kinotic-server/certs/ && pct reboot 121 2>/dev/null || true' \
  -d dev-api.kinotic.ai
echo '0 3 * * * root /opt/certbot/bin/certbot renew -q' > /etc/cron.d/certbot
```

The deploy hook runs on every renewal too, which is all the certificate rotation there is.
`kinotic-dyndns.timer` reads the names to keep current from this certificate and its Azure
credentials from the same ini, so it starts working with the first issuance.

## Applying

```hcl
# local.auto.tfvars (gitignored)
proxmox_host      = "192.168.1.10"
proxmox_password  = "..."                # or PROXMOX_VE_PASSWORD in the environment
server_ip         = "192.168.1.20/24"    # the address the router reserves for server_mac; the interface takes it by DHCP
server_mac        = "BC:24:11:00:00:01"
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
`migration_history` index, then the server.

The API is on `https://dev-api.kinotic.ai` once the router forwards 443 to the server. The
server listens on 443 itself (`api_port`), because a consumer router forwards a port only to
the same port: an autodev hook the applier installs lowers the container's unprivileged port
floor, so the server's own user binds it. The router picks the target by device and reserves
an address for it when the forward is saved, so the server's LAN interface takes its address
by DHCP under a fixed MAC (`server_mac`), and `server_ip` is the address the router reserved,
which the nodes dial;
the portal and the system console are on Front Door as soon as `deploy-ui.sh` in the Azure
root has uploaded them.

## After the first apply

1. **The GitHub App's webhook** → `https://dev-api.kinotic.ai/api/github/webhook`.

2. **The nodes.** Each is Ubuntu 22.04 on its own machine with the kit from
   `deployment/vm-node` (its README: the two XFS `prjquota` partitions, then `setup-node.sh`,
   egress default-deny, `install-vm-manager.sh`, `verify-node.sh`). Its configuration is this
   root's output plus the node's own id; the machine credentials come from a SYSTEM-scope
   machine created in the system console:

   ```bash
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

- **Logs.** Each container's stdout and stderr go to `/var/log/kinotic/<name>.log` on the
  host (16 MB, one rotation); the server's logs are in Loki too, through Grafana.
- **A config or environment change** — `tempo.yml` in `deployment/docker-compose`, a value
  in `main.tf` — is a `terraform apply`: the applier restarts only the containers whose
  manifest or files changed.
- **A newer image** (a republished SNAPSHOT included) is a replacement of the image and the
  containers built from it; every container's state is in host directories, so it comes back
  with its data:

  ```bash
  terraform apply -replace=proxmox_oci_image.kinotic_server -replace='proxmox_virtual_environment_container.fleet["kinotic-server"]'
  ```

  A container replaced this way keeps its vmid, so run the applier yourself afterwards:
  `ssh root@<host> python3 /var/lib/vz/snippets/kinotic-apply-container.py /var/lib/vz/snippets/kinotic-*.manifest.json`.
  To re-run the migration on the same image, `rm /var/lib/kinotic/state/120.ran` first.
- **New secrets** are another `./sync-secrets.sh`, which re-applies the server and Grafana.
- **Stopping a container** for more than a minute: remove its marker first,
  `rm /var/lib/kinotic/state/keepalive/<vmid>`, or `kinotic-keepalive.timer` starts it
  again; the next apply or applier run puts the marker back.
- **The node kit** is idempotent: `ssh kinotic@<node ip> sudo /opt/kinotic/vm-node/verify-node.sh`
  after a reboot, `setup-node.sh` again to pick up a new Kata release.
- **`terraform destroy`** removes the containers, the images, and the private network. The
  host directories are not touched: a new apply mounts the same Elasticsearch data, the same
  store data, and the same secrets. The nodes are not this root's and keep running.
