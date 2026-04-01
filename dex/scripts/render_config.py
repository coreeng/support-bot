from pathlib import Path
import json
import re


env_file = Path(".env.local")
template_file = Path("config/config.example.yaml")
output_file = Path("config/config.yaml")

env = {}
for line in env_file.read_text().splitlines():
    line = line.strip()
    if not line or line.startswith("#"):
        continue
    if "=" not in line:
        continue
    key, value = line.split("=", 1)
    env[key] = value


def _truthy(key: str, e: dict) -> bool:
    return e.get(key, "").lower() in ("1", "true", "yes")


def _falsy(key: str, e: dict) -> bool:
    return e.get(key, "").lower() in ("0", "false", "no")


def build_password_db(e: dict) -> str:
    """Dex local email/password login. Off when DEX_ENABLE_PASSWORD_DB=false (connectors-only login)."""
    if _falsy("DEX_ENABLE_PASSWORD_DB", e):
        return "enablePasswordDB: false\n"
    return """enablePasswordDB: true
staticPasswords:
  - email: ${DEX_LOCAL_USER_EMAIL}
    hash: "${DEX_LOCAL_USER_PASSWORD_HASH}"
    username: ${DEX_LOCAL_USER_USERNAME}
    userID: "${DEX_LOCAL_USER_ID}"
"""


def _dex_callback_issuer(e: dict) -> str:
    """Issuer URL without trailing slash; used for {issuer}/callback (matches Helm)."""
    issuer = e.get("DEX_ISSUER", "http://127.0.0.1:5556").strip()
    return issuer.rstrip("/")


def _ldap_connector_item(e: dict) -> str:
    required = [
        "DEX_LDAP_HOST",
        "DEX_LDAP_BIND_DN",
        "DEX_LDAP_BIND_PW",
        "DEX_LDAP_USER_SEARCH_BASE",
        "DEX_LDAP_GROUP_SEARCH_BASE",
    ]
    missing = [k for k in required if not e.get(k)]
    if missing:
        raise SystemExit(
            "DEX_LDAP_ENABLED is set but .env.local is missing: " + ", ".join(missing)
        )
    pw = json.dumps(e["DEX_LDAP_BIND_PW"])
    return f"""  - type: ldap
    id: ldap
    name: LDAP
    config:
      host: {json.dumps(e["DEX_LDAP_HOST"])}
      insecureNoSSL: true
      bindDN: {json.dumps(e["DEX_LDAP_BIND_DN"])}
      bindPW: {pw}
      usernamePrompt: Email Address
      userSearch:
        baseDN: {json.dumps(e["DEX_LDAP_USER_SEARCH_BASE"])}
        filter: "(objectClass=posixAccount)"
        username: mail
        idAttr: uid
        emailAttr: mail
        nameAttr: cn
      groupSearch:
        baseDN: {json.dumps(e["DEX_LDAP_GROUP_SEARCH_BASE"])}
        filter: "(objectClass=groupOfUniqueNames)"
        userMatchers:
          - userAttr: DN
            groupAttr: uniqueMember
        nameAttr: cn"""


def _google_connector_item(e: dict) -> str:
    required = ["DEX_GOOGLE_CLIENT_ID", "DEX_GOOGLE_CLIENT_SECRET"]
    missing = [k for k in required if not e.get(k)]
    if missing:
        raise SystemExit(
            "DEX_GOOGLE_ENABLED is set but .env.local is missing: " + ", ".join(missing)
        )
    base = _dex_callback_issuer(e)
    redirect = json.dumps(f"{base}/callback")
    return f"""  - type: google
    id: google
    name: Google
    config:
      clientID: {json.dumps(e["DEX_GOOGLE_CLIENT_ID"])}
      clientSecret: {json.dumps(e["DEX_GOOGLE_CLIENT_SECRET"])}
      redirectURI: {redirect}"""


def _microsoft_connector_item(e: dict) -> str:
    required = ["DEX_MICROSOFT_CLIENT_ID", "DEX_MICROSOFT_CLIENT_SECRET"]
    missing = [k for k in required if not e.get(k)]
    if missing:
        raise SystemExit(
            "DEX_MICROSOFT_ENABLED is set but .env.local is missing: " + ", ".join(missing)
        )
    base = _dex_callback_issuer(e)
    redirect = json.dumps(f"{base}/callback")
    tenant = (e.get("DEX_MICROSOFT_TENANT") or "common").strip()
    return f"""  - type: microsoft
    id: microsoft
    name: Microsoft
    config:
      clientID: {json.dumps(e["DEX_MICROSOFT_CLIENT_ID"])}
      clientSecret: {json.dumps(e["DEX_MICROSOFT_CLIENT_SECRET"])}
      redirectURI: {redirect}
      tenant: {json.dumps(tenant)}"""


def build_connectors(e: dict) -> str:
    items = []
    if _truthy("DEX_LDAP_ENABLED", e):
        items.append(_ldap_connector_item(e))
    if _truthy("DEX_GOOGLE_ENABLED", e):
        items.append(_google_connector_item(e))
    if _truthy("DEX_MICROSOFT_ENABLED", e):
        items.append(_microsoft_connector_item(e))
    if not items:
        return ""
    return "connectors:\n" + "\n".join(items) + "\n"


template = template_file.read_text()
template = template.replace("@PASSWORD_DB@", build_password_db(env))
template = template.replace("@CONNECTORS@", build_connectors(env))


def repl(match):
    key = match.group(1)
    return env.get(key, "")


rendered = re.sub(r"\$\{([A-Za-z_][A-Za-z0-9_]*)\}", repl, template)
output_file.write_text(rendered)
