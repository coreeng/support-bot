{{/*
Helpers for the bundled Dex / static-users wiring.
See values.yaml `dex:` and `bundled:` blocks for the user-facing surface.
*/}}

{{/*
Validate that each bundled.staticUsers.<role>.passwordHash is set when the block is enabled.
No defaults — same posture as the Dex client secret. Fails install if any are missing.
*/}}
{{- define "support-bot.bundled.validate" -}}
{{- if .Values.bundled.staticUsers.enabled -}}
{{- range $role := list "leadership" "support" "escalation" "tenant" -}}
{{- $u := index $.Values.bundled.staticUsers $role -}}
{{- if not $u.passwordHash -}}
{{- fail (printf "bundled.staticUsers.%s.passwordHash is required when bundled.staticUsers.enabled=true (generate with: htpasswd -bnBC 10 \"\" PASSWORD | tr -d ':\\n' | sed 's/^\\$2y/\\$2a/')" $role) -}}
{{- end -}}
{{- end -}}
{{- end -}}
{{- end -}}

{{/*
Reports how the Dex OAuth client secret is being supplied:
  - "inline"   plaintext in dex.config.staticClients[0].secret
  - "external" via a `DEX_CLIENT_SECRET` entry in dex.envVars (operator
               provides an existing K8s Secret; Dex resolves $DEX_CLIENT_SECRET
               at startup via its os.ExpandEnv feature; the API reuses the same
               valueFrom for its own DEX_CLIENT_SECRET env)
  - "both"    both set — ambiguous, fail-fast
  - "none"    neither set — fail-fast
*/}}
{{- define "support-bot.dex.clientSecretMode" -}}
{{- $client := index .Values.dex.config.staticClients 0 -}}
{{- $hasInline := $client.secret -}}
{{- $hasExternal := false -}}
{{- range (.Values.dex.envVars | default list) -}}
{{- if eq (.name | default "") "DEX_CLIENT_SECRET" -}}
{{- $hasExternal = true -}}
{{- end -}}
{{- end -}}
{{- if and $hasInline $hasExternal -}}both
{{- else if $hasInline -}}inline
{{- else if $hasExternal -}}external
{{- else -}}none
{{- end -}}
{{- end -}}

{{/*
Validate that the Dex OAuth client secret is supplied via one of the two
supported paths when Dex is bundled. Symmetrical with bundled.staticUsers
passwordHash — explicit only, no defaults.
*/}}
{{- define "support-bot.dex.validateClientSecret" -}}
{{- if .Values.dex.enabled -}}
{{- $mode := include "support-bot.dex.clientSecretMode" . -}}
{{- if eq $mode "none" -}}
{{- fail "dex client secret is required when dex.enabled=true. Set EITHER dex.config.staticClients[0].secret (inline, generate with: openssl rand -hex 32) OR add an entry to dex.envVars named DEX_CLIENT_SECRET with valueFrom.secretKeyRef pointing to your externally-managed Secret." -}}
{{- end -}}
{{- if eq $mode "both" -}}
{{- fail "ambiguous: both dex.config.staticClients[0].secret AND a DEX_CLIENT_SECRET entry in dex.envVars are set. Pick one." -}}
{{- end -}}
{{- end -}}
{{- end -}}

{{/*
Validate that Dex has redirectURIs to register when bundled. Either the user
supplied dex.config.staticClients[0].redirectURIs explicitly, or the chart
auto-derives them from publicWebOrigin. With neither, the rendered config has
redirectURIs: [] and Dex rejects every OAuth callback at runtime with
`Unregistered redirect_uri`.
*/}}
{{- define "support-bot.dex.validateRedirectURIs" -}}
{{- if .Values.dex.enabled -}}
{{- $client := index .Values.dex.config.staticClients 0 -}}
{{- if and (not $client.redirectURIs) (not .Values.publicWebOrigin) -}}
{{- fail "publicWebOrigin is required when dex.enabled=true (it drives Dex staticClients[0].redirectURIs, UI_ORIGIN on the API, and NEXTAUTH_URL on the UI). Set publicWebOrigin to your UI's public URL (e.g. https://support-bot.example.com), or supply dex.config.staticClients[0].redirectURIs explicitly." -}}
{{- end -}}
{{- end -}}
{{- end -}}

{{/*
Validate that ui.env declares AUTH_SECRET (or NEXTAUTH_SECRET) when the UI is
enabled alongside bundled Dex. Scoped to the bundled-auth posture so existing
standalone-UI installs that inject AUTH_SECRET out-of-band (mutating webhook,
post-install patch, GitOps overlay) keep working on upgrade. NextAuth still
requires the secret at startup either way; without it the UI crash-loops.
Either env-var name is accepted; value can be inline or via valueFrom (we
don't inspect).
*/}}
{{- define "support-bot.ui.validateAuthSecret" -}}
{{- if and .Values.ui.enabled .Values.dex.enabled -}}
{{- $found := false -}}
{{- range .Values.ui.env | default list -}}
{{- if or (eq .name "AUTH_SECRET") (eq .name "NEXTAUTH_SECRET") -}}
{{- $found = true -}}
{{- end -}}
{{- end -}}
{{- if not $found -}}
{{- fail "ui.env must include AUTH_SECRET (or NEXTAUTH_SECRET) when ui.enabled=true and dex.enabled=true (generate with: openssl rand -base64 32)" -}}
{{- end -}}
{{- end -}}
{{- end -}}

