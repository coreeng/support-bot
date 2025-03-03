{{/*
Enforces appName and tenantName to be set
{{- $appName := required "appName is required. Please set .Values.appName." .Values.appName }}
{{- $tenantName := required "tenantName is required. Please set .Values.tenantName." .Values.tenantName }}
*/}}

{{/*
Expand the name of the chart.
*/}}
{{- define "app.name" -}}
{{- $appName := .Values.appName | required ".Values.appName is required." -}}
{{- $tenantName := .Values.tenantName | required ".Values.tenantName is required." -}}
{{- default .Chart.Name $appName | trunc 63 | trimSuffix "-" }}
{{- end }}

{{- define "app.image" -}}
{{- $registry := .Values.image.registry | required ".Values.image.registry is required." -}}
{{- $repository := .Values.image.repository | required ".Values.image.repository is required." -}}
{{- $tag := .Values.image.tag | default .Chart.AppVersion }}
{{- printf "%s/%s:%s" $registry $repository $tag }}
{{- end }}

{{/*
Create a default fully qualified app name.
We truncate at 63 chars because some Kubernetes name fields are limited to this (by the DNS naming spec).
If release name contains chart name it will be used as a full name.
*/}}
{{- define "app.fullname" -}}
{{- $appName := .Values.appName | required ".Values.appName is required." -}}
{{- $tenantName := .Values.tenantName | required ".Values.tenantName is required." -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- $name := default .Chart.Name .Values.appName }}
{{- if contains $name .Release.Name }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- if $tenantName }}
{{- printf "%s-%s" $tenantName $name | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}
{{- end }}
{{- end }}

{{/*
Create chart name and version as used by the chart label.
*/}}
{{- define "app.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Common labels
*/}}
{{- define "app.labels" -}}
helm.sh/chart: {{ include "app.chart" . }}
{{ include "app.selectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{/*
Selector labels
*/}}
{{- define "app.selectorLabels" -}}
{{- $appName := .Values.appName | required ".Values.appName is required." -}}
{{- $tenantName := .Values.tenantName | required ".Values.tenantName is required." -}}
app.kubernetes.io/name: {{ include "app.name" . }}
{{- if $tenantName }}
app.kubernetes.io/instance: {{ $tenantName }}
{{- else }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}
{{- end }}

{{/*
Create the name of the service account to use
*/}}
{{- define "app.serviceAccountName" -}}
{{- if .Values.serviceAccount.create }}
{{- default (include "app.fullname" .) .Values.serviceAccount.name }}
{{- else }}
{{- default "default" .Values.serviceAccount.name }}
{{- end }}
{{- end }}
