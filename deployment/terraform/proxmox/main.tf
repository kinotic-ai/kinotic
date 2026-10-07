# ── The development server on Proxmox ────────────────────────────────────────
# One container per service, on one host. Every service the compose stack runs locally —
# the org, system and app servers, the one-shot migration, three Elasticsearch nodes, OpenFGA
# with its Postgres, Loki, Tempo, Mimir, Grafana — is an unprivileged LXC container created from the image compose
# pulls, with its state in a host directory: the Elasticsearch nodes on a physical disk each.
# The servers live on the private network; the edge, an HAProxy container on the LAN, takes
# the router's forwarded 443 and passes each TLS connection, unopened, to the server its SNI
# names. The workload nodes are separate machines provisioned with deployment/vm-node; they
# dial the servers through the edge and the stores on the LAN, and the vm_manager_env output
# is their configuration.
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

  edge_ip    = split("/", var.edge_ip)[0]
  loki_ip    = split("/", var.loki_ip)[0]
  tempo_ip   = split("/", var.tempo_ip)[0]
  mimir_ip   = split("/", var.mimir_ip)[0]
  grafana_ip = split("/", var.grafana_ip)[0]

  # The private network: the host is its gateway, the edge takes .10, the ES nodes .11 to .13,
  # OpenFGA's Postgres .14, OpenFGA .15 and its one-shot containers .16 and .17, the servers .20,
  # .22 and .23, the migration .21
  private_prefix             = split("/", var.private_cidr)[1]
  private_gateway            = cidrhost(var.private_cidr, 1)
  edge_private_ip            = cidrhost(var.private_cidr, 10)
  es_ips                     = [for i in range(3) : cidrhost(var.private_cidr, 11 + i)]
  openfga_db_private_ip      = cidrhost(var.private_cidr, 14)
  openfga_private_ip         = cidrhost(var.private_cidr, 15)
  openfga_migrate_private_ip = cidrhost(var.private_cidr, 16)
  openfga_init_private_ip    = cidrhost(var.private_cidr, 17)
  migration_private_ip       = cidrhost(var.private_cidr, 21)

  # The router forwards the public 443 to the same port on the edge, which serves every name there
  public_port = 443

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
    postgres = {
      entrypoint = "/usr/local/bin/docker-entrypoint.sh postgres"
      env = {
        PATH     = "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
        LANG     = "en_US.utf8"
        PG_MAJOR = "18"
        PGDATA   = "/var/lib/postgresql/18/docker"
      }
    }
    openfga = {
      entrypoint = "/openfga run"
      env = {
        PATH          = "/usr/local/sbin:/usr/local/bin:/usr/bin:/usr/sbin:/sbin:/bin"
        SSL_CERT_FILE = "/etc/ssl/certs/ca-certificates.crt"
      }
    }
    curl = {
      entrypoint = "/entrypoint.sh curl"
      env = {
        PATH           = "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
        CURL_CA_BUNDLE = "/cacert.pem"
      }
    }
    # The image's docker-entrypoint.sh runs a bare haproxy command with -W -db
    haproxy = {
      entrypoint = "/usr/local/bin/docker-entrypoint.sh haproxy -f /usr/local/etc/haproxy/haproxy.cfg"
      env        = { PATH = "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin" }
    }
    # The buildpack images: the three servers and kinotic-migration
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

  # OpenFGA's Postgres, with trust authentication: the private network is the isolation, the
  # decision Elasticsearch runs under, so there is no password to place
  openfga_datastore_env = {
    OPENFGA_DATASTORE_ENGINE = "postgres"
    OPENFGA_DATASTORE_URI    = "postgres://openfga@${local.openfga_db_private_ip}:5432/openfga?sslmode=disable"
  }
  # Postgres listens on TCP only once its initialization is done, so a connection is readiness
  openfga_db_listening = "timeout 2 bash -c 'exec 3<>/dev/tcp/${local.openfga_db_private_ip}/5432'"
  openfga_url          = "http://${local.openfga_private_ip}:8080"
  openfga_healthy      = "curl -sf ${local.openfga_url}/healthz >/dev/null"
  # Creates the platform store, the one store named kinotic-platform that every server looks up by
  # name and never creates, unless the engine has it. Store names are not unique, so the listing is
  # checked first, and a listing that fails stops the script rather than creating a second store
  openfga_init_script = <<-EOT
    #!/bin/sh
    set -e
    stores=$(curl -sf '${local.openfga_url}/stores?name=kinotic-platform')
    if echo "$stores" | grep -Eq '"name":[[:space:]]*"kinotic-platform"'; then
      echo 'The platform store kinotic-platform exists'
    else
      curl -sf -X POST '${local.openfga_url}/stores' -H 'Content-Type: application/json' -d '{"name":"kinotic-platform"}'
      echo; echo 'Created the platform store kinotic-platform'
    fi
  EOT

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
    vm_id            = node.vm_id
    description      = "Elasticsearch ${name}: master-eligible, data on ${node.data_dir}"
    image            = proxmox_oci_image.elasticsearch.id
    cores            = var.es_cores
    memory           = var.es_memory_mb
    order            = 10
    start_on_boot    = true
    run_once         = false
    interfaces       = [{ bridge = var.private_network, address = "${node.ip}/${local.private_prefix}", gateway = local.private_gateway }]
    mounts           = [{ volume = node.data_dir, path = "/usr/share/elasticsearch/data", read_only = false }]
    entrypoint       = local.images.elasticsearch.entrypoint
    image_env        = local.images.elasticsearch.env
    env              = merge(local.es_env, { "node.name" = name })
    privileged_ports = false
    secrets_env      = null
    files            = {}
    uid              = 1000
    gid              = 0
    wait_for         = null
    verify           = null
    timeout          = 300
  } }

  # The three servers. Each serves its gateway on its own port on the private network, behind
  # the edge, under the hostnames its certificate carries, and adds what is its alone to the
  # environment every server shares.
  servers = {
    kinotic-server-management = {
      vm_id       = 121
      private_ip  = cidrhost(var.private_cidr, 20)
      port        = 58503
      hostnames   = [local.azure.api_hostname]
      description = "the organizations' API: the portal, the CLI, MCP hosts, the GitHub webhook"
      env = {
        KINOTIC_MANAGEMENTSERVER_APIBASEURL     = "https://${local.azure.api_hostname}"
        KINOTIC_MANAGEMENTSERVER_PORTALBASEURL  = "https://${local.azure.portal_hostname}"
        # the emailed verification and invite links open in the portal
        KINOTIC_DOMAIN_EMAIL_LINKBASEURL = "https://${local.azure.portal_hostname}"
        KINOTIC_MANAGEMENTAPI_LOKIURL    = local.service_urls["http://loki:3100"]
        KINOTIC_MANAGEMENTAPI_TEMPOURL   = local.service_urls["http://tempo:3200"]
        KINOTIC_MANAGEMENTAPI_MIMIRURL   = local.service_urls["http://mimir:9009"]
      }
    }
    kinotic-server-system = {
      vm_id       = 122
      private_ip  = cidrhost(var.private_cidr, 22)
      port        = 58504
      hostnames   = [local.azure.system_api_hostname]
      description = "the platform's own API: the system console, the nodes' vm-manager"
      env = {
        KINOTIC_SYSTEMSERVER_APIBASEURL     = "https://${local.azure.system_api_hostname}"
        KINOTIC_SYSTEMSERVER_CONSOLEBASEURL = "https://${local.azure.console_hostname}"
        # What a UI build is handed: the app server as a browser reaches it
        KINOTIC_SYSTEMAPI_DEPLOYMENT_APPAPIBASEURL = "https://${local.azure.apps_api_domain}"
        # Invites the member service this server hosts sends are accepted in the portal
        KINOTIC_DOMAIN_EMAIL_LINKBASEURL = "https://${local.azure.portal_hostname}"
        # What a workload dials, by the name its certificate carries, and the one destination its
        # egress policy permits; the node pins every server's name to the edge's LAN address
        # (hosts_entry)
        KINOTIC_SYSTEMAPI_DEPLOYMENT_MANAGEMENTSERVER_HOST = local.azure.api_hostname
        KINOTIC_SYSTEMAPI_DEPLOYMENT_MANAGEMENTSERVER_PORT = tostring(local.public_port)
        KINOTIC_SYSTEMAPI_DEPLOYMENT_APPSERVER_HOST = local.azure.apps_api_domain
        KINOTIC_SYSTEMAPI_DEPLOYMENT_APPSERVER_PORT = tostring(local.public_port)
        KINOTIC_MANAGEMENTAPI_LOKIURL               = local.service_urls["http://loki:3100"]
        KINOTIC_MANAGEMENTAPI_TEMPOURL              = local.service_urls["http://tempo:3200"]
        KINOTIC_MANAGEMENTAPI_MIMIRURL              = local.service_urls["http://mimir:9009"]
      }
    }
    kinotic-server-app = {
      vm_id       = 123
      private_ip  = cidrhost(var.private_cidr, 23)
      port        = 58505
      hostnames   = [local.azure.apps_api_domain, "*.${local.azure.apps_api_domain}"]
      description = "every application's API, at <organizationId>--<applicationId>.${local.azure.apps_api_domain}"
      env = {
        KINOTIC_APPSERVER_APIBASEURL = "https://${local.azure.apps_api_domain}"
      }
    }
  }

  # The non-secret half of what every server's environment shares: the compose services', the
  # Azure root's outputs, and the addresses only this root knows. The secret half is merged on
  # the host.
  server_env = merge(local.azure.dev_server_env, merge([for i, ip in local.es_ips : {
    "KINOTIC_DOMAIN_ELASTICCONNECTIONS_${i}_SCHEME" = "http"
    "KINOTIC_DOMAIN_ELASTICCONNECTIONS_${i}_HOST"   = ip
    "KINOTIC_DOMAIN_ELASTICCONNECTIONS_${i}_PORT"   = "9200"
    }]...), {
    SPRING_PROFILES_ACTIVE       = "production,dev-server"
    BPL_JVM_HEAD_ROOM            = "10"
    JAVA_TOOL_OPTIONS            = "-XX:MaxDirectMemorySize=512m -javaagent:/workspace/BOOT-INF/classes/opentelemetry-javaagent.jar --add-opens=java.base/java.nio=ALL-UNNAMED --add-opens=java.base/java.util=ALL-UNNAMED --add-opens=java.base/java.lang=ALL-UNNAMED --add-opens=java.base/java.lang.invoke=ALL-UNNAMED"
    KINOTIC_MAX_OFF_HEAP_MEMORY  = "419430400"
    KINOTIC_DOMAIN_EMAIL_ENABLED = "true"

    # The authorization engine, whose platform store openfga-init created
    KINOTIC_AUTHZ_APIURL = local.openfga_url

    # The servers find each other over their private addresses and form one Ignite cluster
    KINOTIC_IGNITE_DISCOVERYTYPE  = "LOCAL"
    KINOTIC_IGNITE_LOCALADDRESSES = join(",", [for server in local.servers : "${server.private_ip}:47500"])

    # No collector: the agent exports each signal to its store's OTLP endpoint, under the
    # platform tenant, which is what the compose collector stamps on the servers' telemetry
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
    # Names the bare transport spans under each Elasticsearch call and each Loki, Tempo and Mimir query
    OTEL_INSTRUMENTATION_COMMON_PEER_SERVICE_MAPPING = join(",", concat(
      [for ip in local.es_ips : "${ip}:9200=elasticsearch"],
      ["${local.loki_ip}:3100=loki", "${local.tempo_ip}:3200=tempo", "${local.mimir_ip}:9009=mimir"]
    ))
  })

  server_containers = { for name, server in local.servers : name => {
    vm_id         = server.vm_id
    description   = "${name}: ${server.description}; the gateway on :${server.port} with TLS, behind the edge"
    image         = proxmox_oci_image.server[name].id
    cores         = var.server_cores
    memory        = var.server_memory_mb
    order         = 30
    start_on_boot = true
    run_once      = false
    # The private network alone, for Elasticsearch, the other servers and the edge; the host
    # routes and source-NATs the rest, Azure and the stores on the LAN among it
    interfaces = [{ bridge = var.private_network, address = "${server.private_ip}/${local.private_prefix}", gateway = local.private_gateway }]
    # secrets.yml, the JWT key set and the certificate, placed by sync-secrets.sh and certbot
    mounts     = [{ volume = "${var.secrets_dir}/${name}", path = "/etc/kinotic", read_only = true }]
    entrypoint = local.images.cnb.entrypoint
    image_env  = local.images.cnb.env
    env = merge(local.server_env, server.env, {
      KINOTIC_IGNITE_LOCALADDRESS = server.private_ip
      OTEL_SERVICE_NAME           = name
      OTEL_RESOURCE_ATTRIBUTES    = "service.name=${name},service.instance.id=${name}-${server.vm_id}"
    })
    secrets_env      = "${var.secrets_dir}/kinotic-servers.env"
    files            = {}
    uid              = 1002
    gid              = 1001
    privileged_ports = false
    wait_for         = local.es_healthy
    verify           = null
    timeout          = 300
  } }

  edge_config = templatefile("${path.module}/haproxy.cfg.tftpl", {
    port    = local.public_port
    servers = local.servers
  })

  containers = merge(local.es_containers, local.server_containers, {
    openfga-db = {
      vm_id            = 114
      description      = "Postgres for OpenFGA: the relationships of every authorization store"
      image            = proxmox_oci_image.postgres.id
      cores            = 1
      memory           = 512
      order            = 12
      start_on_boot    = true
      run_once         = false
      interfaces       = [{ bridge = var.private_network, address = "${local.openfga_db_private_ip}/${local.private_prefix}", gateway = local.private_gateway }]
      mounts           = [{ volume = "${local.data_root}/openfga-db", path = "/var/lib/postgresql", read_only = false }]
      entrypoint       = local.images.postgres.entrypoint
      image_env        = local.images.postgres.env
      env              = { POSTGRES_DB = "openfga", POSTGRES_USER = "openfga", POSTGRES_HOST_AUTH_METHOD = "trust" }
      privileged_ports = false
      secrets_env      = null
      files            = {}
      uid              = 70
      gid              = 70
      wait_for         = null
      verify           = null
      timeout          = 300
    }
    openfga-migrate = {
      vm_id            = 115
      description      = "The one-shot schema migration of OpenFGA's Postgres, once per image and configuration"
      image            = proxmox_oci_image.openfga.id
      cores            = 1
      memory           = 256
      order            = 14
      start_on_boot    = false
      run_once         = true
      interfaces       = [{ bridge = var.private_network, address = "${local.openfga_migrate_private_ip}/${local.private_prefix}", gateway = local.private_gateway }]
      mounts           = []
      entrypoint       = "/openfga migrate"
      image_env        = local.images.openfga.env
      env              = local.openfga_datastore_env
      privileged_ports = false
      secrets_env      = null
      files            = {}
      uid              = 65532
      gid              = 65532
      wait_for         = local.openfga_db_listening
      verify           = null
      timeout          = 300
    }
    openfga = {
      vm_id         = 116
      description   = "OpenFGA on :8080, the authorization engine every server checks against"
      image         = proxmox_oci_image.openfga.id
      cores         = 2
      memory        = 1024
      order         = 16
      start_on_boot = true
      run_once      = false
      interfaces    = [{ bridge = var.private_network, address = "${local.openfga_private_ip}/${local.private_prefix}", gateway = local.private_gateway }]
      mounts        = []
      entrypoint    = local.images.openfga.entrypoint
      image_env     = local.images.openfga.env
      # The caches the compose stack runs with: a check answers from cached subproblems,
      # invalidated by the cache controller on every write
      env = merge(local.openfga_datastore_env, {
        OPENFGA_PLAYGROUND_ENABLED           = "false"
        OPENFGA_CHECK_QUERY_CACHE_ENABLED    = "true"
        OPENFGA_CHECK_ITERATOR_CACHE_ENABLED = "true"
        OPENFGA_SHARED_ITERATOR_ENABLED      = "true"
        OPENFGA_CACHE_CONTROLLER_ENABLED     = "true"
      })
      privileged_ports = false
      secrets_env      = null
      files            = {}
      uid              = 65532
      gid              = 65532
      wait_for         = null
      verify           = null
      timeout          = 300
    }
    openfga-init = {
      vm_id            = 117
      description      = "The one-shot creation of the platform store, kinotic-platform, unless the engine has it"
      image            = proxmox_oci_image.curl.id
      cores            = 1
      memory           = 128
      order            = 18
      start_on_boot    = false
      run_once         = true
      interfaces       = [{ bridge = var.private_network, address = "${local.openfga_init_private_ip}/${local.private_prefix}", gateway = local.private_gateway }]
      mounts           = [{ volume = "${local.config_root}/openfga-init", path = "/etc/openfga-init", read_only = true }]
      entrypoint       = "/bin/sh /etc/openfga-init/init.sh"
      image_env        = local.images.curl.env
      env              = {}
      privileged_ports = false
      secrets_env      = null
      files            = { "init.sh" = local.openfga_init_script }
      uid              = 100
      gid              = 101
      wait_for         = local.openfga_healthy
      verify           = "curl -sf '${local.openfga_url}/stores?name=kinotic-platform' | grep -Eq '\"name\":[[:space:]]*\"kinotic-platform\"'"
      timeout          = 300
    }
    loki = {
      vm_id            = 110
      description      = "Loki: logs, multi-tenant; the node's Alloy and the servers push here"
      image            = proxmox_oci_image.loki.id
      cores            = 2
      memory           = 1024
      order            = 20
      start_on_boot    = true
      run_once         = false
      interfaces       = [{ bridge = var.bridge, address = var.loki_ip, gateway = var.gateway }]
      mounts           = [{ volume = "${local.data_root}/loki", path = "/loki", read_only = false }]
      entrypoint       = local.images.loki.entrypoint
      image_env        = local.images.loki.env
      env              = {}
      privileged_ports = false
      secrets_env      = null
      files            = {}
      uid              = 10001
      gid              = 10001
      wait_for         = null
      verify           = null
      timeout          = 300
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
      entrypoint       = local.images.tempo.entrypoint
      image_env        = local.images.tempo.env
      env              = {}
      privileged_ports = false
      secrets_env      = null
      files            = { "tempo.yml" = local.tempo_config }
      uid              = 10001
      gid              = 10001
      wait_for         = null
      verify           = null
      timeout          = 300
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
      entrypoint       = local.images.mimir.entrypoint
      image_env        = local.images.mimir.env
      env              = {}
      privileged_ports = false
      secrets_env      = null
      files            = { "mimir.yml" = file("${local.compose_dir}/mimir.yml") }
      uid              = 10001
      gid              = 10001
      wait_for         = null
      verify           = null
      timeout          = 300
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
      privileged_ports = false
      secrets_env      = "${var.secrets_dir}/grafana.env"
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
      privileged_ports = false
      secrets_env      = null
      files            = {}
      uid              = 1002
      gid              = 1001
      wait_for         = local.es_healthy
      verify           = "curl -sf http://${local.es_ips[0]}:9200/migration_history >/dev/null"
      timeout          = 900
    }
    edge = {
      vm_id         = 124
      description   = "The edge: HAProxy on :${local.public_port}, which the router forwards to; passes each TLS connection, unopened, to the server its SNI names"
      image         = proxmox_oci_image.haproxy.id
      cores         = 1
      memory        = 256
      order         = 35
      start_on_boot = true
      run_once      = false
      # The LAN for peers, GitHub and the nodes, with the address the router reserves for the
      # fixed MAC; the private network for the servers
      interfaces = [
        { bridge = var.bridge, address = "dhcp", gateway = null, mac = var.edge_mac },
        { bridge = var.private_network, address = "${local.edge_private_ip}/${local.private_prefix}", gateway = null, mac = null },
      ]
      mounts           = [{ volume = "${local.config_root}/edge", path = "/usr/local/etc/haproxy", read_only = true }]
      entrypoint       = local.images.haproxy.entrypoint
      image_env        = local.images.haproxy.env
      env              = {}
      privileged_ports = local.public_port < 1024
      secrets_env      = null
      files            = { "haproxy.cfg" = local.edge_config }
      uid              = 99
      gid              = 99
      wait_for         = null
      verify           = null
      timeout          = 300
    }
  })

  # The startup order is the apply order too: Elasticsearch, the authorization engine with its
  # Postgres, schema migration and store creation, the stores, the migration, the servers, and
  # the edge, which opens them to the internet once they are up
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
    # The hook script's path on the host, for the containers that bind a port below 1024
    privileged_ports = c.privileged_ports ? "${var.snippets_dir}/kinotic-unprivileged-ports.sh" : null
    run_once         = c.run_once
    wait_for         = c.wait_for
    verify           = c.verify
    timeout          = c.timeout
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
# The images compose pulls, and the edge's HAProxy, as container templates. A tag is pulled
# once: to pick up a republished SNAPSHOT, replace the image and the containers built from it
# (README.md).

