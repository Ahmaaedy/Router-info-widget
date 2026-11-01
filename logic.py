import argparse
import getpass
import hashlib
import json
import os
import sys

import requests

GET_URL = "/goform/goform_get_cmd_process"
SET_URL = "/goform/goform_set_cmd_process"


def get_cmd(session: requests.Session, base: str, cmd: str) -> str:
    r = session.post(base + GET_URL, data={"cmd": cmd}, timeout=10)
    r.raise_for_status()
    return str(r.json().get(cmd, ""))


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--ip", default="192.168.0.1")
    ap.add_argument("--json", action="store_true", help="print JSON output")
    args = ap.parse_args()

    password = os.environ.get("ROUTER_PASSWORD") or getpass.getpass("Router password: ")
    base = f"http://{args.ip}"

    s = requests.Session()
    s.headers.update({"X-Requested-With": "XMLHttpRequest", "Referer": base + "/index.html"})

    try:
        # Fresh LD token each login (single-use). Hash = SHA256(SHA256(pw) + LD)
        ld = get_cmd(s, base, "LD")
        if not ld:
            raise RuntimeError("Could not get LD token from router")
        inner_hash = hashlib.sha256(password.encode()).hexdigest().upper()
        hashed = hashlib.sha256((inner_hash + ld).encode()).hexdigest().upper()
        r = s.post(
            base + SET_URL,
            data={"isTest": "false", "goformId": "LOGIN", "password": hashed},
            timeout=10,
        )
        r.raise_for_status()
        result = str(r.json().get("result"))
        if result not in ("0", "4"):
            errors = {"1": "login failed", "2": "duplicate user", "3": "bad password", "5": "locked"}
            raise RuntimeError(f"Login failed: {errors.get(result, result)}")
        pct = int(get_cmd(s, base, "battery_value") or 0)
        status = get_cmd(s, base, "battery_charging")
    except (requests.RequestException, RuntimeError, ValueError) as e:
        print(f"Error: {e}", file=sys.stderr)
        sys.exit(1)

    if status == "2":  # full / charge complete
        bars = 4
    elif pct <= 5:
        bars = 0
    elif pct <= 25:
        bars = 1
    elif pct <= 50:
        bars = 2
    elif pct <= 90:
        bars = 3
    else:
        bars = 4

    state = {"0": "on battery", "2": "full"}.get(status, "charging")

    if args.json:
        print(json.dumps({"percent": pct, "bars": bars, "max_bars": 4, "state": state}))
    else:
        print(f"Battery: {bars} of 4 bars (~{pct}%), {state}")
    sys.exit(0)
