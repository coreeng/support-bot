#!/usr/bin/env python3
"""Dex LDAP auth code flow; POST /auth/oauth/exchange (single code use); verify API JWT teams + /auth/me."""
from __future__ import annotations

import base64
import json
import os
import re
import secrets
import sys
import threading
import time
from http.server import BaseHTTPRequestHandler, HTTPServer
from urllib.parse import parse_qs, urlencode, urlparse, urljoin

import requests

REDIRECT_URI = os.environ["REDIRECT_URI"]
CALLBACK_PORT = int(os.environ.get("CALLBACK_PORT", "8765"))
ISSUER = os.environ["DEX_ISSUER_URI"].rstrip("/")
API_BASE = os.environ["API_BASE_URL"].rstrip("/")
LDAP_EMAIL = os.environ["LDAP_USER_EMAIL"]
LDAP_PASSWORD = os.environ["LDAP_USER_PASSWORD"]
CLIENT_ID = os.environ["DEX_CLIENT_ID"]
EXPECTED_TEAM_CODE = os.environ.get("EXPECTED_TEAM_CODE", "core")
MAX_ATTEMPTS = int(os.environ.get("OIDC_LOGIN_ATTEMPTS", "3"))


def log(msg: str) -> None:
    print(msg, flush=True)


def b64url_json(segment: str) -> dict:
    pad = "=" * ((4 - len(segment) % 4) % 4)
    raw = base64.urlsafe_b64decode(segment + pad)
    return json.loads(raw)


class CallbackHandler(BaseHTTPRequestHandler):
    def do_GET(self) -> None:
        self.send_response(200)
        self.end_headers()
        self.wfile.write(b"OK")

    def log_message(self, fmt: str, *args) -> None:
        pass


def main() -> int:
    disc = requests.get(f"{ISSUER}/.well-known/openid-configuration", timeout=30)
    disc.raise_for_status()
    cfg = disc.json()
    auth_ep = cfg["authorization_endpoint"]

    state = secrets.token_urlsafe(16)
    auth_params = {
        "client_id": CLIENT_ID,
        "redirect_uri": REDIRECT_URI,
        "response_type": "code",
        "scope": "openid email profile groups",
        "state": state,
    }
    auth_url = f"{auth_ep}?{urlencode(auth_params)}"

    server = HTTPServer(("127.0.0.1", CALLBACK_PORT), CallbackHandler)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    time.sleep(0.4)

    try:
        s = requests.Session()
        r = s.get(auth_url, allow_redirects=True, timeout=120)
        r.raise_for_status()
        last = r

        for attempt in range(1, MAX_ATTEMPTS + 1):
            if last.url.startswith(f"http://127.0.0.1:{CALLBACK_PORT}"):
                break
            html = last.text
            m = re.search(r'<form[^>]*method\s*=\s*"post"[^>]*action\s*=\s*"([^"]*)"', html, re.I | re.S)
            if not m:
                m = re.search(r'<form[^>]*action\s*=\s*"([^"]*)"[^>]*method\s*=\s*"post"', html, re.I | re.S)
            if not m:
                m = re.search(r'<form[^>]*action\s*=\s*"([^"]*)"', html, re.I | re.S)
            if not m:
                log("No login form found; body snippet: " + html[:600])
                return 1
            action = m.group(1).replace("&amp;", "&")
            action_url = urljoin(last.url, action)
            payload = {"login": LDAP_EMAIL, "password": LDAP_PASSWORD}
            last = s.post(action_url, data=payload, allow_redirects=True, timeout=120)
            last.raise_for_status()
            if last.url.startswith(f"http://127.0.0.1:{CALLBACK_PORT}"):
                break
            log(f"LDAP login attempt {attempt} did not reach callback; url={last.url[:160]}")
            time.sleep(1.5)

        if not last.url.startswith(f"http://127.0.0.1:{CALLBACK_PORT}"):
            log("OAuth redirect did not reach callback: " + last.url[:200])
            return 1

        qs = parse_qs(urlparse(last.url).query)
        if qs.get("error"):
            log("OAuth error query: " + qs["error"][0])
            return 1
        code = (qs.get("code") or [None])[0]
        if not code:
            log("Missing code in callback URL")
            return 1
        got_state = (qs.get("state") or [None])[0]
        if got_state != state:
            log("State mismatch on callback")
            return 1

        ex = requests.post(
            f"{API_BASE}/auth/oauth/exchange",
            json={"provider": "dex", "code": code, "redirectUri": REDIRECT_URI},
            timeout=120,
        )
        if ex.status_code != 200:
            log(f"oauth/exchange failed: {ex.status_code} {ex.text[:400]}")
            return 1
        body = ex.json()
        api_jwt = body.get("token")
        if not api_jwt:
            log("No token in oauth/exchange response")
            return 1
        log("OK_TOKEN")

        parts = api_jwt.split(".")
        if len(parts) < 2:
            log("Invalid API JWT shape")
            return 1
        payload = b64url_json(parts[1])
        teams = payload.get("teams") or []
        team_codes: list[str] = []
        for t in teams:
            if isinstance(t, dict) and t.get("code") is not None:
                team_codes.append(str(t["code"]).lower())
        if EXPECTED_TEAM_CODE.lower() not in team_codes:
            log(f"API JWT teams missing {EXPECTED_TEAM_CODE!r}: {teams!r}")
            return 1
        log("OK_GROUPS")

        me = requests.get(
            f"{API_BASE}/auth/me",
            headers={"Authorization": f"Bearer {api_jwt}"},
            timeout=60,
        )
        if me.status_code != 200:
            log(f"/auth/me failed: {me.status_code}")
            return 1
        me_body = me.json()
        me_codes = [str(t.get("code", "")).lower() for t in me_body.get("teams", [])]
        if EXPECTED_TEAM_CODE.lower() not in me_codes:
            log(f"/auth/me teams missing {EXPECTED_TEAM_CODE!r}: {me_body!r}")
            return 1
        log("OK_API")
        log("OK_ALL")
        return 0
    finally:
        try:
            server.shutdown()
            server.server_close()
        except Exception:
            pass


if __name__ == "__main__":
    sys.exit(main())
