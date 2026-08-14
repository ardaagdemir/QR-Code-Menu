#!/bin/sh
# Dumps POSTGRES_DB to BACKUP_DIR as a timestamped gzip file, then deletes dumps
# older than BACKUP_RETENTION_DAYS. Written to a .tmp path first and atomically
# renamed, so a crash mid-dump never leaves a truncated file with a "real" name.
set -eu

: "${POSTGRES_HOST:?}" "${POSTGRES_PORT:?}" "${POSTGRES_DB:?}" "${POSTGRES_USER:?}" "${POSTGRES_PASSWORD:?}"
: "${BACKUP_DIR:=/backups}"
: "${BACKUP_RETENTION_DAYS:=7}"

TIMESTAMP="$(date -u +%Y%m%dT%H%M%SZ)"
OUT_FILE="${BACKUP_DIR}/${POSTGRES_DB}_${TIMESTAMP}.sql.gz"
TMP_FILE="${OUT_FILE}.tmp"

export PGPASSWORD="${POSTGRES_PASSWORD}"

echo "[postgres-backup] $(date -u +%Y-%m-%dT%H:%M:%SZ) starting dump -> ${OUT_FILE}"
pg_dump -h "${POSTGRES_HOST}" -p "${POSTGRES_PORT}" -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" \
  --format=plain --no-owner --no-privileges | gzip > "${TMP_FILE}"
mv "${TMP_FILE}" "${OUT_FILE}"
echo "[postgres-backup] dump complete: ${OUT_FILE} ($(du -h "${OUT_FILE}" | cut -f1))"

DELETED="$(find "${BACKUP_DIR}" -maxdepth 1 -name "${POSTGRES_DB}_*.sql.gz" -type f -mtime "+${BACKUP_RETENTION_DAYS}" -print -delete)"
if [ -n "${DELETED}" ]; then
  echo "[postgres-backup] retention (${BACKUP_RETENTION_DAYS}d) deleted:"
  echo "${DELETED}"
fi
