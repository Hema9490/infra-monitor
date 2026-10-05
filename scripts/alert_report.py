#!/usr/bin/env python3
"""Print a plain-text report of open alerts and host status from the monitor API.

Usage:
    python3 scripts/alert_report.py --url http://localhost:8080 [--api-key KEY]
"""
import argparse
import json
import sys
import urllib.request


def get(url, api_key):
    req = urllib.request.Request(url)
    if api_key:
        req.add_header("X-API-Key", api_key)
    with urllib.request.urlopen(req, timeout=10) as resp:
        return json.load(resp)


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--url", default="http://localhost:8080")
    ap.add_argument("--api-key", default="")
    args = ap.parse_args()
    base = args.url.rstrip("/")

    try:
        hosts = get(f"{base}/api/hosts", args.api_key)
        checks = get(f"{base}/api/checks", args.api_key)
        alerts = get(f"{base}/api/alerts?status=OPEN", args.api_key)
    except Exception as exc:  # network / auth errors
        print(f"Could not reach monitor at {base}: {exc}", file=sys.stderr)
        return 2

    print("== HOSTS ==")
    for h in hosts:
        print(f"{h['host']:<20} cpu={h['cpuPercent']:5.1f}%  mem={h['memoryPercent']:5.1f}%  "
              f"disk={h['diskPercent']:5.1f}%  last_seen={h['collectedAt']}")
    print("\n== CHECKS ==")
    for c in checks:
        state = "UP  " if c["up"] else "DOWN"
        print(f"{state} {c['name']:<20} {c['type']:<5} {c['target']}  {c['responseMs']}ms  {c['message']}")
    print(f"\n== OPEN ALERTS ({len(alerts)}) ==")
    for a in alerts:
        print(f"[{a['severity']}] {a['createdAt']}  {a['message']}")
        if a.get("remediationNote"):
            print(f"    remediation: {a['remediationNote']}")
    return 1 if alerts else 0


if __name__ == "__main__":
    sys.exit(main())
