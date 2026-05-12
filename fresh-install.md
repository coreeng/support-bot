Create the slack app
- https://github.com/coreeng/support-bot/tree/main/api/service
  - use direct link for app with manifest: https://docs.slack.dev/app-manifests/configuring-apps-with-app-manifests
  - copy xxxx from Settings > Basic information
  - Go to Features > OAuth & permissions" and click "Install to <your-workspace>"
    - this will generate a Bot user OAuth token
    - save the token somewhere
  - Go to Settings > Basic Information
    - Generate a new App-Level Token
    - give it "connections:write" scope
    - save the token somewhere

Create the slack groups and keep a note of their group ID:
- support:
- escalation:

Take a note of the channel ID

Add the slack bot to the channel
- click on slack bot and add to channel



Create the secret
```
kubectl apply -f - <<EOF
apiVersion: v1
kind: Secret
metadata:
  name: support-bot
type: Opaque
stringData:
    SLACK_TOKEN: "xoxb-"
    SLACK_SOCKET_TOKEN: "xapp-"
    SLACK_SIGNING_SECRET: "..."
    JWT_SECRET: "a string that is at least 256 bits long for the jwt exchange"
    GOOGLE_CLIENT_ID: "..."
    GOOGLE_CLIENT_SECRET: "..."
    AZURE_AD_CLIENT_ID: "..."
    AZURE_AD_CLIENT_SECRET:"..."
    AZURE_AD_TENANT_ID: "..."
EOF
```

Deploy the app
```
helm install support-bot oci://ghcr.io/coreeng/charts/support-bot -f local-values.yaml
```

errors
- Caused by: org.springframework.beans.factory.NoSuchBeanDefinitionException: No qualifying bean of type 'com.coreeng.supportbot.teams.PlatformTeamsService' available: expected at least 1 bean which qualifies as autowire candidate. Dependency annotations: {}



To fix:
- Could do with an architecture diagram to make it clear what components are involved and their responsibility. Wasn't obvious we had 2 separated components for bot and UI
- https://github.com/coreeng/support-bot/tree/main/api/k8s/service
  - configMap section values.yaml is inaccurate
  - should probably default to deploy with helm, then have a local setup for those who want to contribute
  - should install dependencies in order
    - slack app, channel and user groups
    - db
    - secret
    - helm chart with custom values
