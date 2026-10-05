# Infra Monitor - Automated Infrastructure Monitoring & Alerting

A small, production-style monitoring platform written in **Java 17 / Spring Boot**. Lightweight Java agents report CPU, memory, disk and uptime from each server. The server stores metrics in PostgreSQL, runs PING / TCP / HTTP health checks, raises and resolves alerts, notifies Slack or email, and can **automatically restart failed services**. Grafana dashboards are provisioned out of the box.

```
 +-------------+   POST /api/metrics    +--------------------------+      +------------+
 | Java agent  | ---------------------> |  Spring Boot server      | ---> | PostgreSQL |
 | (per server)|    (X-API-Key)         |  - ingest + validation   |      +-----+------+
 +-------------+                        |  - threshold alerting    |            |
                                        |  - PING/TCP/HTTP checks  |            v
 +-------------+  <-- health checks --  |  - stale-host detection  |      +------------+
 | Services    |                        |  - auto-remediation      |      |  Grafana   |
 | (web, db..) |  <-- restart script -- |  - retention purge       |      | dashboards |
 +-------------+                        +------------+-------------+      +------------+
                                                     |
                                                     v
                                              Slack / Email alerts
```

## Features

| Area | What it does |
|---|---|
| Metrics | Agent collects CPU %, memory % (uses `MemAvailable` on Linux), disk %, uptime; pushes every N seconds |
| Network checks | PING (ICMP/echo), TCP port, HTTP status + response time, configured in YAML |
| Alerting | Threshold alerts (CPU 85, memory 90, disk 85 by default), `HOST_DOWN` when an agent goes silent, `CHECK_DOWN` for failed checks. One open alert per condition, auto-resolved on recovery |
| Notifications | Slack incoming webhook and/or SMTP email, on open and on resolve |
| Auto-remediation | On a failed check, runs an allow-listed script (e.g. `restart-service.sh nginx`) with cooldown and strict input validation |
| Dashboards | Grafana: CPU/memory/disk trends, response times, open alerts, check status |
| Ops | Docker Compose, GitHub Actions CI, Actuator health, 14-day data retention, optional API key |

## Quick start (Docker)

Requires Docker with Compose v2.

```bash
git clone <your-repo-url> && cd infra-monitor
cp .env.example .env            # optional, set your own passwords
docker compose up --build
```

| Service | URL |
|---|---|
| Monitor API | http://localhost:8080/api/hosts |
| Grafana (admin / admin) | http://localhost:3000 -> dashboard "Infrastructure Monitor" |
| Demo web target | http://localhost:8081 |

Two demo agents (`app-server-1`, `app-server-2`) start automatically.

### Demo: failure detection and auto-remediation

```bash
# start with remediation enabled (mounts docker.sock - demo machines only!)
docker compose -f docker-compose.yml -f docker-compose.remediation.yml up --build -d

docker stop demo-web                                   # simulate an outage
python3 scripts/alert_report.py                         # within ~10-20s: CHECK_DOWN alert opens
docker ps                                               # demo-web has been restarted by the monitor
python3 scripts/alert_report.py                         # alert resolves on the next check
```

Without the remediation override the alert still opens, but nothing is restarted.

## Run locally without Docker

```bash
# 1. PostgreSQL
docker run -d --name pg -e POSTGRES_DB=monitor -e POSTGRES_USER=monitor -e POSTGRES_PASSWORD=monitor -p 5432:5432 postgres:16-alpine
# 2. Build and test everything (Java 17 + Maven)
mvn -B verify
# 3. Server
java -jar server/target/infra-monitor-server.jar
# 4. Agent (new terminal)
MONITOR_SERVER_URL=http://localhost:8080 java -jar agent/target/infra-monitor-agent.jar
```

## Monitoring real servers (e.g. AWS EC2)

1. Run the **server + Postgres + Grafana** on one EC2 instance (t3.small or free tier) with `docker compose up -d`. Open ports 8080 (agents) and 3000 (Grafana) in the security group, restricted to your IPs.
2. On every server you want to monitor, copy `agent/target/infra-monitor-agent.jar` and run it with a systemd unit:

