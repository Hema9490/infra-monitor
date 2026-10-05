#!/usr/bin/env bash
# Restart a service or container by name. Called by the monitor's auto-remediation.
# Usage: restart-service.sh <name>
set -euo pipefail

name="${1:?usage: restart-service.sh <service-or-container-name>}"

if [[ -S /var/run/docker.sock ]]; then
  # Docker Engine API over the socket - no docker CLI needed inside the container.
  curl -fsS --unix-socket /var/run/docker.sock -X POST "http://localhost/containers/${name}/restart?t=10"
  echo "restarted container '${name}' via Docker API"
elif command -v systemctl >/dev/null 2>&1; then
  systemctl restart "${name}"
  echo "restarted systemd service '${name}'"
else
  echo "no supported service manager found (need docker.sock or systemctl)" >&2
  exit 2
fi
