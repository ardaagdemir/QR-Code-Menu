#!/bin/sh
# Archives MEDIA_DIR (media_data volume, mounted read-only) to BACKUP_DIR as a
# timestamped gzip tarball, then deletes archives older than BACKUP_RETENTION_DAYS.
# Written to a .tmp path first and atomically renamed, so a crash mid-tar never leaves a
# truncated file with a "real" name - same discipline as ../postgres-backup/backup.sh.
# Archive members are relative to MEDIA_DIR's contents (product-images/, receipts/), not
# wrapped in a leading directory, so restore-media.sh can extract straight into the
# volume root.
set -eu

: "${MEDIA_DIR:=/media}"
: "${BACKUP_DIR:=/backups}"
: "${BACKUP_RETENTION_DAYS:=7}"

TIMESTAMP="$(date -u +%Y%m%dT%H%M%SZ)"
OUT_FILE="${BACKUP_DIR}/media_${TIMESTAMP}.tar.gz"
TMP_FILE="${OUT_FILE}.tmp"

echo "[media-backup] $(date -u +%Y-%m-%dT%H:%M:%SZ) starting archive -> ${OUT_FILE}"
tar czf "${TMP_FILE}" -C "${MEDIA_DIR}" .
mv "${TMP_FILE}" "${OUT_FILE}"
echo "[media-backup] archive complete: ${OUT_FILE} ($(du -h "${OUT_FILE}" | cut -f1))"

DELETED="$(find "${BACKUP_DIR}" -maxdepth 1 -name "media_*.tar.gz" -type f -mtime "+${BACKUP_RETENTION_DAYS}" -print -delete)"
if [ -n "${DELETED}" ]; then
  echo "[media-backup] retention (${BACKUP_RETENTION_DAYS}d) deleted:"
  echo "${DELETED}"
fi
