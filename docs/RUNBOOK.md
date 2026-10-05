# Troubleshooting Runbook

For each alert: what it means, how to confirm, how to fix. Run commands on the affected host.

## HIGH_CPU:<host>  (CPU above threshold)
**Confirm:** `top -o %CPU` or `ps aux --sort=-%cpu | head`
**Common causes:** runaway process, traffic spike, stuck loop, crypto-miner after a compromise.
**Fix:** identify the process; restart it (`systemctl restart <svc>`); if legitimate load, scale up or out. Check `journalctl -u <svc> --since "30 min ago"` for errors. Unknown process -> treat as a security incident.

## HIGH_MEMORY:<host>
**Confirm:** `free -h`, `ps aux --sort=-%mem | head`, `dmesg | grep -i "out of memory"`
**Common causes:** memory leak, JVM heap too large, too many workers.
**Fix:** restart the leaking service, tune limits (`-Xmx`, worker counts), add swap or RAM. Recurring leak -> take a heap dump (`jcmd <pid> GC.heap_dump`).

## HIGH_DISK:<host>
**Confirm:** `df -h`, then `du -xh / --max-depth=2 | sort -h | tail -20`
**Common causes:** unrotated logs, Docker images/volumes, old backups.
**Fix:** `journalctl --vacuum-size=200M`, `docker system prune` (review first!), configure logrotate, extend the volume. Also check inodes with `df -i`.

## HOST_DOWN:<host>  (no metrics received)
**Meaning:** agent stopped reporting for longer than `stale-host-seconds` (default 60).
**Confirm:** `ping <host>`, `ssh <host>`, then `systemctl status infra-agent` and `journalctl -u infra-agent -n 50`.
**Causes and fixes:**
- Host down -> check cloud console / reboot.
- Agent crashed -> `systemctl restart infra-agent`.
- Network or firewall -> verify the host can reach `http://<server>:8080/actuator/health` (`curl -v`).
- Wrong or missing API key -> agent logs show `HTTP 401`; fix `AGENT_API_KEY`.

## CHECK_DOWN:<check-name>  (PING / TCP / HTTP check failed)
**Confirm from the monitor host:**
- HTTP: `curl -i <url>`
- TCP: `nc -zv <host> <port>`
- PING: `ping -c 3 <host>`
**Causes:** service stopped, port blocked, DNS issue, application error (HTTP 5xx).
**Fix:** restart the service; check app logs; verify security groups / firewall rules.
**Auto-remediation:** if the check has a `remediation:` entry and remediation is enabled, the monitor already tried; read the *remediation note* on the alert (`GET /api/alerts?status=OPEN`). If it keeps failing after the cooldown (default 300 s), restarting is not the fix - investigate logs.

## Monitor itself is misbehaving
| Symptom | Check |
|---|---|
| No data in Grafana | `docker compose ps`; Grafana datasource test; `curl localhost:8080/api/hosts` |
| Server will not start | `docker compose logs server` - usually DB connection (`DB_URL`, password) |
| Alerts but no Slack message | `SLACK_WEBHOOK_URL` set? Look for "Slack webhook returned HTTP" in server logs |
| Email not sent | Needs `SPRING_MAIL_HOST` + credentials; log line "no SMTP server is configured" |
| Remediation did nothing | `MONITOR_REMEDIATION_ENABLED=true`? docker.sock mounted? Script executable? See alert's remediation note |
| Agents get HTTP 401 | `AGENT_API_KEY` must equal server `MONITOR_API_KEY` |
