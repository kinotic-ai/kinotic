# ── The development server on Proxmox ────────────────────────────────────────
# One container per service, on one host. Every service the compose stack runs locally —
# kinotic-server, the one-shot migration, three Elasticsearch nodes, Loki, Tempo, Mimir,
# Grafana — is an unprivileged LXC container created from the image compose pulls, with its
# state in a host directory: the Elasticsearch nodes on a physical disk each. The workload
# nodes are separate machines provisioned with deployment/vm-node; they dial the server and
# the stores on the LAN, and the vm_manager_env output is their configuration.
#
# Terraform owns what the Proxmox API exposes: the private network, the images, the
# containers with their mounts, and the files it uploads to the host. The rest —
# the environment each entrypoint sees, which the API validates as word-keyed and
# Elasticsearch's dotted settings are not, the console log, the resolvers, and the
# ownership of the mounted directories — host/kinotic-apply-container.py applies on the
# host from a manifest per container, merging in the secrets the operator placed there,
# so nothing secret passes through this root or its state (README.md).

data "terraform_remote_state" "azure" {
  backend = "local"
  config = {
    path = var.azure_state_path
  }
}

locals {
  azure = data.terraform_remote_state.azure.outputs

  server_ip  = split("/", var.server_ip)[0]
  loki_ip    = split("/", var.loki_ip)[0]
  tempo_ip   = split("/", var.tempo_ip)[0]
  mimir_ip   = split("/", var.mimir_ip)[0]
  grafana_ip = split("/", var.grafana_ip)[0]

  # The private network: the host is its gateway, the ES nodes take .11 to .13
  private_prefix       = split("/", var.private_cidr)[1]
  private_gateway      = cidrhost(var.private_cidr, 1)
  es_ips               = [for i in range(3) : cidrhost(var.private_cidr, 11 + i)]
  server_private_ip    = cidrhost(var.private_cidr, 20)
  migration_private_ip = cidrhost(var.private_cidr, 21)

  compose_dir = "${path.module}/../../docker-compose"
  config_root = "${var.data_dir}/config"
  data_root   = "${var.data_dir}/data"

  # Each image's entrypoint and environment, as its OCI config declares them. Proxmox takes
  # both from the image when it creates the container, and the provider deletes whichever
  # the configuration leaves unset on the next update, so they are stated here.
  images = {
    elasticsearch = {
      entrypoint = "/bin/tini -- /usr/local/bin/docker-entrypoint.sh eswrapper"
      env = {
        PATH              = "/usr/share/elasticsearch/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
        container         = "oci"
        ELASTIC_CONTAINER = "true"
        SHELL             = "/bin/bash"
      }
    }
    loki = {
      entrypoint = "/usr/bin/loki -config.file=/etc/loki/local-config.yaml -auth.enabled=true -querier.multi-tenant-queries-enabled=true"
      env = {
        PATH          = "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:/busybox"
        SSL_CERT_FILE = "/etc/ssl/certs/ca-certificates.crt"
      }
    }
    tempo = {
      entrypoint = "/tempo -config.file=/etc/tempo/tempo.yml"
      env        = { PATH = "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin" }
    }
    mimir = {
      entrypoint = "/bin/mimir -target=all -config.file=/etc/mimir/mimir.yml"
      env = {
        PATH          = "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
        SSL_CERT_FILE = "/etc/ssl/certs/ca-certificates.crt"
      }
    }
    grafana = {
      entrypoint = "/run.sh"
      env = {
        PATH                  = "/usr/share/grafana/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
        GF_PATHS_CONFIG       = "/etc/grafana/grafana.ini"
        GF_PATHS_DATA         = "/var/lib/grafana"
        GF_PATHS_HOME         = "/usr/share/grafana"
        GF_PATHS_LOGS         = "/var/log/grafana"
        GF_PATHS_PLUGINS      = "/var/lib/grafana/plugins"
        GF_PATHS_PROVISIONING = "/etc/grafana/provisioning"
      }
    }
    # The buildpack images: kinotic-server and kinotic-migration
    cnb = {
      entrypoint = "/cnb/process/web"
      env = {
        PATH                 = "/cnb/process:/cnb/lifecycle:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
        CNB_LAYERS_DIR       = "/layers"
        CNB_APP_DIR          = "/workspace"
        CNB_PLATFORM_API     = "0.14"
        CNB_DEPRECATION_MODE = "quiet"
      }
    }
  }

  # The cluster is healthy once all three nodes have joined; the migration and the server
  # wait for it, and the host reaches the private network directly
  es_healthy = "curl -sf 'http://${local.es_ips[0]}:9200/_cluster/health?wait_for_nodes=3&wait_for_status=yellow&timeout=30s' >/dev/null"

  # Loki, Tempo, Mimir and Grafana run the compose stack's config files, with the compose
  # service names replaced by the containers' addresses. Grafana's `$$` is compose escaping.
  service_urls = {
    "http://loki:3100"  = "http://${local.loki_ip}:3100"
    "http://mimir:9009" = "http://${local.mimir_ip}:9009"
    "http://tempo:3200" = "http://${local.tempo_ip}:3200"
  }
  tempo_config = replace(file("${local.compose_dir}/tempo.yml"), "http://mimir:9009", local.service_urls["http://mimir:9009"])
  grafana_datasources = replace(replace(replace(replace(file("${local.compose_dir}/grafana-datasource.yaml"),
    "http://loki:3100", local.service_urls["http://loki:3100"]),
    "http://mimir:9009", local.service_urls["http://mimir:9009"]),
    "http://tempo:3200", local.service_urls["http://tempo:3200"]),
  "$$", "$")

  es_nodes = { for i in range(3) : "es-${i + 1}" => {
    vm_id    = 101 + i
    ip       = local.es_ips[i]
    data_dir = var.es_data_dirs[i]
  } }

  # Every setting the docker image turns into -E flags. Security is off: the cluster is
  # reachable only on the private network, from the containers on it and the host.
  es_env = {
    "cluster.name"                 = "kinotic"
    "cluster.initial_master_nodes" = join(",", keys(local.es_nodes))
    "discovery.seed_hosts"         = join(",", local.es_ips)
    "node.roles"                   = "master,data,ingest,transform"
    "xpack.security.enabled"       = "false"
    "ES_JAVA_OPTS"                 = "-Xms${var.es_memory_mb / 2}m -Xmx${var.es_memory_mb / 2}m"
  }

  es_containers = { for name, node in local.es_nodes : name => {
    vm_id         = node.vm_id
    description   = "Elasticsearch ${name}: master-eligible, data on ${node.data_dir}"
    image         = proxmox_oci_image.elasticsearch.id
    cores         = var.es_cores
    memory        = var.es_memory_mb
    order         = 10
    start_on_boot = true
    run_once      = false
    interfaces    = [{ bridge = var.private_network, address = "${node.ip}/${local.private_prefix}", gateway = local.private_gateway }]
    mounts        = [{ volume = node.data_dir, path = "/usr/share/elasticsearch/data", read_only = false }]
    entrypoint    = local.images.elasticsearch.entrypoint
    image_env     = local.images.elasticsearch.env
    env           = merge(local.es_env, { "node.name" = name })
    secrets_env   = null
    files         = {}
    uid           = 1000
    gid           = 0
    wait_for      = null
    verify        = null
    timeout       = 300
  } }

  # The non-secret half of the server's environment: the compose service's, the Azure root's
  # outputs, and the addresses only this root knows. The secret half is merged on the host.
  server_env = merge(local.azure.dev_server_env, merge([for i, ip in local.es_ips : {
    "KINOTIC_DOMAIN_ELASTICCONNECTIONS_${i}_SCHEME" = "http"
    "KINOTIC_DOMAIN_ELASTICCONNECTIONS_${i}_HOST"   = ip
    "KINOTIC_DOMAIN_ELASTICCONNECTIONS_${i}_PORT"   = "9200"
    }]...), {
    SPRING_PROFILES_ACTIVE      = "production,dev-server"
    BPL_JVM_HEAD_ROOM           = "10"
    JAVA_TOOL_OPTIONS           = "-XX:MaxDirectMemorySize=512m -javaagent:/workspace/BOOT-INF/classes/opentelemetry-javaagent.jar --add-opens=java.base/java.nio=ALL-UNNAMED --add-opens=java.base/java.util=ALL-UNNAMED --add-opens=java.base/java.lang=ALL-UNNAMED --add-opens=java.base/java.lang.invoke=ALL-UNNAMED"
    KINOTIC_MAX_OFF_HEAP_MEMORY = "419430400"

    # The portal is on Front Door; the router forwards 443 to the API port, so the public
    # API URL has no port
    KINOTIC_DOMAIN_APPBASEURL    = "https://${local.azure.portal_hostname}"
    KINOTIC_DOMAIN_APIBASEURL    = "https://${local.azure.api_hostname}"
    KINOTIC_DOMAIN_EMAIL_ENABLED = "true"
    # What a workload on the node VM dials, and the one destination every egress policy permits
    KINOTIC_SYSTEMAPI_DEPLOYMENT_SERVERHOST = local.server_ip
    KINOTIC_MANAGEMENTAPI_LOKIURL           = local.service_urls["http://loki:3100"]
    KINOTIC_MANAGEMENTAPI_TEMPOURL          = local.service_urls["http://tempo:3200"]
    KINOTIC_MANAGEMENTAPI_MIMIRURL          = local.service_urls["http://mimir:9009"]

    # No collector: the agent exports each signal to its store's OTLP endpoint, under the
    # platform tenant, which is what the compose collector stamps on the server's telemetry
    OTEL_SERVICE_NAME                   = "kinotic-server"
    OTEL_RESOURCE_ATTRIBUTES            = "service.name=kinotic-server"
    OTEL_TRACES_EXPORTER                = "otlp"
    OTEL_METRICS_EXPORTER               = "otlp"
    OTEL_LOGS_EXPORTER                  = "otlp"
    OTEL_EXPORTER_OTLP_PROTOCOL         = "http/protobuf"
    OTEL_EXPORTER_OTLP_TRACES_ENDPOINT  = "http://${local.tempo_ip}:4318/v1/traces"
    OTEL_EXPORTER_OTLP_METRICS_ENDPOINT = "http://${local.mimir_ip}:9009/otlp/v1/metrics"
    OTEL_EXPORTER_OTLP_LOGS_ENDPOINT    = "http://${local.loki_ip}:3100/otlp/v1/logs"
    OTEL_EXPORTER_OTLP_HEADERS          = "X-Scope-OrgID=kinotic-system"
    # jvm.buffer.* and jvm.system.cpu.*, which the agent gates behind this flag
    OTEL_INSTRUMENTATION_RUNTIME_TELEMETRY_EMIT_EXPERIMENTAL_TELEMETRY = "true"
    # Names the bare transport spans under each Elasticsearch call
    OTEL_INSTRUMENTATION_COMMON_PEER_SERVICE_MAPPING = join(",", [for ip in local.es_ips : "${ip}:9200=elasticsearch"])
  })

  containers = merge(local.es_containers, {
    loki = {
      vm_id         = 110
      description   = "Loki: logs, multi-tenant; the node's Alloy and the server push here"
      image         = proxmox_oci_image.loki.id
      cores         = 2
      memory        = 1024
      order         = 20
      start_on_boot = true
      run_once      = false
      interfaces    = [{ bridge = var.bridge, address = var.loki_ip, gateway = var.gateway }]
      mounts        = [{ volume = "${local.data_root}/loki", path = "/loki", read_only = false }]
      entrypoint    = local.images.loki.entrypoint
      image_env     = local.images.loki.env
      env           = {}
      secrets_env   = null
      files         = {}
      uid           = 10001
      gid           = 10001
      wait_for      = null
      verify        = null
      timeout       = 300
    }
    tempo = {
      vm_id         = 111
      description   = "Tempo: traces, multi-tenant; span metrics to Mimir"
      image         = proxmox_oci_image.tempo.id
      cores         = 2
      memory        = 1024
      order         = 20
      start_on_boot = true
      run_once      = false
      interfaces    = [{ bridge = var.bridge, address = var.tempo_ip, gateway = var.gateway }]
      mounts = [
        { volume = "${local.config_root}/tempo", path = "/etc/tempo", read_only = true },
        { volume = "${local.data_root}/tempo", path = "/var/tempo", read_only = false },
      ]
      entrypoint  = local.images.tempo.entrypoint
      image_env   = local.images.tempo.env
      env         = {}
      secrets_env = null
      files       = { "tempo.yml" = local.tempo_config }
      uid         = 10001
      gid         = 10001
      wait_for    = null
      verify      = null
      timeout     = 300
    }
    mimir = {
      vm_id         = 112
      description   = "Mimir: metrics, multi-tenant"
      image         = proxmox_oci_image.mimir.id
      cores         = 2
      memory        = 2048
      order         = 20
      start_on_boot = true
      run_once      = false
      interfaces    = [{ bridge = var.bridge, address = var.mimir_ip, gateway = var.gateway }]
      mounts = [
        { volume = "${local.config_root}/mimir", path = "/etc/mimir", read_only = true },
        { volume = "${local.data_root}/mimir", path = "/var/mimir", read_only = false },
      ]
      entrypoint  = local.images.mimir.entrypoint
      image_env   = local.images.mimir.env
      env         = {}
      secrets_env = null
      files       = { "mimir.yml" = file("${local.compose_dir}/mimir.yml") }
      uid         = 10001
      gid         = 10001
      wait_for    = null
      verify      = null
      timeout     = 300
    }
    grafana = {
      vm_id         = 113
      description   = "Grafana on :3000, with a login: the LAN reaches it"
      image         = proxmox_oci_image.grafana.id
      cores         = 1
      memory        = 512
      order         = 20
      start_on_boot = true
      run_once      = false
      interfaces    = [{ bridge = var.bridge, address = var.grafana_ip, gateway = var.gateway }]
      # The data directory first: the dashboards mount lands inside it
      mounts = [
        { volume = "${local.data_root}/grafana", path = "/var/lib/grafana", read_only = false },
        { volume = "${local.config_root}/grafana/dashboards", path = "/var/lib/grafana/dashboards", read_only = true },
        { volume = "${local.config_root}/grafana/provisioning/datasources", path = "/etc/grafana/provisioning/datasources", read_only = true },
        { volume = "${local.config_root}/grafana/provisioning/dashboards", path = "/etc/grafana/provisioning/dashboards", read_only = true },
      ]
      entrypoint = local.images.grafana.entrypoint
      image_env  = local.images.grafana.env
      env = {
        GF_AUTH_ANONYMOUS_ENABLED                 = "false"
        GF_SECURITY_ADMIN_USER                    = "admin"
        GF_DASHBOARDS_DEFAULT_HOME_DASHBOARD_PATH = "/var/lib/grafana/dashboards/kinotic-server.json"
      }
      secrets_env = "${var.secrets_dir}/grafana.env"
      files = {
        "provisioning/datasources/datasource.yaml" = local.grafana_datasources
        "provisioning/dashboards/dashboards.yaml"  = file("${local.compose_dir}/grafana-dashboards.yaml")
        "dashboards/kinotic-server.json"           = file("${local.compose_dir}/dashboards/kinotic-server.json")
      }
      uid      = 472
      gid      = 0
      wait_for = null
      verify   = null
      timeout  = 300
    }
    kinotic-migration = {
      vm_id         = 120
      description   = "The one-shot migration: runs to completion against es-1, once per image and configuration"
      image         = proxmox_oci_image.kinotic_migration.id
      cores         = 2
      memory        = 2048
      order         = 25
      start_on_boot = false
      run_once      = true
      interfaces    = [{ bridge = var.private_network, address = "${local.migration_private_ip}/${local.private_prefix}", gateway = local.private_gateway }]
      mounts        = []
      entrypoint    = local.images.cnb.entrypoint
      image_env     = local.images.cnb.env
      # The production profile applies no fixture migration: no test users, no console samples
      env = {
        SPRING_PROFILES_ACTIVE           = "production"
        KINOTIC_MIGRATION_ELASTIC_SCHEME = "http"
        KINOTIC_MIGRATION_ELASTIC_HOST   = local.es_ips[0]
        KINOTIC_MIGRATION_ELASTIC_PORT   = "9200"
      }
      secrets_env = null
      files       = {}
      uid         = 1002
      gid         = 1001
      wait_for    = local.es_healthy
      verify      = "curl -sf http://${local.es_ips[0]}:9200/migration_history >/dev/null"
      timeout     = 900
    }
    kinotic-server = {
      vm_id         = 121
      description   = "kinotic-server: REST, STOMP and MCP on :58503 with TLS; the router forwards 443 here"
      image         = proxmox_oci_image.kinotic_server.id
      cores         = var.server_cores
      memory        = var.server_memory_mb
      order         = 30
      start_on_boot = true
      run_once      = false
      # The LAN for peers, the node VM and Azure; the private network for Elasticsearch
      interfaces = [
        { bridge = var.bridge, address = var.server_ip, gateway = var.gateway },
        { bridge = var.private_network, address = "${local.server_private_ip}/${local.private_prefix}", gateway = null },
      ]
      # secrets.yml, the JWT key set and the certificate, placed by sync-secrets.sh and certbot
      mounts      = [{ volume = "${var.secrets_dir}/kinotic-server", path = "/etc/kinotic", read_only = true }]
      entrypoint  = local.images.cnb.entrypoint
      image_env   = local.images.cnb.env
      env         = local.server_env
      secrets_env = "${var.secrets_dir}/kinotic-server.env"
      files       = {}
      uid         = 1002
      gid         = 1001
      wait_for    = local.es_healthy
      verify      = null
      timeout     = 300
    }
  })

  # The startup order is the apply order too: Elasticsearch, the stores, the migration, the server
  apply_order = [for entry in sort([for name, c in local.containers : format("%02d %s", c.order, name)]) : split(" ", entry)[1]]

  # Every config file, uploaded flat as a snippet and copied by the applier into the host
  # directory the container bind-mounts
  config_uploads = merge([for name, c in local.containers : {
    for rel, content in c.files : "${name}/${rel}" => {
      container = name
      file_name = "kinotic-${name}-${replace(rel, "/", "-")}"
      content   = content
      dst       = "${local.config_root}/${name}/${rel}"
    }
  }]...)

  manifests = { for name, c in local.containers : name => {
    vmid        = c.vm_id
    name        = name
    image       = c.image
    entrypoint  = c.entrypoint
    env         = c.env
    secrets_env = c.secrets_env
    files = [for key, upload in local.config_uploads : {
      src  = "${var.snippets_dir}/${upload.file_name}"
      dst  = upload.dst
      mode = "0644"
      uid  = c.uid
      gid  = c.gid
    } if upload.container == name]
    # Every mounted host directory, owned by the container's user before the container exists:
    # Proxmox unpacks the image over the mounts inside the container's user namespace, which
    # cannot take ownership of a directory real root owns
    dirs        = [for m in c.mounts : { path = m.volume, uid = c.uid, gid = c.gid }]
    console_log = "/var/log/kinotic/${name}.log"
    dns         = var.dns_servers
    run_once    = c.run_once
    wait_for    = c.wait_for
    verify      = c.verify
    timeout     = c.timeout
  } }
}

