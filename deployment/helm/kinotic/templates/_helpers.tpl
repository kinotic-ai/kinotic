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
The files a server's platform-secrets volume holds, as a YAML list of the Key Vault secret or
Secret key each comes from and the file name it is mounted as: the server's own JWT key set, and
the properties Spring imports from the directory as a config tree, each file named after the
property it sets. The GitHub App's secrets go only to a server with githubApp set.
Takes (dict "name" <server key> "server" <server values> "root" $).
*/}}
{{- define "kinotic.platformSecretFiles" -}}
{{- $objects := .root.Values.platformSecrets.objects -}}
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
JAVA_TOOL_OPTIONS: "{{ $root.Values.properties.javaToolOptions }} {{ $root.Values.properties.javaModuleAccess }}"
BPL_JVM_HEAD_ROOM: "{{ $root.Values.properties.bplJvmHeadRoom }}"
BPL_JAVA_NMT_ENABLED: "{{ $root.Values.properties.enableNmt }}"
BPL_JMX_ENABLED: "{{ $root.Values.properties.enableJmx }}"

# ── The gateway ───────────────────────────────────────────
KINOTIC_APIGATEWAY_STOMPPORT: "{{ $server.gatewayPort }}"
KINOTIC_APIGATEWAY_WEBSERVER_ENABLED: "{{ $server.webServer.enabled }}"
{{- if $server.webServer.enabled }}
KINOTIC_APIGATEWAY_WEBSERVER_PORT: "{{ $server.webServer.port }}"
{{- end }}

# ── Public URLs + email ───────────────────────────────────
{{- with $server.managementServer }}
KINOTIC_MANAGEMENTSERVER_APIBASEURL: "{{ .apiBaseUrl }}"
KINOTIC_MANAGEMENTSERVER_PORTALBASEURL: "{{ .portalBaseUrl }}"
KINOTIC_DOMAIN_EMAIL_LINKBASEURL: "{{ .portalBaseUrl }}"
{{- end }}
{{- with $server.systemServer }}
KINOTIC_SYSTEMSERVER_APIBASEURL: "{{ .apiBaseUrl }}"
KINOTIC_SYSTEMSERVER_CONSOLEBASEURL: "{{ .consoleBaseUrl }}"
{{- end }}
{{- with $server.deployment }}
KINOTIC_SYSTEMAPI_DEPLOYMENT_APPAPIBASEURL: "{{ .appApiBaseUrl }}"
{{- end }}
{{- if $server.systemServer }}
{{- with (index $root.Values.servers "kinotic-server-management").managementServer }}
# invites the member service this server hosts sends are accepted in the portal
KINOTIC_DOMAIN_EMAIL_LINKBASEURL: "{{ .portalBaseUrl }}"
{{- end }}
{{- end }}
{{- with $server.appServer }}
KINOTIC_APPSERVER_APIBASEURL: "{{ .apiBaseUrl }}"
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

# ── Loki, Tempo, Mimir (LogService, TelemetryService) ─────
KINOTIC_MANAGEMENTAPI_LOKIURL: "{{ $root.Values.kinotic.managementApi.lokiUrl }}"
KINOTIC_MANAGEMENTAPI_TEMPOURL: "{{ $root.Values.kinotic.managementApi.tempoUrl }}"
KINOTIC_MANAGEMENTAPI_MIMIRURL: "{{ $root.Values.kinotic.managementApi.mimirUrl }}"
{{- if $root.Values.tls.enabled }}

# ── SSL/TLS ──────────────────────────────────────────────
KINOTIC_APIGATEWAY_SSL_ENABLED: "true"
KINOTIC_APIGATEWAY_SSL_CERTPATH: "/certs/tls.crt"
KINOTIC_APIGATEWAY_SSL_KEYPATH: "/certs/tls.key"
{{- end }}
{{- if $root.Values.evictionTracking.enabled }}

# ── Eviction tracking ─────────────────────────────────────
KINOTIC_CACHE_EVICTION_CSV_PATH: "{{ $root.Values.evictionTracking.mountPath | default "/eviction-data" }}/evictions-${POD_NAME}.csv"
{{- end }}

# ── Extra environment variables ───────────────────────────
# Override any Spring property via env var naming convention:
#   kinotic.apiGateway.webServer.port → KINOTIC_APIGATEWAY_WEBSERVER_PORT
{{- range $key, $value := $root.Values.extraEnv }}
{{ $key }}: {{ $value | quote }}
{{- end }}
{{- range $key, $value := $server.extraEnv }}
{{ $key }}: {{ $value | quote }}
{{- end }}
{{- end -}}
