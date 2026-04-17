{{- define "support-bot-openldap.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" -}}
{{- end }}

{{- define "support-bot-openldap.fullname" -}}
{{- if .Values.fullnameOverride -}}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- printf "%s-%s" .Release.Name (include "support-bot-openldap.name" .) | trunc 63 | trimSuffix "-" -}}
{{- end -}}
{{- end }}

{{- define "support-bot-openldap.labels" -}}
helm.sh/chart: {{ printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | quote }}
app.kubernetes.io/name: {{ include "support-bot-openldap.name" . | quote }}
app.kubernetes.io/instance: {{ .Release.Name | quote }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
app.kubernetes.io/managed-by: {{ .Release.Service | quote }}
{{- end }}

{{/*
Fail when tls.enabled is true but no cert Secret is configured (ambiguous / insecure).
Explicitly set tls.enabled false via values-integration-ldap-plaintext-ephemeral.yaml for
disposable plaintext 389, or supply tls.certSecret (see values-tls.yaml).
*/}}
{{- define "support-bot-openldap.validate" -}}
{{- if and .Values.tls.enabled (not .Values.tls.certSecret) }}
{{- fail "support-bot-openldap: tls.enabled is true but tls.certSecret is empty. For LDAPS, set tls.certSecret (see api/k8s/ldap/values-tls.yaml). For disposable plaintext on 389 only, apply api/k8s/ldap/values-integration-ldap-plaintext-ephemeral.yaml (tls.enabled: false) and use LDAP_DEPLOY_INSECURE_PLAINTEXT=true with ldap/scripts/helm_ldap.sh deploy-integration. See api/k8s/ldap/README.md." }}
{{- end }}
{{- end }}
