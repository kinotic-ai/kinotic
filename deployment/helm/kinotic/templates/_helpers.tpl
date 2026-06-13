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

