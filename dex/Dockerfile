# Tenant-built Dex image: upstream Dex + Support Bot login theme.
# Published as support-bot-dex:<version> alongside support-bot and support-bot-ui from the main
# support-bot-fast-feedback / support-bot-prod workflows (build context is the dex/ directory).
# Bump DEX_BASE_TAG together with values-dexidp.yaml when upgrading Dex.
ARG DEX_BASE_TAG=v2.46.1
# Upstream Dex bundles an older gomplate (used by its entrypoint for templated configs); replace it with a
# patched release. Drop this stage once the upstream image ships the same or a newer gomplate.
ARG GOMPLATE_TAG=v5.3.1
FROM ghcr.io/hairyhenderson/gomplate:${GOMPLATE_TAG} AS gomplate

FROM ghcr.io/dexidp/dex:${DEX_BASE_TAG}

USER root
RUN apk add --no-cache \
    alpine-release=3.24.3-r0 \
    ca-certificates-bundle=20261020-r0 \
    libcrypto3=3.5.9-r0 \
    libssl3=3.5.9-r0 \
    musl=1.2.6-r3 \
    musl-utils=1.2.6-r3 \
    zlib=1.3.2-r1

COPY --from=gomplate /gomplate /usr/local/bin/gomplate

# Theme assets (whole directory) — referenced by frontend.theme=supportbot in the Dex config.
COPY --chown=1001:1001 web/themes/supportbot/ /srv/dex/web/themes/supportbot/

# Override only the templates we restyle. Dex's untouched templates (approval, oob, device, etc.)
# continue to come from the upstream image's /srv/dex/web/templates/.
COPY --chown=1001:1001 web/templates/header.html   /srv/dex/web/templates/header.html
COPY --chown=1001:1001 web/templates/footer.html   /srv/dex/web/templates/footer.html
COPY --chown=1001:1001 web/templates/login.html    /srv/dex/web/templates/login.html
COPY --chown=1001:1001 web/templates/password.html /srv/dex/web/templates/password.html
COPY --chown=1001:1001 web/templates/error.html    /srv/dex/web/templates/error.html

# Re-declare the non-root user from upstream (ghcr.io/dexidp/dex sets USER 1001:1001).
# Inherited from FROM at runtime; explicit here so static analyzers (e.g. Trivy DS-0002) see it.
USER 1001:1001
