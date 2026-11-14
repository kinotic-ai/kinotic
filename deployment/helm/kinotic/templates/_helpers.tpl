{{/*
Expand the name of the chart.
*/}}
{{- define "kinotic.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{/*
Create a default fully qualified app name: what the servers share (the service account and its
role) and the migration job are named after.
*/}}
{{- define "kinotic.fullname" -}}
{{- $name := default .Chart.Name .Values.nameOverride -}}
{{- if contains $name .Release.Name -}}
{{- .Release.Name | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" -}}
{{- end -}}
{{- end -}}

{{/*
Create chart name and version as used by the chart label.
*/}}
{{- define "kinotic.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{/*
The image reference for a repository, pinned to its sha when one is given.
Takes (dict "image" <repository and sha> "root" $).
*/}}
{{- define "kinotic.image" -}}
{{- if .image.sha -}}
"{{ .image.repository }}@{{ .image.sha }}"
{{- else -}}
"{{ .image.repository }}:{{ .root.Values.image.tag }}"
{{- end -}}
{{- end -}}

{{/*
The files a server's system-secrets volume holds, as a YAML list of the Key Vault secret each
comes from and the file name it is mounted as: the server's own JWT key set, and
the properties Spring imports from the directory as a config tree, each file named after the
property it sets. The GitHub App's secrets go only to a server with githubApp set, the Turnstile
site's secret only to one with turnstile set.
Takes (dict "name" <server key> "server" <server values> "root" $).
*/}}
{{- define "kinotic.systemSecretFiles" -}}
{{- $objects := .root.Values.systemSecrets.objects -}}
- object: {{ .name }}-jwt-signing-keys
  path: jwt-signing-keys
- object: {{ $objects.secretStorageMasterKey }}
  path: kinotic.domain.secretStorage.masterKey
{{- if .server.githubApp }}
- object: {{ $objects.githubAppPrivateKey }}
  path: kinotic.managementApi.github.appPrivateKey
- object: {{ $objects.githubWebhookSecret }}
  path: kinotic.managementApi.github.webhookSecret
{{- end }}
{{- if .server.cloudflareDns }}
- object: {{ $objects.cloudflareDnsApiToken }}
  path: kinotic.domain.publishedSites.cloudflare.apiToken
{{- end }}
{{- if .server.turnstile }}
- object: {{ $objects.turnstileSecretKey }}
  path: kinotic.managementServer.turnstile.secretKey
{{- end }}
{{- end -}}

{{/*
A server's environment, the data of its ConfigMap: what every server shares and what is its
own. Its checksum restarts the server's pods when it changes.
Takes (dict "name" <server key> "server" <server values> "root" $).
*/}}
{{- define "kinotic.serverEnv" -}}
{{- $root := .root -}}
{{- $server := .server -}}
# ── JVM / Buildpack ───────────────────────────────────────
SPRING_PROFILES_ACTIVE: "{{ $root.Values.properties.springActiveProfiles }}"
# The OpenTelemetry Java agent every server image embeds
JAVA_TOOL_OPTIONS: "{{ $root.Values.properties.javaToolOptions }} -javaagent:/workspace/BOOT-INF/classes/opentelemetry-javaagent.jar"
BPL_JVM_HEAD_ROOM: "{{ $root.Values.properties.bplJvmHeadRoom }}"
BPL_JAVA_NMT_ENABLED: "{{ $root.Values.properties.enableNmt }}"
BPL_JMX_ENABLED: "{{ $root.Values.properties.enableJmx }}"

# ── The gateway ───────────────────────────────────────────
KINOTIC_APIGATEWAY_STOMPPORT: "{{ $server.gatewayPort }}"

# ── Public URLs + email ───────────────────────────────────
KINOTIC_DOMAIN_APPAPIBASEURL: "{{ $root.Values.kinotic.domain.appApiBaseUrl }}"
KINOTIC_DOMAIN_PORTALBASEURL: "{{ $root.Values.kinotic.domain.portalBaseUrl }}"
{{- with $server.managementServer }}
{{- with .turnstile }}
KINOTIC_MANAGEMENTSERVER_TURNSTILE_SITEKEY: "{{ .siteKey }}"
{{- end }}
{{- end }}
{{- with $server.systemServer }}
KINOTIC_SYSTEMSERVER_APIBASEURL: "{{ .apiBaseUrl }}"
KINOTIC_SYSTEMSERVER_CONSOLEBASEURL: "{{ .consoleBaseUrl }}"
{{- end }}
KINOTIC_DOMAIN_EMAIL_ENABLED: "{{ $root.Values.kinotic.domain.email.enabled }}"
{{- if $root.Values.kinotic.domain.email.enabled }}
KINOTIC_DOMAIN_EMAIL_ENDPOINT: "{{ required "kinotic.domain.email.endpoint is required when email is enabled" $root.Values.kinotic.domain.email.endpoint }}"
KINOTIC_DOMAIN_EMAIL_SENDERADDRESS: "{{ required "kinotic.domain.email.senderAddress is required when email is enabled" $root.Values.kinotic.domain.email.senderAddress }}"
{{- if $root.Values.kinotic.domain.email.managedIdentityClientId }}
KINOTIC_DOMAIN_EMAIL_MANAGEDIDENTITYCLIENTID: "{{ $root.Values.kinotic.domain.email.managedIdentityClientId }}"
{{- end }}
{{- end }}

# ── Elasticsearch connection ──────────────────────────────
{{- range $index, $value := $root.Values.kinotic.elastic.connections }}
KINOTIC_DOMAIN_ELASTICCONNECTIONS_{{ $index }}_SCHEME: "{{ $value.scheme }}"
KINOTIC_DOMAIN_ELASTICCONNECTIONS_{{ $index }}_HOST: "{{ $value.host }}"
KINOTIC_DOMAIN_ELASTICCONNECTIONS_{{ $index }}_PORT: "{{ $value.port }}"
{{- end }}

# ── Authorization engine ──────────────────────────────────
KINOTIC_AUTHZ_APIURL: "{{ $root.Values.kinotic.authz.apiUrl }}"

# ── Loki, Tempo, Mimir (LogService, TelemetryService) ─────
KINOTIC_MANAGEMENTAPI_APIBASEURL: "{{ $root.Values.kinotic.managementApi.apiBaseUrl }}"
KINOTIC_MANAGEMENTAPI_LOKIURL: "{{ $root.Values.kinotic.managementApi.lokiUrl }}"
KINOTIC_MANAGEMENTAPI_TEMPOURL: "{{ $root.Values.kinotic.managementApi.tempoUrl }}"
KINOTIC_MANAGEMENTAPI_MIMIRURL: "{{ $root.Values.kinotic.managementApi.mimirUrl }}"
# The agent's service name, and the service_name label Alloy gives this server's logs from the
# pods' app label
OTEL_SERVICE_NAME: "{{ .name }}"

# ── OpenTelemetry agent ───────────────────────────────────
# Traces and metrics go to Tempo and Mimir under the system tenant; logs reach Loki through Alloy
OTEL_TRACES_EXPORTER: "otlp"
OTEL_METRICS_EXPORTER: "otlp"
OTEL_LOGS_EXPORTER: "none"
OTEL_EXPORTER_OTLP_PROTOCOL: "http/protobuf"
OTEL_EXPORTER_OTLP_TRACES_ENDPOINT: "{{ $root.Values.otel.tracesEndpoint }}"
OTEL_EXPORTER_OTLP_METRICS_ENDPOINT: "{{ $root.Values.otel.metricsEndpoint }}"
OTEL_EXPORTER_OTLP_HEADERS: "X-Scope-OrgID=kinotic-system"
# jvm.buffer.* and jvm.system.cpu.*, which the agent gates behind this flag
OTEL_INSTRUMENTATION_RUNTIME_TELEMETRY_EMIT_EXPERIMENTAL_TELEMETRY: "true"
# Bridges Micrometer's global registry, where Vert.x registers its HTTP, WebSocket, event bus and pool meters
OTEL_INSTRUMENTATION_MICROMETER_ENABLED: "true"
# Names the bare transport spans under each Elasticsearch call and each Loki, Tempo and Mimir query
OTEL_INSTRUMENTATION_COMMON_PEER_SERVICE_MAPPING: "{{ range $index, $value := $root.Values.kinotic.elastic.connections }}{{ if $index }},{{ end }}{{ $value.host }}:{{ $value.port }}=elasticsearch{{ end }},{{ (urlParse $root.Values.kinotic.managementApi.lokiUrl).host }}=loki,{{ (urlParse $root.Values.kinotic.managementApi.tempoUrl).host }}=tempo,{{ (urlParse $root.Values.kinotic.managementApi.mimirUrl).host }}=mimir"
{{- if $root.Values.tls.enabled }}

# ── SSL/TLS ──────────────────────────────────────────────
KINOTIC_APIGATEWAY_SSL_ENABLED: "true"
KINOTIC_APIGATEWAY_SSL_CERTPATH: "/certs/tls.crt"
KINOTIC_APIGATEWAY_SSL_KEYPATH: "/certs/tls.key"
{{- end }}

# ── Extra environment variables ───────────────────────────
# Override any Spring property via env var naming convention:
#   kinotic.apiGateway.stompPort → KINOTIC_APIGATEWAY_STOMPPORT
{{- range $key, $value := $root.Values.extraEnv }}
{{ $key }}: {{ $value | quote }}
{{- end }}
{{- range $key, $value := $server.extraEnv }}
{{ $key }}: {{ $value | quote }}
{{- end }}
{{- end -}}