# ── The private network ───────────────────────────────────────────────────────
# A simple SDN zone: a bridge with no physical port, whose subnet the host gateways and
# source-NATs, so the Elasticsearch nodes reach the snapshot container in Azure while nothing
# on the LAN reaches them.

resource "proxmox_sdn_zone_simple" "private" {
  id    = var.private_network
  nodes = [var.proxmox_node]
}

resource "proxmox_sdn_vnet" "private" {
  id   = var.private_network
  zone = proxmox_sdn_zone_simple.private.id
}

resource "proxmox_sdn_subnet" "private" {
  vnet    = proxmox_sdn_vnet.private.id
  cidr    = var.private_cidr
  gateway = local.private_gateway
  snat    = true
}

# SDN objects are pending until the cluster's SDN configuration is applied
resource "proxmox_sdn_applier" "private" {
  depends_on = [proxmox_sdn_subnet.private]

  lifecycle {
    replace_triggered_by = [proxmox_sdn_zone_simple.private, proxmox_sdn_vnet.private, proxmox_sdn_subnet.private]
  }
}

# ── Images ────────────────────────────────────────────────────────────────────
# The images compose pulls, as container templates. A tag is pulled once: to pick up a
# republished SNAPSHOT, replace the image and the containers built from it (README.md).

resource "proxmox_oci_image" "kinotic_server" {
  node_name    = var.proxmox_node
  datastore_id = var.files_datastore_id
  reference    = "docker.io/kinoticai/kinotic-server:${var.kinotic_version}"
}

