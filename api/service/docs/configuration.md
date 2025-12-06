# Overview

Support Bot service is a Spring Boot application, which means that it has certain
[conventions](https://docs.spring.io/spring-boot/reference/features/external-config.html#features.external-config.files)
regarding its configuration.

# General approach
Support Bot docker image will contain a jar file under `/application` folder.
Since it's a Spring Boot application, you can mount your `application.yaml` with configurations to `/application/application.yaml`
and the app should automatically find your configuration.

Support Bot already has the default configuration provided. 
You might want to adjust it for your own needs.
In the next section, you'll find default values and configuration options specific to Support Bot.

> Note: Spring Boot provides a lot of configuration options. Address Spring documentation for more information.

# Default Configuration
```yaml
management:
  server:
    port: 8081
  endpoints:
    web:
      base-path: /
      exposure:
        include: health, prometheus
  metrics:
    distribution:
      percentiles-histogram:
        http:
          server:
            requests: true

spring:
  main:
    banner-mode: off
  application:
    name: support-bot
  cloud:
# Uncomment in case you require Azure integration
#     azure:
#       profile:
#         tenant-id: ${AZURE_TENANT_ID}
#       credential:
#         client-id: ${AZURE_CLIENT_ID}
#         client-secret: ${AZURE_CLIENT_SECRET}
    gcp:
      core:
        enabled: false # enable only in case GCP integration is enabled
      credentials:
        scopes: [ "https://www.googleapis.com/auth/cloud-identity.groups.readonly" ]
  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5432/postgres}
    username: ${DB_USERNAME:postgres}
    password: ${DB_PASSWORD:postgres}
    hikari:
      data-source-properties:
        reWriteBatchedInserts: true

slack:
  creds: # Credentials of Slack App
    token: ${SLACK_TOKEN} # Token like: xoxb-abc-def
    socket-token: ${SLACK_SOCKET_TOKEN} # Token like: xapp-1-abc-def-ghi
    signing-secret: ${SLACK_SIGNING_SECRET} # Token like: 1234567890abcdef
  ticket:
    channel-id: ${SLACK_TICKET_CHANNEL_ID} # Channel ID (C1234567890) where tenants post queries
    expected-initial-reaction: eyes # Reaction to trigger ticket creation -- emoji name needs to already exist in slack
    response-initial-reaction: ticket # Reaction posted when ticket is created -- emoji name needs to already exist in slack
    resolved-reaction: white_check_mark # Reaction posted when ticket is resolved -- emoji name needs to already exist in slack
    escalation-reaction: warning # Reaction posted when ticket is escalated -- emoji name needs to already exist in slack

ticket:
  staleness-check-job: # Job that check for stale tickets – open tickets that didn't have any interactions over some period
    enabled: true
    find-stale-cron: 0 0 9 * * 1-5 # Schedule for identifying stale tickets
    time-to-stale: 3d
    remind-about-stale-cron: 0 10 9 * * 1-5 # Schedule for reminding about stale tickets in case no action is performed
    stale-reminder-interval: 1d

enums:
  escalation-teams: # Teams available for query escalation
    - label: wow # Label showed on the UI
      code: wow # Team ID. Must be unique. Have to match platform team name unless platform-integration.fetch.ignore-unknown-teams is set to true
      slack-group-id: S08948NBMED # Slack group ID that will be tagged on escalations
  tags: # Ticket tags
    - label: Ingresses # Label showed on the UI
      code: ingresses # Tag ID
    - label: Networking
      code: networking
    - label: Persistence/Brokers
      code: persistence-brokers
    - label: Observability
      code: observability
    - label: DNS
      code: dns
  impacts: # Ticket impacts
    - label: Production Blocking # Label showed on the UI
      code: productionBlocking # Impact ID
    - label: BAU Blocking
      code: bauBlocking
    - label: Abnormal Behaviour
      code: abnormalBehaviour

platform-integration: # Whether to enable platform integration to automatically scrape for teams and members
  enabled: true
  fetch:
    max-concurrency: 64 # Maximum number of concurrent requests when fetching team data
    timeout: 30s # Timeout for fetching all team data
    ignore-unknown-teams: false # Whether to allow escalation teams that don't exist in platform teams.
                                 # If false, startup will fail if any escalation team is not found in platform teams.
                                 # If true, escalation-only teams are allowed (they will have only 'l2Support' type).
  gcp:
    app-name: Support Bot # Used by GCP client
    enabled: true
  azure:
    enabled: false
  teams-scraping: # team-name <-> cloud group id scrapper configuration
    core-platform: # Scraper specific to CECG's Core Platform
      enabled: true
    k8s-generic: # A generic scraper that might be used in any K8S environment
      enabled: false
      config:
        api-version: v1
        api-group: ""
        kind: Namespace # Search for namespaces with the following filter
        namespace: null # Namespace filter, null for global resources
        filter:
          name-regexp: null # Regexp filter for namespace names
          label-selector: "root.tree.hnc.x-k8s.io/depth" # Label selector filter for namespace labels. Look [here](https://kubernetes.io/docs/concepts/overview/working-with-objects/labels/) for syntax
        teamName: # Will use the namespace name from .metadata.name as a team name
          jq-expression: .metadata.name
        groupRef: # Will use the namespace uid from .metadata.uid as a group reference. Supposed to be changed to a real configuration.
          jq-expression: .metadata.uid


team:
  support:
    name: Core Support # Label showed on the UI
    slack-group-id: S08948NBMED # Slack group ID of the support team
  leadership:
    name: Support Leadership # Label showed on the UI
    code: support-leadership # Slack group ID of the support leadership team
  
ai: # AI powered features
  sentiment-analysis: # Analyze tenant and support sentiment per ticket
    enabled: false
    
rbac: # Restrict ticket creation/editing for tenants
  enabled: true

mock-data: # Generate mock data in case DB is empty. Purely for testing/demo purposes
  enabled: false
```

# Integrations
## Slack
Slack integration is essential for the Support Bot.
You have to create a Slack App for the Support Bot with the following manifest:
```yaml
# Feel free to adjust display_information
display_information:
  name: Core Support Bot
  description: Core Support Bot
  background_color: "#0040ff"
features:
  app_home:
    home_tab_enabled: true
    messages_tab_enabled: false
    messages_tab_read_only_enabled: true
  bot_user:
    display_name: Core Support Bot
    always_online: false
oauth_config:
  scopes:
    bot:
      - app_mentions:read
      - channels:history
      - chat:write
      - groups:history
      - reactions:read
      - reactions:write
      # User scopes are used for mapping users to teams
      - usergroups:read
      - users.profile:read
settings:
  event_subscriptions:
    bot_events:
      - app_home_opened
      - app_mention
      - message.channels
      - message.groups
      - reaction_added
  interactivity:
    is_enabled: true
  org_deploy_enabled: false
  socket_mode_enabled: true
  token_rotation_enabled: false
```

## Kubernetes Cluster
Kubernetes cluster integration is used for scraping team to cloud group relations.
It requires ServiceAccount configuration depending on your scraping mode.
If you use Core Platform integration, these are the expected roles:
- Namespaces: read
- RoleBindings: read in all tenant namespaces

If you configure a generic kubernetes scraper, the required roles will depend on the configuration you provide.

## Google Cloud
You will need GCP integration in case you manage your organization members using Google Groups.
You will need
to create a GCP Service Account with [Groups Reader](https://support.google.com/a/answer/2405986?hl=en) role.

In GCP Service Accounts identified by emails, and they usually have a domain distinct from your organization domain.
In case you face problems assigning the [Groups Reader](https://support.google.com/a/answer/2405986?hl=en) role an email
outside your organization, you should try to create a Google Group under your domain, 
assign the role to the newly created group and make the Service Account a member of the group.

> Note: Support Bot to GCP is purely server-to-server communication, meaning you don't need to configure domain-wide delegation.

## Azure Cloud
You will need Azure Cloud integration in case you manage your organization members using Microsoft Entra ID.
You will need to register an application with the following parameters:
1. Supported account types – `Accounts in this organizational directory only`
2. API Permissions:
2.1 `GroupMember.Read.All` with `Application` type
2.2 `User.ReadBasic.All` with `Application` type

You will also need
to create a secret for the registered application so that it can be used for authentication by Support Bot.