{{/*
The four static users formatted for Dex `staticPasswords:` (YAML list).
Returns empty string when bundled.staticUsers.enabled=false.
*/}}
{{- define "support-bot.bundled.staticPasswords" -}}
{{- if .Values.bundled.staticUsers.enabled -}}
{{- range $role := list "leadership" "support" "escalation" "tenant" }}
{{- $u := index $.Values.bundled.staticUsers $role }}
- email: {{ $u.email | quote }}
  hash: {{ $u.passwordHash | quote }}
  username: {{ $role | quote }}
  userID: {{ $u.userID | quote }}
{{- end -}}
{{- end -}}
{{- end -}}

{{/*
Default Dex redirectURIs derived from publicWebOrigin so the user doesn't repeat them.
Used when dex.config.staticClients[0].redirectURIs is empty.
*/}}
{{- define "support-bot.dex.defaultRedirectURIs" -}}
{{- $o := .Values.publicWebOrigin -}}
{{- if $o }}
- {{ printf "%s/login/oauth2/code/dex" $o | quote }}
- {{ printf "%s/api/oauth/callback/dex" $o | quote }}
{{- end -}}
{{- end -}}

{{/*
In-cluster Dex base URL (svc FQDN). Used for DEX_INTERNAL_BASE_URL on the API so
server-to-server /token /keys /userinfo calls stay inside the cluster.
*/}}
{{- define "support-bot.dex.internalBaseURL" -}}
http://{{ .Values.dex.fullnameOverride | default "support-bot-dex" }}.{{ .Release.Namespace }}.svc.cluster.local:5556
{{- end -}}

{{/*
Name of the K8s Secret holding the Dex OAuth client secret consumed by the API.
*/}}
{{- define "support-bot.dex.clientSecretName" -}}
{{ include "support-bot.fullname" . }}-dex-client
{{- end -}}

{{/*
Returns the user's configMap.config merged with bundled.staticUsers fan-out:
  - team.support.static.members      ← support user
  - team.leadership.static.members   ← leadership user
  - platform-integration.static-user.users.bundled-{escalation,tenant}
  - platform-integration.teams-scraping.static.teams (bundled-escalation, bundled-tenant)
Returns user's configMap.config unchanged when bundled.staticUsers.enabled=false.
Emit with toYaml.
*/}}
{{- define "support-bot.configmap.merged" -}}
{{- $cfg := deepCopy (.Values.configMap.config | default dict) -}}
{{- if .Values.bundled.staticUsers.enabled -}}
{{- $b := .Values.bundled.staticUsers -}}

{{- /* team block */ -}}
{{- $team := index $cfg "team" | default dict -}}
{{- $teamSupport := index $team "support" | default dict -}}
{{- $supportStatic := index $teamSupport "static" | default dict -}}
{{- $supportMembers := index $supportStatic "members" | default list -}}
{{- $supportMembers = append $supportMembers (dict "email" $b.support.email "slack-id" "bundled-support") -}}
{{- $_ := set $supportStatic "enabled" true -}}
{{- $_ := set $supportStatic "members" $supportMembers -}}
{{- $_ := set $teamSupport "static" $supportStatic -}}
{{- $_ := set $team "support" $teamSupport -}}

{{- $teamLeadership := index $team "leadership" | default dict -}}
{{- $leadershipStatic := index $teamLeadership "static" | default dict -}}
{{- $leadershipMembers := index $leadershipStatic "members" | default list -}}
{{- $leadershipMembers = append $leadershipMembers (dict "email" $b.leadership.email "slack-id" "bundled-leadership") -}}
{{- $_ := set $leadershipStatic "enabled" true -}}
{{- $_ := set $leadershipStatic "members" $leadershipMembers -}}
{{- $_ := set $teamLeadership "static" $leadershipStatic -}}
{{- $_ := set $team "leadership" $teamLeadership -}}
{{- $_ := set $cfg "team" $team -}}

{{- /* platform-integration block */ -}}
{{- $pi := index $cfg "platform-integration" | default dict -}}
{{- $staticUser := index $pi "static-user" | default dict -}}
{{- $users := index $staticUser "users" | default dict -}}
{{- $_ := set $users "bundled-escalation" (list $b.escalation.email) -}}
{{- $_ := set $users "bundled-tenant" (list $b.tenant.email) -}}
{{- $_ := set $staticUser "enabled" true -}}
{{- $_ := set $staticUser "users" $users -}}
{{- $_ := set $pi "static-user" $staticUser -}}

{{- $ts := index $pi "teams-scraping" | default dict -}}
{{- $tsStatic := index $ts "static" | default dict -}}
{{- $teams := index $tsStatic "teams" | default list -}}
{{- $teams = append $teams (dict "name" "bundled-escalation" "group-ref" "bundled-escalation") -}}
{{- $teams = append $teams (dict "name" "bundled-tenant"     "group-ref" "bundled-tenant") -}}
{{- $_ := set $tsStatic "enabled" true -}}
{{- $_ := set $tsStatic "teams" $teams -}}
{{- $_ := set $ts "static" $tsStatic -}}
{{- $_ := set $pi "teams-scraping" $ts -}}
{{- $_ := set $cfg "platform-integration" $pi -}}
{{- end -}}
{{- toYaml $cfg -}}
{{- end -}}