resource "proxmox_oci_image" "kinotic_migration" {
  node_name    = var.proxmox_node
  datastore_id = var.files_datastore_id
  reference    = "docker.io/kinoticai/kinotic-migration:${var.kinotic_version}"
}

resource "proxmox_oci_image" "elasticsearch" {
  node_name    = var.proxmox_node
  datastore_id = var.files_datastore_id
  reference    = "docker.elastic.co/elasticsearch/elasticsearch:${var.elasticsearch_version}"
}

resource "proxmox_oci_image" "loki" {
  node_name    = var.proxmox_node
  datastore_id = var.files_datastore_id
  reference    = "docker.io/grafana/loki:${var.loki_version}"
}

resource "proxmox_oci_image" "tempo" {
  node_name    = var.proxmox_node
  datastore_id = var.files_datastore_id
  reference    = "docker.io/grafana/tempo:${var.tempo_version}"
}

resource "proxmox_oci_image" "mimir" {
  node_name    = var.proxmox_node
  datastore_id = var.files_datastore_id
  reference    = "docker.io/grafana/mimir:${var.mimir_version}"
}

resource "proxmox_oci_image" "grafana" {
  node_name    = var.proxmox_node
  datastore_id = var.files_datastore_id
  reference    = "docker.io/grafana/grafana:${var.grafana_version}"
}

