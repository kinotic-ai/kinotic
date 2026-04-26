{{/*
Expand the name of the chart.
*/}}
{{- define "kinotic-server.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{/*
Alias for compatibility with ignite-service.yaml
*/}}
{{- define "kinotic.name" -}}
{{- include "kinotic-server.name" . -}}
{{- end -}}

{{/*
Create a default fully qualified app name.
*/}}
{{- define "kinotic-server.fullname" -}}
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
Name of the k8s Secret holding platform secrets (JWT signing keys, masterKeys).
Defaults to "<fullname>-platform-secrets"; can be overridden via platformSecrets.secretName
when an externally-managed Secret must be mounted instead.
*/}}
{{- define "kinotic-server.platformSecretsSecretName" -}}
{{- if .Values.platformSecrets.secretName -}}
{{- .Values.platformSecrets.secretName -}}
{{- else -}}
{{- printf "%s-platform-secrets" (include "kinotic-server.fullname" .) -}}
{{- end -}}
{{- end -}}

{{/*
Name of the k8s Secret holding OIDC client secrets (one data key per OidcConfiguration id).
Same override semantics as platformSecretsSecretName.
*/}}
{{- define "kinotic-server.oidcSecretsSecretName" -}}
{{- if .Values.oidcSecrets.secretName -}}
{{- .Values.oidcSecrets.secretName -}}
{{- else -}}
{{- printf "%s-oidc-client-secrets" (include "kinotic-server.fullname" .) -}}
{{- end -}}
{{- end -}}

{{/*
Returns "true" when an OIDC-secrets source is configured (Azure Key Vault or KinD Secret),
empty string otherwise. Used to gate the volume + env var rendering in the deployment.
*/}}
{{- define "kinotic-server.oidcSecretsEnabled" -}}
{{- if or .Values.oidcSecrets.keyVault.name .Values.oidcSecrets.secretName -}}true{{- end -}}
{{- end -}}