```ini
# /etc/systemd/system/infra-agent.service
[Unit]
Description=Infra Monitor Agent
After=network-online.target

[Service]
Environment=MONITOR_SERVER_URL=http://<server-private-ip>:8080
Environment=AGENT_HOST_NAME=web-01
Environment=AGENT_API_KEY=<same value as MONITOR_API_KEY>
ExecStart=/usr/bin/java -jar /opt/infra-agent/infra-monitor-agent.jar
Restart=always

[Install]
WantedBy=multi-user.target
```

3. **Always set `MONITOR_API_KEY`** when the server is reachable from anywhere other than localhost.
4. Add your own checks in `server/src/main/resources/application-docker.yml` (or override via environment).

## Configuration

Set through environment variables (see `.env.example`) or `application.yml`.

| Variable | Default | Purpose |
|---|---|---|
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | local Postgres | Database |
| `MONITOR_API_KEY` | empty (open) | Shared secret for `/api/**`, sent as `X-API-Key` |
| `THRESHOLD_CPU` / `_MEMORY` / `_DISK` | 85 / 90 / 85 | Alert thresholds (%) |
| `SLACK_WEBHOOK_URL` | empty | Slack incoming webhook |
| `ALERT_EMAIL_TO`, `ALERT_EMAIL_FROM` | empty | Email recipients (comma separated). Also set `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD` |
| `MONITOR_REMEDIATION_ENABLED` | false | Allow remediation scripts to run |
| Agent: `MONITOR_SERVER_URL`, `AGENT_HOST_NAME`, `AGENT_INTERVAL_SECONDS`, `AGENT_API_KEY`, `AGENT_DISK_PATH` | see code | Agent settings |

Check definition example:

```yaml
monitor:
  checks:
    - name: shop-site
      type: HTTP
      url: https://example.com/health
      remediation: restart-service.sh nginx     # optional
    - name: db
      type: TCP
      host: 10.0.1.20
      port: 5432
```

## REST API

| Method | Path | Description |
|---|---|---|
| POST | `/api/metrics` | Agent ingest (validated JSON) -> 202 |
| GET | `/api/metrics?host=&limit=` | Recent metrics, newest first (max 1000) |
| GET | `/api/hosts` | Latest metric per host |
| GET | `/api/checks` | Latest result of each configured check |
| GET | `/api/alerts?status=OPEN` | Alerts (`OPEN` / `RESOLVED` / all) |
| POST | `/api/alerts/{id}/resolve` | Manually resolve an alert |
| GET | `/actuator/health` | Health probe |

## Project layout

```
agent/      zero-dependency Java agent (java.net.http + JMX + /proc)
server/     Spring Boot app: model, repo, service, web, tests
scripts/    restart-service.sh (remediation), alert_report.py (CLI report)
grafana/    provisioned datasource + dashboard JSON
docs/       RUNBOOK.md - troubleshooting guide per alert type
.github/    CI: build, test, docker build, compose validation
```

## Security design notes

- Remediation commands come **only from config**, never from API input; scripts must live inside `scripts-dir`; no shell is used; tokens are matched against `[A-Za-z0-9_.@-]+`; a cooldown prevents restart loops. Covered by unit tests.
- API key comparison is constant-time. Use HTTPS (reverse proxy such as Nginx/Caddy or an AWS ALB) in front of the server for anything beyond a private network.
- The docker.sock mount in `docker-compose.remediation.yml` is powerful; it is opt-in by design.

## Known limitations / next steps

- Schema is created by Hibernate `ddl-auto=update`; adopt Flyway for production migrations.
- Single server instance; for HA, move the scheduled jobs behind a lock (ShedLock).
- Agent reports whole-host disk for one path; add per-mount reporting if needed.
- Possible extensions: Prometheus endpoint, per-host thresholds, alert escalation, Terraform for the EC2 setup.

## Resume summary

**Automated Infrastructure Monitoring & Alerting System** - *Java 17, Spring Boot, PostgreSQL, Docker, GitHub Actions, Grafana, Bash, Python*

- Built a Java agent + Spring Boot platform tracking CPU, memory, disk, uptime and PING/TCP/HTTP health across multiple Linux servers, with PostgreSQL storage and a validated REST API.
- Implemented threshold and stale-host alerting with de-duplication and auto-resolve, plus Slack/email notifications.
- Designed secure auto-remediation (allow-listed Bash scripts, no shell, cooldowns) that restarts failed services without manual intervention.
- Containerized with Docker Compose, added GitHub Actions CI, provisioned Grafana dashboards, and documented a troubleshooting runbook.
