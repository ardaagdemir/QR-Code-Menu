#!/bin/sh
# Runs backup.sh once immediately, then every BACKUP_INTERVAL_SECONDS - same reasoning
# as ../postgres-backup/entrypoint.sh (plain sleep loop, not cron).
set -eu

: "${BACKUP_INTERVAL_SECONDS:=86400}"
: "${BACKUP_DIR:=/backups}"

mkdir -p "${BACKUP_DIR}"

echo "[media-backup] interval=${BACKUP_INTERVAL_SECONDS}s retention=${BACKUP_RETENTION_DAYS:-7}d dir=${BACKUP_DIR}"

while true; do
  /usr/local/bin/backup.sh || echo "[media-backup] backup.sh failed at $(date -u +%Y-%m-%dT%H:%M:%SZ)" >&2
  sleep "${BACKUP_INTERVAL_SECONDS}"
done