# ── Files on the host ─────────────────────────────────────────────────────────

resource "proxmox_virtual_environment_file" "applier" {
  content_type = "snippets"
  datastore_id = var.files_datastore_id
  node_name    = var.proxmox_node

  source_raw {
    file_name = "kinotic-apply-container.py"
    data      = file("${path.module}/host/kinotic-apply-container.py")
  }
}

resource "proxmox_virtual_environment_file" "config" {
  for_each = local.config_uploads

  content_type = "snippets"
  datastore_id = var.files_datastore_id
  node_name    = var.proxmox_node

  source_raw {
    file_name = each.value.file_name
    data      = each.value.content
  }
}

resource "proxmox_virtual_environment_file" "manifest" {
  for_each = local.manifests

  content_type = "snippets"
  datastore_id = var.files_datastore_id
  node_name    = var.proxmox_node

  source_raw {
    file_name = "kinotic-${each.key}.manifest.json"
    data      = jsonencode(each.value)
  }
}

# ── The containers ────────────────────────────────────────────────────────────

resource "proxmox_virtual_environment_container" "fleet" {
  for_each = local.containers

  node_name    = var.proxmox_node
  vm_id        = each.value.vm_id
  description  = each.value.description
  tags         = ["kinotic", "dev-server"]
  unprivileged = true

  # The applier starts a container once its environment is in place and stops it to change
  # it, so whether one runs is not this resource's to reconcile
  started       = false
  start_on_boot = each.value.start_on_boot

  operating_system {
    template_file_id = each.value.image
  }

  cpu {
    cores = each.value.cores
  }

  memory {
    dedicated = each.value.memory
    swap      = 0
  }

  disk {
    datastore_id = var.vm_datastore_id
    size         = 8
  }

  # An OCI image has no network stack for Proxmox to hand the address to: the host sets it
  dynamic "network_interface" {
    for_each = each.value.interfaces
    content {
      name         = "eth${network_interface.key}"
      bridge       = network_interface.value.bridge
      host_managed = true
    }
  }

  # The image's own environment; the manifest's is applied on the host, since the API
  # validates each key as a word and Elasticsearch's dotted settings are not
  environment_variables = each.value.image_env

  initialization {
    hostname   = each.key
    entrypoint = each.value.entrypoint

    dns {
      servers = var.dns_servers
    }

    dynamic "ip_config" {
      for_each = each.value.interfaces
      content {
        ipv4 {
          address = ip_config.value.address
          gateway = ip_config.value.gateway
        }
      }
    }
  }

  dynamic "mount_point" {
    for_each = each.value.mounts
    content {
      volume    = mount_point.value.volume
      path      = mount_point.value.path
      read_only = mount_point.value.read_only
    }
  }

  startup {
    order    = each.value.order
    up_delay = each.value.order == 10 ? 30 : 0
  }

  lifecycle {
    ignore_changes = [started]
  }

  depends_on = [proxmox_sdn_applier.private, terraform_data.prepare]
}

