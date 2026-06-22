# Tenant-built Dex image: upstream Dex + Support Bot login theme.
# Published as support-bot-dex:<version> alongside support-bot and support-bot-ui from the main
# support-bot-fast-feedback / support-bot-prod workflows (build context is the dex/ directory).
# Bump DEX_BASE_TAG together with values-dexidp.yaml when upgrading Dex.
ARG DEX_BASE_TAG=v2.45.1
FROM ghcr.io/dexidp/dex:${DEX_BASE_TAG}

USER root
RUN apk add --no-cache \
    alpine-baselayout=3.7.2-r0 \
    alpine-baselayout-data=3.7.2-r0 \
    alpine-release=3.23.5-r0 \
    apk-tools=3.0.6-r0 \
    ca-certificates-bundle=20260611-r0 \
    libapk=3.0.6-r0 \
    libcrypto3=3.5.7-r0 \
    libssl3=3.5.7-r0 \
    musl=1.2.5-r23 \
    musl-utils=1.2.5-r23 \
    zlib=1.3.2-r0

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
