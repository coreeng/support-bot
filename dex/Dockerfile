# Tenant-built Dex image: upstream Dex + Support Bot login theme.
# Published as support-bot-dex:<version> alongside support-bot and support-bot-ui from the main
# support-bot-fast-feedback / support-bot-prod workflows (build context is the dex/ directory).
# Bump DEX_BASE_TAG together with values-dexidp.yaml when upgrading Dex.
ARG DEX_BASE_TAG=v2.44.0
FROM ghcr.io/dexidp/dex:${DEX_BASE_TAG}

# Theme assets (whole directory) — referenced by frontend.theme=supportbot in the Dex config.
COPY --chown=1001:1001 web/themes/supportbot/ /srv/dex/web/themes/supportbot/

# Override only the templates we restyle. Dex's untouched templates (approval, oob, device, etc.)
# continue to come from the upstream image's /srv/dex/web/templates/.
COPY --chown=1001:1001 web/templates/header.html   /srv/dex/web/templates/header.html
COPY --chown=1001:1001 web/templates/footer.html   /srv/dex/web/templates/footer.html
COPY --chown=1001:1001 web/templates/login.html    /srv/dex/web/templates/login.html
COPY --chown=1001:1001 web/templates/password.html /srv/dex/web/templates/password.html
COPY --chown=1001:1001 web/templates/error.html    /srv/dex/web/templates/error.html