locals {
  applier_triggers = {
    applier   = sha256(file("${path.module}/host/kinotic-apply-container.py"))
    manifests = { for name, m in local.manifests : name => sha256(jsonencode(m)) }
    configs   = { for key, upload in local.config_uploads : key => sha256(upload.content) }
  }
  applier_command = "ssh -o BatchMode=yes root@${var.proxmox_host} python3 ${var.snippets_dir}/kinotic-apply-container.py"
  manifest_paths  = join(" ", [for name in local.apply_order : "${var.snippets_dir}/kinotic-${name}.manifest.json"])
}

# Places every container's directories and config files before the containers exist:
# Proxmox mounts the bind mounts and unpacks the image over them inside the container's
# user namespace, which cannot take ownership of a host directory real root owns
resource "terraform_data" "prepare" {
  triggers_replace = local.applier_triggers

  provisioner "local-exec" {
    command = "${local.applier_command} --prepare ${local.manifest_paths}"
  }

  depends_on = [
    proxmox_virtual_environment_file.applier,
    proxmox_virtual_environment_file.config,
    proxmox_virtual_environment_file.manifest,
  ]
}

# Runs the applier over every manifest in startup order, after any of them, their config
# files, or the applier itself changed. A container replaced by hand keeps its vmid, so
# nothing here notices: run the same command yourself (README.md).
resource "terraform_data" "apply" {
  triggers_replace = local.applier_triggers

  provisioner "local-exec" {
    command = "${local.applier_command} ${local.manifest_paths}"
  }

  depends_on = [proxmox_virtual_environment_container.fleet, terraform_data.prepare]
}