resource "proxmox_oci_image" "server" {
  for_each = local.servers

  node_name    = var.proxmox_node
  datastore_id = var.files_datastore_id
  reference    = "docker.io/kinoticai/${each.key}:${var.kinotic_version}"
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

resource "proxmox_oci_image" "postgres" {
  node_name    = var.proxmox_node
  datastore_id = var.files_datastore_id
  reference    = "docker.io/library/postgres:${var.postgres_version}"
}

resource "proxmox_oci_image" "openfga" {
  node_name    = var.proxmox_node
  datastore_id = var.files_datastore_id
  reference    = "docker.io/openfga/openfga:${var.openfga_version}"
}

resource "proxmox_oci_image" "curl" {
  node_name    = var.proxmox_node
  datastore_id = var.files_datastore_id
  reference    = "docker.io/curlimages/curl:${var.curl_version}"
}

resource "proxmox_oci_image" "haproxy" {
  node_name    = var.proxmox_node
  datastore_id = var.files_datastore_id
  reference    = "docker.io/library/haproxy:${var.haproxy_version}"
}

# ── Files on the host ─────────────────────────────────────────────────────────

resource "proxmox_virtual_environment_file" "unprivileged_ports_hook" {
  content_type = "snippets"
  datastore_id = var.files_datastore_id
  node_name    = var.proxmox_node

  source_raw {
    file_name = "kinotic-unprivileged-ports.sh"
    data      = file("${path.module}/host/kinotic-unprivileged-ports.sh")
  }
}

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
      mac_address  = lookup(network_interface.value, "mac", null)
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
    # A replaced container reads back Proxmox's console defaults as drift, and the in-place
    # update that clears them restarts the container on the next apply
    ignore_changes = [started, console]
  }

  depends_on = [proxmox_sdn_applier.private, terraform_data.prepare]
}

# The management server takes the vmid the single kinotic-server had, so the container is replaced in
# place rather than created beside one that still holds its vmid
moved {
  from = proxmox_virtual_environment_container.fleet["kinotic-server"]
  to   = proxmox_virtual_environment_container.fleet["kinotic-org-server"]
}

# The servers' modules were renamed; each container follows its server's new name
moved {
  from = proxmox_virtual_environment_container.fleet["kinotic-org-server"]
  to   = proxmox_virtual_environment_container.fleet["kinotic-server-management"]
}

moved {
  from = proxmox_virtual_environment_container.fleet["kinotic-system-server"]
  to   = proxmox_virtual_environment_container.fleet["kinotic-server-system"]
}

moved {
  from = proxmox_virtual_environment_container.fleet["kinotic-app-server"]
  to   = proxmox_virtual_environment_container.fleet["kinotic-server-app"]
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
    proxmox_virtual_environment_file.unprivileged_ports_hook,
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
