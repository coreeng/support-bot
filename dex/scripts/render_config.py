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


def build_ldap_connector(e: dict) -> str:
    if e.get("DEX_LDAP_ENABLED", "").lower() not in ("1", "true", "yes"):
        return ""
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
    return f"""connectors:
  - type: ldap
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
        nameAttr: cn
"""


template = template_file.read_text()
template = template.replace("@LDAP_CONNECTOR@", build_ldap_connector(env))


def repl(match):
    key = match.group(1)
    return env.get(key, "")


rendered = re.sub(r"\$\{([A-Za-z_][A-Za-z0-9_]*)\}", repl, template)
output_file.write_text(rendered)
