from pathlib import Path
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

template = template_file.read_text()


def repl(match):
    key = match.group(1)
    return env.get(key, "")


rendered = re.sub(r"\$\{([A-Za-z_][A-Za-z0-9_]*)\}", repl, template)
output_file.write_text(rendered)
