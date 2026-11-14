#!/usr/bin/env python3
"""Obtain a fresh QA session, retrying only transient auth failures.
Never log credentials, response bodies, tokens, or user identifiers.
"""
import json
import os
import re
import sys
import time
import urllib.error
import urllib.request

URL = "https://okegwojigrtxadvgxpwm.supabase.co/auth/v1/token?grant_type=password"
RETRYABLE = {408, 429, 500, 502, 503, 504}
DELAYS = (0, 3, 8, 15, 30)


def login():
    body = json.dumps({
        "email": os.environ["SERA_E2E_OWNER_EMAIL"].strip(),
        "password": os.environ["SERA_E2E_OWNER_PASSWORD"],
    }).encode()
    headers = {"apikey": os.environ["SERA_SUPABASE_ANON_KEY"].strip(),
               "Content-Type": "application/json"}
    for attempt, delay in enumerate(DELAYS, 1):
        if delay:
            time.sleep(delay)
        request = urllib.request.Request(URL, data=body, headers=headers)
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                payload = json.load(response)
                refresh = payload.get("refresh_token", "")
                access = payload.get("access_token", "")
                if not isinstance(refresh, str) or not re.fullmatch(r"[A-Za-z0-9_.-]+", refresh) or not access:
                    raise SystemExit("QA authentication returned an invalid session.")
                return refresh, access
        except urllib.error.HTTPError as exc:
            code = exc.code
            if code not in RETRYABLE:
                raise SystemExit(f"QA authentication rejected (HTTP {code}); check QA account and configured secrets.")
            print(f"QA authentication temporarily unavailable (HTTP {code}); attempt {attempt}/{len(DELAYS)}.", file=sys.stderr)
        except (urllib.error.URLError, TimeoutError, OSError):
            print(f"QA authentication transport unavailable; attempt {attempt}/{len(DELAYS)}.", file=sys.stderr)
    raise SystemExit("QA authentication unavailable after bounded retries; release blocked.")


def main():
    try:
        refresh, access = login()
    except KeyError:
        raise SystemExit("Required QA authentication secret is missing.")
    if "--preflight" in sys.argv:
        # Verify the account can actually see a published post without printing data.
        url = "https://okegwojigrtxadvgxpwm.supabase.co/rest/v1/posts?select=id&status=eq.published&limit=1"
        req = urllib.request.Request(url, headers={"apikey": os.environ["SERA_SUPABASE_ANON_KEY"].strip(),
                                    "Authorization": "Bearer " + access, "Accept": "application/json"})
        for attempt, delay in enumerate(DELAYS, 1):
            if delay:
                time.sleep(delay)
            try:
                with urllib.request.urlopen(req, timeout=30) as response:
                    rows = json.load(response)
                if not isinstance(rows, list) or not rows or not rows[0].get("id"):
                    raise SystemExit("QA account has no visible published posts; release blocked.")
                print("QA authentication and live published-post access verified.")
                return
            except urllib.error.HTTPError as exc:
                if exc.code not in RETRYABLE:
                    raise SystemExit(f"QA live feed rejected (HTTP {exc.code}); release blocked.")
                print(f"QA feed temporarily unavailable (HTTP {exc.code}); attempt {attempt}/{len(DELAYS)}.", file=sys.stderr)
            except (urllib.error.URLError, TimeoutError, OSError):
                print(f"QA feed transport unavailable; attempt {attempt}/{len(DELAYS)}.", file=sys.stderr)
        raise SystemExit("QA live feed unavailable after bounded retries; release blocked.")
    # Caller captures and masks this token; never print it in preflight.
    print(refresh)


if __name__ == "__main__":
    main()
