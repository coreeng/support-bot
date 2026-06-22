# Tenant-built Dex image: patched upstream Dex + Support Bot login theme.
# Published as support-bot-dex:<version> alongside support-bot and support-bot-ui from the main
# support-bot-fast-feedback / support-bot-prod workflows (build context is the dex/ directory).
# Bump DEX_VERSION/DEX_REF together with values-dexidp.yaml when upgrading Dex.
ARG DEX_VERSION=v2.45.1
ARG DEX_REF=11d2eeb52b42e1980e14cb91e69dd9e3faab2076

FROM docker.io/golang:1.26.4-alpine3.23 AS build

ARG DEX_VERSION
ARG DEX_REF
WORKDIR /src/dex

RUN apk add --no-cache build-base git && \
    git init . && \
    git remote add origin https://github.com/dexidp/dex.git && \
    git fetch --depth 1 origin "${DEX_REF}" && \
    git checkout FETCH_HEAD && \
    go get \
        github.com/Azure/go-ntlmssp@v0.1.1 \
        github.com/go-jose/go-jose/v4@v4.1.4 \
        github.com/russellhaering/goxmldsig@v1.6.0 \
        go.opentelemetry.io/otel@v1.41.0 \
        go.opentelemetry.io/otel/metric@v1.41.0 \
        go.opentelemetry.io/otel/trace@v1.41.0 \
        google.golang.org/grpc@v1.79.3 && \
    go mod tidy && \
    go build -o /usr/local/bin/dex -ldflags="-w -X main.version=${DEX_VERSION}" github.com/dexidp/dex/cmd/dex

FROM docker.io/alpine:3.23

USER root
RUN apk upgrade --no-cache && \
    apk add --no-cache ca-certificates && \
    addgroup -S -g 1001 dex && \
    adduser -S -D -H -u 1001 -G dex dex && \
    mkdir -p /etc/dex /var/dex /srv/dex && \
    chown -R 1001:1001 /var/dex /srv/dex

COPY --from=build /usr/local/bin/dex /usr/local/bin/dex
COPY --from=build --chown=1001:1001 /src/dex/web /srv/dex/web

# Theme assets (whole directory) — referenced by frontend.theme=supportbot in the Dex config.
COPY --chown=1001:1001 web/themes/supportbot/ /srv/dex/web/themes/supportbot/

# Override only the templates we restyle. Dex's untouched templates (approval, oob, device, etc.)
# continue to come from the upstream image's /srv/dex/web/templates/.
COPY --chown=1001:1001 web/templates/header.html   /srv/dex/web/templates/header.html
COPY --chown=1001:1001 web/templates/footer.html   /srv/dex/web/templates/footer.html
COPY --chown=1001:1001 web/templates/login.html    /srv/dex/web/templates/login.html
COPY --chown=1001:1001 web/templates/password.html /srv/dex/web/templates/password.html
COPY --chown=1001:1001 web/templates/error.html    /srv/dex/web/templates/error.html

# Re-declare the non-root user explicitly so static analyzers (e.g. Trivy DS-0002) see it.
USER 1001:1001
CMD ["dex"]
