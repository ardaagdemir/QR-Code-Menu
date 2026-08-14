#!/bin/sh
# Runs backup.sh once immediately, then every BACKUP_INTERVAL_SECONDS. A plain sleep
# loop rather than cron: this container's only job is periodic pg_dump, and busybox
# crond does not inherit the environment docker-compose injects without extra
# plumbing - not worth it for a single scheduled job.
set -eu

: "${BACKUP_INTERVAL_SECONDS:=86400}"
: "${BACKUP_DIR:=/backups}"

mkdir -p "${BACKUP_DIR}"

echo "[postgres-backup] interval=${BACKUP_INTERVAL_SECONDS}s retention=${BACKUP_RETENTION_DAYS:-7}d dir=${BACKUP_DIR}"

while true; do
  /usr/local/bin/backup.sh || echo "[postgres-backup] backup.sh failed at $(date -u +%Y-%m-%dT%H:%M:%SZ)" >&2
  sleep "${BACKUP_INTERVAL_SECONDS}"
done
